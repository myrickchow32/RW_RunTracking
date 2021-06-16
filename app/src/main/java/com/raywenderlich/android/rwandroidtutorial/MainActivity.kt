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
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.raywenderlich.android.runtracking.databinding.ActivityMainBinding
import com.raywenderlich.android.rwandroidtutorial.MapsActivityViewModel
import com.raywenderlich.android.rwandroidtutorial.MapsActivityViewModelFactory
import com.raywenderlich.android.rwandroidtutorial.TrackingApplication
import com.raywenderlich.android.rwandroidtutorial.TrackingEntity
import com.tbruyelle.rxpermissions2.RxPermissions
import java.util.*

/**
 * Main Screen
 */
class MainActivity : AppCompatActivity(), OnMapReadyCallback, SensorEventListener {
  private lateinit var binding: ActivityMainBinding

  // ViewModel
  private val mapsActivityViewModel: MapsActivityViewModel by viewModels {
    MapsActivityViewModelFactory(getTrackingRepository())
  }

  // Location & Map
  private lateinit var mMap: GoogleMap
  lateinit var fusedLocationProviderClient: FusedLocationProviderClient
  var polylineOptions = PolylineOptions()
  val locationCallback = object: LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult?) {
      super.onLocationResult(locationResult)
      locationResult ?: return
      locationResult.locations.forEach {
        val trackingEntity = TrackingEntity(Calendar.getInstance().timeInMillis, it.latitude, it.longitude)
        mapsActivityViewModel.insert(trackingEntity)
      }
    }
  }

  // SharedPreferences
  companion object {
    private const val KEY_SHARED_PREFERENCE = "com.rwRunTrackingApp.sharedPreferences"
    private const val KEY_IS_TRACKING = "com.rwRunTrackingApp.isTracking"
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

    // Location
    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

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

    // 1
    mapsActivityViewModel.allTrackingEntities.observe(this) { allTrackingEntities ->
      if (allTrackingEntities.isEmpty()) {
        updateAllDisplayText(0, 0f)
      }
    }

    // 2
    mapsActivityViewModel.lastTrackingEntity.observe(this) { lastTrackingEntity ->
      lastTrackingEntity ?: return@observe
      addLocationToRoute(lastTrackingEntity)
    }

    // 3
    mapsActivityViewModel.totalDistanceTravelled.observe(this) {
      it ?: return@observe
      val stepCount = mapsActivityViewModel.currentNumberOfStepCount.value ?: 0
      updateAllDisplayText(stepCount, it)
    }

    // 4
    mapsActivityViewModel.currentNumberOfStepCount.observe(this) {
      val totalDistanceTravelled = mapsActivityViewModel.totalDistanceTravelled.value ?: 0f
      updateAllDisplayText(it, totalDistanceTravelled)
    }

    // 5
    mapsActivityViewModel.allTrackingEntitiesRecord.observe(this) {
      addLocationListToRoute(it)
    }

    if (isTracking) {
      startTracking()
    }
  }

  // Repository
  private fun getTrackingApplicationInstance() = application as TrackingApplication
  private fun getTrackingRepository() = getTrackingApplicationInstance().trackingRepository

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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      RxPermissions(this).request(Manifest.permission.ACTIVITY_RECOGNITION)
        .subscribe { isGranted ->
          if (isGranted) {
            setupStepCounterListener()
          }
          setupLocationChangeListener()
        }
    } else {
      setupStepCounterListener()
      setupLocationChangeListener()
    }
  }

  private fun stopTracking() {
    polylineOptions = PolylineOptions()

    mapsActivityViewModel.deleteAllTrackingEntity()
    fusedLocationProviderClient.removeLocationUpdates(locationCallback)

    // Stop step sensor listener
    val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    sensorManager.unregisterListener(this, stepCounterSensor)
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

    runWithLocationPermissionChecking {
      mMap.isMyLocationEnabled = true
    }

    // Add a marker in Hong Kong and move the camera
    val latitude = 22.3193
    val longitude = 114.1694
    val hongKongLatLong = LatLng(latitude, longitude)

    val zoomLevel = 9.5f
    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(hongKongLatLong, zoomLevel))

    // Draw all the previous points on the map
    if (isTracking) {
      mapsActivityViewModel.getAllTrackingEntities()
    }
  }

  private fun addLocationListToRoute(trackingEntityList: List<TrackingEntity>) {
    if (!this::mMap.isInitialized) {
      return
    }
    mMap.clear()
    trackingEntityList.forEach { trackingEntity ->
      val newLatLngInstance = trackingEntity.asLatLng()
      polylineOptions.points.add(newLatLngInstance)
    }
    mMap.addPolyline(polylineOptions)
  }

  private fun addLocationToRoute(trackingEntity: TrackingEntity) {
    mMap.clear()
    val newLatLngInstance = trackingEntity.asLatLng()
    polylineOptions.points.add(newLatLngInstance)
    mMap.addPolyline(polylineOptions)
  }

  // Step Counter related codes
  private fun setupStepCounterListener() {
    val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    stepCounterSensor ?: return
    sensorManager.registerListener(this@MainActivity, stepCounterSensor, SensorManager.SENSOR_DELAY_FASTEST)
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    Log.d("TAG", "onAccuracyChanged: Sensor: $sensor; accuracy: $accuracy")
  }

  override fun onSensorChanged(sensorEvent: SensorEvent?) {
    Log.d("TAG", "onSensorChanged")
    sensorEvent ?: return
    val firstSensorEvent = sensorEvent.values.firstOrNull() ?: return
    Log.d("TAG", "Steps count: $firstSensorEvent ")
    val isFirstStepCountRecord = mapsActivityViewModel.currentNumberOfStepCount.value == 0
    if (isFirstStepCountRecord) {
      mapsActivityViewModel.initialStepCount = firstSensorEvent.toInt()
      mapsActivityViewModel.currentNumberOfStepCount.value = 1
    } else {
      mapsActivityViewModel.currentNumberOfStepCount.value = firstSensorEvent.toInt() - mapsActivityViewModel.initialStepCount
    }
  }

  // Location
  @SuppressLint("CheckResult")
  private fun runWithLocationPermissionChecking(callback: () -> Unit) {
    RxPermissions(this).request(Manifest.permission.ACCESS_FINE_LOCATION)
      .subscribe { isGranted ->
        if (isGranted) {
          callback()
        } else {
          Toast.makeText(this, "Please grant Location permission", Toast.LENGTH_LONG).show()
        }
      }
  }

  @SuppressLint("MissingPermission")
  fun setupLocationChangeListener() {
    runWithLocationPermissionChecking {
      val locationRequest = LocationRequest()
      locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
      locationRequest.interval = 5000 // 5000ms (5s)
      fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }
  }
}
