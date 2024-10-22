package com.example.dailycommute

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import androidx.appcompat.widget.SearchView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private lateinit var searchView: SearchView
    private lateinit var saveButton: Button
    private lateinit var btnWork: Button
    private lateinit var btnHome: Button
    private lateinit var btnOther: Button
    private var currentAddress: Address? = null
    private var selectedButton: Button? = null
    private var tagName="Untagged";
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val TAG = "com.example.dailycommute.MapsActivity"
        private const val PREFS_NAME = "SavedLocations"
        private const val LOCATIONS_KEY = "locations"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Places API
        try {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
            placesClient = Places.createClient(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Places API: ${e.message}")
            Toast.makeText(this, "Error initializing Places API", Toast.LENGTH_LONG).show()
        }

        // Initialize UI components
        searchView = findViewById(R.id.search_location)
        saveButton = findViewById(R.id.save_button)
        saveButton.visibility = View.GONE

        btnWork = findViewById(R.id.btn_work)
        btnHome = findViewById(R.id.btn_home)
        btnOther = findViewById(R.id.btn_other)

        // Set up button listeners
        btnWork.setOnClickListener { handleButtonClick(btnWork) }
        btnHome.setOnClickListener { handleButtonClick(btnHome) }
        btnOther.setOnClickListener { handleButtonClick(btnOther) }

        saveButton.setOnClickListener {
            saveLocation()
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this) ?: run {
            Log.e(TAG, "Error: Map fragment not found")
            Toast.makeText(this, "Error: Map fragment not found", Toast.LENGTH_LONG).show()
        }

        // Set up search functionality
        setupSearchView()
    }

    private fun handleButtonClick(button: Button) {
        // Reset the background of all buttons
        resetButtonBackgrounds()

        // Highlight the selected button
        button.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_button_color)) // #9DE615
        selectedButton = button
         tagName=button.text.toString()
        // Show the save button if any button is selected
        saveButton.visibility = View.VISIBLE
    }

    private fun resetButtonBackgrounds() {
        val defaultColor = ContextCompat.getColor(this, R.color.default_button_color) // White
        btnWork.setBackgroundColor(defaultColor)
        btnHome.setBackgroundColor(defaultColor)
        btnOther.setBackgroundColor(defaultColor)
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchLocation(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    private fun searchLocation(query: String) {
        if (!::mMap.isInitialized) {
            Log.e(TAG, "Google Map not initialized")
            Toast.makeText(this, "Map not ready, please try again", Toast.LENGTH_SHORT).show()
            return
        }

        if (query.isBlank()) {
            Log.e(TAG, "Search query is blank")
            Toast.makeText(this, "Please enter a location to search", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val geocoder = Geocoder(this)
            val addresses = geocoder.getFromLocationName(query, 1)

            if (addresses.isNullOrEmpty()) {
                Log.e(TAG, "No addresses found for query: $query")
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
                return
            }

            currentAddress = addresses[0]
            val latLng = LatLng(currentAddress!!.latitude, currentAddress!!.longitude)

            mMap.clear() // Clear previous markers
            mMap.addMarker(MarkerOptions().position(latLng).title(currentAddress!!.featureName))
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

            // Show the save button if any button is selected
            saveButton.visibility = if (selectedButton != null) View.VISIBLE else View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "Error searching for location: ${e.message}", e)
            Toast.makeText(this, "Error searching for location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveLocation() {
        currentAddress?.let { address ->
            val savedLocation = SavedLocation(
                tagName ?: "Unknown Location",
                address.getAddressLine(0) ?: "",
                address.latitude,
                address.longitude
            )

            val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val locationsJson = sharedPref.getString(LOCATIONS_KEY, "[]")
            val locationType = object : TypeToken<MutableList<SavedLocation>>() {}.type
            val locations: MutableList<SavedLocation> = Gson().fromJson(locationsJson, locationType)

            locations.add(savedLocation)

            with(sharedPref.edit()) {
                putString(LOCATIONS_KEY, Gson().toJson(locations))
                apply()
            }

            Toast.makeText(this, "Location saved successfully", Toast.LENGTH_SHORT).show()
            resetButtonBackgrounds()
            saveButton.visibility = View.GONE
        } ?: run {
            Toast.makeText(this, "No location to save", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    mMap.addMarker(MarkerOptions().position(currentLatLng).title("Current Location"))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    enableMyLocation()
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }
}

data class SavedLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)
