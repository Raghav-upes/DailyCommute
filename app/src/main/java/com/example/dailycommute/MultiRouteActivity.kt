package com.example.dailycommute

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Debug
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MultiRouteActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var placesClient: PlacesClient
    private var isFollowingLocation = true
    private var currentLatLng: LatLng? = null
    private var totalDistance = 0
    private var totalDuration = 0


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json"
    }

    private lateinit var placeInputs: List<String> ;

    private var nearestPlaces: MutableList<LatLng> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route)
        placeInputs = intent.getStringArrayListExtra("placeInputs") ?: emptyList()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Places API
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)

        // Initialize the map fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this) ?: run {
            Log.e("MultiDestinationRoute", "Map fragment not found")
            Toast.makeText(this, "Map fragment not found", Toast.LENGTH_LONG).show()
        }
    }

    // Threshold in meters to determine if the location has changed significantly enough to redraw routes
    private val LOCATION_CHANGE_THRESHOLD = 10.0f

    // Store the last known location to compare changes
    private var lastKnownLatLng: LatLng? = null

    private fun hasLocationChanged(newLatLng: LatLng?): Boolean {
        if (lastKnownLatLng == null || newLatLng == null) {
            return true // Always return true if there is no previous location
        }

        val resultsArray = FloatArray(1)
        Location.distanceBetween(
            lastKnownLatLng!!.latitude, lastKnownLatLng!!.longitude,
            newLatLng.latitude, newLatLng.longitude,
            resultsArray
        )
        val distance = resultsArray[0]

        return distance > LOCATION_CHANGE_THRESHOLD
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
                interval = 5000
                fastestInterval = 2000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    val newLatLng = LatLng(location.latitude, location.longitude)

                    // Check if the location has changed significantly before updating the map and drawing routes
                    if (hasLocationChanged(newLatLng)) {
                        currentLatLng = newLatLng
                        lastKnownLatLng = newLatLng  // Update the last known location

                        if (isFollowingLocation) {
                            val cameraPosition = CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(currentLatLng!!)
                                    .zoom(18f)
                                    .tilt(45f)
                                    .build()
                            )
                            mMap.animateCamera(cameraPosition)
                        }

                        // Clear the map and redraw only if necessary
                        mMap.clear()
                        mMap.addMarker(MarkerOptions().position(currentLatLng!!).title("Current Location"))

                        findNearestPlaces()
                    }
                }
            }


            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

            mMap.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    isFollowingLocation = false
                }
            }

            mMap.setOnCameraIdleListener {
                isFollowingLocation = true
            }
        }
    }

    private fun findNearestPlaces() {
        if (currentLatLng != null) {
            nearestPlaces.clear()

            placeInputs.forEach { placeType ->
                val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                        "location=${currentLatLng!!.latitude},${currentLatLng!!.longitude}" +
                        "&rankby=distance" +  // Rank results by distance from current location
                        "&type=$placeType" +
                        "&key=${getString(R.string.google_maps_key)}"

                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnUiThread {
                            Toast.makeText(this@MultiRouteActivity, "Error fetching nearby places", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseData = response.body?.string()
                        Log.d("MultiDestinationRoute", "Nearby Places API response: $responseData")

                        val jsonObject = JSONObject(responseData ?: "")
                        val results = jsonObject.getJSONArray("results")

                        // Take the first result, which should be the nearest one
                        if (results.length() > 0) {
                            val placeObject = results.getJSONObject(0)
                            val latLng = placeObject.getJSONObject("geometry").getJSONObject("location")
                            val nearestPlaceLatLng = LatLng(latLng.getDouble("lat"), latLng.getDouble("lng"))
                            val nearestPlaceName = placeObject.getString("name")

                            runOnUiThread {
                                // Add the nearest place as a marker
                                mMap.addMarker(MarkerOptions().position(nearestPlaceLatLng).title(nearestPlaceName))
                                nearestPlaces.add(nearestPlaceLatLng)
                                drawRoutes()
                            }
                        }
                    }
                })
            }
        } else {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
        }
    }





    private fun drawRoutes() {
        currentLatLng?.let { origin ->
            val sortedDestinations = nearestPlaces.sortedBy { destination ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    origin.latitude, origin.longitude,
                    destination.latitude, destination.longitude,
                    results
                )
                results[0]
            }

            var prevLocation = origin

            sortedDestinations.forEach { destinationLatLng ->
                getDirections(prevLocation, destinationLatLng)
                prevLocation = destinationLatLng
            }
        }
    }

    private fun getDirections(origin: LatLng, destination: LatLng) {
        val url = "$DIRECTIONS_API_URL?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&key=${getString(R.string.google_maps_key)}&mode=driving"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MultiRouteActivity, "Error fetching directions", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                Log.w("MultiDestinationRoute", "Directions API response: $responseData")
                val jsonObject = JSONObject(responseData ?: "")
                val routes = jsonObject.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val legs = route.getJSONArray("legs")
                    // Assuming you're interested in the first leg of the first route
                    val leg = legs.getJSONObject(0)

                    // Extract distance and duration
                    val distanceText = leg.getJSONObject("distance").getString("text")
                    val durationText = leg.getJSONObject("duration").getString("text")

                    // Accumulate the total distance and duration
                    val distanceValue = leg.getJSONObject("distance").getDouble("value") // distance in meters
                    val durationValue = leg.getJSONObject("duration").getDouble("value") // duration in seconds

                    totalDistance += distanceValue.toInt() // accumulate distance
                    totalDuration += durationValue.toInt() // accumulate duration

                    runOnUiThread {
                        // Draw the polyline
                        val points = ArrayList<LatLng>()
                        val overviewPolyline = route.getJSONObject("overview_polyline")
                        val encodedString = overviewPolyline.getString("points")
                        points.addAll(decodePolyline(encodedString))

                        val polylineOptions = PolylineOptions()
                            .addAll(points)
                            .width(15f)
                            .color(R.color.purple_500)
                        mMap.addPolyline(polylineOptions)

                        // Update total distance and time text views
                        findViewById<TextView>(R.id.distanceTextView).text = "Total Distance: ${totalDistance / 1000} km"
                        findViewById<TextView>(R.id.timeTextView).text = "Estimated Time: ${totalDuration / 60} min"
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

            val p = LatLng(lat / 1E5, lng / 1E5)
            poly.add(p)
        }

        return poly
    }
}
