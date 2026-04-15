package com.pravos.kamindriver.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConfigResponse(
    val ok: Boolean,
    @Json(name = "mapboxToken") val mapboxToken: String
)
