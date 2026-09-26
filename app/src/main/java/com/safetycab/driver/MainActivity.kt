package com.safetycab.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
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

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

import java.util.HashMap


class MainActivity : AppCompatActivity() {

    // ============================================================
    // SERIAL NO. 01 — PERMISSIONS / CONSTANTS
    // ============================================================

    private val LOCATION_PERMISSION_REQUEST = 1001
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST = 1002

    private val PREFS_NAME = "SafetyCabDriverPrefs"
    private val DUTY_KEY = "dutyOn"


    // ============================================================
    // SERIAL NO. 02 — DYNAMIC DRIVER / DEVICE ID
    // ============================================================

    /*
     * Driver ID is NOT hard-coded.
     *
     * Driver ID comes from:
     *
     * driverDevices/<Firebase Auth UID>/driverId
     */

    private var driverId: String? = null

    /*
     * Firebase Anonymous Auth UID.
     *
     * This is the Device Pairing ID.
     */

    private var firebaseUid: String? = null


    // ============================================================
    // SERIAL NO. 03 — UI REFERENCES
    // ============================================================

    private lateinit var btnDuty: Button
    private lateinit var tvDutyStatus: TextView
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvTrackingStatus: TextView
    private lateinit var tvLastLocation: TextView
    private lateinit var tvConnectionStatus: TextView

    /*
     * QR button is created in code.
     * No XML change required.
     */

    private var btnPairingQr: Button? = null


    // ============================================================
    // SERIAL NO. 04 — LOCATION / FIREBASE
    // ============================================================

    private lateinit var fusedLocationClient:
            FusedLocationProviderClient

    private lateinit var locationCallback:
            LocationCallback

    private lateinit var firebaseAuth:
            FirebaseAuth

    private lateinit var firebaseDatabase:
            FirebaseDatabase


    // ============================================================
    // SERIAL NO. 05 — APP STATE
    // ============================================================

    private var dutyOn = false

    private var firebaseReady = false

    private var driverReady = false

    private var waitingForBackgroundPermission = false


    // ============================================================
    // SERIAL NO. 06 — ACTIVITY CREATE
    // ============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )


        btnDuty =
            findViewById(
                R.id.btnDuty
            )

        tvDutyStatus =
            findViewById(
                R.id.tvDutyStatus
            )

        tvGpsStatus =
            findViewById(
                R.id.tvGpsStatus
            )

        tvTrackingStatus =
            findViewById(
                R.id.tvTrackingStatus
            )

        tvLastLocation =
            findViewById(
                R.id.tvLastLocation
            )

        tvConnectionStatus =
            findViewById(
                R.id.tvConnectionStatus
            )


        /*
         * Duty disabled until Firebase
         * verifies this device.
         */

        btnDuty.isEnabled = false


        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(
                    this
                )


        locationCallback =
            object : LocationCallback() {

                override fun onLocationResult(
                    locationResult: LocationResult
                ) {

                    val location: Location? =
                        locationResult.lastLocation


                    if (location != null) {

                        tvGpsStatus.text =
                            "GPS: Active"


                        tvLastLocation.text =
                            "Latitude: ${location.latitude}\n" +
                            "Longitude: ${location.longitude}\n" +
                            "Last Update: Just now"


                        tvTrackingStatus.text =
                            "Tracking: GPS Active"


                        ensurePairingQrButton()


                        sendLocationToFirebase(
                            location
                        )
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


        restoreDutyState()
    }


    // ============================================================
    // SERIAL NO. 07 — LOCAL DUTY PREFERENCES
    // ============================================================

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


    // ============================================================
    // SERIAL NO. 08 — RESTORE DUTY
    // ============================================================

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


            ensurePairingQrButton()

            return
        }


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


            ensurePairingQrButton()

            return
        }


        dutyOn = true

        tvDutyStatus.text =
            "ON DUTY"

        btnDuty.text =
            "STOP DUTY"

        tvGpsStatus.text =
            "GPS: Starting..."

        tvTrackingStatus.text =
            "Tracking: Starting..."


        ensurePairingQrButton()


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


    // ============================================================
    // SERIAL NO. 09 — LOCATION PERMISSIONS
    // ============================================================

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


    // ============================================================
    // SERIAL NO. 10 — FIREBASE INITIALIZATION
    // ============================================================

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


    // ============================================================
    // SERIAL NO. 11 — FIREBASE ANONYMOUS LOGIN
    // ============================================================

