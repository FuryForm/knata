package com.furyform.knata.functions

import com.furyform.knata.JSONataException
import com.furyform.knata.evaluator.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * JSONata encoding/URL standard library functions.
 * Pure Kotlin implementation — no java.* imports, compatible with all KMP targets.
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

    @OptIn(ExperimentalEncodingApi::class)
    private fun fnBase64Encode(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return Base64.encode(s.encodeToByteArray())
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun fnBase64Decode(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return try {
            Base64.decode(s).decodeToString()
        } catch (_: IllegalArgumentException) {
            throw JSONataException.RuntimeError("Invalid base64 string", "D3001")
        }
    }

    private fun fnEncodeUrl(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return encodeUri(s)
    }

    private fun fnDecodeUrl(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return percentDecode(s)
    }

    private fun fnEncodeUrlComponent(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        // RFC 3986 unreserved chars — encode everything else
        val safe = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
        return buildString {
            for (ch in s) {
                if (ch in safe) append(ch)
                else for (byte in ch.toString().encodeToByteArray())
                    append("%${(byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')}")
            }
        }
    }

    private fun fnDecodeUrlComponent(args: List<Any?>): Any? {
        val s = (args.getOrNull(0) as? String) ?: return null
        return percentDecode(s)
    }
}

/** Encode a full URI — preserves URI-reserved delimiters, encodes everything else over UTF-8 bytes. */
private fun encodeUri(uri: String): String {
    val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'();/?:@&=+\$,#%"
    return buildString {
        for (ch in uri) {
            if (ch in allowed) append(ch)
            else for (byte in ch.toString().encodeToByteArray())
                append("%${(byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')}")
        }
    }
}

/** Percent-decode a string, interpreting `%XX` sequences as UTF-8 byte runs. */
private fun percentDecode(s: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < s.length) {
        if (s[i] == '%' && i + 2 < s.length) {
            val value = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (value != null) { bytes.add(value.toByte()); i += 3; continue }
        }
        bytes.addAll(s[i].toString().encodeToByteArray().toList())
        i++
    }
    return bytes.toByteArray().decodeToString()
}
