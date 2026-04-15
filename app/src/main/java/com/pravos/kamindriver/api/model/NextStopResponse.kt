package com.pravos.kamindriver.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Response from GET /api/route/{num}/next */
@JsonClass(generateAdapter = true)
data class NextStopResponse(
    val ok: Boolean,
    /** Null when the route is complete and there are no more stops. */
    val next: ClientStop?,
    @Json(name = "eta_min") val etaMin: Int?
)
