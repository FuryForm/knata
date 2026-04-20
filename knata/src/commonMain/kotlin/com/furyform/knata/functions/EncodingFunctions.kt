package com.furyform.knata.functions

import com.furyform.knata.JSONataException
import com.furyform.knata.evaluator.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * JSONata encoding/URL standard library functions.
 */
object EncodingFunctions {
    fun register(env: Environment) {
        env.fn("base64encode")       { args, _, _ -> fnBase64Encode(args) }
        env.fn("base64decode")       { args, _, _ -> fnBase64Decode(args) }
        env.fn("encodeUrl")          { args, _, _ -> fnEncodeUrl(args) }
        env.fn("decodeUrl")          { args, _, _ -> fnDecodeUrl(args) }
        env.fn("encodeUrlComponent") { args, _, _ -> fnEncodeUrlComponent(args) }
        env.fn("decodeUrlComponent") { args, _, _ -> fnDecodeUrlComponent(args) }
    }

    // $base64encode(str)
    private fun fnBase64Encode(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return Base64.getEncoder().encodeToString(s.toByteArray(StandardCharsets.UTF_8))
    }

    // $base64decode(str)
    private fun fnBase64Decode(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return try {
            String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw JSONataException.RuntimeError("Invalid base64 string", "D3001")
        }
    }

    // $encodeUrl(str)
    private fun fnEncodeUrl(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        // encode everything except: A-Z a-z 0-9 - _ . ! ~ * ' ( ) ; / ? : @ & = + $ , # %
        return encodeUri(s)
    }

    // $decodeUrl(str)
    private fun fnDecodeUrl(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return URLDecoder.decode(s, "UTF-8")
    }

    // $encodeUrlComponent(str)
    private fun fnEncodeUrlComponent(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")
    }

    // $decodeUrlComponent(str)
    private fun fnDecodeUrlComponent(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    }
}

/** Encode a full URI — preserves URI delimiters, encodes non-ASCII/unsafe chars. */
private fun encodeUri(uri: String): String {
    val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'();/?:@&=+\$,#%"
    val sb = StringBuilder()
    for (byte in uri.toByteArray(StandardCharsets.UTF_8)) {
        val c = byte.toInt() and 0xFF
        if (c < 128 && uri[sb.length.coerceAtMost(uri.length - 1)].let { it in allowed }) {
            sb.append(c.toChar())
        } else {
            sb.append("%${c.toString(16).uppercase().padStart(2, '0')}")
        }
    }
    // Better: iterate char-by-char
    return buildString {
        for (ch in uri) {
            if (ch in allowed) append(ch)
            else {
                for (byte in ch.toString().toByteArray(StandardCharsets.UTF_8)) {
                    append("%${(byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')}")
                }
            }
        }
    }
}
