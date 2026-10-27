package com.alberto.velo

import android.Manifest
import android.app.*
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.telecom.*
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

fun Context.hasPermission(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
fun Context.hasRole(role: String) = getSystemService(RoleManager::class.java).isRoleHeld(role)

object CallFacts {
    fun identity(details: Call.Details): Identity = when (details.handlePresentation) {
        TelecomManager.PRESENTATION_RESTRICTED -> Identity.PRIVATE
        TelecomManager.PRESENTATION_UNKNOWN, TelecomManager.PRESENTATION_UNAVAILABLE, TelecomManager.PRESENTATION_PAYPHONE -> Identity.UNAVAILABLE
        else -> if (details.handle?.schemeSpecificPart.isNullOrBlank()) Identity.UNAVAILABLE else Identity.VISIBLE
    }
    fun lookup(context: Context, number: String, signal: CancellationSignal?): Boolean? {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return null
        return runCatching {
            context.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)), arrayOf(ContactsContract.PhoneLookup._ID), null, null, null, signal)?.use { it.moveToFirst() }
        }.getOrNull()
    }
    fun input(context: Context, store: Store, details: Call.Details, signal: CancellationSignal? = null, state: State = store.load()): CallInput {
        val identity = identity(details)
        val number = if (identity == Identity.VISIBLE) details.handle?.schemeSpecificPart.orEmpty() else ""
        val canonical = store.canonical(number,state.settings.region)
        val audio = context.getSystemService(AudioManager::class.java)
        val voip = audio.mode == AudioManager.MODE_IN_COMMUNICATION
        val last = if (state.profile.repeated && number.isNotBlank()) HistoryWrites.recent.latest(canonical,System.currentTimeMillis()) ?: store.lastAttempt(number) else null
        val emergency = details.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE) || runCatching { context.getSystemService(android.telephony.TelephonyManager::class.java).isEmergencyNumber(number) }.getOrDefault(false)
        return CallInput(number, canonical, identity, null, voip, last, emergency)
    }
}

object Responses {
    fun build(d: Decision, settings: Settings): CallScreeningService.CallResponse = CallScreeningService.CallResponse.Builder().apply {
        when (d.action) {
            Action.ALLOW -> setDisallowCall(false)
            Action.REJECT -> { setDisallowCall(true); setRejectCall(true); setSkipNotification(settings.skipSystemNotification) }
            Action.IGNORE -> { setDisallowCall(true); setRejectCall(false); setSkipNotification(settings.skipSystemNotification) }
            Action.SILENCE -> { setDisallowCall(false); setSilenceCall(true) }
        }
        setSkipCallLog(false)
    }.build()
}

class VeloScreeningService : CallScreeningService() {
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        val started = SystemClock.elapsedRealtime()
        val store = Store(this)
        val state = store.load()
        val answered = AtomicBoolean(false)
        val signal = CancellationSignal()
        val timeout = Runnable {
            if (answered.compareAndSet(false, true)) {
                runCatching { respondToCall(details, Responses.build(Decision(Action.ALLOW, "Tiempo de consulta agotado"), state.settings)) }
                signal.cancel()
            }
        }
        main.postDelayed(timeout, 3500)
        executor.execute {
            var input = runCatching { CallFacts.input(this, store, details, signal, state) }.getOrNull()
            var decision = if (input == null) Decision(Action.ALLOW, "No se pudo verificar; se permite por seguridad") else RuleEngine.decide(state, input!!)
            if (input != null && decision.needsContacts && !answered.get()) {
                input = input!!.copy(inContacts = CallFacts.lookup(this,input!!.number,signal))
                decision = RuleEngine.decide(state,input!!)
            }
            if (answered.compareAndSet(false, true)) {
                main.removeCallbacks(timeout)
                val sent = runCatching { respondToCall(details, Responses.build(decision, state.settings)) }.isSuccess
                if (input != null && sent) {
                    val event = CallEvent(number = input.number, identity = input.identity, action = decision.action, reason = decision.reason, elapsedMs = SystemClock.elapsedRealtime() - started)
                    if (input.identity == Identity.VISIBLE && (event.action != Action.ALLOW || state.settings.logAllowed)) HistoryWrites.recent.record(input.canonical,event.time)
                    HistoryWrites.executor.execute {
                        runCatching { store.record(event) }.onFailure { android.util.Log.e("Velo", "No se pudo registrar la llamada", it) }
                        CallNotifications.blocked(applicationContext,event,state.settings)
                    }
                }
            }
        }
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}

private object HistoryWrites { val executor = Executors.newSingleThreadExecutor(); val recent = RecentAttempts() }

object CallNotifications {
    const val BLOCKED = "velo-blocked"
    fun blocked(context: Context, event: CallEvent, settings: Settings) {
        if (!settings.notifications || event.action == Action.ALLOW || !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS) && Build.VERSION.SDK_INT >= 33) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(BLOCKED, "Llamadas filtradas", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(event.id.hashCode(), NotificationCompat.Builder(context, BLOCKED).setSmallIcon(R.drawable.ic_velo).setContentTitle("Velo · ${event.action.label}").setContentText("${event.number.ifBlank { event.identity.label }} · ${event.reason}").setContentIntent(open).setAutoCancel(true).build())
    }
}


