package com.shadowhunt.app.location

import com.shadowhunt.app.data.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocationBus {
    private val _latest = MutableStateFlow<GeoPoint?>(null)
    val latest = _latest.asStateFlow()
    fun publish(point: GeoPoint) { _latest.value = point }
    fun clear() { _latest.value = null }
}
