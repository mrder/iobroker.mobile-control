package com.mobilecontrol.app.domain.model

/** A single "alarm went active" transition the backend recorded (see GET /api/v1/alarm-events),
 *  used to catch up on what happened while this device was disconnected. */
data class AlarmEvent(
    val objectId: String,
    val value: Boolean,
    val timestampMs: Long,
)
