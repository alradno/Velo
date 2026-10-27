package com.alberto.velo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun SetupBanner(activity: MainActivity) {
    val status=activity.setup.readiness
    if(!status.loaded||status.pending.isEmpty())return
    val c=MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=6.dp),shape=RoundedCornerShape(18.dp),color=c.secondary.copy(alpha=.09f),border=BorderStroke(1.dp,c.secondary.copy(alpha=.3f))) {
        Row(Modifier.padding(start=13.dp,end=5.dp,top=5.dp,bottom=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Icon(VeloIcons.Info,null,Modifier.size(20.dp),c.secondary)
            Column(Modifier.weight(1f)) {
                Text("${status.pending.size} ${if(status.pending.size==1)"ajuste pendiente" else "ajustes pendientes"}",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                Text(status.pending.first().title,fontSize=10.sp,color=c.onSurfaceVariant,maxLines=2)
            }
            VTextButton(activity.setup::show){Text("Revisar",fontSize=12.sp)}
        }
    }
}

@Composable internal fun SetupDialog(activity: MainActivity) {
    val setup=activity.setup;val status=setup.readiness
    VDialog(onDismissRequest=setup::dismiss,title={Text(if(setup.firstVisit)"Conecta tu tranquilidad." else if(status.pending.isEmpty())"Todo comprobado." else "Revisa tu protección.")},text={
        Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text(if(setup.firstVisit)"Prepara Velo para filtrar con tus reglas. Te pedirá los permisos y abrirá los ajustes necesarios, uno a uno. Puedes continuar aunque dejes alguno pendiente."
                else "Se comprueba al abrir Velo y al volver de los ajustes. Cada estado se lee del sistema; una autorización denegada sigue figurando como pendiente.",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!status.loaded)VLoading()
            status.checks.forEach { check -> SetupCheckRow(check,setup::request,setup::confirmOppoReview) }
            if(!activity.state.settings.enabled)Info("La protección está desactivada en Velo. Puedes activarla en Inicio después de configurar los permisos.")
            if(status.pending.isEmpty())Info("Los ajustes verificables están listos. Los ajustes extra de ColorOS conservan su indicación de revisión manual.",VeloIcons.Check)
        }
    },confirmButton={
        if(status.pending.any{it.state==CheckState.MISSING&&it.step!=SetupStep.OPPO})VButton(setup::startWizard,enabled=!setup.wizardRunning){Text(if(setup.wizardRunning)"Configurando…" else "Configurar")}
        else VButton(setup::dismiss){Text("Listo")}
    },dismissButton={if(status.pending.any{it.state==CheckState.MISSING})VTextButton(setup::dismiss){Text("Más tarde")}})
}

@Composable private fun SetupCheckRow(check: SetupCheck,request: (SetupStep)->Unit,confirmOppo: ()->Unit) {
    val c=MaterialTheme.colorScheme
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(if(check.needsAttention)VeloIcons.Info else VeloIcons.Check,null,Modifier.size(19.dp),if(check.needsAttention)c.secondary else c.primary)
            Column(Modifier.weight(1f)) {
                Text(check.title,fontSize=14.sp,fontWeight=FontWeight.Medium)
                Text(when(check.state){CheckState.READY->"Verificado por Android";CheckState.MISSING->"Pendiente";CheckState.UNKNOWN->"No se pudo comprobar";CheckState.MANUAL->"Revisión manual";CheckState.NOT_NEEDED->if(check.step==SetupStep.OPPO)"Revisado por ti · no verificable" else "No necesario"},fontSize=10.sp,color=if(check.needsAttention)c.secondary else c.onSurfaceVariant)
            }
        }
        Text(check.detail,fontSize=11.sp,lineHeight=17.sp,color=c.onSurfaceVariant)
        if(check.needsAttention||check.step==SetupStep.OPPO) {
            VOutlinedButton({request(check.step)},Modifier.fillMaxWidth()){Text(if(check.step==SetupStep.OPPO||check.step==SetupStep.BACKGROUND)"Abrir información de Velo" else "Revisar ajuste",fontSize=12.sp)}
            if(check.step==SetupStep.OPPO&&check.needsAttention)VTextButton(confirmOppo,Modifier.fillMaxWidth()){Text("He revisado estos ajustes",fontSize=12.sp)}
        }
        HorizontalDivider(color=c.outline.copy(alpha=.25f))
    }
}
