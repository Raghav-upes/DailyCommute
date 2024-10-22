package com.example.dailycommute

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    private lateinit var cardContainer: LinearLayout
    private lateinit var noDestinationsTextView: TextView // Reference to the new TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.homescreen)



        cardContainer = findViewById(R.id.cardContainer)
        noDestinationsTextView = findViewById(R.id.noDestinationsTextView) // Initialize the TextView

        val addButton: Button = findViewById(R.id.AddBtn)
        addButton.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            startActivity(intent)
        }

        val multiBtn:Button=findViewById(R.id.MultiPlaceBtn)
        multiBtn.setOnClickListener{
            val intent=Intent(this,AutoCompleteActivity::class.java)
            startActivity(intent)
        }

        loadSavedLocations() // Load the saved locations when the activity is first created
    }

    override fun onResume() {
        super.onResume()
        refreshLocationList() // Reload the saved locations when the activity resumes
    }

    private fun refreshLocationList() {
        cardContainer.removeAllViews() // Clear the container before adding new views
        loadSavedLocations()           // Reload the saved locations
    }

    private fun loadSavedLocations() {
        val savedLocations = getSavedLocations()
        Log.d("MainActivity", "Number of saved locations: ${savedLocations.size}") // Debug log

        // Control visibility based on the number of saved locations
        if (savedLocations.isEmpty()) {
            noDestinationsTextView.visibility = View.VISIBLE // Show the TextView if no locations
            Log.d("MainActivity", "No destinations found. Showing prompt.") // Debug log
        } else {
            noDestinationsTextView.visibility = View.GONE // Hide the TextView if there are locations
        }

        for (location in savedLocations) {
            addLocationCard(location)
        }
    }

    private fun addLocationCard(location: SavedLocation) {
        val inflater = LayoutInflater.from(this)
        val cardView = inflater.inflate(R.layout.location_card, cardContainer, false)

        val addressTypeView = cardView.findViewById<TextView>(R.id.AddressType)
        val addressView = cardView.findViewById<TextView>(R.id.Address)

        addressTypeView.text = location.name
        addressView.text = location.address

        // Set long click listener for the cardView
        cardView.setOnLongClickListener {
            showRemoveMenu(it, location)
            true
        }

        cardView.setOnClickListener {
            val intent = Intent(this, RouteActivity::class.java)
            intent.putExtra("latitude", location.latitude)
            intent.putExtra("longitude", location.longitude)
            startActivity(intent)
        }

        cardContainer.addView(cardView)
    }

    private fun showRemoveMenu(view: View, location: SavedLocation) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.popup_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.remove_location -> {
                    removeLocation(location)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun removeLocation(location: SavedLocation) {
        val sharedPref = getSharedPreferences("SavedLocations", MODE_PRIVATE)
        val locationsJson = sharedPref.getString("locations", "[]")
        val locationType = object : TypeToken<MutableList<SavedLocation>>() {}.type
        val locations: MutableList<SavedLocation> = Gson().fromJson(locationsJson, locationType)

        locations.remove(location)

        with(sharedPref.edit()) {
            putString("locations", Gson().toJson(locations))
            apply()
        }

        refreshLocationList() // Refresh the list after removing a location
    }

    private fun getSavedLocations(): List<SavedLocation> {
        val sharedPref = getSharedPreferences("SavedLocations", MODE_PRIVATE)
        val locationsJson = sharedPref.getString("locations", "[]")
        val locationType = object : TypeToken<List<SavedLocation>>() {}.type
        return Gson().fromJson(locationsJson, locationType)
    }
}