    private fun signInFirebase() {

        if (
            firebaseAuth.currentUser != null
        ) {

            firebaseReady = true

            firebaseUid =
                firebaseAuth
                    .currentUser
                    ?.uid


            showPairingId()


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

                    firebaseUid =
                        firebaseAuth
                            .currentUser
                            ?.uid


                    showPairingId()


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


    // ============================================================
    // SERIAL NO. 12 — SHOW PAIRING ID
    // ============================================================

    private fun showPairingId() {

        val uid =
            firebaseUid


        if (
            !uid.isNullOrEmpty()
        ) {

            tvTrackingStatus.text =
                "Pairing ID:\n$uid"


            ensurePairingQrButton()
        }
    }


    // ============================================================
    // SERIAL NO. 13 — CREATE PAIRING QR BUTTON
    // ============================================================

    private fun ensurePairingQrButton() {

        if (
            btnPairingQr != null
        ) {

            return
        }


        val uid =
            firebaseUid


        if (
            uid.isNullOrEmpty()
        ) {

            return
        }


        val parent =
            tvTrackingStatus.parent


        if (
            parent !is ViewGroup
        ) {

            return
        }


        val button =
            Button(this)


        button.text =
            "📷 SHOW PAIRING QR"


        button.isAllCaps =
            false


        button.setOnClickListener {

            showPairingQrDialog()
        }


        try {

            val index =
                parent.indexOfChild(
                    tvTrackingStatus
                )


            if (index >= 0) {

                parent.addView(
                    button,
                    index + 1
                )

            } else {

                parent.addView(
                    button
                )
            }


            btnPairingQr =
                button

        } catch (e: Exception) {

            btnPairingQr =
                null
        }
    }


    // ============================================================
    // SERIAL NO. 14 — SHOW PAIRING QR
    // ============================================================

    private fun showPairingQrDialog() {

        val uid =
            firebaseUid


        if (
            uid.isNullOrEmpty()
        ) {

            AlertDialog.Builder(this)
                .setTitle(
                    "Pairing QR"
                )
                .setMessage(
                    "Firebase Pairing ID अभी उपलब्ध नहीं है."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }


        try {

            val qrBitmap =
                generateQrBitmap(
                    uid,
                    700,
                    700
                )


            val container =
                LinearLayout(this)


            container.orientation =
                LinearLayout.VERTICAL


            container.gravity =
                Gravity.CENTER


            val padding =
                (
                    20 *
                    resources.displayMetrics.density
                ).toInt()


            container.setPadding(
                padding,
                padding,
                padding,
                padding
            )


            val title =
                TextView(this)


            title.text =
                "Driver Pairing QR"


            title.textSize =
                20f


            title.gravity =
                Gravity.CENTER


            title.setTextColor(
                Color.BLACK
            )


            container.addView(
                title,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )


            val imageView =
                ImageView(this)


            imageView.setImageBitmap(
                qrBitmap
            )


            imageView.adjustViewBounds =
                true


            val imageParams =
                LinearLayout.LayoutParams(
                    (
                        280 *
                        resources.displayMetrics.density
                    ).toInt(),
                    (
                        280 *
                        resources.displayMetrics.density
                    ).toInt()
                )


            imageParams.gravity =
                Gravity.CENTER


            imageParams.topMargin =
                (
                    15 *
                    resources.displayMetrics.density
                ).toInt()


            imageParams.bottomMargin =
                (
                    15 *
                    resources.displayMetrics.density
                ).toInt()


            container.addView(
                imageView,
                imageParams
            )


            val uidText =
                TextView(this)


            uidText.text =
                "Pairing ID:\n$uid"


            uidText.textSize =
                13f


            uidText.gravity =
                Gravity.CENTER


            uidText.setTextColor(
                Color.DKGRAY
            )


            container.addView(
                uidText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )


            AlertDialog.Builder(this)
                .setView(
                    container
                )
                .setNegativeButton(
                    "CLOSE",
                    null
                )
                .show()

        } catch (e: Exception) {

            AlertDialog.Builder(this)
                .setTitle(
                    "QR Error"
                )
                .setMessage(
                    "Pairing QR generate नहीं हो पाया.\n\n${e.message}"
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()
        }
    }


    // ============================================================
    // SERIAL NO. 15 — QR BITMAP GENERATOR
    // ============================================================

    private fun generateQrBitmap(
        text: String,
        width: Int,
        height: Int
    ): Bitmap {

        val bitMatrix =
            MultiFormatWriter()
                .encode(
                    text,
                    BarcodeFormat.QR_CODE,
                    width,
                    height
                )


        val bitmap =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )


        for (
            x in 0 until width
        ) {

            for (
                y in 0 until height
            ) {

                bitmap.setPixel(
                    x,
                    y,
                    if (
                        bitMatrix.get(
                            x,
                            y
                        )
                    ) {

                        Color.BLACK

                    } else {

                        Color.WHITE
                    }
                )
            }
        }


        return bitmap
    }


    // ============================================================
    // SERIAL NO. 16 — LOAD DRIVER ID
    // ============================================================

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


        firebaseUid =
            uid


        showPairingId()


        firebaseDatabase
            .getReference(
                "driverDevices"
            )
            .child(
                uid
            )
            .child(
                "driverId"
            )
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

                    driverId =
                        value
                            .trim()
                            .toUpperCase()


                    driverReady =
                        true


                    btnDuty.isEnabled =
                        true


                    tvConnectionStatus.text =
                        "Firebase: Connected"


                    tvTrackingStatus.text =
                        "Driver ID: $driverId\n" +
                        "Pairing ID:\n$uid"


                    ensurePairingQrButton()


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

                    driverId =
                        null


                    driverReady =
                        false


                    btnDuty.isEnabled =
                        false


                    tvDutyStatus.text =
                        "DRIVER NOT REGISTERED"


                    btnDuty.text =
                        "START DUTY"


                    tvGpsStatus.text =
                        "GPS: Not Started"


                    tvTrackingStatus.text =
                        "Pairing ID:\n$uid\n\n" +
                        "Driver registration required"


                    tvLastLocation.text =
                        "Device not registered"


                    ensurePairingQrButton()
                }

            }
            .addOnFailureListener {

                driverId =
                    null


                driverReady =
                    false


                btnDuty.isEnabled =
                    false


                tvTrackingStatus.text =
                    "Pairing ID:\n$uid\n\n" +
                    "Driver verification failed"


                ensurePairingQrButton()
            }
    }


