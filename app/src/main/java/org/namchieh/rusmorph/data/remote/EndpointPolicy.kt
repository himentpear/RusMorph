package org.namchieh.rusmorph.data.remote

import java.net.URI

/** Keeps local-development cleartext from accidentally becoming a production endpoint. */
object EndpointPolicy {
    fun isAllowed(baseUrl: String, allowCleartext: Boolean): Boolean {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return false
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) return false
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> allowCleartext
            else -> false
        }
    }

    fun normalized(baseUrl: String, allowCleartext: Boolean): String? =
        baseUrl.trim().takeIf { isAllowed(it, allowCleartext) }
            ?.let { if (it.endsWith('/')) it else "$it/" }
}
