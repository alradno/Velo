package com.alberto.velo

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings as AndroidSettings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    var state by mutableStateOf(State())
    var filteredToday by mutableLongStateOf(0L)
    var historyCount by mutableLongStateOf(0L)
    var unlocked by mutableStateOf(true)
    var statusRevision by mutableIntStateOf(0)
    var pickedRule by mutableStateOf<NumberRule?>(null)
    val setup = SetupController(this)
    private val io = Executors.newSingleThreadExecutor()
    private var refreshGeneration = 0
    private var exportText = ""
    private var importKind = "backup"
    private var contactAllow = false
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh(); setup.refresh() }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh(); setup.refresh() }
    private val exportJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> writeExport(uri) }
    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> writeExport(uri) }
    private val exportHistoryCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) io.execute {
            val result = runCatching { contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use(store::exportHistory) }
            runOnUiThread { toast(if (result.isSuccess) "Historial exportado" else "No se pudo exportar el historial") }
        }
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) io.execute {
        val result = runCatching {
            val text = contentResolver.openInputStream(uri)!!.use { stream ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = stream.read(buffer); if (count < 0) break; require(output.size() + count <= 2_000_000) { "El archivo supera 2 MB" }; output.write(buffer, 0, count) }
                output.toString("UTF-8")
            }
            if (importKind == "backup") { store.restore(text); "Copia restaurada" }
            else {
                val rules = NumberCsv.import(text) { store.canonical(it) }
                val current = store.load(); val merged = (current.rules + rules).distinctBy { listOf(it.value, it.match, it.allow, it.folder) }
                require(merged.size <= 5000)
                val folders = (current.folders + rules.map { Folder(it.folder) }).distinctBy { it.name }
                store.save(current.copy(rules = merged, folders = folders)); "${rules.size} reglas leídas"
            }
        }
        runOnUiThread { refresh(); toast(result.getOrElse { "No se importó: ${it.message}" }) }
    } }
    private val contact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri -> runCatching { contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) pickedRule = NumberRule(value = it.getString(0), canonical = store.canonical(it.getString(0)), note = it.getString(1).orEmpty(), allow = contactAllow) } }.onFailure { toast("No se pudo leer el contacto") } }
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); store = Store(this); refresh(); unlocked = !store.hasPin(); setContent { VeloTheme(state.settings) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { if (unlocked) { VeloAppScreen(this); if (setup.visible) SetupDialog(this) } else UnlockScreen(store) { unlocked = true } } } } }
    override fun onResume() { super.onResume(); if (::store.isInitialized) { refresh(); setup.onResume(); if (store.hasPin()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
    override fun onStop() { setup.onStop(isChangingConfigurations); if (::store.isInitialized && store.hasPin() && !isChangingConfigurations) unlocked = false; super.onStop() }
    fun refresh() {
        state = store.load(); val generation = ++refreshGeneration
        io.execute {
            val result = runCatching { store.eventCount() to store.filteredSince(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
            runOnUiThread { if (generation == refreshGeneration) {
                result.onSuccess { (count, today) -> historyCount = count; filteredToday = today; statusRevision++ }
                    .onFailure { toast("No se pudo leer el historial: ${it.message}") }
            } }
        }
    }
    fun historyPage(onlyFiltered: Boolean, search: String, start: Long, before: EventCursor? = null, limit: Int = 100, after: EventCursor? = null, signal: android.os.CancellationSignal? = null) = store.historyPage(onlyFiltered, search, start, before,limit,after,signal)
    fun statistics(start: Long, end: Long, signal: android.os.CancellationSignal? = null) = store.statistics(start, end, signal)
    fun save(s: State) { val oldDaily = store.load().settings.dailyBackup; val oldNotifications = state.settings.notifications; runCatching { store.save(s); state = s; if(oldNotifications != s.settings.notifications) setup.refresh(); if (oldDaily != s.settings.dailyBackup) Backups.schedule(this) }.onFailure { toast("No se guardó: ${it.message}") } }
    fun profile(p: Profile) = save(state.copy(profiles = state.profiles.map { if (it.id == p.id) p else it }))
    fun rule(r: NumberRule) { val s = state; if (s.rules.none { it.id == r.id } && s.rules.size >= 5000) { toast("Límite de 5.000 reglas"); return }; save(s.withRule(r.copy(canonical = store.canonical(r.value)))) }
    fun requestRole(role: String) { val manager = getSystemService(RoleManager::class.java); if (manager.isRoleAvailable(role)) roleRequest.launch(manager.createRequestRoleIntent(role)) else toast("Este sistema no ofrece ese rol") }
    fun requestContacts() = permissions.launch(arrayOf(Manifest.permission.READ_CONTACTS))
    fun requestNotifications() { if (Build.VERSION.SDK_INT >= 33) permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
    fun pickContact(allow: Boolean = false) { contactAllow = allow; contact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
    fun exportBackup() { exportText = store.export(); exportJson.launch("Velo-${LocalDate.now()}.json") }
    fun exportNumbers() { exportText = NumberCsv.export(state.rules); exportCsv.launch("Velo-numeros.csv") }
    fun exportHistory() { exportHistoryCsv.launch("Velo-historial-${LocalDate.now()}.csv") }
    fun importBackup() { importKind = "backup"; importFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
    fun importNumbers() { importKind = "numbers"; importFile.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }
    fun localBackup() { io.execute { val result = runCatching { Backups.create(this) }; runOnUiThread { statusRevision++; toast(if (result.isSuccess) "Copia local creada" else "No se pudo crear la copia") } } }
    fun restoreLocal(file: java.io.File) { io.execute { val result = runCatching { store.restore(file.readText()) }; runOnUiThread { refresh(); toast(if (result.isSuccess) "Copia restaurada" else "Copia inválida") } } }
    fun setPin(pin: String, done: () -> Unit) { io.execute { val result = runCatching { store.setPin(pin) }; runOnUiThread { if (result.isSuccess) { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE); done() } else toast(result.exceptionOrNull()?.message.orEmpty()) } } }
    fun removePin() { store.removePin(); window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE); statusRevision++ }
    fun hasPin() = store.hasPin()
    fun clearHistory() { io.execute { val result = runCatching { store.clearEvents() }; runOnUiThread { result.onSuccess { refresh() }.onFailure { toast("No se pudo vaciar el historial") } } } }
    fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    fun openSystem() { startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    private fun writeExport(uri: Uri?) { if (uri == null) return; val text = exportText; io.execute { val result = runCatching { contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) } }; runOnUiThread { toast(if (result.isSuccess) "Archivo guardado" else "No se pudo guardar") } } }
}

@Composable private fun UnlockScreen(store: Store, done: () -> Unit) {
    var pin by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var attempts by remember { mutableIntStateOf(0) }; var retryAfter by remember { mutableLongStateOf(0) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Icon(VeloIcons.Lock, null, Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp)); Text("Tu espacio,\ntus reglas.", fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
        Text("Introduce tu PIN para abrir Velo", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        VField(pin, { pin = it.filter(Char::isDigit).take(12) }, labelText="PIN", visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        Text(error, color = MaterialTheme.colorScheme.error)
        VButton(onClick = {
            if (System.currentTimeMillis() < retryAfter) { error = "Espera unos segundos antes de intentarlo"; return@VButton }
            busy = true
            scope.launchSafe { val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { store.verifyPin(pin) }; busy = false; if (ok) done() else { attempts++; if (attempts >= 5) retryAfter = System.currentTimeMillis() + 30_000; error = "PIN incorrecto"; pin = "" } }
        }, enabled = pin.length >= 6 && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Comprobando…" else "Desbloquear") }
    }
}
private fun kotlinx.coroutines.CoroutineScope.launchSafe(block: suspend () -> Unit) = launch { block() }

private val tabs = listOf("Inicio", "Reglas", "Horarios", "Actividad", "Ajustes")
private val tabIcons = listOf(VeloIcons.Shield, VeloIcons.Tune, VeloIcons.Schedule, VeloIcons.History, VeloIcons.Settings)

@OptIn(ExperimentalAnimationApi::class)
@Composable private fun VeloAppScreen(activity: MainActivity) {
    var tab by remember { mutableIntStateOf(0) }; var simulator by remember { mutableStateOf(false) }; var edit by remember { mutableStateOf<NumberRule?>(null) }; var showStats by remember { mutableStateOf(false) }
    val state = activity.state
    LaunchedEffect(activity.pickedRule) { activity.pickedRule?.let { edit = it; activity.pickedRule = null; tab = 1 } }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().systemBarsPadding().then(if(edit != null) Modifier.clearAndSetSemantics {} else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(VeloIcons.Shield, null, Modifier.size(25.dp), colors.primary); Spacer(Modifier.width(8.dp)); Text("velo", fontWeight = FontWeight.Bold, fontSize = 29.sp, letterSpacing = (-1).sp)
            Spacer(Modifier.weight(1f)); Surface(color = colors.surfaceVariant, shape = CircleShape) { Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(VeloIcons.Lock, null, Modifier.size(12.dp), colors.primary); Spacer(Modifier.width(5.dp)); Text("SOLO EN TU MÓVIL", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp) } }
        }
        SetupBanner(activity)
        val content: @Composable (Int) -> Unit = { selected -> when (selected) {
            0 -> Home(activity, { tab = 4 }, { tab = 1 }, { simulator = true }, { showStats = true; tab = 3 })
            1 -> RulesScreen(activity, { edit = it }, { edit = it }, { simulator = true })
            2 -> ScheduleScreen(activity)
            3 -> ActivityScreen(activity, showStats, { showStats = it }) { edit = it }
            else -> SettingsScreen(activity)
        } }
        Box(Modifier.weight(1f)) { if (LocalVeloMotion.current) Crossfade(tab, animationSpec = tween(220), label = "Navegación") { content(it) } else content(tab) }
        Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), color = colors.surface, shape = RoundedCornerShape(28.dp), tonalElevation = 3.dp) {
            Row(Modifier.padding(horizontal = 5.dp, vertical = 9.dp)) { tabs.forEachIndexed { index, label ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { tab = index }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(CircleShape).background(if (tab == index) colors.primaryContainer else Color.Transparent).padding(horizontal = 14.dp, vertical = 5.dp)) { Icon(tabIcons[index], label, Modifier.size(22.dp), if (tab == index) colors.primary else colors.onSurfaceVariant) }
                    Spacer(Modifier.height(4.dp)); Text(label, fontSize = 10.sp, color = if (tab == index) colors.onSurface else colors.onSurfaceVariant, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal)
                }
            } }
        }
    }
    if (simulator) SimulatorDialog(activity) { simulator = false }
    if (edit != null) RuleDialog(activity, edit, { edit = null })
    }
}

