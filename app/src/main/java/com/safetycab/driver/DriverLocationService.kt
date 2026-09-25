package com.safetycab.driver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper

import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

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


class DriverLocationService : Service() {

    companion object {

        const val ACTION_START =
            "com.safetycab.driver.START_LOCATION"

        const val ACTION_STOP =
            "com.safetycab.driver.STOP_LOCATION"

        private const val CHANNEL_ID =
            "SafetyCabDriverLocation"

        private const val NOTIFICATION_ID =
            1001
    }


    private lateinit var fusedLocationClient:
            FusedLocationProviderClient

    private lateinit var locationCallback:
            LocationCallback

    private lateinit var firebaseAuth:
            FirebaseAuth

    private lateinit var firebaseDatabase:
            FirebaseDatabase

    private var firebaseReady = false

    /*
     * Driver ID is NOT hard-coded.
     *
     * It will be loaded from:
     *
     * driverDevices/<Firebase Auth UID>/driverId
     */
    private var driverId: String? = null

    private var trackingStarted = false


    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(this)


        locationCallback =
            object : LocationCallback() {

                override fun onLocationResult(
                    locationResult: LocationResult
                ) {

                    val location: Location? =
                        locationResult.lastLocation

                    if (location != null) {

                        sendLocationToFirebase(
                            location
                        )
                    }
                }
            }


        initializeFirebase()
    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * ACTION_STOP completely stops GPS.
         */
        if (
            intent?.action ==
            ACTION_STOP
        ) {

            stopLocationTracking()

            stopForeground(true)

            stopSelf()

            return START_NOT_STICKY
        }


        /*
         * Start foreground service immediately.
         */
        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )


        /*
         * Firebase authentication and Driver ID
         * verification happen automatically.
         */
        startTrackingWhenDriverReady()


        /*
         * Keep service alive.
         */
        return START_STICKY
    }


    private fun initializeFirebase() {

        try {

            var firebaseApp =
                FirebaseApp
                    .getApps(this)
                    .firstOrNull()


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


            /*
             * Existing Firebase identity.
             */
            if (
                firebaseAuth.currentUser != null
            ) {

                firebaseReady = true

                loadDriverId()

            } else {

                /*
                 * Create Firebase Anonymous identity.
                 */
                firebaseAuth
                    .signInAnonymously()
                    .addOnCompleteListener { task ->

                        if (task.isSuccessful) {

                            firebaseReady = true

                            loadDriverId()

                        } else {

                            firebaseReady = false
                        }
                    }
            }

        } catch (e: Exception) {

            firebaseReady = false
        }
    }


    private fun loadDriverId() {

        if (!firebaseReady) {
            return
        }


        val user =
            firebaseAuth.currentUser


        if (user == null) {

            driverId = null

            return
        }


        val uid =
            user.uid


        /*
         * Read:
         *
         * driverDevices/<UID>/driverId
         */
        firebaseDatabase
            .getReference(
                "driverDevices"
            )
            .child(uid)
            .child("driverId")
            .get()
            .addOnSuccessListener { snapshot ->

                val value =
                    snapshot.getValue(
                        String::class.java
                    )


                if (
                    value != null &&
                    value.trim().isNotEmpty()
                ) {

                    /*
                     * Use toUpperCase() for compatibility
                     * with the current Kotlin environment.
                     */
                    driverId =
                        value
                            .trim()
                            .toUpperCase()


                    /*
                     * Driver is registered.
                     * GPS can now start.
                     */
                    startLocationTracking()

                } else {

                    /*
                     * Device is NOT registered.
                     * GPS will NOT start.
                     */
                    driverId = null

                    trackingStarted = false
                }

            }
            .addOnFailureListener {

                driverId = null

                trackingStarted = false
            }
    }


    private fun startTrackingWhenDriverReady() {

        if (
            firebaseReady &&
            !driverId.isNullOrEmpty()
        ) {

            startLocationTracking()
        }
    }


    private fun startLocationTracking() {

        /*
         * Prevent duplicate GPS callbacks.
         */
        if (trackingStarted) {
            return
        }


        /*
         * Driver ID must be verified first.
         */
        if (
            driverId == null ||
            driverId!!.isEmpty()
        ) {

            return
        }


        /*
         * Check GPS permission.
         */
        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }


        val locationRequest =
            LocationRequest.create().apply {

                /*
                 * Background GPS interval:
                 * 10 minutes.
                 */
                interval = 600000

                fastestInterval = 3000

                priority =
                    LocationRequest
                        .PRIORITY_HIGH_ACCURACY
            }


        fusedLocationClient
            .requestLocationUpdates(

                locationRequest,

                locationCallback,

                Looper.getMainLooper()
            )


        trackingStarted = true
    }


    private fun sendLocationToFirebase(
        location: Location
    ) {

        /*
         * Firebase authentication must be ready.
         */
        if (!firebaseReady) {
            return
        }


        /*
         * Only verified Driver ID can send location.
         */
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
                true


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

        } catch (e: Exception) {

            // Ignore temporary Firebase errors
        }
    }


    private fun stopLocationTracking() {

        /*
         * Stop GPS updates.
         */
        if (
            ::fusedLocationClient
                .isInitialized
        ) {

            try {

                fusedLocationClient
                    .removeLocationUpdates(
                        locationCallback
                    )

            } catch (e: Exception) {

                // Ignore
            }
        }


        trackingStarted = false


        /*
         * Mark the verified driver OFF DUTY.
         */
        try {

            val currentDriverId =
                driverId


            if (
                ::firebaseDatabase.isInitialized &&
                firebaseReady &&
                currentDriverId != null &&
                currentDriverId.isNotEmpty()
            ) {

                val databaseReference =
                    firebaseDatabase
                        .getReference(
                            "liveLocations"
                        )
                        .child(
                            currentDriverId
                        )


                val updates =
                    HashMap<String, Any>()


                updates["driverId"] =
                    currentDriverId


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
            }

        } catch (e: Exception) {

            // Ignore Firebase stop error
        }
    }


    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(

                    CHANNEL_ID,

                    "Safety Cab GPS",

                    NotificationManager
                        .IMPORTANCE_LOW
                )


            channel.description =
                "Safety Cab driver location tracking"


            val manager =
                getSystemService(
                    NotificationManager::class.java
                )


            manager.createNotificationChannel(
                channel
            )
        }
    }


    private fun createNotification():
            Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )

            .setContentTitle(
                "Safety Cab Driver"
            )

            .setContentText(
                "GPS tracking is active"
            )

            .setSmallIcon(
                android.R.drawable.ic_menu_mylocation
            )

            .setOngoing(true)

            .setPriority(
                NotificationCompat
                    .PRIORITY_LOW
            )

            .build()
    }


    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }


    override fun onDestroy() {

        /*
         * Do NOT mark OFF DUTY here.
         *
         * Android may temporarily destroy/recreate
         * a START_STICKY foreground service.
         */

        try {

            if (
                ::fusedLocationClient
                    .isInitialized
            ) {

                fusedLocationClient
                    .removeLocationUpdates(
                        locationCallback
                    )
            }

        } catch (e: Exception) {

            // Ignore
        }


        trackingStarted = false

        super.onDestroy()
    }
}
