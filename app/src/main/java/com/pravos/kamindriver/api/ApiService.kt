package com.pravos.kamindriver.api

import com.pravos.kamindriver.api.model.ArrivedRequest
import com.pravos.kamindriver.api.model.ArrivedResponse
import com.pravos.kamindriver.api.model.ConfigResponse
import com.pravos.kamindriver.api.model.NextStopResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    /** Returns the app configuration, including the Mapbox public access token. */
    @GET("api/config")
    suspend fun getConfig(): ConfigResponse

    /**
     * Returns the next stop on the given route from the driver's current location.
     *
     * @param routeNum  Route number (1 or 2).
     * @param fromLat   Driver's current latitude.
     * @param fromLng   Driver's current longitude.
     */
    @GET("api/route/{num}/next")
    suspend fun getNextStop(
        @Path("num") routeNum: Int,
        @Query("from_lat") fromLat: Double,
        @Query("from_lng") fromLng: Double
    ): NextStopResponse

    /**
     * Reports that the driver has arrived at a client.
     *
     * @param routeNum  Route number (1 or 2).
     * @param body      Arrival details including client ID and current location.
     * @return          The next stop, or null if the route is complete.
     */
    @POST("api/route/{num}/arrived")
    suspend fun postArrived(
        @Path("num") routeNum: Int,
        @Body body: ArrivedRequest
    ): ArrivedResponse
}
