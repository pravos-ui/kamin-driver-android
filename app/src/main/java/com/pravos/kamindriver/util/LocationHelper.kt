package com.pravos.kamindriver.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper around [com.google.android.gms.location.FusedLocationProviderClient].
 *
 * The caller is responsible for ensuring that ACCESS_FINE_LOCATION permission
 * has been granted before calling [getCurrentLocation].
 */
object LocationHelper {

    /**
     * Returns the best available current location.
     *
     * First tries a fresh high-accuracy fix; falls back to the last known
     * location if a fresh fix is unavailable.
     *
     * @throws Exception when no location is available (e.g. GPS disabled, no cached fix).
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()

        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }

            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(location)
                    } else {
                        // Fresh fix unavailable — try the last cached location
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) {
                                    cont.resume(last)
                                } else {
                                    cont.resumeWithException(
                                        IllegalStateException("Location unavailable. Enable GPS and try again.")
                                    )
                                }
                            }
                            .addOnFailureListener { cont.resumeWithException(it) }
                    }
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }
}
