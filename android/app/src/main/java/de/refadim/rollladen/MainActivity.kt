@file:OptIn(ExperimentalMaterial3Api::class)

package de.refadim.rollladen

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root() }
    }
}

/** Wurzel: Ruhebildschirm oder Steuerung; jede Berührung setzt den Inaktivitäts-Timer zurück. */
@Composable
fun Root(vm: AppViewModel = viewModel()) {
    val dark = vm.display.darkMode
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF81C784))
    else lightColorScheme(primary = Color(0xFF2E7D32))
    MaterialTheme(colorScheme = scheme) { RootContent(vm, dark) }
}

@Composable
private fun RootContent(vm: AppViewModel, dark: Boolean) {
    val view = LocalView.current
    val keepOn = vm.display.keepScreenOn
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { }
    }

    val window = (LocalContext.current as? Activity)?.window
    LaunchedEffect(vm.idle, dark) {
        window?.let { w ->
            val c = WindowCompat.getInsetsController(w, w.decorView)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
            if (vm.idle) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                c.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    vm.touch()
                }
            }
        }
    ) {
        // Surface setzt die Schriftfarbe passend zu hell/dunkel für alle Seiten
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            if (vm.idle) IdleScreen(vm) else App(vm)
        }
    }
}

private sealed interface Detail {
    data class EditShutter(val id: String) : Detail
    data class EditRule(val id: String) : Detail
    data class EditActor(val id: String) : Detail
    data class EditSwitchRule(val id: String) : Detail
    data class EditInput(val id: String) : Detail
    data object Inputs : Detail
    data object Display : Detail
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Rollläden", Icons.Filled.Home),
    Tab("Wochenplan", Icons.Filled.DateRange),
    Tab("Schalter", Icons.Filled.Lightbulb),
    Tab("Einstellungen", Icons.Filled.Settings),
)

@Composable
fun App(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }

    BackHandler(enabled = detail != null) {
        detail = if (detail is Detail.EditInput) Detail.Inputs else null
    }

    when (val d = detail) {
        is Detail.EditShutter -> ShutterEditScreen(vm, d.id) { detail = null }
        is Detail.EditRule -> RuleEditScreen(vm, d.id) { detail = null }
        is Detail.EditActor -> ActorEditScreen(vm, d.id) { detail = null }
        is Detail.EditSwitchRule -> SwitchRuleEditScreen(vm, d.id) { detail = null }
        is Detail.EditInput -> InputEditScreen(vm, d.id) { detail = Detail.Inputs }
        Detail.Inputs -> InputsScreen(vm, onEdit = { detail = Detail.EditInput(it) }, onBack = { detail = null })
        Detail.Display -> DisplaySettingsScreen(vm) { detail = null }
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
                    if (vm.display.screensaverEnabled) {
                        NavigationBarItem(
                            selected = false,
                            onClick = { vm.sleep() },
                            icon = { Icon(Icons.Filled.Wallpaper, contentDescription = null) },
                            label = { Text("Ruhebild") },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
                when (tab) {
                    0 -> ShuttersScreen(vm) { detail = Detail.EditShutter(it) }
                    1 -> SchedulesScreen(vm) { detail = Detail.EditRule(it) }
                    2 -> SwitchesScreen(vm, onEditActor = { detail = Detail.EditActor(it) },
                        onEditRule = { detail = Detail.EditSwitchRule(it) })
                    else -> SettingsScreen(vm, onOpenDisplay = { detail = Detail.Display },
                        onOpenInputs = { detail = Detail.Inputs })
                }
            }
        }
    }
}
