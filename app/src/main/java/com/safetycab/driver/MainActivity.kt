package com.safetycab.driver

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST = 1001

    private lateinit var btnDuty: Button
    private lateinit var tvDutyStatus: TextView
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvTrackingStatus: TextView
    private lateinit var tvLastLocation: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var dutyOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        btnDuty = findViewById(R.id.btnDuty)
        tvDutyStatus = findViewById(R.id.tvDutyStatus)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        tvTrackingStatus = findViewById(R.id.tvTrackingStatus)
        tvLastLocation = findViewById(R.id.tvLastLocation)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(locationResult: LocationResult) {

                val location: Location? =
                    locationResult.lastLocation

                if (location != null) {

                    val latitude = location.latitude
                    val longitude = location.longitude

                    tvGpsStatus.text = "GPS: Active"

                    tvLastLocation.text =
                        "Latitude: $latitude\n" +
                        "Longitude: $longitude\n" +
                        "Last Update: Just now"

                    tvTrackingStatus.text =
                        "Tracking: GPS Active"
                }
            }
        }

        btnDuty.setOnClickListener {

            if (!dutyOn) {
                startDuty()
            } else {
                stopDuty()
            }
        }
    }

    private fun startDuty() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )

            return
        }

        dutyOn = true

        tvDutyStatus.text = "ON DUTY"
        btnDuty.text = "STOP DUTY"
        tvGpsStatus.text = "GPS: Starting..."
        tvTrackingStatus.text = "Tracking: Starting..."

        startLocationUpdates()
    }

    private fun startLocationUpdates() {

        val locationRequest =
            LocationRequest.create().apply {

                interval = 5000

                fastestInterval = 3000

                priority =
                    LocationRequest.PRIORITY_HIGH_ACCURACY
            }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopDuty() {

        dutyOn = false

        fusedLocationClient.removeLocationUpdates(
            locationCallback
        )

        tvDutyStatus.text = "OFF DUTY"
        btnDuty.text = "START DUTY"
        tvGpsStatus.text = "GPS: Not Started"
        tvTrackingStatus.text = "Tracking: Stopped"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == LOCATION_PERMISSION_REQUEST) {

            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startDuty()
            } else {

                tvGpsStatus.text =
                    "GPS: Permission Denied"

                tvTrackingStatus.text =
                    "Tracking: Stopped"
            }
        }
    }
}
