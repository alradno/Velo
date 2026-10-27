package com.alberto.velo

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun SettingsScreen(activity: MainActivity) {
    val state = activity.state; val settings = state.settings; val p = state.profile
    var profiles by remember { mutableStateOf(false) }; var pin by remember { mutableStateOf(false) }; var backups by remember { mutableStateOf(false) }; var restore by remember { mutableStateOf(false) }; var removePin by remember { mutableStateOf(false) }; var region by remember { mutableStateOf(settings.region) }
    activity.statusRevision
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Justo como te gusta", "Hazlo tuyo.", "Permisos claros, perfiles personales y copias que tú controlas.") }
        item { Section("Conexión con Android") }
        item { Panel {
            val readiness=activity.setup.readiness
            Text(if(readiness.pending.isEmpty()) "Permisos y ajustes comprobados" else "${readiness.pending.size} ajustes pendientes",fontWeight=FontWeight.Medium)
            Text("Filtro, contactos, avisos, batería, segundo plano y conservación de permisos. Se comprueban cada vez que abres Velo.",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            VTonalButton(activity.setup::show,Modifier.fillMaxWidth()){Text("Revisar permisos y protección")}
            VTextButton(activity::openSystem) { Text("Ajustes de Velo en Android") }
            VTextButton({ activity.startActivity(Intent(SystemSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }) { Text("Cambiar las apps predeterminadas") }
        } }
        item { Section("Perfiles") }
        item { Panel { Text("Activo · ${p.name}", fontWeight = FontWeight.Medium); Text("Cada perfil tiene sus filtros, método, repetición y horarios. Puedes aplicar una regla a todos los perfiles o elegir algunos.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); VTonalButton({ profiles = true }, Modifier.fillMaxWidth()) { Text("Gestionar perfiles") } } }
        item { Section("Aspecto y movimiento") }
        item { Panel {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("dark" to "Oscuro", "light" to "Claro", "system" to "Sistema").forEach { (id, name) -> VChip(settings.theme == id, { activity.save(state.copy(settings = settings.copy(theme = id))) }, { Text(name) }) } }
            Text("Tamaño de texto · ${(settings.fontScale * 100).toInt()}%", fontSize = 12.sp)
            VSlider(settings.fontScale, { activity.state = state.copy(settings = settings.copy(fontScale = it)) }, onValueChangeFinished = { activity.save(activity.state) }, valueRange = .9f..1.3f, steps = 7)
            Toggle("Animaciones suaves", "Transiciones y movimiento de la protección", settings.animations, { activity.save(state.copy(settings = settings.copy(animations = it))) })
        } }
        item { Section("Registro y avisos") }
        item { Panel {
            Toggle("Notificar llamadas filtradas", "Puedes desactivar el aviso de Velo", settings.notifications, { activity.save(state.copy(settings = settings.copy(notifications = it))); if (it) activity.requestNotifications() })
            Toggle("Guardar llamadas permitidas", "Se registran solo las llamadas que recibe Velo", settings.logAllowed, { activity.save(state.copy(settings = settings.copy(logAllowed = it))) })
            Toggle("Ocultar aviso de bloqueo del sistema", "Android puede conservar su propio historial", settings.skipSystemNotification, { activity.save(state.copy(settings = settings.copy(skipSystemNotification = it))) })
            Text(if (settings.retentionDays == 0) "Conservar historial · Sin límite" else "Conservar historial · ${settings.retentionDays} días", fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0, 7, 30, 90, 365).forEach { days -> VChip(settings.retentionDays == days, { activity.save(state.copy(settings = settings.copy(retentionDays = days))) }, { Text(if (days == 0) "Sin límite" else "$days días", fontSize = 11.sp) }) } }
            Info(if (settings.retentionDays == 0) "No se borra por antigüedad ni por cantidad. Se conserva en este móvil hasta que lo borres o elimines los datos de Velo." else "Los registros anteriores al plazo se eliminan al guardar nuevas llamadas. Elige Sin límite para conservarlos todos.")
            VOutlinedButton(activity::exportHistory, Modifier.fillMaxWidth()) { Text("Exportar historial · CSV") }
        } }
        item { Section("Copias y portabilidad") }
        item { Panel {
            Toggle("Copia local diaria", "Android ejecuta la tarea de forma aproximada; no se sube a la nube", settings.dailyBackup, { activity.save(state.copy(settings = settings.copy(dailyBackup = it))) })
            Text("Historial de copias · ${settings.keepBackups}", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(1, 7, 14, 30).forEach { n -> VChip(settings.keepBackups == n, { activity.save(state.copy(settings = settings.copy(keepBackups = n))) }, { Text("$n") }) } }
            VTonalButton(activity::localBackup, Modifier.fillMaxWidth()) { Text("Crear copia local ahora") }
            VTextButton({ backups = true }) { Text("Ver y restaurar copias locales") }
            VOutlinedButton(activity::exportBackup, Modifier.fillMaxWidth()) { Text("Exportar copia completa · JSON") }
            VTextButton({ restore = true }) { Text("Restaurar copia desde un archivo") }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { VOutlinedButton(activity::importNumbers, Modifier.weight(1f)) { Text("Importar números", fontSize = 11.sp) }; VOutlinedButton(activity::exportNumbers, Modifier.weight(1f)) { Text("Exportar CSV", fontSize = 11.sp) } }
            Info("JSON incluye listas, carpetas, perfiles y ajustes. El PIN y el historial se quedan fuera: exporta el historial por separado en CSV. Las copias locales se borran al desinstalar.")
        } }
        item { Section("Privacidad") }
        item { Panel {
            Toggle("Proteger acceso con PIN", "Protege la apertura y oculta capturas de pantalla", activity.hasPin(), { if (it) pin = true else removePin = true })
            Text("Velo no solicita permiso de Internet. Tus contactos se consultan en el dispositivo y no se copian a un servidor.", fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VField(region, { region = it.uppercase().filter(Char::isLetter).take(2) }, labelText="País de los números locales · ES, FR, IT…", singleLine = true, modifier = Modifier.fillMaxWidth())
            VTextButton({
                val store = Store(activity)
                activity.save(state.copy(settings = settings.copy(region = region), rules = state.rules.map { it.copy(canonical = store.canonical(it.value, region)) }))
                activity.toast("País aplicado a las reglas")
            }, enabled = region.matches(Regex("[A-Z]{2}"))) { Text("Aplicar país") }
        } }
        item { Text("VELO ${BuildConfig.VERSION_NAME} · PERSONAL\nDiseñada para tu OPPO con Android 17.\nLas llamadas de WhatsApp y otras apps no se bloquean directamente. El filtro VoIP se refiere a llamadas telefónicas que llegan durante una conversación de voz.", fontSize = 10.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (profiles) ProfilesDialog(activity) { profiles = false }
    if (pin) PinDialog(activity) { pin = false }
    if (removePin) Confirm("Quitar protección de acceso", "Ya has desbloqueado Velo con tu PIN. La app podrá abrirse sin él.", { activity.removePin(); removePin = false }, { removePin = false })
    if (restore) Confirm("Restaurar una copia", "El archivo sustituirá listas, perfiles y ajustes actuales. Puedes exportar una copia antes de continuar.", { restore = false; activity.importBackup() }, { restore = false })
    if (backups) {
        var selected by remember { mutableStateOf<java.io.File?>(null) }
        val files = Backups.list(activity)
        VDialog(onDismissRequest = { backups = false }, title = { Text("Copias locales") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { if (files.isEmpty()) Text("Aún no hay copias locales."); files.forEach { f -> VOutlinedButton({ selected = f }, Modifier.fillMaxWidth()) { Text(Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm:ss")), fontSize = 12.sp) } } } }, confirmButton = { VTextButton({ backups = false }) { Text("Cerrar") } })
        selected?.let { file -> Confirm("Restaurar copia local", "Sustituirá tus reglas, perfiles y ajustes actuales.", { activity.restoreLocal(file); selected = null; backups = false }, { selected = null }) }
    }
}

@Composable private fun PermissionRow(title: String, enabled: Boolean, text: String, request: () -> Unit) { Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium); Text(if (enabled) "Concedido" else "Pendiente", color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, fontSize = 11.sp) }; Text(text, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp)); if (!enabled) VTextButton(request, contentPadding = PaddingValues(0.dp)) { Text("Configurar", fontSize = 12.sp) } } }

@Composable private fun PinDialog(activity: MainActivity, dismiss: () -> Unit) { var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    VDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Crea tu PIN") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Info("Usa de 6 a 12 dígitos. Si lo olvidas, no podrás abrir la configuración sin borrar los datos de Velo."); VField(pin, { pin = it.filter(Char::isDigit).take(12) }, labelText="PIN", visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)); VField(confirm, { confirm = it.filter(Char::isDigit).take(12) }, labelText="Repite el PIN", visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)) } }, confirmButton = { VButton({ busy = true; activity.setPin(pin) { busy = false; dismiss(); activity.statusRevision++ } }, enabled = !busy && pin.length >= 6 && pin == confirm) { Text(if (busy) "Guardando…" else "Guardar PIN") } }, dismissButton = { VTextButton(dismiss, enabled = !busy) { Text("Cancelar") } })
}

@Composable private fun ProfilesDialog(activity: MainActivity, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var edit by remember { mutableStateOf<Profile?>(null) }; var editedName by remember { mutableStateOf("") }
    VDialog(onDismissRequest = dismiss, title = { Text("Tus perfiles") }, text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        activity.state.profiles.forEach { p -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { VTextButton({ activity.save(activity.state.copy(settings = activity.state.settings.copy(selectedProfile = p.id))) }) { Text((if (activity.state.profile.id == p.id) "✓ " else "") + p.name) }; VIconButton({ edit = p; editedName = p.name }) { Icon(VeloIcons.Edit, "Editar perfil") } } }
        VField(name, { name = it.take(80) }, labelText="Nombre del nuevo perfil")
        VButton({ val s = activity.state; val clone = s.profile.copy(id = java.util.UUID.randomUUID().toString(), name = name.trim()); val rules = s.rules.map { if (s.profile.id in it.profiles) it.copy(profiles = it.profiles + clone.id) else it }; activity.save(s.copy(profiles = s.profiles + clone, rules = rules, settings = s.settings.copy(selectedProfile = clone.id))); name = "" }, enabled = name.isNotBlank() && activity.state.profiles.size < 50) { Text("Duplicar activo como nuevo") }
    } }, confirmButton = { VTextButton(dismiss) { Text("Listo") } })
    edit?.let { p -> VDialog(onDismissRequest = { edit = null }, title = { Text("Editar perfil") }, text = { Column { VField(editedName, { editedName = it.take(80) }, labelText="Nombre"); Info("Al eliminar un perfil, sus reglas exclusivas se conservan desactivadas.") } }, confirmButton = { VTextButton({ activity.profile(p.copy(name = editedName.trim())); edit = null }, enabled = editedName.isNotBlank()) { Text("Guardar") } }, dismissButton = { VTextButton({ val s = activity.state; val remaining = s.profiles.filterNot { it.id == p.id }; val rules = s.rules.map { r -> if (p.id !in r.profiles) r else if (r.profiles.size == 1) r.copy(profiles = emptySet(), enabled = false) else r.copy(profiles = r.profiles - p.id) }; activity.save(s.copy(profiles = remaining, rules = rules, settings = s.settings.copy(selectedProfile = if (s.profile.id == p.id) remaining.first().id else s.settings.selectedProfile))); edit = null }, enabled = activity.state.profiles.size > 1) { Text("Eliminar perfil") } }) }
}


