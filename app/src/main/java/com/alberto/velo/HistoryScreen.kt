package com.alberto.velo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun ActivityScreen(activity: MainActivity, statistics: Boolean, onStatistics: (Boolean) -> Unit, add: (NumberRule) -> Unit) {
    var period by remember { mutableStateOf(HistoryPeriod.ALL) }
    var search by remember { mutableStateOf("") }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VChip(!statistics, { onStatistics(false) }, { Text("Historial") }, leadingIcon = { Icon(VeloIcons.History, null, Modifier.size(17.dp)) })
            VChip(statistics, { onStatistics(true) }, { Text("Estadísticas") }, leadingIcon = { Icon(VeloIcons.BarChart, null, Modifier.size(17.dp)) })
            Spacer(Modifier.weight(1f))
            VIconButton(activity::refresh) { Icon(VeloIcons.Refresh, "Actualizar historial y estadísticas") }
        }
        if (statistics) StatisticsScreen(activity, period, { period = it }) { number -> search = number; onStatistics(false) }
        else HistoryList(activity, period, { period = it }, search, { search = it }, add)
    }
}

@Composable private fun PeriodSelector(period: HistoryPeriod, select: (HistoryPeriod) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(HistoryPeriod.ALL, HistoryPeriod.TODAY, HistoryPeriod.WEEK, HistoryPeriod.MONTH, HistoryPeriod.QUARTER, HistoryPeriod.YEAR).forEach { p -> VChip(period == p, { select(p) }, { Text(p.label, fontSize = 11.sp) }) }
    }
}

