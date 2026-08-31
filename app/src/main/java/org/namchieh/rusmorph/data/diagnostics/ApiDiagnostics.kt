package org.namchieh.rusmorph.data.diagnostics

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApiTraceEvent(val operation: String, val requestId: String, val outcome: String, val httpStatus: Int? = null, val errorCode: String? = null, val elapsedMs: Long? = null)

interface AgentDiagnostics {
    val enabled: StateFlow<Boolean>
    val events: StateFlow<List<ApiTraceEvent>>
    fun setEnabled(value: Boolean)
    fun started(operation: String, requestId: String)
    fun finished(operation: String, requestId: String, httpStatus: Int?, errorCode: String?, elapsedMs: Long)
}

class ApiDiagnostics(context: Context) : AgentDiagnostics {
    private val preferences = context.getSharedPreferences("api_diagnostics", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    override val enabled = _enabled.asStateFlow()
    private val _events = MutableStateFlow<List<ApiTraceEvent>>(emptyList())
    override val events = _events.asStateFlow()
    override fun setEnabled(value: Boolean) { preferences.edit().putBoolean(KEY_ENABLED, value).apply(); _enabled.value = value; if (!value) _events.value = emptyList() }
    override fun started(operation: String, requestId: String) { if (_enabled.value) append(ApiTraceEvent(operation, requestId, "请求中")) }
    override fun finished(operation: String, requestId: String, httpStatus: Int?, errorCode: String?, elapsedMs: Long) {
        if (_enabled.value) append(ApiTraceEvent(operation, requestId, if (httpStatus in 200..299 && errorCode == null) "成功" else "失败", httpStatus, errorCode, elapsedMs))
    }
    private fun append(event: ApiTraceEvent) { _events.value = (listOf(event) + _events.value).take(30) }
    private companion object { const val KEY_ENABLED = "enabled" }
}

object NoopAgentDiagnostics : AgentDiagnostics {
    private val disabled = MutableStateFlow(false)
    private val emptyEvents = MutableStateFlow<List<ApiTraceEvent>>(emptyList())
    override val enabled = disabled.asStateFlow()
    override val events = emptyEvents.asStateFlow()
    override fun setEnabled(value: Boolean) = Unit
    override fun started(operation: String, requestId: String) = Unit
    override fun finished(operation: String, requestId: String, httpStatus: Int?, errorCode: String?, elapsedMs: Long) = Unit
}
