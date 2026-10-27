package com.alberto.velo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable internal fun RulesScreen(activity: MainActivity, edit: (NumberRule) -> Unit, add: (NumberRule) -> Unit, simulate: () -> Unit) {
    val state=activity.state;val p=state.profile;val c=MaterialTheme.colorScheme
    var allow by remember { mutableStateOf(false) };var search by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf(false) };var folder by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    val filtered=remember(state.rules,allow,folder,search) { state.rules.filter { it.allow==allow && (folder==null||it.folder==folder) && (it.value.contains(search,true)||it.note.contains(search,true)) } }
    val totals=remember(state.rules) { state.rules.count { !it.allow } to state.rules.count { it.allow } }
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Tú decides quién pasa", "Tus reglas.","Un filtro para cada número. Una excepción para cada persona.") }
        item { ProfilePicker(activity) }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(c.surface).padding(6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                RuleTab("Bloquear",totals.first,!allow,{allow=false},Modifier.weight(1f))
                RuleTab("Permitir",totals.second,allow,{allow=true},Modifier.weight(1f))
            }
        }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            VButton({add(NumberRule(value="",allow=allow,folder=folder?:"General"))},Modifier.weight(1f)) {
                Icon(VeloIcons.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(9.dp));Text("Añadir regla")
            }
            VOutlinedButton({activity.pickContact(allow)}) { Icon(VeloIcons.Contacts,null,Modifier.size(19.dp));Spacer(Modifier.width(7.dp));Text("Contacto") }
        } }
        item { VField(search,{search=it},placeholder={Text("Buscar número o nota")},leadingIcon={Icon(VeloIcons.Search,null,Modifier.size(20.dp))},singleLine=true,
            trailingIcon={if(search.isNotEmpty()) VIconButton({search=""}) {Icon(VeloIcons.Close,"Limpiar búsqueda",Modifier.size(17.dp))}}) }
        item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                VChip(folder==null,{folder=null},{Text("Todas")})
                state.folders.forEach { f -> VChip(folder==f.name,{folder=f.name},{Text(f.name+if(!f.enabled)" · pausa" else "")}) }
            }
            VIconButton({folders=true}) {Icon(VeloIcons.FolderOpen,"Gestionar carpetas",Modifier.size(21.dp))}
        } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("${filtered.size} ${if(filtered.size==1)"regla" else "reglas"}",fontSize=12.sp,fontWeight=FontWeight.Medium)
            Text(if(allow)"Prioridad sobre bloqueos" else "Número o patrón",fontSize=11.sp,color=c.onSurfaceVariant)
        } }
        if(filtered.isEmpty()) item {
            Panel {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(19.dp)).background(c.primaryContainer),contentAlignment=Alignment.Center) {Icon(if(allow)VeloIcons.FavoriteBorder else VeloIcons.Tune,null,Modifier.size(27.dp),c.primary)}
                Text(if(search.isNotBlank())"Sin coincidencias" else if(allow)"Haz sitio a los tuyos." else "Tu lista empieza aquí.",fontSize=22.sp,lineHeight=27.sp,fontWeight=FontWeight.Medium)
                Text(if(allow)"Añade a las personas que deben pasar incluso con el bloqueo total. Las excepciones se aplican con la protección activa y la lista de permitidos habilitada." else "Bloquea un número completo o varios con un prefijo, una terminación o un patrón.",fontSize=12.sp,lineHeight=19.sp,color=c.onSurfaceVariant)
            }
        }
        items(filtered,key={it.id}) { r ->
            val scopedOut=r.profiles.isNotEmpty()&&p.id !in r.profiles
            val folderPaused=state.folders.any { it.name==r.folder&&!it.enabled }
            Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=c.surface,border=BorderStroke(1.dp,c.outline.copy(alpha=.25f))) {
                Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(43.dp).clip(RoundedCornerShape(15.dp)).background(if(r.allow)c.primaryContainer else c.secondary.copy(alpha=.1f)),contentAlignment=Alignment.Center) {Icon(if(r.allow)VeloIcons.FavoriteBorder else VeloIcons.Block,null,Modifier.size(22.dp),if(r.allow)c.primary else c.secondary)}
                        Column(Modifier.weight(1f).clickable {edit(r)}) {Text(r.note.ifBlank{r.value},fontSize=17.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis);if(r.note.isNotBlank())Text(r.value,fontSize=13.sp,color=c.onSurfaceVariant)}
                        VSwitch(r.enabled,{activity.rule(r.copy(enabled=it))},Modifier.semanticsLabel("Activar regla ${r.value}"))
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {Text("${r.match.label} · ${r.folder}",fontSize=11.sp,color=c.primary);Text(if(!r.enabled)"Desactivada" else if(folderPaused)"Carpeta en pausa" else if(scopedOut)"Otro perfil" else if(r.allow)"Permitir" else (r.action?:p.action).label,fontSize=10.sp,color=c.onSurfaceVariant)}
                        VTextButton({edit(r)}) {Text("Editar");Spacer(Modifier.width(6.dp));Icon(VeloIcons.Edit,null,Modifier.size(16.dp))}
                    }
                }
            }
        }
        item { Panel {
            Row(Modifier.fillMaxWidth().clickable{advanced=!advanced},verticalAlignment=Alignment.CenterVertically) {
                Icon(VeloIcons.Tune,null,Modifier.size(23.dp),c.primary);Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {Text("Comportamiento del perfil",fontSize=15.sp,fontWeight=FontWeight.Medium);Text("${p.name} · ${p.action.label}",fontSize=11.sp,color=c.onSurfaceVariant)}
                VIconButton({advanced=!advanced}) {Icon(if(advanced)VeloIcons.Close else VeloIcons.Add,if(advanced)"Ocultar opciones" else "Mostrar opciones",Modifier.size(18.dp))}
            }
            if(advanced) {
                HorizontalDivider(color=c.outline.copy(alpha=.3f));ActionPicker(p.action){activity.profile(p.copy(action=it))}
                Toggle("Lista de permitidos","Activa las excepciones del perfil",p.allowList,{activity.profile(p.copy(allowList=it))})
                Toggle("Bloquear todas","La lista de permitidos sigue teniendo prioridad",p.allCalls,{activity.profile(p.copy(allCalls=it))})
                Toggle("Identidad no disponible","El operador no facilita un número válido",p.unavailableCalls,{activity.profile(p.copy(unavailableCalls=it))})
                Toggle("Durante una conversación VoIP","Se detecta mediante el modo de audio; depende de la app de voz",p.voipCalls,{activity.profile(p.copy(voipCalls=it))})
                Toggle("Dejar pasar si insiste","Segundo intento visible en ${p.repeatMinutes} minutos",p.repeated,{activity.profile(p.copy(repeated=it))})
                if(p.repeated) {Text("Ventana: ${p.repeatMinutes} min",fontSize=12.sp);VSlider(p.repeatMinutes.toFloat(),{activity.profile(p.copy(repeatMinutes=it.toInt().coerceIn(1,30)))},valueRange=1f..30f,steps=28)}
                Info("Los privados no pueden usar la excepción por repetición: no hay una identidad fiable que comparar.")
            }
            VTextButton(simulate,Modifier.fillMaxWidth()) {Icon(VeloIcons.Science,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Probar reglas en el simulador")}
        } }
    }
    if(folders) FolderDialog(activity){folders=false;if(activity.state.folders.none{it.name==folder})folder=null}
}

