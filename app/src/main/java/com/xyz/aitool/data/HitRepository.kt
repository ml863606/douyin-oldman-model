package com.xyz.aitool.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HitRepository {
    private const val PREFS_NAME = "hits"
    private const val KEY_HITS = "recent_hits"
    private const val KEY_VIDEO_LOGS = "video_logs"
    private const val KEY_OPERATION_LOGS = "operation_logs"
    private const val KEY_SQLITE_MIGRATED = "sqlite_migrated"
    private const val KEY_CUSTOM_RULES = "custom_rules"
    private const val KEY_MONITORED_PACKAGES = "monitored_packages"
    private const val KEY_APP_INFO_CACHE = "app_info_cache"
    private const val KEY_RULES_ENABLED = "rules_enabled"
    private const val KEY_ALERT_MESSAGE = "alert_message"
    private const val KEY_ALERT_ACTION = "alert_action"
    private const val KEY_ALERT_SIZE = "alert_size"
    private const val KEY_RECOGNITION_MODE = "recognition_mode"
    private const val KEY_WARNING_FONT_SIZE = "warning_font_size"
    private const val KEY_DEBUG_MODE = "debug_mode"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    const val DEFAULT_ALERT_MESSAGE = "检测到疑似风险内容，请谨慎观看。"
    const val DEFAULT_WARNING_FONT_SIZE = 42
    private const val MAX_HITS = 30
    private const val MAX_VIDEO_LOGS = 80
    private const val MAX_OPERATION_LOGS = 120
    val DEFAULT_MONITORED_PACKAGES = setOf(
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.aweme.lite",
    )

    fun areRulesEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_RULES_ENABLED, true)
    }

    fun setRulesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RULES_ENABLED, enabled).apply()
    }

    fun getAlertMessage(context: Context): String {
        return prefs(context).getString(KEY_ALERT_MESSAGE, DEFAULT_ALERT_MESSAGE)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_ALERT_MESSAGE
    }

    fun setAlertMessage(context: Context, message: String) {
        val cleaned = message.trim().ifBlank { DEFAULT_ALERT_MESSAGE }
        prefs(context).edit().putString(KEY_ALERT_MESSAGE, cleaned).apply()
    }

    fun getAlertAction(context: Context): AlertAction {
        val raw = prefs(context).getString(KEY_ALERT_ACTION, AlertAction.STRONG_REMINDER.name)
        return runCatching {
            AlertAction.valueOf(raw ?: AlertAction.STRONG_REMINDER.name)
        }.getOrDefault(AlertAction.STRONG_REMINDER)
    }

    fun setAlertAction(context: Context, action: AlertAction) {
        prefs(context).edit().putString(KEY_ALERT_ACTION, action.name).apply()
    }

    fun getAlertSize(context: Context): AlertSize {
        val raw = prefs(context).getString(KEY_ALERT_SIZE, AlertSize.FULLSCREEN.name)
        return runCatching {
            AlertSize.valueOf(raw ?: AlertSize.FULLSCREEN.name)
        }.getOrDefault(AlertSize.FULLSCREEN)
    }

    fun setAlertSize(context: Context, size: AlertSize) {
        prefs(context).edit().putString(KEY_ALERT_SIZE, size.name).apply()
    }

    fun getRecognitionMode(context: Context): RecognitionMode {
        val raw = prefs(context).getString(KEY_RECOGNITION_MODE, RecognitionMode.AUTO.name)
        return runCatching {
            RecognitionMode.valueOf(raw ?: RecognitionMode.AUTO.name)
        }.getOrDefault(RecognitionMode.AUTO)
    }

    fun setRecognitionMode(context: Context, mode: RecognitionMode) {
        prefs(context).edit().putString(KEY_RECOGNITION_MODE, mode.name).apply()
    }

    fun getWarningFontSize(context: Context): Int {
        return prefs(context)
            .getInt(KEY_WARNING_FONT_SIZE, DEFAULT_WARNING_FONT_SIZE)
            .coerceIn(24, 42)
    }

    fun setWarningFontSize(context: Context, size: Int) {
        prefs(context)
            .edit()
            .putInt(KEY_WARNING_FONT_SIZE, size.coerceIn(24, 42))
            .apply()
    }

    fun isDebugModeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DEBUG_MODE, true)
    }

    fun setDebugModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun getHits(context: Context): List<RiskHit> {
        ensureSqliteMigrated(context)
        return LogDatabase.get(context).getHits(MAX_HITS)
    }

    fun recordHit(context: Context, hit: RiskHit) {
        ensureSqliteMigrated(context)
        LogDatabase.get(context).insertHit(hit, MAX_HITS)
    }

    fun clearHits(context: Context) {
        ensureSqliteMigrated(context)
        LogDatabase.get(context).clearHits()
    }

    fun getVideoLogs(context: Context): List<ParsedVideoLog> {
        ensureSqliteMigrated(context)
        return LogDatabase.get(context).getVideoLogs(MAX_VIDEO_LOGS)
    }

    fun recordVideoLog(context: Context, log: ParsedVideoLog) {
        ensureSqliteMigrated(context)
        LogDatabase.get(context).insertVideoLog(log, MAX_VIDEO_LOGS)
    }

    fun clearVideoLogs(context: Context) {
        ensureSqliteMigrated(context)
        LogDatabase.get(context).clearVideoLogs()
    }

    fun getOperationLogs(context: Context): List<OperationLog> {
        ensureSqliteMigrated(context)
        return LogDatabase.get(context).getOperationLogs(MAX_OPERATION_LOGS)
    }

    fun recordOperationLog(context: Context, action: String, message: String) {
        val now = System.currentTimeMillis()
        ensureSqliteMigrated(context)
        LogDatabase.get(context).insertOperationLog(
            OperationLog(
                id = now * 1000L + (System.nanoTime() % 1000L),
                timeMillis = now,
                action = action,
                message = message,
            ),
            MAX_OPERATION_LOGS,
        )
    }

    fun clearOperationLogs(context: Context) {
        ensureSqliteMigrated(context)
        LogDatabase.get(context).clearOperationLogs()
    }

    fun clearRecordLogs(context: Context) {
        ensureSqliteMigrated(context)
        LogDatabase.get(context).clearAllRecordLogs()
    }

    fun getCustomRules(context: Context): List<CustomRule> {
        val raw = prefs(context).getString(KEY_CUSTOM_RULES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toCustomRule())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addCustomRule(context: Context, target: RuleTarget, keyword: String) {
        val cleaned = keyword.trim()
        if (cleaned.isBlank()) return

        val rules = getCustomRules(context)
        val exists = rules.any {
            it.target == target && it.keyword.equals(cleaned, ignoreCase = true)
        }
        if (exists) return

        saveCustomRules(
            context,
            listOf(
                CustomRule(
                    id = System.currentTimeMillis(),
                    target = target,
                    keyword = cleaned,
                ),
            ) + rules,
        )
    }

    fun removeCustomRule(context: Context, id: Long) {
        saveCustomRules(context, getCustomRules(context).filterNot { it.id == id })
    }

    fun updateCustomRule(context: Context, id: Long, target: RuleTarget, keyword: String) {
        val cleaned = keyword.trim()
        if (cleaned.isBlank()) return

        val rules = getCustomRules(context)
        val exists = rules.any {
            it.id != id && it.target == target && it.keyword.equals(cleaned, ignoreCase = true)
        }
        if (exists) return

        saveCustomRules(
            context,
            rules.map { rule ->
                if (rule.id == id) {
                    rule.copy(target = target, keyword = cleaned)
                } else {
                    rule
                }
            },
        )
    }

    fun getMonitoredPackages(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_MONITORED_PACKAGES, null)
        if (raw.isNullOrBlank()) return DEFAULT_MONITORED_PACKAGES

        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    val packageName = array.optString(index)
                    if (packageName.isNotBlank()) {
                        add(packageName)
                    }
                }
            }
        }.getOrDefault(DEFAULT_MONITORED_PACKAGES)
    }

    fun setPackageMonitored(
        context: Context,
        packageName: String,
        selected: Boolean,
        label: String,
        iconBase64: String,
    ) {
        val updated = getMonitoredPackages(context).toMutableSet().apply {
            if (selected) {
                add(packageName)
            } else {
                remove(packageName)
            }
        }
        if (label.isNotBlank() || iconBase64.isNotBlank()) {
            cacheAppInfo(context, AppInfoCache(packageName, label, iconBase64))
        }
        saveMonitoredPackages(context, updated)
    }

    fun getCachedAppInfo(context: Context, packageName: String): AppInfoCache? {
        return getCachedApps(context).firstOrNull { it.packageName == packageName }
    }

    fun getCachedApps(context: Context): List<AppInfoCache> {
        val raw = prefs(context).getString(KEY_APP_INFO_CACHE, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toAppInfoCache())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ensureSqliteMigrated(context: Context) {
        val preferences = prefs(context)
        if (preferences.getBoolean(KEY_SQLITE_MIGRATED, false)) {
            LogDatabase.get(context).cleanupExpired()
            return
        }

        val database = LogDatabase.get(context)
        parseRiskHits(preferences.getString(KEY_HITS, "[]") ?: "[]")
            .forEach { database.insertHit(it, MAX_HITS) }
        parseVideoLogs(preferences.getString(KEY_VIDEO_LOGS, "[]") ?: "[]")
            .forEach { database.insertVideoLog(it, MAX_VIDEO_LOGS) }
        parseOperationLogs(preferences.getString(KEY_OPERATION_LOGS, "[]") ?: "[]")
            .forEach { database.insertOperationLog(it, MAX_OPERATION_LOGS) }
        database.cleanupExpired()

        preferences.edit()
            .putBoolean(KEY_SQLITE_MIGRATED, true)
            .remove(KEY_HITS)
            .remove(KEY_VIDEO_LOGS)
            .remove(KEY_OPERATION_LOGS)
            .apply()
    }

    private fun parseRiskHits(raw: String): List<RiskHit> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toRiskHit())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseVideoLogs(raw: String): List<ParsedVideoLog> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toParsedVideoLog())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseOperationLogs(raw: String): List<OperationLog> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toOperationLog())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomRules(context: Context, rules: List<CustomRule>) {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_CUSTOM_RULES, array.toString()).apply()
    }

    private fun saveMonitoredPackages(context: Context, packageNames: Set<String>) {
        val array = JSONArray()
        packageNames.sorted().forEach { array.put(it) }
        prefs(context).edit().putString(KEY_MONITORED_PACKAGES, array.toString()).apply()
    }

    private fun cacheAppInfo(context: Context, appInfo: AppInfoCache) {
        val updated = (listOf(appInfo) + getCachedApps(context))
            .distinctBy { it.packageName }
            .take(120)
        val array = JSONArray()
        updated.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_APP_INFO_CACHE, array.toString()).apply()
    }

    private fun RiskHit.toJson(): JSONObject {
        val rules = JSONArray()
        matchedRules.forEach { rules.put(it) }
        return JSONObject()
            .put("id", id)
            .put("timeMillis", timeMillis)
            .put("text", text)
            .put("matchedRules", rules)
            .put("score", score)
            .put("source", source)
            .put("ocrDurationMillis", ocrDurationMillis ?: -1L)
            .put("recognitionDurationMillis", recognitionDurationMillis ?: -1L)
            .put("appearanceToRecognitionMillis", appearanceToRecognitionMillis ?: -1L)
    }

    private fun JSONObject.toRiskHit(): RiskHit {
        val rules = getJSONArray("matchedRules")
        return RiskHit(
            id = optLong("id"),
            timeMillis = optLong("timeMillis"),
            text = optString("text"),
            matchedRules = buildList {
                for (index in 0 until rules.length()) {
                    add(rules.optString(index))
                }
            },
            score = optInt("score"),
            source = optString("source", "未知"),
            ocrDurationMillis = optLong("ocrDurationMillis", -1L).takeIf { it >= 0L },
            recognitionDurationMillis = optLong("recognitionDurationMillis", -1L).takeIf { it >= 0L },
            appearanceToRecognitionMillis = optLong("appearanceToRecognitionMillis", -1L).takeIf { it >= 0L },
        )
    }

    private fun ParsedVideoLog.toJson(): JSONObject {
        val tagArray = JSONArray()
        tags.forEach { tagArray.put(it) }
        return JSONObject()
            .put("id", id)
            .put("timeMillis", timeMillis)
            .put("packageName", packageName)
            .put("appLabel", appLabel)
            .put("author", author)
            .put("title", title)
            .put("content", content)
            .put("tags", tagArray)
            .put("rawText", rawText)
            .put("source", source)
            .put("ocrDurationMillis", ocrDurationMillis ?: -1L)
            .put("recognitionDurationMillis", recognitionDurationMillis ?: -1L)
            .put("appearanceToRecognitionMillis", appearanceToRecognitionMillis ?: -1L)
    }

    private fun OperationLog.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("timeMillis", timeMillis)
            .put("action", action)
            .put("message", message)
    }

    private fun JSONObject.toOperationLog(): OperationLog {
        return OperationLog(
            id = optLong("id"),
            timeMillis = optLong("timeMillis"),
            action = optString("action"),
            message = optString("message"),
        )
    }

    private fun JSONObject.toParsedVideoLog(): ParsedVideoLog {
        val tagArray = optJSONArray("tags") ?: JSONArray()
        return ParsedVideoLog(
            id = optLong("id"),
            timeMillis = optLong("timeMillis"),
            packageName = optString("packageName"),
            appLabel = optString("appLabel", optString("packageName", "未知App")),
            author = optString("author"),
            title = optString("title"),
            content = optString("content"),
            tags = buildList {
                for (index in 0 until tagArray.length()) {
                    add(tagArray.optString(index))
                }
            },
            rawText = optString("rawText"),
            source = optString("source", "未知"),
            ocrDurationMillis = optLong("ocrDurationMillis", -1L).takeIf { it >= 0L },
            recognitionDurationMillis = optLong("recognitionDurationMillis", -1L).takeIf { it >= 0L },
            appearanceToRecognitionMillis = optLong("appearanceToRecognitionMillis", -1L).takeIf { it >= 0L },
        )
    }

    private fun CustomRule.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("target", target.name)
            .put("keyword", keyword)
            .put("enabled", enabled)
    }

    private fun JSONObject.toCustomRule(): CustomRule {
        val target = runCatching {
            RuleTarget.valueOf(optString("target", RuleTarget.TITLE.name))
        }.getOrDefault(RuleTarget.TITLE)
        return CustomRule(
            id = optLong("id"),
            target = target,
            keyword = optString("keyword"),
            enabled = optBoolean("enabled", true),
        )
    }

    private fun AppInfoCache.toJson(): JSONObject {
        return JSONObject()
            .put("packageName", packageName)
            .put("label", label)
            .put("iconBase64", iconBase64)
    }

    private fun JSONObject.toAppInfoCache(): AppInfoCache {
        return AppInfoCache(
            packageName = optString("packageName"),
            label = optString("label"),
            iconBase64 = optString("iconBase64"),
        )
    }
}