@Composable internal fun PageTitle(eyebrow: String, title: String, subtitle: String? = null) { Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Text(title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp); if (subtitle != null) Text(subtitle, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp) }
@Composable internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .22f))) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
@Composable internal fun Section(text: String, trailing: String? = null) { Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); if (trailing != null) Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) } }
@Composable internal fun Toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(23.dp), MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium); if (description.isNotBlank()) Text(description, Modifier.padding(top = 4.dp), fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(10.dp)); VSwitch(checked, onChange, Modifier.semanticsLabel(title))
    }
}
@Composable internal fun Info(text: String, icon: ImageVector = VeloIcons.Info) { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { Icon(icon, null, Modifier.size(16.dp), MaterialTheme.colorScheme.secondary); Text(text, fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun Empty(icon: ImageVector, title: String, text: String) { Panel { Icon(icon, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary); Text(title, fontWeight = FontWeight.SemiBold); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 19.sp) } }

@Composable private fun Home(activity: MainActivity, settings: () -> Unit, rules: () -> Unit, simulate: () -> Unit, statistics: () -> Unit) {
    val state = activity.state; val profile = state.profile; val colors = MaterialTheme.colorScheme; activity.statusRevision
    val hasRole = activity.hasRole(RoleManager.ROLE_CALL_SCREENING)
    val contacts = activity.hasPermission(Manifest.permission.READ_CONTACTS)
    val pause = state.settings.pauseUntil > System.currentTimeMillis()
    val active = state.settings.enabled && hasRole && !pause
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Spacer(Modifier.height(8.dp)); PageTitle("Tu tranquilidad, a medida", "Elige quién puede\ninterrumpirte.") }
        item {
            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(30.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 203.dp)) {
                    val motion = state.settings.animations && AndroidSettings.Global.getFloat(activity.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0
                    val pulse = if (motion) rememberInfiniteTransition(label = "Respiración").animateFloat(.86f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Aura").value else 1f
                    Canvas(Modifier.size(230.dp).align(Alignment.CenterEnd).offset(x = 76.dp)) {
                        val r = size.minDimension * .42f * if (motion) pulse else 1f
                        drawCircle(colors.primary.copy(alpha = .06f), r)
                        drawCircle(colors.primary.copy(alpha = .13f), r * .72f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        drawCircle(colors.primary.copy(alpha = .18f), r * .48f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    }
                    Column(Modifier.padding(23.dp).padding(end = 48.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(colors.primary)); Spacer(Modifier.width(7.dp)); Text(if (active) "PROTECCIÓN ACTIVADA" else if (pause) "EN PAUSA" else "LISTO PARA CONFIGURAR", color = colors.onPrimaryContainer, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        Spacer(Modifier.height(15.dp)); Text(if (active) "Un poco\nmás de calma." else "Tu calma\nempieza aquí.", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, color = colors.onPrimaryContainer, letterSpacing = (-.8).sp)
                        Spacer(Modifier.height(15.dp)); Text("Perfil ${profile.name}", color = colors.onPrimaryContainer.copy(alpha = .7f), fontSize = 12.sp)
                    }
                    Icon(VeloIcons.VerifiedUser, null, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp).size(47.dp), colors.primary)
                }
            }
        }
        item { Panel {
            Toggle("Protección de llamadas", if (!hasRole) "Selecciona Velo como filtro para empezar" else "Aplica las reglas del perfil activo", state.settings.enabled, { activity.save(state.copy(settings = state.settings.copy(enabled = it))); if (it && !hasRole) activity.requestRole(RoleManager.ROLE_CALL_SCREENING) })
            if (!hasRole) VTonalButton({ activity.requestRole(RoleManager.ROLE_CALL_SCREENING) }, Modifier.fillMaxWidth()) { Text("Activar Velo como filtro") }
            if (!contacts) VTextButton({ activity.requestContacts() }) { Text("Permitir acceso a contactos") }
            if (pause) VTextButton({ activity.save(state.copy(settings = state.settings.copy(pauseUntil = 0))) }) { Text("Reanudar ahora") }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Metric("${activity.filteredToday}", "filtradas hoy", Modifier.weight(1f)); Metric("${state.rules.count { it.enabled }}", "reglas guardadas", Modifier.weight(1f)) } }
        item { VTextButton(statistics, Modifier.fillMaxWidth()) { Icon(VeloIcons.BarChart, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Ver estadísticas de llamadas") } }
        item { Section("Tus filtros rápidos", profile.name) }
        item { Panel {
            Toggle("Números desconocidos", "Número visible que no está en contactos", profile.unknownCalls, { activity.profile(profile.copy(unknownCalls = it)) }, VeloIcons.PersonOff)
            HorizontalDivider(color = colors.outline.copy(alpha = .4f))
            Toggle("Números privados", "Llamadas con identidad oculta", profile.privateCalls, { activity.profile(profile.copy(privateCalls = it)) }, VeloIcons.VisibilityOff)
            if (profile.privateCalls) Info("Se bloquean los privados que Android entrega al filtro. El número oculto no puede identificarse ni compararse con tus listas.")
            VTextButton(settings) { Text("Revisar permisos de Velo"); Icon(VeloIcons.ArrowForward, null, Modifier.padding(start = 7.dp).size(17.dp)) }
        } }
        item { VOutlinedButton(simulate, Modifier.fillMaxWidth().height(52.dp)) { Icon(VeloIcons.Science, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Probar una regla") } }
        item { Text("Sin anuncios. Sin cuentas. Tus reglas se quedan contigo.", color = colors.onSurfaceVariant, fontSize = 10.sp, lineHeight = 16.sp) }
    }
}
@Composable private fun Metric(value: String, label: String, modifier: Modifier) { Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(20.dp)) { Text(value, fontSize = 29.sp, fontWeight = FontWeight.Medium); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) } } }

@Composable internal fun ProfilePicker(activity: MainActivity) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { activity.state.profiles.forEach { p -> VChip(p.id == activity.state.profile.id, { activity.save(activity.state.copy(settings = activity.state.settings.copy(selectedProfile = p.id))) }, { Text(p.name) }, leadingIcon = if (p.id == activity.state.profile.id) ({ Icon(VeloIcons.Check, null, Modifier.size(15.dp)) }) else null) } }
}

@Composable internal fun ActionPicker(action: Action, select: (Action) -> Unit) {
    Column { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { Action.values().filter { it != Action.ALLOW }.forEach { a -> VChip(action == a, { select(a) }, { Text(a.label, fontSize = 11.sp) }) } }; Text(when (action) { Action.REJECT -> "Cuelga como un rechazo manual. El buzón depende del operador."; Action.IGNORE -> "El filtro bloquea sin enviar un rechazo manual."; Action.SILENCE -> "La llamada sigue visible, pero no suena."; else -> "La llamada pasa." }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp) }
}

@Composable internal fun FolderDialog(activity: MainActivity, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }; var rename by remember { mutableStateOf<Folder?>(null) }; var renameText by remember { mutableStateOf("") }
    VDialog(onDismissRequest = dismiss, title = { Text("Carpetas") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        activity.state.folders.forEach { f -> Row(verticalAlignment = Alignment.CenterVertically) { Text(f.name, Modifier.weight(1f), fontSize = 13.sp); VSwitch(f.enabled, { activity.save(activity.state.copy(folders = activity.state.folders.map { if (it.name == f.name) it.copy(enabled = !f.enabled) else it })) }); if (f.name != "General") VIconButton({ rename = f; renameText = f.name }) { Icon(VeloIcons.Edit, "Editar carpeta", Modifier.size(16.dp)) } } }
        VField(name, { name = it.take(80) }, labelText="Nombre de carpeta"); VButton({ activity.save(activity.state.copy(folders = activity.state.folders + Folder(name.trim()))); name = "" }, enabled = name.isNotBlank() && activity.state.folders.none { it.name.equals(name.trim(), true) } && activity.state.folders.size < 200) { Text("Crear carpeta") }
    } }, confirmButton = { VTextButton(dismiss) { Text("Listo") } })
    rename?.let { f -> VDialog(onDismissRequest = { rename = null }, title = { Text("Editar carpeta") }, text = { VField(renameText, { renameText = it.take(80) }, labelText="Nombre") }, confirmButton = { VTextButton({ val s = activity.state; activity.save(s.copy(folders = s.folders.map { if (it.name == f.name) it.copy(name = renameText.trim()) else it }, rules = s.rules.map { if (it.folder == f.name) it.copy(folder = renameText.trim()) else it })); rename = null }, enabled = renameText.isNotBlank() && activity.state.folders.none { it.name == renameText.trim() && it.name != f.name }) { Text("Guardar") } }, dismissButton = { VTextButton({ val s = activity.state; activity.save(s.copy(folders = s.folders.filterNot { it.name == f.name }, rules = s.rules.map { if (it.folder == f.name) it.copy(folder = "General") else it })); rename = null }) { Text("Quitar carpeta") } }) }
}

private fun minuteText(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
private val dayNames = listOf("L", "M", "X", "J", "V", "S", "D")
@Composable private fun ScheduleScreen(activity: MainActivity) {
    val p = activity.state.profile; var edit by remember { mutableStateOf<TimeWindow?>(null) }; var add by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Haz sitio para lo importante", "A tu ritmo.", "Programa varios periodos por día. Los horarios usan la hora local del teléfono.") }
        item { ProfilePicker(activity) }
        item { Panel { Toggle("Usar horario", "Fuera de estos periodos se permiten las llamadas", p.scheduled, { activity.profile(p.copy(scheduled = it)) }); if (p.scheduled && p.windows.isEmpty()) Info("Todavía no hay periodos: el perfil no bloqueará hasta que añadas uno.") } }
        items(p.windows, key = { it.id }) { w -> Panel {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f).clickable { edit = w }) { Text("${minuteText(w.start)} — ${minuteText(w.end)}", fontSize = 23.sp, fontWeight = FontWeight.Medium); Text(if (w.start == w.end) "Todo el día" else if (w.start > w.end) "Termina al día siguiente" else "Periodo de bloqueo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }; VSwitch(w.enabled, { activity.profile(p.copy(windows = p.windows.map { if (it.id == w.id) it.copy(enabled = !w.enabled) else it })) }); VIconButton({ edit = w }) { Icon(VeloIcons.Edit, "Editar horario", Modifier.size(18.dp)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { dayNames.forEachIndexed { i, name -> Box(Modifier.size(28.dp).clip(CircleShape).background(if (i + 1 in w.days) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(name, fontSize = 10.sp, color = if (i + 1 in w.days) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } } }
        } }
        if (p.windows.isEmpty()) item { Empty(VeloIcons.NightsStay, "Cada momento tiene su horario", "Añade periodos para trabajar, descansar o dormir. Puedes cruzar la medianoche y elegir días independientes.") }
        item { VButton({ add = true }, Modifier.fillMaxWidth().height(50.dp), enabled = p.windows.size < 100) { Icon(VeloIcons.Add, null); Text("Añadir periodo") } }
        item { Section("Pausa rápida") }
        item { Panel { Text("Permite todas las llamadas durante un rato.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf(15, 30, 60).forEach { m -> VOutlinedButton({ activity.save(activity.state.copy(settings = activity.state.settings.copy(pauseUntil = System.currentTimeMillis() + m * 60_000L))) }) { Text("$m min", fontSize = 11.sp) } } } } }
    }
    if (add || edit != null) WindowDialog(edit, { w -> activity.profile(activity.state.profile.copy(windows = activity.state.profile.windows.filterNot { it.id == w.id } + w)); add = false; edit = null }, { edit?.let { old -> activity.profile(activity.state.profile.copy(windows = activity.state.profile.windows.filterNot { it.id == old.id })) }; add = false; edit = null }, { add = false; edit = null })
}

@Composable private fun WindowDialog(original: TimeWindow?, save: (TimeWindow) -> Unit, delete: () -> Unit, dismiss: () -> Unit) {
    var start by remember { mutableIntStateOf(original?.start ?: 22 * 60) }; var end by remember { mutableIntStateOf(original?.end ?: 8 * 60) }; var days by remember { mutableStateOf(original?.days ?: (1..7).toSet()) }; var clock by remember { mutableIntStateOf(0) }
    VDialog(onDismissRequest = dismiss, title = { Text("Periodo de bloqueo") }, text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { VOutlinedButton({ clock = 1 }) { Text(minuteText(start)) }; Text("→", Modifier.align(Alignment.CenterVertically)); VOutlinedButton({ clock = 2 }) { Text(minuteText(end)) } }
        Text("Días de inicio", fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { dayNames.forEachIndexed { i, text -> Box(Modifier.size(33.dp).clip(CircleShape).background(if (i + 1 in days) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable { days = if (i + 1 in days) days - (i + 1) else days + (i + 1) }, contentAlignment = Alignment.Center) { Text(text, fontSize = 11.sp) } } }
        Info(if (start == end) "La misma hora de inicio y fin significa todo el día." else if (start > end) "Este periodo termina al día siguiente, aunque ese día no esté seleccionado." else "Se incluye la hora de inicio; se excluye la de fin.")
        if (original != null) VTextButton(delete) { Text("Eliminar periodo", color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { VButton({ save((original ?: TimeWindow()).copy(start = start, end = end, days = days)) }, enabled = days.isNotEmpty()) { Text("Guardar") } }, dismissButton = { VTextButton(dismiss) { Text("Cancelar") } })
    if (clock != 0) VTimeDialog(if(clock == 1) start else end, { if(clock == 1) start = it else end = it }, { clock = 0 })
}

@Composable private fun SimulatorDialog(activity: MainActivity, dismiss: () -> Unit) {
    var number by remember { mutableStateOf("") }; var identity by remember { mutableStateOf(Identity.VISIBLE) }; var contact by remember { mutableStateOf(false) }; var voip by remember { mutableStateOf(false) }; var repeat by remember { mutableStateOf(false) }; var currentSettings by remember { mutableStateOf(false) }; var result by remember { mutableStateOf<Decision?>(null) }; var date by remember { mutableStateOf(LocalDate.now()) }; var minute by remember { mutableIntStateOf(LocalTime.now().hour * 60 + LocalTime.now().minute) }; var showDate by remember { mutableStateOf(false) }; var showTime by remember { mutableStateOf(false) }
    VDialog(onDismissRequest = dismiss, title = { Text("Prueba tus reglas") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Info("Simulación local: no genera llamadas ni comprueba si Android las entrega a Velo.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { Identity.values().forEach { kind -> VChip(identity == kind, { identity = kind; result = null }, { Text(kind.label, fontSize = 10.sp) }) } }
        if (identity == Identity.VISIBLE) VField(number, { number = it; result = null }, labelText="Número de prueba", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Toggle("Está en contactos", "Dato simulado", contact, { contact = it; result = null })
        Toggle("Conversación VoIP activa", "Dato simulado", voip, { voip = it; result = null })
        Toggle("Segundo intento en un minuto", "Dato simulado", repeat, { repeat = it; result = null })
        Toggle("Respetar activación y pausa", "Desactivado: prueba el perfil como si estuviera activo", currentSettings, { currentSettings = it; result = null })
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { VOutlinedButton({ showDate = true }) { Text(date.format(DateTimeFormatter.ofPattern("dd/MM"))) }; VOutlinedButton({ showTime = true }) { Text(minuteText(minute)) } }
        result?.let { d -> Surface(color = if (d.action == Action.ALLOW) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Text(d.action.label, fontSize = 23.sp, fontWeight = FontWeight.SemiBold); Text(d.reason, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp)) } } }
    } }, confirmButton = { VButton({ val s = activity.state; val now = date.atTime(minute / 60, minute % 60).atZone(ZoneId.systemDefault()); val simulated = if (currentSettings) s else s.copy(settings = s.settings.copy(enabled = true, pauseUntil = 0)); val store = Store(activity); result = RuleEngine.decide(simulated, CallInput(number = number, canonical = store.canonical(number), identity = identity, inContacts = contact, voip = voip, lastAttempt = if (repeat) now.toInstant().toEpochMilli() - 60_000 else null), now) }, enabled = identity != Identity.VISIBLE || RuleEngine.digits(number).isNotEmpty()) { Text("Comprobar") } }, dismissButton = { VTextButton(dismiss) { Text("Cerrar") } })
    if(showDate) VDateDialog(date, { date = it; result = null }, { showDate = false })
    if(showTime) VTimeDialog(minute, { minute = it; result = null }, { showTime = false })
}

@Composable internal fun Confirm(title: String, text: String, accept: () -> Unit, dismiss: () -> Unit) { VDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { VTextButton(accept) { Text("Confirmar") } }, dismissButton = { VTextButton(dismiss) { Text("Cancelar") } }) }