    // ============================================================
    // SERIAL NO. 17 — START DUTY
    // ============================================================

    private fun startDuty() {

        if (
            !driverReady ||
            driverId.isNullOrEmpty()
        ) {

            tvTrackingStatus.text =
                "Tracking: Driver not verified"

            return
        }


        if (
            !hasLocationPermission()
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


        if (
            !hasBackgroundLocationPermission()
        ) {

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


                    startActivity(
                        intent
                    )

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


        dutyOn =
            true


        saveDutyState(
            true
        )


        tvDutyStatus.text =
            "ON DUTY"


        btnDuty.text =
            "STOP DUTY"


        tvGpsStatus.text =
            "GPS: Starting..."


        tvTrackingStatus.text =
            "Driver ID: $driverId\n" +
            "Tracking: Starting..."


        ensurePairingQrButton()


        startDriverLocationService()


        startLocationUpdates()
    }


    // ============================================================
    // SERIAL NO. 18 — FOREGROUND LOCATION SERVICE
    // ============================================================

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
                "Driver ID: $driverId\n" +
                "Tracking: GPS Active"

        } catch (e: Exception) {

            tvTrackingStatus.text =
                "Tracking: Service Start Failed"
        }
    }


    // ============================================================
    // SERIAL NO. 19 — LOCATION UPDATES
    // ============================================================

    private fun startLocationUpdates() {

        val locationRequest =
            LocationRequest.create().apply {

                interval =
                    5000

                fastestInterval =
                    3000

                priority =
                    LocationRequest
                        .PRIORITY_HIGH_ACCURACY
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


        fusedLocationClient
            .requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
    }


    // ============================================================
    // SERIAL NO. 20 — SEND LOCATION TO FIREBASE
    // ============================================================

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


    // ============================================================
    // SERIAL NO. 21 — STOP DUTY
    // ============================================================

    private fun stopDuty() {

        dutyOn =
            false


        saveDutyState(
            false
        )


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


            startService(
                serviceIntent
            )

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
            "Driver ID: $driverId\n" +
            "Tracking: Stopped"


        ensurePairingQrButton()
    }


    // ============================================================
    // SERIAL NO. 22 — PERMISSION RESULT
    // ============================================================

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


    // ============================================================
    // SERIAL NO. 23 — RESUME
    // ============================================================

    override fun onResume() {

        super.onResume()


        if (
            waitingForBackgroundPermission
        ) {

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


    // ============================================================
    // SERIAL NO. 24 — DESTROY
    // ============================================================

    override fun onDestroy() {

        /*
         * Do NOT stop DriverLocationService here.
         * Foreground service continues independently.
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