@Composable private fun HistoryList(activity: MainActivity, period: HistoryPeriod, selectPeriod: (HistoryPeriod) -> Unit, search: String, onSearch: (String) -> Unit, add: (NumberRule) -> Unit) {
    var onlyFiltered by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(search.trim()) }
    var selected by remember { mutableStateOf<CallEvent?>(null) }
    var clear by remember { mutableStateOf(false) }
    val revision = activity.statusRevision
    LaunchedEffect(search) { delay(250); query = search.trim() }
    val pages = remember(onlyFiltered,query,period,revision) {
        val start = period.start(ZonedDateTime.now())
        Pager(PagingConfig(pageSize=100,initialLoadSize=100,prefetchDistance=20,maxSize=300,enablePlaceholders=false)) {
            HistoryPagingSource { key, size, signal -> activity.historyPage(onlyFiltered,query,start,key?.before,size,key?.after,signal) }
        }.flow
    }
    val calls = pages.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    LaunchedEffect(onlyFiltered,query,period,revision) { listState.scrollToItem(0) }
    val loading = calls.loadState.refresh is LoadState.Loading
    val error = calls.loadState.refresh is LoadState.Error || calls.loadState.append is LoadState.Error
    val zone = ZoneId.systemDefault()
    val dateFormat = remember(zone) { DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm:ss",Locale("es")).withZone(zone) }
    LazyColumn(state=listState,contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle("Cada llamada, con contexto", "Tu historial.", "${activity.historyCount} registros guardados · ${if (activity.state.settings.retentionDays == 0) "sin límite de antigüedad" else "conservación de ${activity.state.settings.retentionDays} días"}") }
        item { PeriodSelector(period, selectPeriod) }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            VChip(onlyFiltered, { onlyFiltered = !onlyFiltered }, { Text(if (onlyFiltered) "Solo filtradas" else "Todas las decisiones") })
            Spacer(Modifier.weight(1f))
            VIconButton(activity::exportHistory) { Icon(VeloIcons.FileDownload, "Exportar todo el historial en CSV") }
            VIconButton({ clear = true }) { Icon(VeloIcons.DeleteOutline, "Vaciar todo el historial") }
        } }
        item { VField(search, onSearch, Modifier.fillMaxWidth(), labelText="Buscar número o motivo", singleLine = true, shape = RoundedCornerShape(17.dp), trailingIcon = { if (search.isNotEmpty()) VIconButton({ onSearch("") }) { Icon(VeloIcons.Close, "Quitar búsqueda") } }) }
        if (search.trim() != query) item { VProgress(modifier=Modifier.fillMaxWidth()) }
        if (error) item { Panel { Info("No se pudo cargar el historial. Tus datos se conservan."); VTextButton(calls::retry) { Text("Reintentar") } } }
        if (!loading && !error && calls.itemCount == 0) item { Panel { Icon(VeloIcons.Inbox, null, Modifier.size(30.dp), MaterialTheme.colorScheme.primary); Text("No hay llamadas en esta selección", fontWeight = FontWeight.Medium); Text("Prueba otro periodo o quita los filtros. Aquí solo aparecen las llamadas que Android entrega a Velo.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(count=calls.itemCount,key=calls.itemKey { it.id }) { index ->
            val e = calls[index] ?: return@items
            Surface(Modifier.fillMaxWidth().clickable { selected = e }, shape = RoundedCornerShape(21.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Icon(if (e.action == Action.ALLOW) VeloIcons.Call else if (e.action == Action.SILENCE) VeloIcons.VolumeOff else VeloIcons.Block, null, Modifier.size(23.dp), if (e.action == Action.ALLOW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    Column(Modifier.weight(1f)) {
                        Text(e.number.ifBlank { e.identity.label }, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("${e.action.label} · ${e.reason}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(remember(e.time,dateFormat) { dateFormat.format(Instant.ofEpochMilli(e.time)) }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    Icon(VeloIcons.ChevronRight, null, Modifier.size(18.dp))
                }
            }
        }
        if (loading || calls.loadState.append is LoadState.Loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { VLoading(Modifier.size(25.dp)) } }
        item { Text("La exportación incluye todo el historial, aunque tengas filtros activos. Borrar el historial también elimina sus estadísticas.", fontSize = 10.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    selected?.let { e -> VDialog(onDismissRequest = { selected = null }, title = { Text(e.number.ifBlank { e.identity.label }) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${e.action.label} · ${e.reason}")
            Text("${e.source} · ${e.elapsedMs} ms de evaluación", fontSize = 12.sp)
            if (e.number.isNotBlank()) {
                VButton({ add(NumberRule(value = e.number, allow = true)); selected = null }) { Text("Añadir a permitidos") }
                VOutlinedButton({ add(NumberRule(value = e.number)); selected = null }) { Text("Añadir a bloqueados") }
            }
        }
    }, confirmButton = { VTextButton({ selected = null }) { Text("Cerrar") } }) }
    if (clear) Confirm("Vaciar todo el historial", "Se borrarán todos los registros, también los que no aparecen con tus filtros actuales, y sus estadísticas. Tus reglas se conservan.", { activity.clearHistory(); clear = false }, { clear = false })
}

@Composable private fun StatisticsScreen(activity: MainActivity, period: HistoryPeriod, selectPeriod: (HistoryPeriod) -> Unit, onNumber: (String) -> Unit) {
    var stats by remember { mutableStateOf(CallStatistics()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var selectedBucket by remember { mutableStateOf<TrendBucket?>(null) }
    LaunchedEffect(period, activity.statusRevision) {
        loading = true; error = false; now = ZonedDateTime.now()
        val result = try { Result.success(HistoryReads.run { signal -> activity.statistics(period.start(now),now.toInstant().toEpochMilli()+1,signal) }) }
            catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(failure: Exception) { Result.failure(failure) }
        result.onSuccess { stats = it }.onFailure { error = true }
        loading = false
    }
    val numberFormat = remember { java.text.NumberFormat.getIntegerInstance(Locale("es")) }
    fun count(n: Long) = numberFormat.format(n)
    val firstDate = if (period == HistoryPeriod.ALL) stats.firstTime?.let { Instant.ofEpochMilli(it).atZone(now.zone).toLocalDate() } ?: now.toLocalDate() else Instant.ofEpochMilli(period.start(now)).atZone(now.zone).toLocalDate()
    val buckets = remember(stats, firstDate, now.toLocalDate()) { HistoryMath.buckets(stats.daily, firstDate, now.toLocalDate()) }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Tu protección en cifras", "Menos interrupciones.", "Patrones de las llamadas registradas por Velo. Todo se calcula en este móvil.") }
        item { PeriodSelector(period, selectPeriod) }
        if (loading) item { VProgress(modifier=Modifier.fillMaxWidth()) }
        if (error) item { Info("No se pudieron calcular las estadísticas. Pulsa actualizar para reintentarlo.") }
        if (!loading && !error) {
            item { Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("INTERRUPCIONES FILTRADAS", fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (stats.total == 0L) "—" else "${stats.percentage}%", fontSize = 49.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${count(stats.filtered)} de ${count(stats.total)}\nregistros del periodo", Modifier.padding(bottom = 9.dp), fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("Incluye rechazadas, ignoradas y silenciadas.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(count(stats.privateFiltered), "privadas filtradas", Modifier.weight(1f))
                StatCard(count(stats.causes[FilterCause.UNKNOWN] ?: 0), "fuera de contactos", Modifier.weight(1f))
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(count(stats.uniqueVisibleFiltered), "números visibles filtrados", Modifier.weight(1f))
                StatCard(count(stats.actions[Action.ALLOW] ?: 0), "llamadas permitidas", Modifier.weight(1f))
            } }
            if (stats.total == 0L) item { Info("Aún no hay registros en este periodo. Los gráficos se rellenarán cuando Android entregue llamadas a Velo; no se incluyen simulaciones.") }
            item { Section("Evolución del periodo") }
            item { Panel {
                Text("Filtradas y permitidas", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                val max = buckets.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1L
                Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    buckets.forEach { bucket ->
                        val target = bucket.total.toFloat() / max
                        val animated by animateFloatAsState(target, tween(if (activity.state.settings.animations) 450 else 0), label = "Evolución")
                        val filteredColor = MaterialTheme.colorScheme.primary
                        val allowedColor = MaterialTheme.colorScheme.secondary.copy(alpha = .45f)
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { selectedBucket = bucket }.semantics { contentDescription = "${bucket.start} a ${bucket.end}: ${bucket.filtered} filtradas, ${bucket.total - bucket.filtered} permitidas" }, contentAlignment = Alignment.BottomCenter) {
                            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                                val h = size.height * animated
                                val filteredH = if (bucket.total == 0L) 0f else h * bucket.filtered.toFloat() / bucket.total
                                drawRoundRect(allowedColor, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h.coerceAtLeast(2.dp.toPx())), size = androidx.compose.ui.geometry.Size(size.width, h.coerceAtLeast(2.dp.toPx())), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                                if (filteredH > 0) drawRoundRect(filteredColor, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - filteredH), size = androidx.compose.ui.geometry.Size(size.width, filteredH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(firstDate.format(DateTimeFormatter.ofPattern("d MMM yy", Locale("es"))), fontSize = 10.sp); Text(now.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yy", Locale("es"))), fontSize = 10.sp) }
                Text("Toca una barra para ver su intervalo. Verde: filtradas · tono suave: permitidas.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { Section("Por qué se filtraron") }
            item { Panel {
                if (stats.causes.isEmpty()) Text("Sin llamadas filtradas en este periodo", fontSize = 12.sp)
                stats.causes.entries.sortedByDescending { it.value }.forEach { (cause, amount) -> CountBar(cause.label, amount, stats.filtered, MaterialTheme.colorScheme.primary) }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(Action.REJECT, Action.IGNORE, Action.SILENCE).forEach { action -> Column {
                        Text(count(stats.actions[action] ?: 0L), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(when (action) { Action.REJECT -> "Rechazadas"; Action.IGNORE -> "Ignoradas"; else -> "Silenciadas" }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
            } }
            item { Section("Cuándo insisten más") }
            item { Panel {
                val peak = stats.hours.maxOrNull() ?: 0
                val hour = stats.hours.indexOf(peak).coerceAtLeast(0)
                Text(if (peak == 0L) "Sin una franja destacada todavía" else "${hour.toString().padStart(2, '0')}:00–${hour.toString().padStart(2, '0')}:59 · ${count(peak)} filtradas", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                val colors = MaterialTheme.colorScheme
                Canvas(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = stats.hours.mapIndexed { h, n -> "$h horas: $n filtradas" }.joinToString(", ") }) {
                    val step = size.width / 24; val maximum = peak.coerceAtLeast(1)
                    stats.hours.forEachIndexed { h, n -> val height = (size.height * n.toFloat() / maximum).coerceAtLeast(2.dp.toPx()); drawRoundRect(if (n == peak && peak > 0) colors.primary else colors.primary.copy(alpha = .35f), androidx.compose.ui.geometry.Offset(step * h, size.height - height), androidx.compose.ui.geometry.Size(step * .7f, height), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("00 h", "06 h", "12 h", "18 h", "23 h").forEach { Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                HorizontalDivider()
                val maxDay = stats.weekdays.maxOrNull()?.coerceAtLeast(1) ?: 1L
                listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo").forEachIndexed { i, day -> CountBar(day, stats.weekdays[i], maxDay, MaterialTheme.colorScheme.secondary) }
                Text("Horas y días en la zona horaria actual del móvil. Los empates de hora muestran la primera franja.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { Section("Números más insistentes") }
            item { Panel {
                if (stats.topNumbers.isEmpty()) Text("Aún no hay números visibles filtrados", fontSize = 12.sp)
                stats.topNumbers.forEachIndexed { index, number ->
                    Row(Modifier.fillMaxWidth().clickable { onNumber(number.number) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", Modifier.width(25.dp), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        Text(number.number, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${count(number.count)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Icon(VeloIcons.ChevronRight, "Ver llamadas de este número", Modifier.size(18.dp))
                    }
                }
                Info("Los privados se cuentan como llamadas, no como personas distintas. Toca un número visible para consultar sus llamadas.")
            } }
            item { Info("Las cifras describen el historial guardado, no todas las llamadas de la SIM. Si desactivas el registro de permitidas o borras parte del historial, el porcentaje solo representa los registros restantes. No se estima tiempo ahorrado.") }
        }
    }
    selectedBucket?.let { b -> VDialog(onDismissRequest = { selectedBucket = null }, title = { Text("${b.start.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es")))} – ${b.end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es")))}") }, text = { Text("${count(b.filtered)} filtradas\n${count(b.total - b.filtered)} permitidas\n${count(b.total)} registros en total") }, confirmButton = { VTextButton({ selectedBucket = null }) { Text("Cerrar") } }) }
}

@Composable private fun StatCard(value: String, label: String, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) { Text(value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold); Text(label, fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun CountBar(label: String, amount: Long, total: Long, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontSize = 11.sp); Text(java.text.NumberFormat.getIntegerInstance(Locale("es")).format(amount), fontSize = 11.sp, fontWeight = FontWeight.Medium) }
        VProgress(if (total == 0L) 0f else (amount.toFloat() / total).coerceIn(0f, 1f), Modifier.fillMaxWidth().height(4.dp), color = color, trackColor = color.copy(alpha = .12f))
    }
}
