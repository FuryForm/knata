package com.furyform.knata.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.furyform.knata.sample.ui.ExamplesTab
import com.furyform.knata.sample.ui.KnataTheme
import com.furyform.knata.sample.ui.PlaygroundTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KnataTheme {
                KnataSampleApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnataSampleApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    label = { Text("Examples") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    label = { Text("Playground") },
                )
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        when (selectedTab) {
            0 -> ExamplesTab(contentModifier)
            1 -> PlaygroundTab(contentModifier)
        }
    }
}