@Composable private fun RuleTab(title: String,count: Int,selected: Boolean,click: () -> Unit,modifier: Modifier) {
    val c=MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(if(selected)c.primaryContainer else Color.Transparent).selectable(selected,role=Role.Tab,onClick=click).padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {Icon(if(title=="Bloquear")VeloIcons.Block else VeloIcons.FavoriteBorder,null,Modifier.size(22.dp),if(selected)c.primary else c.onSurfaceVariant);Text(count.toString(),fontSize=21.sp,color=if(selected)c.primary else c.onSurfaceVariant,fontWeight=FontWeight.Medium)}
        Text(title,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=if(selected)c.onPrimaryContainer else c.onSurfaceVariant)
    }
}

@Composable internal fun RuleDialog(activity: MainActivity,original: NumberRule?,dismiss: () -> Unit) {
    val c=MaterialTheme.colorScheme;val existing=activity.state.rules.any{it.id==original?.id}
    var number by remember{mutableStateOf(original?.value.orEmpty())};var note by remember{mutableStateOf(original?.note.orEmpty())}
    var match by remember{mutableStateOf(original?.match?:Match.EXACT)};var allow by remember{mutableStateOf(original?.allow?:false)}
    var folder by remember{mutableStateOf(original?.folder?:"General")};var ownAction by remember{mutableStateOf(original?.action!=null)}
    var action by remember{mutableStateOf(original?.action?:activity.state.profile.action)};var delete by remember{mutableStateOf(false)}
    var scoped by remember{mutableStateOf(original?.profiles?.isNotEmpty()==true)}
    var profileIds by remember{mutableStateOf(original?.profiles?.takeIf{it.isNotEmpty()}?:setOf(activity.state.profile.id))}
    val valid=RuleEngine.digits(number).isNotEmpty()&&(!scoped||profileIds.isNotEmpty())
    BackHandler(onBack=dismiss)
        Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=c.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    VIconButton(dismiss){Icon(VeloIcons.Close,"Cancelar edición",Modifier.size(21.dp))}
                    Column(Modifier.weight(1f)) {Text(if(existing)"EDITAR REGLA" else "NUEVA REGLA",fontSize=9.sp,letterSpacing=1.8.sp,color=c.primary,fontWeight=FontWeight.Bold);Text("A tu manera.",fontSize=26.sp,fontWeight=FontWeight.SemiBold)}
                    Icon(if(allow)VeloIcons.FavoriteBorder else VeloIcons.Shield,null,Modifier.size(30.dp),c.primary)
                }
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {VChip(!allow,{allow=false},{Text("Bloquear")},Modifier.weight(1f),{Icon(VeloIcons.Block,null,Modifier.size(18.dp))});VChip(allow,{allow=true},{Text("Permitir")},Modifier.weight(1f),{Icon(VeloIcons.FavoriteBorder,null,Modifier.size(18.dp))})} }
                    item { Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        EditorHeading("01","El número")
                        VField(number,{number=it.take(64)},labelText="Número o patrón",placeholder={Text("Ej. +34 600 123 456")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true,leadingIcon={Icon(VeloIcons.Call,null,Modifier.size(21.dp))})
                        Text("¿Cómo debe coincidir?",fontSize=12.sp,color=c.onSurfaceVariant)
                        Match.values().toList().chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {row.forEach { m -> MatchOption(m,match==m,{match=m},Modifier.weight(1f))}} }
                        if(number.isNotBlank()) Surface(shape=RoundedCornerShape(18.dp),color=c.primaryContainer.copy(alpha=.6f)) {Row(Modifier.padding(15.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {Icon(VeloIcons.Info,null,Modifier.size(17.dp),c.primary);Text("${if(allow)"Permitirá" else "Bloqueará"} ${when(match){Match.EXACT->"este número";Match.PREFIX->"los que empiecen por";Match.CONTAINS->"los que contengan";Match.SUFFIX->"los que terminen en"}} ${number.trim()}.",fontSize=12.sp,lineHeight=18.sp)}}
                    } }
                    item { Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {EditorHeading("02","Dale un lugar");VField(note,{note=it.take(500)},labelText="Nombre o nota · opcional",placeholder={Text("Para reconocer esta regla")});Text("Carpeta",fontSize=12.sp,color=c.onSurfaceVariant);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){activity.state.folders.forEach{f->VChip(folder==f.name,{folder=f.name},{Text(f.name)},leadingIcon={Icon(VeloIcons.FolderOpen,null,Modifier.size(16.dp))})}}} }
                    item { Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {EditorHeading("03","Cuándo y cómo");Panel {
                        Toggle("Elegir perfiles","Desactivado: se comparte con todos",scoped,{scoped=it})
                        if(scoped) {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){activity.state.profiles.forEach{p->VChip(p.id in profileIds,{profileIds=if(p.id in profileIds)profileIds-p.id else profileIds+p.id},{Text(p.name)},multiSelect=true)}};if(profileIds.isEmpty())Text("Selecciona al menos un perfil",color=c.error,fontSize=12.sp)}
                        if(!allow){Toggle("Método de bloqueo propio","Desactivado: usa el método del perfil",ownAction,{ownAction=it});if(ownAction)ActionPicker(action){action=it}}
                        else Info("Tiene prioridad sobre los bloqueos durante la protección activa, con la lista de permitidos habilitada.")
                    }} }
                    if(existing) item {VTextButton({delete=true},Modifier.fillMaxWidth()){Icon(VeloIcons.DeleteOutline,null,Modifier.size(18.dp),c.error);Spacer(Modifier.width(8.dp));Text("Eliminar regla",color=c.error)}}
                }
                Surface(color=c.surface,shadowElevation=12.dp) {Column(Modifier.padding(horizontal=24.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text(if(valid)"${if(allow)"Permitir" else (if(ownAction)action else activity.state.profile.action).label} · ${match.label} · $folder" else if(RuleEngine.digits(number).isEmpty()) "Introduce un número para continuar" else "Selecciona al menos un perfil",fontSize=10.sp,color=c.onSurfaceVariant,maxLines=2)
                    VButton({activity.rule((original?:NumberRule(value=number)).copy(value=number.trim(),note=note,match=match,allow=allow,folder=folder,action=if(!allow&&ownAction)action else null,profiles=if(scoped)profileIds else emptySet()));dismiss()},Modifier.fillMaxWidth(),enabled=valid){Icon(VeloIcons.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(9.dp));Text(if(existing)"Guardar cambios" else "Crear regla")}
                }}
            }
        }
    if(delete)Confirm("Eliminar esta regla","Se quitará de la lista.",{activity.save(activity.state.copy(rules=activity.state.rules.filterNot{it.id==original?.id}));dismiss()},{delete=false})
}
@Composable private fun EditorHeading(number: String,title: String) {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Text(number,fontSize=11.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text(title,fontSize=18.sp,fontWeight=FontWeight.Medium)}}
@Composable private fun MatchOption(match: Match,selected: Boolean,onClick: () -> Unit,modifier: Modifier) {
    val c=MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(if(selected)c.primaryContainer else c.surface).border(1.dp,if(selected)c.primary.copy(alpha=.65f) else c.outline.copy(alpha=.3f),RoundedCornerShape(18.dp)).selectable(selected,role=Role.RadioButton,onClick=onClick).padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(when(match){Match.EXACT->"600 123 456";Match.PREFIX->"600 ··· ···";Match.CONTAINS->"··· 123 ···";Match.SUFFIX->"··· ··· 456"},fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=if(selected)c.primary else c.onSurfaceVariant)
        Row(verticalAlignment=Alignment.CenterVertically) {Text(match.label,Modifier.weight(1f),fontSize=11.sp);if(selected)Icon(VeloIcons.Check,null,Modifier.size(14.dp),c.primary)}
    }
}
