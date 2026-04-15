package com.pravos.kamindriver.ui.navigation

import android.location.Location
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.ui.maneuver.api.MapboxDistanceFormatter
import com.mapbox.navigation.ui.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.tripprogress.api.MapboxTripProgressApi
import com.mapbox.navigation.ui.tripprogress.model.DistanceRemainingFormatter
import com.mapbox.navigation.ui.tripprogress.model.EstimatedTimeToArrivalFormatter
import com.mapbox.navigation.ui.tripprogress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.ui.tripprogress.model.TimeRemainingFormatter
import com.mapbox.navigation.ui.tripprogress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.voice.api.MapboxSpeechApi
import com.mapbox.navigation.ui.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.ui.voice.model.SpeechVolume
import com.pravos.kamindriver.R
import com.pravos.kamindriver.api.ApiClient
import com.pravos.kamindriver.api.model.ArrivedRequest
import com.pravos.kamindriver.api.model.ClientStop
import com.pravos.kamindriver.databinding.ActivityNavigationBinding
import com.pravos.kamindriver.util.LocationHelper
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

/**
 * Screen 2 — Turn-by-turn navigation.
 *
 * Uses Mapbox Navigation SDK v2 components:
 * - [com.mapbox.maps.MapView]                            — the map
 * - [MapboxNavigationViewportDataSource]                 — camera data source
 * - [NavigationCamera]                                   — camera that follows the driver
 * - [MapboxRouteLineApi] / [MapboxRouteLineView]         — route drawn on the map
 * - [MapboxManeuverApi] / [MapboxManeuverView]           — next-turn instructions
 * - [MapboxTripProgressApi] / [MapboxTripProgressView]   — ETA & distance remaining
 * - [MapboxSpeechApi] / [MapboxVoiceInstructionsPlayer]  — voice guidance
 *
 * On "ПРИБУВ":
 * 1. POSTs arrival event to the backend.
 * 2. If a next stop is returned, updates destination and continues navigating.
 * 3. If no next stop, shows a completion dialog and finishes.
 */
class NavigationActivity : AppCompatActivity() {

    // -------------------------------------------------------------------------
    // Intent extras (keys)
    // -------------------------------------------------------------------------
    companion object {
        const val EXTRA_ROUTE_NUM = "extra_route_num"
        const val EXTRA_MAPBOX_TOKEN = "extra_mapbox_token"
        const val EXTRA_CLIENT_ID = "extra_client_id"
        const val EXTRA_CLIENT_SURNAME = "extra_client_surname"
        const val EXTRA_CLIENT_ADDRESS = "extra_client_address"
        const val EXTRA_CLIENT_LAT = "extra_client_lat"
        const val EXTRA_CLIENT_LNG = "extra_client_lng"
    }

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------
    private lateinit var binding: ActivityNavigationBinding

    // -------------------------------------------------------------------------
    // Data from intent
    // -------------------------------------------------------------------------
    private var routeNum: Int = 1
    private lateinit var mapboxToken: String
    private lateinit var currentClientId: String
    private lateinit var currentClientSurname: String
    private lateinit var currentClientAddress: String
    private var currentDestLat: Double = 0.0
    private var currentDestLng: Double = 0.0

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    /** Most recently matched driver location — used when posting "arrived". */
    private var lastLocation: Location? = null
    private var isVoiceMuted = false

    // -------------------------------------------------------------------------
    // Mapbox Navigation SDK — core
    // -------------------------------------------------------------------------
    private lateinit var mapboxNavigation: MapboxNavigation
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera

    // -------------------------------------------------------------------------
    // Mapbox Navigation SDK — UI components
    // -------------------------------------------------------------------------
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi
    private lateinit var speechApi: MapboxSpeechApi
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer

