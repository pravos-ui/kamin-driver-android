package com.pravos.kamindriver

import android.app.Application
import com.mapbox.common.MapboxOptions

/**
 * Application class. Initialises the Mapbox access token as early as possible
 * so that any Mapbox SDK component can be used right after launch.
 *
 * The token is either:
 *  1. A debug fallback baked in at build-time via the MAPBOX_ACCESS_TOKEN gradle property, or
 *  2. A token fetched at runtime from GET /api/config (set via [setMapboxToken]).
 */
class KaminDriverApp : Application() {

    companion object {
        lateinit var instance: KaminDriverApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Apply the compile-time debug token if one was provided.
        // This allows testing navigation in debug builds without a backend connection.
        val debugToken = BuildConfig.MAPBOX_TOKEN_DEBUG
        if (debugToken.isNotBlank()) {
            MapboxOptions.accessToken = debugToken
        }
    }

    /**
     * Sets the Mapbox access token fetched from the backend.
     * Must be called before any [com.mapbox.maps.MapView] or
     * [com.mapbox.navigation.core.MapboxNavigation] is initialised.
     */
    fun setMapboxToken(token: String) {
        if (token.isNotBlank()) {
            MapboxOptions.accessToken = token
        }
    }
}
