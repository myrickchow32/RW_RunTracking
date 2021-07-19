/*
 * Copyright (c) 2021 Razeware LLC
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 * 
 * This project and source code may use libraries or frameworks that are
 * released under various Open-Source licenses. Use of those libraries and
 * frameworks are governed by their own individual licenses.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.runtracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.raywenderlich.android.runtracking.databinding.ActivityMainBinding

/**
 * Main Screen
 */
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
  private lateinit var binding: ActivityMainBinding

  // Location & Map
  private lateinit var mMap: GoogleMap

  companion object {
    // SharedPreferences
    private const val KEY_SHARED_PREFERENCE = "com.rwRunTrackingApp.sharedPreferences"
    private const val KEY_IS_TRACKING = "com.rwRunTrackingApp.isTracking"

    // Permission
    private const val REQUEST_CODE_FINE_LOCATION = 1
    private const val REQUEST_CODE_ACTIVITY_RECOGNITION = 2
  }

  private var isTracking: Boolean
    get() = this.getSharedPreferences(KEY_SHARED_PREFERENCE, Context.MODE_PRIVATE).getBoolean(KEY_IS_TRACKING, false)
    set(value) = this.getSharedPreferences(KEY_SHARED_PREFERENCE, Context.MODE_PRIVATE).edit().putBoolean(KEY_IS_TRACKING, value).apply()

  override fun onCreate(savedInstanceState: Bundle?) {
    // Switch to AppTheme for displaying the activity
    setTheme(R.style.AppTheme)
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    val view = binding.root
    setContentView(view)

    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)


    // Set up button click events
    binding.startButton.setOnClickListener {
      // Clear the PolylineOptions from Google Map
      mMap.clear()

      // Update Start & End Button
      isTracking = true
      updateButtonStatus()

      // Reset the display text
      updateAllDisplayText(0, 0f)

      startTracking()
    }
    binding.endButton.setOnClickListener { endButtonClicked() }

    // Update layouts
    updateButtonStatus()

    if (isTracking) {
      startTracking()
    }
  }

  // UI related codes
  private fun updateButtonStatus() {
    binding.startButton.isEnabled = !isTracking
    binding.endButton.isEnabled = isTracking
  }

  private fun updateAllDisplayText(stepCount: Int, totalDistanceTravelled: Float) {
    binding.numberOfStepTextView.text =  String.format("Step count: %d", stepCount)
    binding.totalDistanceTextView.text = String.format("Total distance: %.2fm", totalDistanceTravelled)

    val averagePace = if (stepCount != 0) totalDistanceTravelled / stepCount.toDouble() else 0.0
    binding.averagePaceTextView.text = String.format("Average pace: %.2fm/ step", averagePace)
  }

  private fun endButtonClicked() {
    AlertDialog.Builder(this)
        .setTitle("Are you sure to stop tracking?")
        .setPositiveButton("Confirm") { _, _ ->
          isTracking = false
          updateButtonStatus()
          stopTracking()
        }.setNegativeButton("Cancel") { _, _ ->
        }
        .create()
        .show()
  }

  // Tracking
  @SuppressLint("CheckResult")
  private fun startTracking() {

  }

  private fun stopTracking() {
    
  }

  // Map related codes
  /**
   * Manipulates the map once available.
   * This callback is triggered when the map is ready to be used.
   * This is where we can add markers or lines, add listeners or move the camera. In this case,
   * we just add a marker near Sydney, Australia.
   * If Google Play services is not installed on the device, the user will be prompted to install
   * it inside the SupportMapFragment. This method will only be triggered once the user has
   * installed Google Play services and returned to the app.
   */
  @SuppressLint("MissingPermission")
  override fun onMapReady(googleMap: GoogleMap) {
    mMap = googleMap

//    runWithLocationPermissionChecking {
//      mMap.isMyLocationEnabled = true
//    }

    // Add a marker in Hong Kong and move the camera
    val latitude = 22.3193
    val longitude = 114.1694
    val hongKongLatLong = LatLng(latitude, longitude)

    val zoomLevel = 9.5f
    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(hongKongLatLong, zoomLevel))
  }

}
