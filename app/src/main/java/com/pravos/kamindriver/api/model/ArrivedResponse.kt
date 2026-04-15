package com.pravos.kamindriver.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Response from POST /api/route/{num}/arrived */
@JsonClass(generateAdapter = true)
data class ArrivedResponse(
    val ok: Boolean,
    /** Next stop, or null when the route is complete. */
    val next: ClientStop?,
    @Json(name = "eta_to_next_min") val etaToNextMin: Int?,
    val notifications: List<String>?
)
