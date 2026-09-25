package com.safetycab.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
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

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

import java.util.HashMap


class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST = 1001
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST = 1002

    /*
     * IMPORTANT:
     *
     * Driver ID is NO LONGER hard-coded.
     *
     * It will be loaded from:
     *
     * driverDevices/<Firebase Auth UID>/driverId
     */
    private var driverId: String? = null

    private val PREFS_NAME = "SafetyCabDriverPrefs"
    private val DUTY_KEY = "dutyOn"

    private lateinit var btnDuty: Button
    private lateinit var tvDutyStatus: TextView
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvTrackingStatus: TextView
    private lateinit var tvLastLocation: TextView
    private lateinit var tvConnectionStatus: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase

    private var dutyOn = false
    private var firebaseReady = false
    private var driverReady = false
    private var waitingForBackgroundPermission = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        btnDuty = findViewById(R.id.btnDuty)
        tvDutyStatus = findViewById(R.id.tvDutyStatus)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        tvTrackingStatus = findViewById(R.id.tvTrackingStatus)
        tvLastLocation = findViewById(R.id.tvLastLocation)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)

        /*
         * Disable Duty until Firebase verifies
         * that this device belongs to a registered driver.
         */
        btnDuty.isEnabled = false

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(
                locationResult: LocationResult
            ) {

                val location: Location? =
                    locationResult.lastLocation

                if (location != null) {

                    val latitude = location.latitude
                    val longitude = location.longitude

                    tvGpsStatus.text =
                        "GPS: Active"

                    tvLastLocation.text =
                        "Latitude: $latitude\n" +
                        "Longitude: $longitude\n" +
                        "Last Update: Just now"

                    tvTrackingStatus.text =
                        "Tracking: GPS Active"

                    sendLocationToFirebase(location)
                }
            }
        }

        initializeFirebase()

        btnDuty.setOnClickListener {

            if (!driverReady) {

                tvTrackingStatus.text =
                    "Tracking: Driver registration not verified"

                return@setOnClickListener
            }

            if (!dutyOn) {

                startDuty()

            } else {

                stopDuty()
            }
        }

        /*
         * Restore Duty state only after the device
         * Driver ID has been verified by Firebase.
         */
        restoreDutyState()
    }


    private fun getPreferences() =
        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )


    private fun saveDutyState(
        enabled: Boolean
    ) {

        getPreferences()
            .edit()
            .putBoolean(
                DUTY_KEY,
                enabled
            )
            .apply()
    }


    private fun restoreDutyState() {

        val savedDuty =
            getPreferences()
                .getBoolean(
                    DUTY_KEY,
                    false
                )

        if (!savedDuty) {

            dutyOn = false

            tvDutyStatus.text =
                "OFF DUTY"

            btnDuty.text =
                "START DUTY"

            tvGpsStatus.text =
                "GPS: Not Started"

            tvTrackingStatus.text =
                "Tracking: Stopped"

            return
        }


        /*
         * If Firebase has not verified the Driver ID yet,
         * do not start GPS.
         */
        if (!driverReady) {

            dutyOn = false

            tvDutyStatus.text =
                "DRIVER VERIFYING..."

            btnDuty.text =
                "START DUTY"

            tvGpsStatus.text =
                "GPS: Waiting"

            tvTrackingStatus.text =
                "Tracking: Driver verification pending"

            return
        }


        /*
         * Previously ON DUTY
         */
        dutyOn = true

        tvDutyStatus.text =
            "ON DUTY"

        btnDuty.text =
            "STOP DUTY"

        tvGpsStatus.text =
            "GPS: Starting..."

        tvTrackingStatus.text =
            "Tracking: Starting..."


        if (
            hasLocationPermission() &&
            hasBackgroundLocationPermission()
        ) {

            startDriverLocationService()

            startLocationUpdates()

        } else {

            tvTrackingStatus.text =
                "Tracking: Background Permission Needed"
        }
    }


    private fun hasLocationPermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }


    private fun hasBackgroundLocationPermission(): Boolean {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.Q
        ) {

            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun initializeFirebase() {

        try {

            var firebaseApp =
                FirebaseApp.getApps(this).firstOrNull()

            if (firebaseApp == null) {

                val options =
                    FirebaseOptions.Builder()
                        .setApiKey(
                            "AIzaSyBqSICccKKX94x04rjCUHXjW5EJoaI10Bc"
                        )
                        .setApplicationId(
                            "1:900129993912:web:48c98c4d62154d0f39704d"
                        )
                        .setProjectId(
                            "safety-cab-radar"
                        )
                        .setDatabaseUrl(
                            "https://safety-cab-radar-default-rtdb.asia-southeast1.firebasedatabase.app"
                        )
                        .setStorageBucket(
                            "safety-cab-radar.firebasestorage.app"
                        )
                        .setGcmSenderId(
                            "900129993912"
                        )
                        .build()

                firebaseApp =
                    FirebaseApp.initializeApp(
                        this,
                        options
                    )
            }


            if (firebaseApp == null) {

                firebaseReady = false

                tvConnectionStatus.text =
                    "Firebase: Initialization Failed"

                return
            }


            firebaseAuth =
                FirebaseAuth.getInstance(
                    firebaseApp
                )

            firebaseDatabase =
                FirebaseDatabase.getInstance(
                    firebaseApp
                )

            tvConnectionStatus.text =
                "Firebase: Connecting..."

            signInFirebase()

        } catch (e: Exception) {

            firebaseReady = false
            driverReady = false

            tvConnectionStatus.text =
                "Firebase: Error"
        }
    }


    private fun signInFirebase() {

        if (firebaseAuth.currentUser != null) {

            firebaseReady = true

            tvConnectionStatus.text =
                "Firebase: Connected"

            loadDriverId()

            return
        }


        firebaseAuth
            .signInAnonymously()
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {

                    firebaseReady = true

                    tvConnectionStatus.text =
                        "Firebase: Connected"

                    loadDriverId()

                } else {

                    firebaseReady = false
                    driverReady = false

                    tvConnectionStatus.text =
                        "Firebase: Login Failed"

                    btnDuty.isEnabled = false
                }
            }
    }


    /*
     * Load the Driver ID assigned to this Firebase
     * Anonymous Auth UID.
     *
     * Firebase path:
     *
     * driverDevices/<UID>/driverId
     */
    private fun loadDriverId() {

        val user =
            firebaseAuth.currentUser

        if (user == null) {

            driverReady = false

            btnDuty.isEnabled = false

            tvTrackingStatus.text =
                "Tracking: Firebase identity unavailable"

            return
        }


        val uid =
            user.uid


        firebaseDatabase
            .getReference("driverDevices")
            .child(uid)
            .child("driverId")
            .get()
            .addOnSuccessListener { snapshot ->

                val value =
                    snapshot.getValue(String::class.java)


                if (
                    value != null &&
                    value.trim().isNotEmpty()
                ) {

                    driverId =
                        value.trim().uppercase()

                    driverReady = true

                    btnDuty.isEnabled = true

                    tvConnectionStatus.text =
                        "Firebase: Connected"

                    tvTrackingStatus.text =
                        "Driver ID: $driverId"

                    /*
                     * If Duty was already ON before
                     * the app was closed, restore it now.
                     */
                    val savedDuty =
                        getPreferences()
                            .getBoolean(
                                DUTY_KEY,
                                false
                            )

                    if (savedDuty) {

                        restoreDutyState()
                    }

                } else {

                    driverId = null
                    driverReady = false

                    btnDuty.isEnabled = false

                    tvDutyStatus.text =
                        "DRIVER NOT REGISTERED"

                    btnDuty.text =
                        "START DUTY"

                    tvGpsStatus.text =
                        "GPS: Not Started"

                    tvTrackingStatus.text =
                        "Tracking: Device not registered"

                    tvLastLocation.text =
                        "Driver registration required"

                }

            }
            .addOnFailureListener {

                driverId = null
                driverReady = false

                btnDuty.isEnabled = false

                tvTrackingStatus.text =
                    "Tracking: Driver verification failed"
            }
    }


    private fun startDuty() {

        if (!driverReady || driverId.isNullOrEmpty()) {

            tvTrackingStatus.text =
                "Tracking: Driver not verified"

            return
        }


        if (!hasLocationPermission()) {

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


        if (!hasBackgroundLocationPermission()) {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.R
            ) {

                waitingForBackgroundPermission =
                    true

                try {

                    val intent =
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        )

                    intent.data =
                        Uri.parse(
                            "package:$packageName"
                        )

                    startActivity(intent)

                } catch (e: Exception) {

                    tvTrackingStatus.text =
                        "Tracking: Open App Settings"
                }

                return

            } else {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ),
                    BACKGROUND_LOCATION_PERMISSION_REQUEST
                )

                return
            }
        }


        dutyOn = true

        saveDutyState(true)


        tvDutyStatus.text =
            "ON DUTY"

        btnDuty.text =
            "STOP DUTY"

        tvGpsStatus.text =
            "GPS: Starting..."

        tvTrackingStatus.text =
            "Tracking: Starting..."


        startDriverLocationService()

        startLocationUpdates()
    }


    private fun startDriverLocationService() {

        try {

            val serviceIntent =
                Intent(
                    this,
                    DriverLocationService::class.java
                ).apply {

                    action =
                        DriverLocationService.ACTION_START
                }


            ContextCompat.startForegroundService(
                this,
                serviceIntent
            )

            tvTrackingStatus.text =
                "Tracking: GPS Active"

        } catch (e: Exception) {

            tvTrackingStatus.text =
                "Tracking: Service Start Failed"
        }
    }


    private fun startLocationUpdates() {

        val locationRequest =
            LocationRequest.create().apply {

                interval = 5000

                fastestInterval = 3000

                priority =
                    LocationRequest.PRIORITY_HIGH_ACCURACY
            }


        if (
            ActivityCompat.checkSelfPermission(
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


    private fun sendLocationToFirebase(
        location: Location
    ) {

        if (!firebaseReady) {
            return
        }

        if (!driverReady) {
            return
        }

        val currentDriverId =
            driverId

        if (
            currentDriverId == null ||
            currentDriverId.isEmpty()
        ) {
            return
        }


        try {

            val databaseReference =
                firebaseDatabase
                    .getReference(
                        "liveLocations"
                    )
                    .child(
                        currentDriverId
                    )


            val locationData =
                HashMap<String, Any>()


            locationData["driverId"] =
                currentDriverId

            locationData["lat"] =
                location.latitude

            locationData["lng"] =
                location.longitude

            locationData["online"] =
                true

            locationData["duty"] =
                dutyOn

            locationData["accuracy"] =
                location.accuracy.toDouble()

            locationData["speed"] =
                location.speed.toDouble()

            locationData["heading"] =
                location.bearing.toDouble()

            locationData["updatedAt"] =
                System.currentTimeMillis()


            databaseReference
                .setValue(
                    locationData
                )
                .addOnSuccessListener {

                    tvConnectionStatus.text =
                        "Firebase: Connected"

                }
                .addOnFailureListener {

                    tvConnectionStatus.text =
                        "Firebase: Write Failed"
                }

        } catch (e: Exception) {

            tvConnectionStatus.text =
                "Firebase: Write Error"
        }
    }


    private fun stopDuty() {

        dutyOn = false

        saveDutyState(false)


        try {

            fusedLocationClient
                .removeLocationUpdates(
                    locationCallback
                )

        } catch (e: Exception) {
            // Ignore
        }


        try {

            val serviceIntent =
                Intent(
                    this,
                    DriverLocationService::class.java
                ).apply {

                    action =
                        DriverLocationService.ACTION_STOP
                }

            startService(serviceIntent)

        } catch (e: Exception) {
            // Ignore
        }


        if (
            firebaseReady &&
            driverReady &&
            !driverId.isNullOrEmpty()
        ) {

            try {

                val databaseReference =
                    firebaseDatabase
                        .getReference(
                            "liveLocations"
                        )
                        .child(
                            driverId!!
                        )


                val updates =
                    HashMap<String, Any>()


                updates["driverId"] =
                    driverId!!

                updates["online"] =
                    false

                updates["duty"] =
                    false

                updates["updatedAt"] =
                    System.currentTimeMillis()


                databaseReference
                    .updateChildren(
                        updates
                    )

            } catch (e: Exception) {
                // Ignore Firebase stop update error
            }
        }


        tvDutyStatus.text =
            "OFF DUTY"

        btnDuty.text =
            "START DUTY"

        tvGpsStatus.text =
            "GPS: Not Started"

        tvTrackingStatus.text =
            "Tracking: Stopped"
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


        if (
            requestCode ==
            LOCATION_PERMISSION_REQUEST
        ) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                startDuty()

            } else {

                tvGpsStatus.text =
                    "GPS: Permission Denied"

                tvTrackingStatus.text =
                    "Tracking: Stopped"
            }
        }


        if (
            requestCode ==
            BACKGROUND_LOCATION_PERMISSION_REQUEST
        ) {

            val granted =
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED


            if (granted) {

                startDuty()

            } else {

                tvTrackingStatus.text =
                    "Tracking: Background Permission Needed"
            }
        }
    }


    override fun onResume() {

        super.onResume()


        if (waitingForBackgroundPermission) {

            waitingForBackgroundPermission =
                false


            if (
                hasBackgroundLocationPermission()
            ) {

                tvTrackingStatus.text =
                    "Tracking: Background Permission Granted"

                startDuty()

            } else {

                tvTrackingStatus.text =
                    "Tracking: Background Permission Not Granted"
            }
        }
    }


    override fun onDestroy() {

        /*
         * IMPORTANT:
         *
         * Do NOT stop DriverLocationService here.
         *
         * Foreground service must continue when
         * Activity is closed/backgrounded.
         */

        if (
            ::fusedLocationClient.isInitialized &&
            ::locationCallback.isInitialized
        ) {

            fusedLocationClient
                .removeLocationUpdates(
                    locationCallback
                )
        }


        super.onDestroy()
    }
}
