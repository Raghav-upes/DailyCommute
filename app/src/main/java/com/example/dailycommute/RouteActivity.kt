package com.example.dailycommute

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult


class RouteActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var destinationLatLng: LatLng? = null
    private lateinit var locationCallback: LocationCallback
    private var isFollowingLocation = true
    private lateinit var audioManager: AudioManager

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json"
        private const val SILENT_MODE_DISTANCE_THRESHOLD = 30

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager




        Log.w("RouteActivity", "Directions API response:")
        // Get the destination location from the intent
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        destinationLatLng = LatLng(latitude, longitude)

        // Initialize FusedLocationProviderClient for current location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize the map fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this) ?: run {
            Log.e("RouteActivity", "Map fragment not found")
            Toast.makeText(this, "Map fragment not found", Toast.LENGTH_LONG).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            mMap.isMyLocationEnabled = true

            val locationRequest = LocationRequest.create().apply {
                interval = 5000 // Update location every 5 seconds
                fastestInterval = 2000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    val currentLatLng = LatLng(location.latitude, location.longitude)

                    // If the user is not manually moving the map, keep following the location
                    if (isFollowingLocation) {
                        val bearing = if (location.bearing != 0f) location.bearing else mMap.cameraPosition.bearing  // Use bearing only when it's significant

                        // Move and rotate the camera to follow the current location
                        val cameraPosition = CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(currentLatLng)  // Update to the new current location
                                .zoom(18f)  // Keep zoomed in
                                .bearing(bearing)  // Rotate the map according to movement
                                .tilt(45f)  // Optional tilt for a 3D effect
                                .build()
                        )
                        mMap.animateCamera(cameraPosition)
                    }

                    // Update marker at the current location
                    mMap.clear()
                    mMap.addMarker(MarkerOptions().position(currentLatLng).title("Current Location"))

                    // Check if within 10 meters of the destination
                    destinationLatLng?.let { destination ->
                        mMap.addMarker(MarkerOptions().position(destination).title("Destination"))
                        getDirections(currentLatLng, destination)

                        // Check if within 10 meters of the destination
                        val distanceToDestination = FloatArray(1)
                        Location.distanceBetween(
                            currentLatLng.latitude, currentLatLng.longitude,
                            destination.latitude, destination.longitude,
                            distanceToDestination
                        )
                        if (distanceToDestination[0] <= SILENT_MODE_DISTANCE_THRESHOLD) {
                            // Switch phone to silent mode
                            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                            Toast.makeText(this@RouteActivity, "Phone is now in Silent Mode", Toast.LENGTH_SHORT).show()
                        } else {
                            // Optionally reset to normal mode if not in range
                            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        }
                    }
                }
            }

            // Request location updates
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

            // Detect when the user manually moves the camera
            mMap.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    isFollowingLocation = false  // Stop auto-following when the user manually moves the map
                }
            }

            // Resume following the location when the camera becomes idle (user stopped interacting)
            mMap.setOnCameraIdleListener {
                isFollowingLocation = true  // Resume following location when the camera becomes idle
            }
        }
    }

    override fun onStop() {
        super.onStop()
        fusedLocationClient.removeLocationUpdates(locationCallback)  // Stop location updates when the activity stops
    }

    private fun getDirections(origin: LatLng, destination: LatLng) {
        val url = "$DIRECTIONS_API_URL?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&key=${getString(R.string.google_maps_key)}&mode=driving"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@RouteActivity, "Error fetching directions", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                Log.w("RouteActivity", "Directions API response: $responseData")
                val jsonObject = JSONObject(responseData ?: "")
                val routes = jsonObject.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val legs = route.getJSONArray("legs")
                    val leg = legs.getJSONObject(0)

                    // Extract distance and time
                    val distanceText = leg.getJSONObject("distance").getString("text")
                    val durationText = leg.getJSONObject("duration").getString("text")

                    val points = ArrayList<LatLng>()
                    val overviewPolyline = route.getJSONObject("overview_polyline")
                    val encodedString = overviewPolyline.getString("points")
                    points.addAll(decodePolyline(encodedString))

                    runOnUiThread {
                        // Update the TextViews with distance and time
                        findViewById<TextView>(R.id.distanceTextView).text = "Distance: $distanceText"
                        findViewById<TextView>(R.id.timeTextView).text = "Estimated Time: $durationText"

                        val polylineOptions = PolylineOptions()
                            .addAll(points)
                            .width(15f)
                            .color(R.color.purple_500)
                        mMap.addPolyline(polylineOptions)
                    }
                }
            }
        })
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val latLng = LatLng((lat.toDouble() / 1E5), (lng.toDouble() / 1E5))
            poly.add(latLng)
        }
        return poly
    }


}