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

        private const val DRIVER_ID =
            "DC001"
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
         * IMPORTANT:
         *
         * MainActivity may start the service without
         * an ACTION. Therefore any start command that
         * is NOT ACTION_STOP will start tracking.
         *
         * This also helps when Android recreates a
         * START_STICKY service with a null intent.
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
         * Start Foreground Service immediately.
         */
        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )


        /*
         * Start GPS tracking.
         */
        startLocationTracking()


        /*
         * IMPORTANT:
         *
         * Ask Android to recreate this service if
         * the process is removed.
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
             * Firebase Authentication
             *
             * RTDB rules require authenticated user.
             */

            if (
                firebaseAuth.currentUser != null
            ) {

                firebaseReady = true

            } else {

                firebaseAuth
                    .signInAnonymously()
                    .addOnCompleteListener {

                        firebaseReady =
                            it.isSuccessful
                    }
            }

        } catch (e: Exception) {

            firebaseReady = false
        }
    }


    private fun startLocationTracking() {

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
                 * TEST MODE
                 *
                 * GPS update every 5 seconds.
                 *
                 * First verify background tracking.
                 *
                 * Later we can change this to
                 * 15 minutes.
                 */

                interval = 5000

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
    }


    private fun sendLocationToFirebase(
        location: Location
    ) {

        /*
         * Firebase may still be signing in.
         */
        if (!firebaseReady) {

            return
        }


        try {

            val databaseReference =
                firebaseDatabase
                    .getReference(
                        "liveLocations"
                    )
                    .child(
                        DRIVER_ID
                    )


            val locationData =
                HashMap<String, Any>()


            locationData["driverId"] =
                DRIVER_ID


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


        /*
         * Only ACTION_STOP should mark
         * the driver OFF DUTY.
         *
         * We deliberately do NOT write OFF DUTY
         * from onDestroy().
         *
         * This is important because Android can
         * destroy/recreate a START_STICKY service.
         */

        try {

            if (
                ::firebaseDatabase
                    .isInitialized
            ) {

                val databaseReference =
                    firebaseDatabase
                        .getReference(
                            "liveLocations"
                        )
                        .child(
                            DRIVER_ID
                        )


                val updates =
                    HashMap<String, Any>()


                updates["driverId"] =
                    DRIVER_ID


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
         * IMPORTANT:
         *
         * Do NOT mark OFF DUTY here.
         *
         * Android may destroy the service temporarily.
         * START_STICKY should allow it to be recreated.
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


        super.onDestroy()
    }
}
