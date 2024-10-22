package com.example.dailycommute

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AutoCompleteActivity : AppCompatActivity() {

    private val placeTypes = arrayOf(
        "accounting", "airport", "amusement_park", "aquarium", "art_gallery", "atm", "bakery",
        "bank", "bar", "beauty_salon", "bicycle_store", "book_store", "bowling_alley", "bus_station",
        "cafe", "campground", "car_dealer", "car_rental", "car_repair", "car_wash", "casino",
        "cemetery", "church", "city_hall", "clothing_store", "convenience_store", "courthouse",
        "dentist", "department_store", "doctor", "drugstore", "electrician", "electronics_store",
        "embassy", "fire_station", "florist", "funeral_home", "furniture_store", "gas_station",
        "gym", "hair_care", "hardware_store", "hindu_temple", "home_goods_store", "hospital",
        "insurance_agency", "jewelry_store", "laundry", "lawyer", "library", "light_rail_station",
        "liquor_store", "local_government_office", "locksmith", "lodging", "meal_delivery",
        "meal_takeaway", "mosque", "movie_rental", "movie_theater", "moving_company", "museum",
        "night_club", "painter", "park", "parking", "pet_store", "pharmacy", "physiotherapist",
        "plumber", "police", "post_office", "primary_school", "real_estate_agency", "restaurant",
        "roofing_contractor", "rv_park", "school", "secondary_school", "shoe_store", "shopping_mall",
        "spa", "stadium", "storage", "store", "subway_station", "supermarket", "synagogue", "taxi_stand",
        "tourist_attraction", "train_station", "transit_station", "travel_agency", "university",
        "veterinary_care", "zoo"
    )

    private lateinit var cardContainer: LinearLayout
    private lateinit var noDestinationsTextView: TextView
    private var placeCounter = 1
    private lateinit var searchButton: Button // Declare search button
    private val placeInputs = mutableListOf<String>() // List to hold the place inputs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.multi_place_add)

        cardContainer = findViewById(R.id.cardContainer)
        noDestinationsTextView = findViewById(R.id.noDestinationsTextView)
        val addButton: Button = findViewById(R.id.AddBtn)
        searchButton = findViewById(R.id.searchBtn)
        // Hide the no destinations message on first click
        addButton.setOnClickListener {
            noDestinationsTextView.visibility = View.GONE
            addPlaceView()
        }

        searchButton.setOnClickListener {
            collectPlaceInputs()
            val intent = Intent(this, MultiRouteActivity::class.java).apply {
                putStringArrayListExtra("placeInputs", ArrayList(placeInputs))
            }
            startActivity(intent)
        }
    }


    private fun addPlaceView() {
        // Create TextView for the new place
        val placeTextView = TextView(this).apply {
            text = "Place $placeCounter"
            textSize = 18f
            setPadding(16, 16, 16, 8) // Padding around the TextView
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 8) // Margin for proper spacing
            }
        }

        // Create AutoCompleteTextView for place input
        val placeAutoComplete = AutoCompleteTextView(this).apply {
            hint = "Search for a place..."
            textSize = 16f
            setPadding(16, 16, 16, 16) // Padding inside the AutoCompleteTextView
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 16) // Margin around the AutoCompleteTextView for spacing
            }
            setAdapter(ArrayAdapter(
                this@AutoCompleteActivity,
                android.R.layout.simple_dropdown_item_1line,
                placeTypes
            ))
        }

        // Add both TextView and AutoCompleteTextView to the container
        cardContainer.addView(placeTextView)
        cardContainer.addView(placeAutoComplete)

        placeCounter++
    }


    private fun collectPlaceInputs() {
        placeInputs.clear() // Clear previous inputs

        // Iterate through the views in cardContainer to get AutoCompleteTextViews
        for (i in 0 until cardContainer.childCount) {
            val view = cardContainer.getChildAt(i)
            if (view is AutoCompleteTextView) {
                // Add the text from the AutoCompleteTextView to the list
                val input = view.text.toString()
                if (input.isNotEmpty()) {
                    placeInputs.add(input)
                }
            }
        }
    }

}
