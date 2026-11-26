@file:OptIn(ExperimentalMaterial3Api::class)

package de.refadim.rollladen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.round

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val s = vm.settings
    var latText by remember { mutableStateOf(if (s.latitude != 0.0) s.latitude.toString() else "") }
    var lonText by remember { mutableStateOf(if (s.longitude != 0.0) s.longitude.toString() else "") }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    fun applyLocation(loc: Location?) {
        if (loc == null) {
            locationMessage = "Standort konnte nicht ermittelt werden – bitte Koordinaten manuell eingeben."
            return
        }
        val lat = round(loc.latitude * 10000) / 10000
        val lon = round(loc.longitude * 10000) / 10000
        latText = lat.toString()
        lonText = lon.toString()
        vm.updateSettings(vm.settings.copy(latitude = lat, longitude = lon, timeZone = ZoneId.systemDefault().id))
        locationMessage = "Standort übernommen."
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchLocation(context, ::applyLocation)
        else locationMessage = "Standortzugriff nicht erlaubt."
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Einstellungen") }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Standort für Sonnenauf-/-untergang", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    latText, { t ->
                        latText = t
                        t.replace(',', '.').toDoubleOrNull()?.let { vm.updateSettings(vm.settings.copy(latitude = it)) }
                    },
                    label = { Text("Breitengrad") }, placeholder = { Text("50.94") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    lonText, { t ->
                        lonText = t
                        t.replace(',', '.').toDoubleOrNull()?.let { vm.updateSettings(vm.settings.copy(longitude = it)) }
                    },
                    label = { Text("Längengrad") }, placeholder = { Text("6.96") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(onClick = {
                locationMessage = "Ermittle Standort …"
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) fetchLocation(context, ::applyLocation)
                else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) {
                Icon(Icons.Filled.LocationOn, null)
                Spacer(Modifier.width(8.dp))
                Text("Aktuellen Standort übernehmen")
            }
            locationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            SunCalculator.today(s)?.let { (rise, set) ->
                val f = DateTimeFormatter.ofPattern("HH:mm")
                Text("Heute: Sonnenaufgang ${rise.format(f)} Uhr, Sonnenuntergang ${set.format(f)} Uhr",
                    style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()
            Text("Zeitzone: ${s.timeZone}")
            TextButton(onClick = { vm.updateSettings(s.copy(timeZone = ZoneId.systemDefault().id)) }) {
                Text("Zeitzone dieses Geräts übernehmen")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Standort & Zeitzone an Shellys senden", Modifier.weight(1f))
                Switch(checked = s.pushLocation, onCheckedChange = { vm.updateSettings(s.copy(pushLocation = it)) })
            }
            Text(
                "Die Shellys berechnen Sonnenauf- und -untergang selbst. Dafür brauchen sie Standort und Zeitzone – " +
                    "beides wird beim Übertragen des Wochenplans gesetzt.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text("So funktioniert’s", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "• Die App steuert die Shellys direkt im WLAN über deren lokale Schnittstelle – ohne Cloud.\n" +
                    "• Der Wochenplan wird als Zeitplan auf jedem Shelly gespeichert und dort ausgeführt.\n" +
                    "• Von unterwegs funktioniert die Steuerung nur per VPN ins Heimnetz (z. B. FRITZ!Box-VPN).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchLocation(context: Context, onResult: (Location?) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val last = lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (last != null) {
        onResult(last)
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER
        else LocationManager.GPS_PROVIDER
        lm.getCurrentLocation(provider, null, context.mainExecutor) { onResult(it) }
    } else {
        onResult(null)
    }
}
