package com.alberto.velo

/** System readiness is separate from the user's filtering rules and backup format. */
enum class SetupStep { FILTER, CONTACTS, NOTIFICATIONS, BATTERY, BACKGROUND, UNUSED_APPS, OPPO }
enum class CheckState { READY, MISSING, UNKNOWN, MANUAL, NOT_NEEDED }
data class SetupCheck(val step: SetupStep,val state: CheckState,val title: String,val detail: String) {
    val needsAttention get() = state in setOf(CheckState.MISSING,CheckState.UNKNOWN,CheckState.MANUAL)
}
data class Readiness(val checks: List<SetupCheck> = emptyList(),val loaded: Boolean = false) {
    val pending get() = checks.filter { it.needsAttention }
    val fingerprint get() = pending.joinToString("|") { "${it.step}:${it.state}" }
    /** A declined or unavailable request is attempted once per explicit wizard run. */
    fun next(attempted: Set<SetupStep>) = pending.firstOrNull {
        it.step !in attempted && it.step != SetupStep.OPPO && it.state == CheckState.MISSING
    }?.step
}

object ReadinessPolicy {
    fun checks(filter: Boolean,filterAvailable: Boolean,contacts: Boolean,notifications: Boolean,
        notificationsWanted: Boolean,batteryExempt: Boolean,backgroundRestricted: Boolean,
        unusedState: CheckState,oppo: Boolean,oppoReviewed: Boolean): Readiness = Readiness(listOfNotNull(
        SetupCheck(SetupStep.FILTER,if(filter)CheckState.READY else CheckState.MISSING,"Filtro de llamadas",
            if(filter)"Velo es el filtro seleccionado en Android." else if(!filterAvailable)"Este sistema no ofrece el rol de filtro. Revisa las apps predeterminadas." else "Selecciona Velo para que Android le entregue las llamadas."),
        SetupCheck(SetupStep.CONTACTS,if(contacts)CheckState.READY else CheckState.MISSING,"Contactos",
            if(contacts)"Permiso concedido. Los contactos se consultan solo en este móvil." else "Necesario para distinguir desconocidos y aplicar reglas a contactos."),
        SetupCheck(SetupStep.NOTIFICATIONS,if(!notificationsWanted)CheckState.NOT_NEEDED else if(notifications)CheckState.READY else CheckState.MISSING,"Avisos de llamadas",
            if(!notificationsWanted)"Los has desactivado en Velo." else if(notifications)"Permiso y canal de llamadas habilitados." else "Permite las notificaciones de Velo y su canal Llamadas filtradas."),
        SetupCheck(SetupStep.BATTERY,if(batteryExempt)CheckState.READY else CheckState.MISSING,"Sin optimización de batería",
            if(batteryExempt)"Android confirma que Velo está en su lista de excepciones." else "Excluye Velo de la optimización para reducir restricciones de energía."),
        SetupCheck(SetupStep.BACKGROUND,if(backgroundRestricted)CheckState.MISSING else CheckState.READY,"Actividad en segundo plano",
            if(backgroundRestricted)"Android informa de una restricción. Permite la actividad en segundo plano." else "Android no informa de una restricción estándar. Los ajustes extra de OPPO se revisan por separado."),
        SetupCheck(SetupStep.UNUSED_APPS,unusedState,"Conservar permisos al no usar Velo",when(unusedState) {
            CheckState.READY->"La retirada automática de permisos por inactividad está desactivada."
            CheckState.NOT_NEEDED->"Este Android no aplica esta función a Velo."
            CheckState.MISSING->"Desactiva Gestionar aplicaciones no usadas para evitar que Android retire permisos o archive Velo."
            else->"No se pudo comprobar este ajuste. Revísalo en la información de Velo."
        }),
        if(oppo)SetupCheck(SetupStep.OPPO,if(oppoReviewed)CheckState.NOT_NEEDED else CheckState.MANUAL,"Ajustes adicionales de OPPO",
            if(oppoReviewed)"Revisión manual registrada. Velo no puede comprobar automáticamente el inicio automático de ColorOS; revísalo si cambias esos ajustes."
            else "En Uso de la batería, permite la actividad en segundo plano. Si aparece Inicio automático, habilítalo. ColorOS no expone todos estos estados a Velo.") else null
    ),loaded=true)
}
