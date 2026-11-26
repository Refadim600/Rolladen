@file:OptIn(ExperimentalMaterial3Api::class)

package de.refadim.rollladen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF81C784))
            else lightColorScheme(primary = Color(0xFF2E7D32))
            MaterialTheme(colorScheme = scheme) { App() }
        }
    }
}

private sealed interface Detail {
    data class EditShutter(val id: String) : Detail
    data class EditRule(val id: String) : Detail
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Rollläden", Icons.Filled.Home),
    Tab("Wochenplan", Icons.Filled.DateRange),
    Tab("Einstellungen", Icons.Filled.Settings),
)

@Composable
fun App(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }

    BackHandler(enabled = detail != null) { detail = null }

    when (val d = detail) {
        is Detail.EditShutter -> ShutterEditScreen(vm, d.id) { detail = null }
        is Detail.EditRule -> RuleEditScreen(vm, d.id) { detail = null }
        null -> Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
                when (tab) {
                    0 -> ShuttersScreen(vm) { detail = Detail.EditShutter(it) }
                    1 -> SchedulesScreen(vm) { detail = Detail.EditRule(it) }
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}
