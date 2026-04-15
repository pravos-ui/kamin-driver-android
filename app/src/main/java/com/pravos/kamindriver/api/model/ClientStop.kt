package com.pravos.kamindriver.api.model

import com.squareup.moshi.JsonClass

/** A single client stop returned by the backend. */
@JsonClass(generateAdapter = true)
data class ClientStop(
    val id: String,
    val surname: String,
    val address: String,
    val lat: Double,
    val lng: Double
)
