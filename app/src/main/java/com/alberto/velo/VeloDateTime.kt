package com.alberto.velo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun VTimeDialog(minute: Int,save: (Int) -> Unit,dismiss: () -> Unit) {
    var hour by remember {mutableStateOf((minute/60).toString().padStart(2,'0'))}
    var minutes by remember {mutableStateOf((minute%60).toString().padStart(2,'0'))}
    val h=hour.toIntOrNull();val m=minutes.toIntOrNull()
    VDialog(dismiss,{Text("Elige la hora")},{Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            VField(hour,{hour=it.filter(Char::isDigit).take(2)},Modifier.weight(1f),labelText="Hora · 00–23",singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            VField(minutes,{minutes=it.filter(Char::isDigit).take(2)},Modifier.weight(1f),labelText="Minuto · 00–59",singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        }
        Info("Formato de 24 horas · hora local del teléfono")
    }},{VButton({save(h!!*60+m!!);dismiss()},enabled=h!=null&&h in 0..23&&m!=null&&m in 0..59){Text("Usar hora")}}, {VTextButton(dismiss){Text("Cancelar")}})
}

@Composable internal fun VDateDialog(date: LocalDate,save: (LocalDate) -> Unit,dismiss: () -> Unit) {
    var month by remember {mutableStateOf(YearMonth.from(date))};var selected by remember {mutableStateOf(date)}
    val c=MaterialTheme.colorScheme
    VDialog(dismiss,{Text("Elige el día")},{Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            VTextButton({month=month.minusMonths(1)},Modifier.semanticsLabel("Mes anterior")){Text("‹",fontSize=25.sp)}
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale("es"))),Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Medium)
            VTextButton({month=month.plusMonths(1)},Modifier.semanticsLabel("Mes siguiente")){Text("›",fontSize=25.sp)}
        }
        Row {listOf("L","M","X","J","V","S","D").forEach {Box(Modifier.weight(1f).height(32.dp),contentAlignment=Alignment.Center){Text(it,fontSize=11.sp,color=c.onSurfaceVariant)}}}
        val offset=month.atDay(1).dayOfWeek.value-1
        (0 until (offset+month.lengthOfMonth()+6)/7).forEach {week->Row { (0..6).forEach {day->
            val number=week*7+day-offset+1
            Box(Modifier.weight(1f).height(44.dp),contentAlignment=Alignment.Center){if(number in 1..month.lengthOfMonth()) {
                val d=month.atDay(number);val active=d==selected
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(if(active)c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent).clickable {selected=d}.semanticsLabel(d.toString()),contentAlignment=Alignment.Center){Text(number.toString(),color=if(active)c.primary else c.onSurface,fontSize=13.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal)}
            }}
        }}}
        VTextButton({selected=LocalDate.now();month=YearMonth.from(selected)}){Text("Ir a hoy")}
    }},{VButton({save(selected);dismiss()}){Text("Usar día")}}, {VTextButton(dismiss){Text("Cancelar")}})
}
