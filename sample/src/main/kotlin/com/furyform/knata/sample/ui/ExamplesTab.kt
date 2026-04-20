package com.furyform.knata.sample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.furyform.knata.Knata

private data class Example(
    val title: String,
    val description: String,
    val json: String,
    val expression: String,
)

private val EXAMPLES = listOf(
    Example(
        title = "Sum of prices",
        description = "Sum all product prices in nested orders",
        json = """
{
  "Account": {
    "Name": "Firefly",
    "Order": [
      {
        "OrderID": "order001",
        "Product": [
          { "ProductName": "Widget", "Price": 9.99, "Quantity": 3 },
          { "ProductName": "Gadget", "Price": 24.99, "Quantity": 1 }
        ]
      },
      {
        "OrderID": "order002",
        "Product": [
          { "ProductName": "Doohickey", "Price": 7.49, "Quantity": 5 }
        ]
      }
    ]
  }
}""".trim(),
        expression = "\$sum(Account.Order.Product.Price)",
    ),
    Example(
        title = "Filter products",
        description = "Find products with Price > 10",
        json = """
{
  "products": [
    { "name": "Apple",  "price": 1.2  },
    { "name": "Laptop", "price": 999  },
    { "name": "Pen",    "price": 0.5  },
    { "name": "Phone",  "price": 699  }
  ]
}""".trim(),
        expression = "products[price > 10].name",
    ),
    Example(
        title = "String transform",
        description = "Upper-case and join all names",
        json = """
{
  "people": [
    { "first": "Alice", "last": "Smith"  },
    { "first": "Bob",   "last": "Jones"  },
    { "first": "Carol", "last": "White"  }
  ]
}""".trim(),
        expression = "\$join(people.(\$uppercase(first) & \" \" & \$uppercase(last)), \", \")",
    ),
    Example(
        title = "Group by category",
        description = "Collect all names from array into one list",
        json = """
[
  { "name": "Banana",    "cat": "fruit"    },
  { "name": "Carrot",    "cat": "veggie"   },
  { "name": "Apple",     "cat": "fruit"    },
  { "name": "Broccoli",  "cat": "veggie"   }
]""".trim(),
        expression = "\$sort(name)",
    ),
    Example(
        title = "Map & compute",
        description = "Compute total cost (price × qty) per product",
        json = """
{
  "items": [
    { "name": "Pen",    "price": 1.5,  "qty": 10 },
    { "name": "Book",   "price": 12,   "qty": 2  },
    { "name": "Ruler",  "price": 2.25, "qty": 4  }
  ]
}""".trim(),
        expression = "items.{ \"name\": name, \"total\": \$string(\$round(price * qty, 2)) }",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamplesTab(modifier: Modifier = Modifier) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    val example = EXAMPLES[selectedIndex]

    fun runExample() {
        try {
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(example.json)
            val data = jsonElementToKotlin(parsed)
            val value = Knata.evaluate(example.expression, data)
            result = resultToString(value)
            isError = false
        } catch (e: Exception) {
            result = "Error: ${e.message}"
            isError = true
        }
        showResult = true
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Knata Examples") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Choose an example", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EXAMPLES.forEachIndexed { index, ex ->
                    FilterChip(
                        selected = selectedIndex == index,
                        onClick = {
                            selectedIndex = index
                            showResult = false
                            result = null
                        },
                        label = { Text(ex.title, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.defaultMinSize(minWidth = 96.dp),
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    example.description,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionLabel("Expression")
            CodeBox(example.expression)

            SectionLabel("Input JSON")
            CodeBox(example.json)

            Button(
                onClick = { runExample() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Run Expression")
            }

            AnimatedVisibility(
                visible = showResult && result != null,
                enter = fadeIn() + expandVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Result")
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isError)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            result ?: "",
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CodeBox(code: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                code,
                modifier = Modifier.padding(10.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