    // -------------------------------------------------------------------------
    // Observers / callbacks
    // -------------------------------------------------------------------------

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        val routes = routeUpdateResult.navigationRoutes
        if (routes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(routes) { value ->
                binding.mapView.getMapboxMap().getStyle()?.let { style ->
                    routeLineView.renderRouteDrawData(style, value)
                }
            }
            viewportDataSource.onRouteChanged(routes.first())
            viewportDataSource.evaluate()
        } else {
            binding.mapView.getMapboxMap().getStyle()?.let { style ->
                routeLineView.renderClearRouteLineValue(style, routeLineApi.clearRouteLine())
            }
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()
        }
    }

    private val routeProgressObserver = RouteProgressObserver { progress: RouteProgress ->
        routeLineApi.updateWithRouteProgress(progress) { result ->
            binding.mapView.getMapboxMap().getStyle()?.let { style ->
                routeLineView.renderRouteLineUpdate(style, result)
            }
        }
        maneuverApi.getManeuvers(progress) { result ->
            binding.maneuverView.renderManeuvers(result)
        }
        binding.tripProgressView.render(tripProgressApi.getTripProgress(progress))
        binding.maneuverView.visibility = View.VISIBLE
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) { /* no-op */ }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhanced = locationMatcherResult.enhancedLocation
            lastLocation = enhanced
            viewportDataSource.onLocationChanged(enhanced)
            viewportDataSource.evaluate()
        }
    }

    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        if (!isVoiceMuted) {
            speechApi.generate(voiceInstructions) { expected ->
                expected.fold(
                    { error ->
                        voiceInstructionsPlayer.play(error.fallback) { announcement ->
                            speechApi.clean(announcement)
                        }
                    },
                    { value ->
                        voiceInstructionsPlayer.play(value.announcement) { announcement ->
                            speechApi.clean(announcement)
                        }
                    }
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        readIntentExtras()
        ensureMapboxToken()
        initNavigationComponents()
        initMapStyle()
        updateClientInfoUI()

        binding.btnArrived.setOnClickListener { onArrivedClicked() }
        binding.soundButton.setOnClickListener { toggleVoice() }
    }

    override fun onStart() {
        super.onStart()
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
    }

    override fun onStop() {
        super.onStop()
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        maneuverApi.cancel()
        routeLineApi.cancel()
        speechApi.cancel()
        voiceInstructionsPlayer.shutdown()
        mapboxNavigation.stopTripSession()
        MapboxNavigationProvider.destroy()
    }

    // -------------------------------------------------------------------------
    // Initialisation helpers
    // -------------------------------------------------------------------------

    private fun readIntentExtras() {
        routeNum = intent.getIntExtra(EXTRA_ROUTE_NUM, 1)
        mapboxToken = intent.getStringExtra(EXTRA_MAPBOX_TOKEN) ?: ""
        currentClientId = intent.getStringExtra(EXTRA_CLIENT_ID) ?: ""
        currentClientSurname = intent.getStringExtra(EXTRA_CLIENT_SURNAME) ?: ""
        currentClientAddress = intent.getStringExtra(EXTRA_CLIENT_ADDRESS) ?: ""
        currentDestLat = intent.getDoubleExtra(EXTRA_CLIENT_LAT, 0.0)
        currentDestLng = intent.getDoubleExtra(EXTRA_CLIENT_LNG, 0.0)
    }

    private fun ensureMapboxToken() {
        if (mapboxToken.isNotBlank()) {
            MapboxOptions.accessToken = mapboxToken
        }
    }

    private fun initNavigationComponents() {
        // --- Core navigation ---
        mapboxNavigation = MapboxNavigationProvider.create(
            NavigationOptions.Builder(applicationContext)
                .accessToken(mapboxToken)
                .build()
        )

        // --- Camera ---
        viewportDataSource = MapboxNavigationViewportDataSource(binding.mapView.getMapboxMap())
        navigationCamera = NavigationCamera(
            binding.mapView.getMapboxMap(),
            binding.mapView.camera,
            viewportDataSource
        )
        navigationCamera.requestNavigationCameraToFollowing()

        // --- Route line ---
        val routeLineOptions = MapboxRouteLineOptions.Builder(this)
            .withRouteLineBelowLayerId("road-label-navigation")
            .build()
        routeLineApi = MapboxRouteLineApi(routeLineOptions)
        routeLineView = MapboxRouteLineView(routeLineOptions)

        // --- Maneuver instructions ---
        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(DistanceFormatterOptions.Builder(this).build())
        )

        // --- Trip progress ---
        val distanceFormatterOptions = DistanceFormatterOptions.Builder(this).build()
        tripProgressApi = MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(this)
                .distanceRemainingFormatter(DistanceRemainingFormatter(distanceFormatterOptions))
                .timeRemainingFormatter(TimeRemainingFormatter(this))
                .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
                .estimatedTimeToArrivalFormatter(EstimatedTimeToArrivalFormatter(this))
                .build()
        )

        // --- Voice ---
        val language = Locale.getDefault().language
        speechApi = MapboxSpeechApi(this, mapboxToken, language)
        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(this, mapboxToken, language)
    }

    private fun initMapStyle() {
        binding.mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            mapboxNavigation.startTripSession()
            requestRouteToDestination(currentDestLat, currentDestLng)
        }
    }

    // -------------------------------------------------------------------------
    // Route requests
    // -------------------------------------------------------------------------

    /**
     * Requests a new route to the given destination.
     * Uses the last known GPS location as origin; if unavailable uses the destination
     * as a stand-in until a real GPS fix arrives and triggers an automatic reroute.
     */
    private fun requestRouteToDestination(destLat: Double, destLng: Double) {
        val origin = lastLocation?.let {
            Point.fromLngLat(it.longitude, it.latitude)
        } ?: Point.fromLngLat(destLng, destLat)

        val destination = Point.fromLngLat(destLng, destLat)

        val routeOptions = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(this)
            .coordinatesList(listOf(origin, destination))
            .alternatives(false)
            .build()

        mapboxNavigation.requestRoutes(routeOptions, object : NavigationRouterCallback {
            override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: RouterOrigin) {
                mapboxNavigation.setNavigationRoutes(routes)
            }

            override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                showError("Route request failed: ${reasons.firstOrNull()?.message}")
            }

            override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) { /* no-op */ }
        })
    }

    // -------------------------------------------------------------------------
    // ПРИБУВ — driver arrived at client
    // -------------------------------------------------------------------------

    private fun onArrivedClicked() {
        binding.btnArrived.isEnabled = false
        binding.progressOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val location = LocationHelper.getCurrentLocation(this@NavigationActivity)
                val arrivedAt = Instant.now().toString()

                val response = ApiClient.service.postArrived(
                    routeNum,
                    ArrivedRequest(
                        clientId = currentClientId,
                        arrivedAt = arrivedAt,
                        fromLat = location.latitude,
                        fromLng = location.longitude
                    )
                )

                if (response.next != null) {
                    updateCurrentStop(response.next)
                    requestRouteToDestination(response.next.lat, response.next.lng)
                    binding.btnArrived.isEnabled = true
                } else {
                    showRouteCompleteDialog()
                }
            } catch (e: Exception) {
                showError(getString(R.string.error_network, e.message))
                binding.btnArrived.isEnabled = true
            } finally {
                binding.progressOverlay.visibility = View.GONE
            }
        }
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private fun updateCurrentStop(stop: ClientStop) {
        currentClientId = stop.id
        currentClientSurname = stop.surname
        currentClientAddress = stop.address
        currentDestLat = stop.lat
        currentDestLng = stop.lng
        updateClientInfoUI()
    }

    private fun updateClientInfoUI() {
        binding.tvNavClientSurname.text = currentClientSurname
        binding.tvNavClientAddress.text = currentClientAddress
    }

    private fun toggleVoice() {
        isVoiceMuted = !isVoiceMuted
        if (isVoiceMuted) {
            voiceInstructionsPlayer.volume(SpeechVolume(0f))
        } else {
            voiceInstructionsPlayer.volume(SpeechVolume(1f))
        }
    }

    private fun showError(message: String) {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }

    private fun showRouteCompleteDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.route_done_title))
            .setMessage(getString(R.string.route_done_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
