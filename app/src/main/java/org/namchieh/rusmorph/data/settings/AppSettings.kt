package org.namchieh.rusmorph.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableDeckLimit = MutableStateFlow(
        sanitizeDeckLimit(preferences.getInt(DECK_LIMIT_KEY, DEFAULT_DECK_LIMIT)),
    )
    val deckLimit: StateFlow<Int> = mutableDeckLimit.asStateFlow()

    private val mutableActiveCourseId = MutableStateFlow(
        preferences.getString(ACTIVE_COURSE_KEY, DEFAULT_COURSE_ID) ?: DEFAULT_COURSE_ID
    )
    val activeCourseId: StateFlow<String> = mutableActiveCourseId.asStateFlow()

    private val mutableDailyNewWordTarget = MutableStateFlow(
        preferences.getInt(DAILY_NEW_WORD_KEY, DEFAULT_DAILY_NEW_WORDS)
    )
    val dailyNewWordTarget: StateFlow<Int> = mutableDailyNewWordTarget.asStateFlow()

    private val mutableAutoAdvanceEnabled = MutableStateFlow(
        preferences.getBoolean(AUTO_ADVANCE_KEY, true)
    )
    val autoAdvanceEnabled: StateFlow<Boolean> = mutableAutoAdvanceEnabled.asStateFlow()

    private val mutableDeveloperMode = MutableStateFlow(
        preferences.getBoolean(DEVELOPER_MODE_KEY, false),
    )
    val developerMode: StateFlow<Boolean> = mutableDeveloperMode.asStateFlow()

    fun setDeckLimit(value: Int) {
        val sanitized = sanitizeDeckLimit(value)
        preferences.edit().putInt(DECK_LIMIT_KEY, sanitized).apply()
        mutableDeckLimit.value = sanitized
    }

    fun setActiveCourseId(courseId: String) {
        preferences.edit().putString(ACTIVE_COURSE_KEY, courseId).apply()
        mutableActiveCourseId.value = courseId
    }

    fun setDailyNewWordTarget(target: Int) {
        val sanitized = if (target in DAILY_WORD_OPTIONS) target else DEFAULT_DAILY_NEW_WORDS
        preferences.edit().putInt(DAILY_NEW_WORD_KEY, sanitized).apply()
        mutableDailyNewWordTarget.value = sanitized
    }

    fun setAutoAdvanceEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(AUTO_ADVANCE_KEY, enabled).apply()
        mutableAutoAdvanceEnabled.value = enabled
    }

    fun setDeveloperMode(enabled: Boolean) {
        preferences.edit().putBoolean(DEVELOPER_MODE_KEY, enabled).apply()
        mutableDeveloperMode.value = enabled
    }

    fun getLastAutoAdvanceDate(): String = preferences.getString(LAST_AUTO_ADVANCE_DATE_KEY, "") ?: ""

    fun setLastAutoAdvanceDate(date: String) {
        preferences.edit().putString(LAST_AUTO_ADVANCE_DATE_KEY, date).apply()
    }

    fun getLastUpdateCheckTimestamp(): Long = preferences.getLong(LAST_UPDATE_CHECK_KEY, 0L)

    fun setLastUpdateCheckTimestamp(value: Long) {
        preferences.edit().putLong(LAST_UPDATE_CHECK_KEY, value.coerceAtLeast(0L)).apply()
    }

    fun getSkippedUpdateVersionCode(): Int = preferences.getInt(SKIPPED_UPDATE_VERSION_KEY, 0)

    fun setSkippedUpdateVersionCode(value: Int) {
        preferences.edit().putInt(SKIPPED_UPDATE_VERSION_KEY, value.coerceAtLeast(0)).apply()
    }

    fun clearSkippedUpdateVersion() {
        preferences.edit().remove(SKIPPED_UPDATE_VERSION_KEY).apply()
    }

    companion object {
        const val DEFAULT_DECK_LIMIT = 50
        const val MAX_DECK_LIMIT = 200
        val DECK_LIMIT_OPTIONS = listOf(50, 100, 150, 200)

        const val DEFAULT_COURSE_ID = "university-russian-1"
        const val COURSE_1_ID = "university-russian-1"
        const val COURSE_2_ID = "university-russian-2"

        const val DEFAULT_DAILY_NEW_WORDS = 10
        val DAILY_WORD_OPTIONS = listOf(5, 10, 15, 20, 30)

        private const val PREFERENCES_NAME = "app_settings"
        private const val DECK_LIMIT_KEY = "deck_card_limit"
        private const val ACTIVE_COURSE_KEY = "active_course_id"
        private const val DAILY_NEW_WORD_KEY = "daily_new_word_target"
        private const val AUTO_ADVANCE_KEY = "auto_advance_enabled"
        private const val DEVELOPER_MODE_KEY = "developer_mode"
        private const val LAST_AUTO_ADVANCE_DATE_KEY = "last_auto_advance_date"
        private const val LAST_UPDATE_CHECK_KEY = "last_update_check_timestamp"
        private const val SKIPPED_UPDATE_VERSION_KEY = "skipped_update_version_code"
        fun sanitizeDeckLimit(value: Int): Int =
            DECK_LIMIT_OPTIONS.firstOrNull { it == value } ?: DEFAULT_DECK_LIMIT
    }
}
