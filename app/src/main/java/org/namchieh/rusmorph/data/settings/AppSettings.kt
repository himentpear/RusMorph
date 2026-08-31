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

    fun setDeckLimit(value: Int) {
        val sanitized = sanitizeDeckLimit(value)
        preferences.edit().putInt(DECK_LIMIT_KEY, sanitized).apply()
        mutableDeckLimit.value = sanitized
    }

    companion object {
        const val DEFAULT_DECK_LIMIT = 50
        const val MAX_DECK_LIMIT = 200
        val DECK_LIMIT_OPTIONS = listOf(50, 100, 150, 200)

        private const val PREFERENCES_NAME = "app_settings"
        private const val DECK_LIMIT_KEY = "deck_card_limit"

        fun sanitizeDeckLimit(value: Int): Int =
            DECK_LIMIT_OPTIONS.firstOrNull { it == value } ?: DEFAULT_DECK_LIMIT
    }
}
