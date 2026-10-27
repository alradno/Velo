package com.alberto.velo

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.content.PackageManagerCompat
import android.net.Uri

/** Foreground-only checks. No polling, services or changes to call-filtering preferences. */
class SetupController(private val activity: MainActivity) {
    var readiness by mutableStateOf(Readiness()); private set
    var visible by mutableStateOf(false); private set
    var wizardRunning by mutableStateOf(false); private set
    var firstVisit by mutableStateOf(false); private set
    private val prefs by lazy { activity.getSharedPreferences("velo-setup",Context.MODE_PRIVATE) }
    private var generation=0
    private var announceNextResume=true
    private var announcementPending=false
    private var externalRequest=false
    private var advancePending=false
    private var attempted=emptySet<SetupStep>()
    private val settingsResult=activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned() }
    private val permissionResult=activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { returned() }

    fun onResume() { val announce=announceNextResume;announceNextResume=false;refresh(announce=announce) }
    fun onStop(changingConfiguration: Boolean) { if(!externalRequest&&!changingConfiguration)announceNextResume=true }
    fun show() { firstVisit=false;visible=true;refresh() }
    fun dismiss() { visible=false;wizardRunning=false }
    fun startWizard() { attempted=emptySet();wizardRunning=true;refresh(advance=true) }
    fun confirmOppoReview() { prefs.edit().putBoolean("oppoReviewed",true).apply();refresh() }
    private fun returned() { externalRequest=false;activity.refresh();refresh(advance=wizardRunning) }

    fun refresh(announce: Boolean=false,advance: Boolean=false) {
        announcementPending=announcementPending||announce
        advancePending=advancePending||advance
        val ticket=++generation
        val future=runCatching { PackageManagerCompat.getUnusedAppRestrictionsStatus(activity) }.getOrNull()
        if(future==null) { apply(ticket,CheckState.UNKNOWN);return }
        future.addListener({
            val status=runCatching { future.get() }.getOrDefault(UnusedAppRestrictionsConstants.ERROR)
            val unused=when(status) {
                UnusedAppRestrictionsConstants.DISABLED->CheckState.READY
                UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE->CheckState.NOT_NEEDED
                UnusedAppRestrictionsConstants.API_30,UnusedAppRestrictionsConstants.API_30_BACKPORT,UnusedAppRestrictionsConstants.API_31->CheckState.MISSING
                else->CheckState.UNKNOWN
            }
            apply(ticket,unused)
        },ContextCompat.getMainExecutor(activity))
    }

    private fun apply(ticket: Int,unused: CheckState) {
        if(ticket!=generation||activity.isDestroyed)return
        val roles=activity.getSystemService(RoleManager::class.java)
        val notifications=activity.getSystemService(NotificationManager::class.java)
        val notificationPermission=Build.VERSION.SDK_INT<33||activity.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        val channel=notifications.getNotificationChannel(CallNotifications.BLOCKED)
        val notificationsReady=notificationPermission&&notifications.areNotificationsEnabled()&&(channel==null||channel.importance!=NotificationManager.IMPORTANCE_NONE)
        readiness=ReadinessPolicy.checks(
            filter=roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),filterAvailable=roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING),
            contacts=activity.hasPermission(Manifest.permission.READ_CONTACTS),notifications=notificationsReady,
            notificationsWanted=activity.state.settings.notifications,
            batteryExempt=activity.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(activity.packageName),
            backgroundRestricted=activity.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
            unusedState=unused,oppo=isColorOs(),oppoReviewed=prefs.getBoolean("oppoReviewed",false))
        if(announcementPending) {
            firstVisit=!prefs.getBoolean("introSeen",false)
            if(firstVisit||readiness.pending.isNotEmpty())visible=true
            prefs.edit().putBoolean("introSeen",true).apply()
            announcementPending=false
        }
        if(advancePending&&wizardRunning) {
            advancePending=false
            if(!activity.unlocked) {wizardRunning=false;return}
            val next=readiness.next(attempted)
            if(next==null)wizardRunning=false else {attempted=attempted+next;request(next)}
        }
    }

    fun request(step: SetupStep) {
        when(step) {
            SetupStep.FILTER->{
                val roles=activity.getSystemService(RoleManager::class.java)
                open(if(roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING))roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING) else Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            SetupStep.CONTACTS->permission(Manifest.permission.READ_CONTACTS,"contactsAsked")
            SetupStep.NOTIFICATIONS->{
                if(Build.VERSION.SDK_INT>=33&&!activity.hasPermission(Manifest.permission.POST_NOTIFICATIONS))permission(Manifest.permission.POST_NOTIFICATIONS,"notificationsAsked")
                else {
                    val channel=activity.getSystemService(NotificationManager::class.java).getNotificationChannel(CallNotifications.BLOCKED)
                    val intent=if(channel?.importance==NotificationManager.IMPORTANCE_NONE)Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID,CallNotifications.BLOCKED)
                        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    open(intent.putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName))
                }
            }
            SetupStep.BATTERY->open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,packageUri()),Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            SetupStep.UNUSED_APPS->{
                // UNKNOWN uses app info; IntentCompat requires a confirmed available feature.
                val available=readiness.checks.any {it.step==step&&it.state==CheckState.MISSING}
                open(if(available)IntentCompat.createManageUnusedAppRestrictionsIntent(activity,activity.packageName) else appInfo())
            }
            SetupStep.BACKGROUND,SetupStep.OPPO->open(appInfo())
        }
    }
    private fun permission(permission: String,key: String) {
        if(prefs.getBoolean(key,false)&&!activity.shouldShowRequestPermissionRationale(permission)) {open(appInfo());return}
        prefs.edit().putBoolean(key,true).apply()
        externalRequest=true
        runCatching {permissionResult.launch(arrayOf(permission))}.onFailure {failed()}
    }
    private fun open(intent: Intent,fallback: Intent=appInfo()) {
        externalRequest=true
        runCatching {settingsResult.launch(intent)}.recoverCatching {settingsResult.launch(fallback)}.onFailure {failed()}
    }
    private fun failed() {externalRequest=false;wizardRunning=false;activity.toast("No se pudo abrir ese ajuste. Puedes revisarlo en la información de Velo.");refresh()}
    private fun packageUri()=Uri.parse("package:${activity.packageName}")
    private fun appInfo()=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri())
    private fun isColorOs()=listOf(Build.MANUFACTURER,Build.BRAND).any {it.lowercase() in setOf("oppo","oplus","realme","oneplus")}
}
