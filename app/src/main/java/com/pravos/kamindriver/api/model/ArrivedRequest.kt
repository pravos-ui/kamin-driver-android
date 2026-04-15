package com.pravos.kamindriver.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Request body for POST /api/route/{num}/arrived */
@JsonClass(generateAdapter = true)
data class ArrivedRequest(
    @Json(name = "client_id") val clientId: String,
    @Json(name = "arrivedAt") val arrivedAt: String,
    @Json(name = "from_lat") val fromLat: Double,
    @Json(name = "from_lng") val fromLng: Double
)
