package com.furyform.knata.sample.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.furyform.knata.Knata

private const val DEFAULT_JSON = """{
  "example": [
    { "value": 4  },
    { "value": 7  },
    { "value": 13 }
  ]
}"""

private const val DEFAULT_EXPR = "\$sum(example.value)"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundTab(modifier: Modifier = Modifier) {
    var jsonText by remember { mutableStateOf(DEFAULT_JSON) }
    var exprText by remember { mutableStateOf(DEFAULT_EXPR) }
    var result by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var hasRun by remember { mutableStateOf(false) }

    fun evaluate() {
        try {
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(jsonText.trim())
            val data = jsonElementToKotlin(parsed)
            val value = Knata.evaluate(exprText.trim(), data)
            result = resultToString(value)
            isError = false
        } catch (e: Exception) {
            result = e.message ?: "Unknown error"
            isError = true
        }
        hasRun = true
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Knata Playground") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "JSON Input",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { jsonText = DEFAULT_JSON }) { Text("Reset") }
                IconButton(onClick = { jsonText = "" }) {
                    Icon(Icons.Default.Clear, "Clear JSON")
                }
            }
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 300.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                label = { Text("Paste or type JSON here") },
                singleLine = false,
                isError = jsonText.isNotBlank() && runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(jsonText)
                }.isFailure,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "JSONata Expression",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { exprText = DEFAULT_EXPR }) { Text("Reset") }
                IconButton(onClick = { exprText = "" }) {
                    Icon(Icons.Default.Clear, "Clear expression")
                }
            }
            OutlinedTextField(
                value = exprText,
                onValueChange = { exprText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                label = { Text("e.g. example.value") },
                singleLine = false,
            )

            Button(
                onClick = { evaluate() },
                modifier = Modifier.fillMaxWidth(),
                enabled = jsonText.isNotBlank() && exprText.isNotBlank(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Evaluate")
            }

            if (hasRun && result != null) {
                Text(
                    "Result",
                    style = MaterialTheme.typography.titleSmall,
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isError)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            result ?: "",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                if (isError) {
                    Text(
                        "Check the expression syntax or JSON format",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            HorizontalDivider()
            Text("Quick Reference", style = MaterialTheme.typography.labelLarge)
            QuickReferenceChips { exprText = it }
        }
    }
}

@Composable
private fun QuickReferenceChips(onPick: (String) -> Unit) {
    val snippets = listOf(
        Pair("Path",      "example.value"),
        Pair("Filter",    "example[value > 5]"),
        Pair("Map",       "example.value"),
        Pair("Sum",       "\$sum(example.value)"),
        Pair("Count",     "\$count(example)"),
        Pair("String fn", "\$string(\$sum(example.value))"),
        Pair("Ternary",   "example[value > 10] ? \"big\" : \"small\""),
        Pair("Coalesce",  "example.value[0] ?? 0"),
        Pair("Concat",    "\$join(example.value, \", \"")"),
        Pair("Sort",      "\$sort(example.value)"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        snippets.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (label, snippet) ->
                    SuggestionChip(
                        onClick = { onPick(snippet) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}
