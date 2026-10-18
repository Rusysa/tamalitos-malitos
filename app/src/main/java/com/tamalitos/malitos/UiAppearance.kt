package com.tamalitos.malitos

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

internal enum class AppearanceMode(val label: String, val detail: String) {
    SYSTEM("Seguir el sistema", "Cambia con la apariencia de tu dispositivo"),
    LIGHT("Modo claro", "Fondos crema y acentos verdes"),
    DARK("Modo oscuro", "Fondos oscuros y contraste suave")
}
internal object AppearancePreferences {
    fun read(context: Context): AppearanceMode {
        val name = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("mode", null)
        return AppearanceMode.entries.firstOrNull { it.name == name } ?: AppearanceMode.SYSTEM
    }
    fun write(context: Context, mode: AppearanceMode) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
    }
}

@Composable internal fun AppearanceDialog(mode: AppearanceMode, choose: (AppearanceMode) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Apariencia") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Elige cómo quieres ver tu negocio. Tu preferencia se guarda en este dispositivo.")
            AppearanceMode.entries.forEach { option ->
                Surface(shape = MaterialTheme.shapes.medium,
                    color = if (option == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().semantics { role = Role.RadioButton; selected = option == mode }
                        .clickable { choose(option) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (option) { AppearanceMode.SYSTEM -> Icons.Default.SettingsBrightness; AppearanceMode.LIGHT -> Icons.Default.LightMode; AppearanceMode.DARK -> Icons.Default.DarkMode }, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(option.label, style = MaterialTheme.typography.titleSmall); Text(option.detail, style = MaterialTheme.typography.bodySmall) }
                        RadioButton(selected = option == mode, onClick = null)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(dismiss) { Text("Listo") } })
}
