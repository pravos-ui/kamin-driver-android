package com.pravos.kamindriver.ui.routepicker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pravos.kamindriver.BuildConfig
import com.pravos.kamindriver.KaminDriverApp
import com.pravos.kamindriver.api.ApiClient
import com.pravos.kamindriver.api.model.ClientStop
import com.pravos.kamindriver.databinding.ActivityRoutePickerBinding
import com.pravos.kamindriver.ui.navigation.NavigationActivity
import com.pravos.kamindriver.util.LocationHelper
import kotlinx.coroutines.launch

/**
 * Screen 1 — Route picker.
 *
 * Flow:
 * 1. User taps Маршрут 1 or Маршрут 2.
 * 2. App requests ACCESS_FINE_LOCATION if not yet granted.
 * 3. App fetches /api/config to obtain (and store) the Mapbox token.
 * 4. App fetches /api/route/{num}/next with the current GPS location.
 * 5. Displays client surname, address and ETA.
 * 6. User taps "Старт навігації" to launch [NavigationActivity].
 */
class RoutePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoutePickerBinding

    private var selectedRoute: Int = 0
    private var mapboxToken: String = BuildConfig.MAPBOX_TOKEN_DEBUG
    private var nextStop: ClientStop? = null
    private var etaMin: Int? = null

    // Runtime location-permission request
    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                fetchNextStop(selectedRoute)
            } else {
                showError(getString(com.pravos.kamindriver.R.string.permission_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoutePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRoute1.setOnClickListener { onRouteButtonClicked(1) }
        binding.btnRoute2.setOnClickListener { onRouteButtonClicked(2) }
        binding.btnStartNavigation.setOnClickListener { startNavigation() }
    }

    // -------------------------------------------------------------------------
    // User interactions
    // -------------------------------------------------------------------------

    private fun onRouteButtonClicked(num: Int) {
        selectedRoute = num
        hideError()

        if (hasLocationPermission()) {
            fetchNextStop(num)
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // -------------------------------------------------------------------------
    // Network calls
    // -------------------------------------------------------------------------

    private fun fetchNextStop(routeNum: Int) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                // Fetch the Mapbox token from the backend when none is available yet.
                if (mapboxToken.isBlank()) {
                    val config = ApiClient.service.getConfig()
                    mapboxToken = config.mapboxToken
                    (application as KaminDriverApp).setMapboxToken(mapboxToken)
                }

                val location = LocationHelper.getCurrentLocation(this@RoutePickerActivity)
                val response = ApiClient.service.getNextStop(
                    routeNum,
                    location.latitude,
                    location.longitude
                )

                nextStop = response.next
                etaMin = response.etaMin

                showNextClientCard(response.next, response.etaMin)
                binding.btnStartNavigation.isEnabled = response.next != null
            } catch (e: Exception) {
                showError(getString(com.pravos.kamindriver.R.string.error_network, e.message))
            } finally {
                showLoading(false)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private fun startNavigation() {
        val stop = nextStop ?: return

        if (mapboxToken.isBlank()) {
            showError(getString(com.pravos.kamindriver.R.string.token_missing))
            return
        }

        val intent = Intent(this, NavigationActivity::class.java).apply {
            putExtra(NavigationActivity.EXTRA_ROUTE_NUM, selectedRoute)
            putExtra(NavigationActivity.EXTRA_MAPBOX_TOKEN, mapboxToken)
            putExtra(NavigationActivity.EXTRA_CLIENT_ID, stop.id)
            putExtra(NavigationActivity.EXTRA_CLIENT_SURNAME, stop.surname)
            putExtra(NavigationActivity.EXTRA_CLIENT_ADDRESS, stop.address)
            putExtra(NavigationActivity.EXTRA_CLIENT_LAT, stop.lat)
            putExtra(NavigationActivity.EXTRA_CLIENT_LNG, stop.lng)
        }
        startActivity(intent)
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private fun showNextClientCard(stop: ClientStop?, eta: Int?) {
        if (stop == null) {
            binding.cardNextClient.visibility = View.VISIBLE
            binding.tvClientSurname.text = getString(com.pravos.kamindriver.R.string.route_completed)
            binding.tvClientAddress.text = ""
            binding.tvEta.visibility = View.GONE
            return
        }
        binding.cardNextClient.visibility = View.VISIBLE
        binding.tvClientSurname.text = stop.surname
        binding.tvClientAddress.text = stop.address
        if (eta != null) {
            binding.tvEta.text = getString(com.pravos.kamindriver.R.string.eta_format, eta)
            binding.tvEta.visibility = View.VISIBLE
        } else {
            binding.tvEta.visibility = View.GONE
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRoute1.isEnabled = !loading
        binding.btnRoute2.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}
