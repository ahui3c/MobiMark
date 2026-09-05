package tw.ahuimark.battery.core

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.annotation.RequiresApi
import org.json.JSONObject
import tw.ahuimark.battery.MainActivity
import java.io.File

/** Own-rule management on Android 10+, guarded global-policy restoration on Android 8/9. */
class TestDndController(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val prefs = app.getSharedPreferences("test_dnd_recovery", Context.MODE_PRIVATE)
    private val conditionId = Uri.parse("condition://${app.packageName}/battery-test")
    val hasAccess: Boolean get() = manager.isNotificationPolicyAccessGranted
    val hasPendingRecovery: Boolean get() = prefs.contains("snapshot")

    fun isProtectionActive(): Boolean = runCatching {
        if (!hasAccess) return@runCatching false
        if (Build.VERSION.SDK_INT >= 29) {
            manager.automaticZenRules.any { (id, rule) ->
                rule.conditionId == conditionId && rule.isEnabled &&
                    manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    (if (Build.VERSION.SDK_INT >= 35) manager.getAutomaticZenRuleState(id) == Condition.STATE_TRUE
                    else manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } else manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }.getOrDefault(false)

    private val guard = DndSessionGuard(object : DndBackend {
        override val hasAccess get() = this@TestDndController.hasAccess
        override fun capture(): String = if (Build.VERSION.SDK_INT >= 29) {
            JSONObject().put("mode", "rule").toString()
        } else {
            JSONObject().put("mode", "legacy")
                .put("filter", manager.currentInterruptionFilter)
                .put("policy", encodePolicy(manager.notificationPolicy))
                .put("applied", encodePolicy(legacyPolicy())).toString()
        }

        override fun activate(snapshot: String) {
            if (Build.VERSION.SDK_INT >= 29) activateRule() else {
                manager.notificationPolicy = legacyPolicy()
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        }

        override fun restore(snapshot: String) {
            val saved = JSONObject(snapshot)
            if (saved.getString("mode") == "rule") {
                // Matching condition URI also finds a rule created just before a process crash,
                // even if the returned ID was never persisted. Never touch other rules.
                manager.automaticZenRules.filterValues { it.conditionId == conditionId }.keys.forEach {
                    check(manager.removeAutomaticZenRule(it)) { "系統尚未移除測試勿擾規則" }
                }
            } else {
                val applied = saved.getJSONObject("applied")
                val original = saved.getJSONObject("policy")
                val current = encodePolicy(manager.notificationPolicy).toString()
                // Do not overwrite a policy/filter the user changed during the test.
                if (current == applied.toString()) {
                    manager.notificationPolicy = decodePolicy(original)
                }
                if ((current == applied.toString() || current == original.toString()) &&
                    manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    manager.setInterruptionFilter(saved.getInt("filter"))
                }
            }
        }
    }, object : DndJournal {
        override fun read(): String? = prefs.getString("snapshot", null)
        override fun write(snapshot: String?) {
            check(prefs.edit().apply {
                if (snapshot == null) remove("snapshot") else putString("snapshot", snapshot)
            }.commit()) { "無法儲存勿擾復原紀錄" }
        }
    })

    fun begin(sessionId: String, enabled: Boolean): Result<Boolean> {
        // Finish recovery against the OLD session's audit before starting a new one.
        end("before_start").onFailure { return Result.failure(it) }
        if (!prefs.edit().putString("audit_session", sessionId).commit()) {
            return Result.failure(IllegalStateException("無法儲存勿擾測試紀錄"))
        }
        return guard.begin(enabled).also { result ->
            event(if (result.isSuccess) if (result.getOrThrow()) "enabled" else "disabled_by_setting" else "enable_failed",
                result.exceptionOrNull()?.message.orEmpty())
        }
    }

    fun end(reason: String): Result<Boolean> = guard.end().also { result ->
        if (result.getOrNull() == true) event("restored", reason)
        if (result.isFailure) event("restore_failed", result.exceptionOrNull()?.message.orEmpty())
    }

    fun event(name: String, detail: String = "") {
        val id = prefs.getString("audit_session", null) ?: return
        runCatching {
            val directory = File(app.filesDir, "test-events").apply { mkdirs() }
            File(directory, "$id.jsonl").appendText(JSONObject().put("timeMs", System.currentTimeMillis())
                .put("event", name).put("detail", detail)
                .put("systemFilter", manager.currentInterruptionFilter).toString() + "\n")
        }.onFailure { android.util.Log.e("MobiMarkDnd", "Audit write failed", it) }
    }

    @RequiresApi(29)
    private fun activateRule() {
        val builder = ZenPolicy.Builder().disallowAllSounds().allowMedia(true).hideAllVisualEffects()
        if (Build.VERSION.SDK_INT >= 30) builder.allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
        if (Build.VERSION.SDK_INT >= 35) builder.allowPriorityChannels(false)
        val policy = builder.build()
        val rule = AutomaticZenRule("MobiMark 續航測試", null,
            ComponentName(app, MainActivity::class.java), conditionId, policy,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
        val id = manager.addAutomaticZenRule(rule)
        check(!id.isNullOrBlank()) { "系統無法建立測試勿擾規則" }
        manager.setAutomaticZenRuleState(id,
            Condition(conditionId, "續航測試進行中", Condition.STATE_TRUE))
        check(manager.getAutomaticZenRule(id)?.isEnabled == true) { "測試勿擾規則未啟用" }
    }

    @Suppress("DEPRECATION")
    private fun legacyPolicy(): NotificationManager.Policy = NotificationManager.Policy(
        if (Build.VERSION.SDK_INT >= 28) NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA else 0,
        NotificationManager.Policy.PRIORITY_SENDERS_ANY,
        NotificationManager.Policy.PRIORITY_SENDERS_ANY,
        if (Build.VERSION.SDK_INT >= 28) NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
            NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or
            NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR or NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or
            NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT or NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST else
            NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON or NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF
    )

    @Suppress("DEPRECATION")
    private fun encodePolicy(policy: NotificationManager.Policy) = JSONObject()
        .put("categories", policy.priorityCategories).put("calls", policy.priorityCallSenders)
        .put("messages", policy.priorityMessageSenders).put("visual",
            // Android 9 may add deprecated SCREEN_ON/OFF aliases to the detailed flags.
            // Compare their canonical form so our policy is still recognized at cleanup.
            if (Build.VERSION.SDK_INT >= 28) policy.suppressedVisualEffects and
                (NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF).inv()
            else policy.suppressedVisualEffects)

    private fun decodePolicy(json: JSONObject) = NotificationManager.Policy(
        json.getInt("categories"), json.getInt("calls"), json.getInt("messages"), json.getInt("visual"))
}
