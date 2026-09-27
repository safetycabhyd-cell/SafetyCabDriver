package com.safetycab.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
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
import android.widget.Switch
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

import java.util.HashMap
import java.net.URL


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

    private var driverId: String? = null

    private var firebaseUid: String? = null


    // ============================================================
    // SERIAL NO. 03 — UI REFERENCES
    // ============================================================

    private lateinit var btnDuty: Switch
    private lateinit var tvDutyStatus: TextView
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvTrackingStatus: TextView
    private lateinit var tvLastLocation: TextView
    private lateinit var tvConnectionStatus: TextView

    private lateinit var ivDriverPhoto: ImageView
    private lateinit var ivCarPhoto: ImageView
    private lateinit var tvCarNumber: TextView
    private lateinit var tvDriverPhotoStatus: TextView
    private lateinit var tvCarPhotoStatus: TextView
    private lateinit var settingsPanel: LinearLayout
    private lateinit var settingsToggle: Button

    private var driverPhotoUrl: String? = null
    private var carPhotoUrl: String? = null
    private var carNumber: String? = null

    private val COMPANY_PHONE = "08062180745"

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

        createDashboard()

        btnDuty.isEnabled = false

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

                        tvGpsStatus.text =
                            "GPS: Active"

                        tvLastLocation.text =
                            "Latitude: ${location.latitude}\n" +
                            "Longitude: ${location.longitude}\n" +
                            "Last Update: Just now"

                        tvTrackingStatus.text =
                            "Tracking: GPS Active"

                        ensurePairingQrButton()

                        sendLocationToFirebase(location)
                    }
                }
            }

        initializeFirebase()

        btnDuty.setOnClickListener {

            if (!driverReady) {

                btnDuty.isChecked = false
                btnDuty.text = "OFF"

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


        settingsToggle.setOnClickListener {

            val show =
                settingsPanel.visibility !=
                        android.view.View.VISIBLE

            settingsPanel.visibility =
                if (show) {

                    android.view.View.VISIBLE

                } else {

                    android.view.View.GONE
                }

            settingsToggle.text =
                if (show) {

                    "⚙ Settings  ▲"

                } else {

                    "⚙ Settings  ▼"
                }
        }

        restoreDutyState()
    }


    // ============================================================
    // SERIAL NO. 06A — SAFETY CAB DRIVER DASHBOARD
    // ============================================================

    private fun createDashboard() {

        val scroll =
            ScrollView(this)

        scroll.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

        scroll.setBackgroundColor(Color.WHITE)


        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.setPadding(
            dp(14),
            dp(10),
            dp(14),
            dp(24)
        )

        scroll.addView(root)


        // ========================================================
        // SAFETY CAB COMPLETE LOGO
        // ========================================================

        val logoContainer =
            LinearLayout(this)

        logoContainer.orientation =
            LinearLayout.VERTICAL

        logoContainer.gravity =
            Gravity.CENTER

        logoContainer.setBackgroundColor(
            Color.WHITE
        )

        logoContainer.setPadding(
            dp(4),
            dp(4),
            dp(4),
            dp(4)
        )


        val logo =
            ImageView(this)

        logo.setImageResource(
            R.drawable.file_000000005080820eb97aa57d3fa76943
        )

        logo.scaleType =
            ImageView.ScaleType.FIT_CENTER

        logo.contentDescription =
            "Safety Cab Logo"


        logoContainer.addView(
            logo,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(210)
            )
        )


        root.addView(
            logoContainer,
            marginLp(
                0,
                0,
                0,
                6
            )
        )


        // ========================================================
        // DUTY STATUS — TOP
        // ========================================================

        val dutyCard =
            LinearLayout(this)

        dutyCard.orientation =
            LinearLayout.HORIZONTAL

        dutyCard.gravity =
            Gravity.CENTER_VERTICAL

        dutyCard.setPadding(
            dp(16),
            dp(10),
            dp(12),
            dp(10)
        )

        dutyCard.setBackgroundColor(
            Color.rgb(
                239,
                247,
                255
            )
        )


        val dutyLabel =
            TextView(this)

        dutyLabel.text =
            "🚕  DUTY"

        dutyLabel.textSize =
            19f

        dutyLabel.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        dutyLabel.setTextColor(
            Color.rgb(
                0,
                70,
                145
            )
        )


        dutyCard.addView(
            dutyLabel,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )


        btnDuty =
            Switch(this)

        btnDuty.text =
            "OFF"

        btnDuty.textSize =
            15f

        btnDuty.isChecked =
            false

        btnDuty.setTextColor(
            Color.rgb(
                0,
                70,
                145
            )
        )


        dutyCard.addView(
            btnDuty,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )


        root.addView(
            dutyCard,
            marginLp(
                0,
                0,
                0,
                4
            )
        )


        tvDutyStatus =
            TextView(this)

        tvDutyStatus.text =
            "OFF DUTY"

        tvDutyStatus.textSize =
            13f

        tvDutyStatus.gravity =
            Gravity.CENTER

        tvDutyStatus.setTextColor(
            Color.DKGRAY
        )


        root.addView(
            tvDutyStatus,
            marginLp(
                0,
                0,
                0,
                12
            )
        )


        // ========================================================
        // DRIVER PROFILE
        // ========================================================

        val driverCard =
            createInfoCard()


        ivDriverPhoto =
            createPhotoView()


        driverCard.addView(
            ivDriverPhoto,
            LinearLayout.LayoutParams(
                dp(105),
                dp(105)
            )
        )


        val driverInfo =
            LinearLayout(this)

        driverInfo.orientation =
            LinearLayout.VERTICAL

        driverInfo.setPadding(
            dp(14),
            0,
            0,
            0
        )


        driverInfo.addView(
            textView(
                "DRIVER",
                13f,
                Color.GRAY
            ),
            lp()
        )


        val idView =
            textView(
                "Not Registered",
                25f,
                Color.rgb(
                    0,
                    70,
                    145
                )
            )

        idView.id =
            R.id.tvDriverId


        driverInfo.addView(
            idView,
            lp()
        )


        tvDriverPhotoStatus =
            textView(
                "Driver photo",
                13f,
                Color.GRAY
            )


        driverInfo.addView(
            tvDriverPhotoStatus,
            lp()
        )


        driverCard.addView(
            driverInfo,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )


        root.addView(
            driverCard,
            marginLp(
                0,
                0,
                0,
                10
            )
        )


        // ========================================================
        // CAR INFORMATION
        // ========================================================

        val carCard =
            createInfoCard()


        ivCarPhoto =
            createPhotoView()


        carCard.addView(
            ivCarPhoto,
            LinearLayout.LayoutParams(
                dp(105),
                dp(105)
            )
        )


        val carInfo =
            LinearLayout(this)

        carInfo.orientation =
            LinearLayout.VERTICAL

        carInfo.setPadding(
            dp(14),
            0,
            0,
            0
        )


        carInfo.addView(
            textView(
                "CAR NUMBER",
                13f,
                Color.GRAY
            ),
            lp()
        )


        tvCarNumber =
            textView(
                "Not Registered",
                23f,
                Color.rgb(
                    0,
                    70,
                    145
                )
            )


        carInfo.addView(
            tvCarNumber,
            lp()
        )


        tvCarPhotoStatus =
            textView(
                "Car photo",
                13f,
                Color.GRAY
            )


        carInfo.addView(
            tvCarPhotoStatus,
            lp()
        )


        carCard.addView(
            carInfo,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )


        root.addView(
            carCard,
            marginLp(
                0,
                0,
                0,
                12
            )
        )


        // ========================================================
        // HELP & SUPPORT
        // ========================================================

        val supportButton =
            Button(this)

        supportButton.text =
            "📞  Help & Support"

        supportButton.textSize =
            17f

        supportButton.isAllCaps =
            false

        supportButton.setTextColor(
            Color.WHITE
        )

        supportButton.setBackgroundColor(
            Color.rgb(
                0,
                94,
                180
            )
        )


        supportButton.setOnClickListener {

            try {

                val intent =
                    Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse(
                            "tel:$COMPANY_PHONE"
                        )
                    )

                startActivity(intent)

            } catch (e: Exception) {
                // Ignore if dialer is unavailable.
            }
        }


        root.addView(
            supportButton,
            marginLp(
                0,
                0,
                0,
                12
            )
        )


        // ========================================================
        // SETTINGS BUTTON
        // ========================================================

        settingsToggle =
            Button(this)

        settingsToggle.text =
            "⚙ Settings  ▼"

        settingsToggle.isAllCaps =
            false

        settingsToggle.textSize =
            16f

        settingsToggle.setTextColor(
            Color.rgb(
                0,
                70,
                145
            )
        )


        root.addView(
            settingsToggle,
            marginLp(
                0,
                0,
                0,
                4
            )
        )


        // ========================================================
        // SETTINGS PANEL
        // ========================================================

        settingsPanel =
            LinearLayout(this)

        settingsPanel.orientation =
            LinearLayout.VERTICAL

        settingsPanel.setPadding(
            dp(14),
            dp(12),
            dp(14),
            dp(12)
        )

        settingsPanel.setBackgroundColor(
            Color.rgb(
                247,
                250,
                253
            )
        )

        settingsPanel.visibility =
            android.view.View.GONE


        tvConnectionStatus =
            textView(
                "Firebase: Not Connected",
                15f,
                Color.DKGRAY
            )


        settingsPanel.addView(
            tvConnectionStatus,
            marginLp(
                0,
                0,
                0,
                6
            )
        )


        tvGpsStatus =
            textView(
                "GPS: Not Started",
                15f,
                Color.DKGRAY
            )


        settingsPanel.addView(
            tvGpsStatus,
            marginLp(
                0,
                0,
                0,
                6
            )
        )


        tvTrackingStatus =
            textView(
                "Tracking: Stopped",
                15f,
                Color.DKGRAY
            )


        settingsPanel.addView(
            tvTrackingStatus,
            marginLp(
                0,
                0,
                0,
                6
            )
        )


        tvLastLocation =
            textView(
                "Latitude: --\n" +
                "Longitude: --\n" +
                "Last Update: --",
                14f,
                Color.DKGRAY
            )


        settingsPanel.addView(
            tvLastLocation,
            marginLp(
                0,
                0,
                0,
                4
            )
        )


        root.addView(
            settingsPanel,
            lp()
        )


        // ========================================================
        // SHOW DASHBOARD
        // ========================================================

        setContentView(scroll)
    }


    // ============================================================
    // SERIAL NO. 07 — UI HELPERS
    // ============================================================

    private fun dp(
        value: Int
    ): Int =
        (
            value *
            resources.displayMetrics.density
        ).toInt()


    private fun lp():
            LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )


    private fun marginLp(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {

            setMargins(
                dp(left),
                dp(top),
                dp(right),
                dp(bottom)
            )
        }
    }


    private fun createInfoCard():
            LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.HORIZONTAL

            gravity =
                Gravity.CENTER_VERTICAL

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            setBackgroundColor(
                Color.rgb(
                    247,
                    250,
                    253
                )
            )
        }
    }


    private fun createPhotoView():
            ImageView {

        return ImageView(this).apply {

            setBackgroundColor(
                Color.rgb(
                    225,
                    236,
                    248
                )
            )

            scaleType =
                ImageView.ScaleType.CENTER_CROP

            setImageResource(
                android.R.drawable.ic_menu_camera
            )
        }
    }


    private fun textView(
        value: String,
        size: Float,
        color: Int
    ): TextView {

        return TextView(this).apply {

            text =
                value

            textSize =
                size

            setTextColor(
                color
            )

            setPadding(
                0,
                dp(3),
                0,
                dp(3)
            )
        }
    }


    private fun setDutyUi(
        enabled: Boolean
    ) {

        dutyOn =
            enabled

        btnDuty.isChecked =
            enabled

        btnDuty.text =
            if (enabled) "ON" else "OFF"

        tvDutyStatus.text =
            if (enabled) {

                "ON DUTY"

            } else {

                "OFF DUTY"
            }
    }


    private fun updateProfileUi() {

        findViewById<TextView>(
            R.id.tvDriverId
        )?.text =
            driverId ?: "Not Registered"


        tvCarNumber.text =
            carNumber
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Not Registered"


        tvDriverPhotoStatus.text =
            if (
                driverPhotoUrl.isNullOrBlank()
            ) {

                "Driver photo not registered"

            } else {

                "Driver photo available"
            }


        tvCarPhotoStatus.text =
            if (
                carPhotoUrl.isNullOrBlank()
            ) {

                "Car photo not registered"

            } else {

                "Car photo available"
            }


        if (
            !driverPhotoUrl.isNullOrBlank()
        ) {

            loadImageIntoView(
                driverPhotoUrl!!,
                ivDriverPhoto,
                tvDriverPhotoStatus,
                "Driver photo loaded"
            )
        }


        if (
            !carPhotoUrl.isNullOrBlank()
        ) {

            loadImageIntoView(
                carPhotoUrl!!,
                ivCarPhoto,
                tvCarPhotoStatus,
                "Car photo loaded"
            )
        }
    }


    private fun loadImageIntoView(
        url: String,
        imageView: ImageView,
        statusView: TextView,
        successText: String
    ) {

        Thread {

            try {

                val bitmap: Bitmap? =
                    if (url.startsWith("data:image", ignoreCase = true)) {

                        val commaIndex =
                            url.indexOf(',')

                        if (commaIndex >= 0) {

                            val base64Data =
                                url.substring(commaIndex + 1)

                            val bytes =
                                Base64.decode(
                                    base64Data,
                                    Base64.DEFAULT
                                )

                            BitmapFactory.decodeByteArray(
                                bytes,
                                0,
                                bytes.size
                            )

                        } else {

                            null
                        }

                    } else {

                        URL(url)
                            .openStream()
                            .use {

                                BitmapFactory
                                    .decodeStream(it)
                            }
                    }


                runOnUiThread {

                    if (bitmap != null) {

                        imageView.setImageBitmap(
                            bitmap
                        )

                        statusView.text =
                            successText

                    } else {

                        statusView.text =
                            "Photo could not be loaded"
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusView.text =
                        "Photo could not be loaded"
                }
            }

        }.start()
    }


    // ============================================================
    // SERIAL NO. 08 — LOCAL DUTY PREFERENCES
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
    // SERIAL NO. 09 — RESTORE DUTY
    // ============================================================

    private fun restoreDutyState() {

        val savedDuty =
            getPreferences()
                .getBoolean(
                    DUTY_KEY,
                    false
                )


        if (!savedDuty) {

            setDutyUi(false)

            tvGpsStatus.text =
                "GPS: Not Started"

            tvTrackingStatus.text =
                "Tracking: Stopped"

            ensurePairingQrButton()

            return
        }


        if (!driverReady) {

            setDutyUi(false)

            tvDutyStatus.text =
                "DRIVER VERIFYING..."

            tvGpsStatus.text =
                "GPS: Waiting"

            tvTrackingStatus.text =
                "Tracking: Driver verification pending"

            ensurePairingQrButton()

            return
        }


        setDutyUi(true)

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
    // SERIAL NO. 10 — LOCATION PERMISSIONS
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
    // SERIAL NO. 11 — FIREBASE INITIALIZATION
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

                firebaseReady =
                    false

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

            firebaseReady =
                false

            driverReady =
                false

            tvConnectionStatus.text =
                "Firebase: Error"
        }
    }


    // ============================================================
    // SERIAL NO. 12 — FIREBASE ANONYMOUS LOGIN
    // ============================================================

    private fun signInFirebase() {

        if (
            firebaseAuth.currentUser != null
        ) {

            firebaseReady =
                true

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

                    firebaseReady =
                        true

                    firebaseUid =
                        firebaseAuth
                            .currentUser
                            ?.uid


                    showPairingId()


                    tvConnectionStatus.text =
                        "Firebase: Connected"


                    loadDriverId()

                } else {

                    firebaseReady =
                        false

                    driverReady =
                        false

                    tvConnectionStatus.text =
                        "Firebase: Login Failed"

                    btnDuty.isEnabled =
                        false
                }
            }
    }


    // ============================================================
    // SERIAL NO. 13 — SHOW PAIRING ID
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
    // SERIAL NO. 14 — CREATE PAIRING QR BUTTON
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
            "📷 Scanner / Pairing QR"

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
    // SERIAL NO. 15 — SHOW PAIRING QR
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
    // SERIAL NO. 16 — QR BITMAP GENERATOR
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
    // SERIAL NO. 17 — LOAD DRIVER ID
    // ============================================================

    private fun loadDriverId() {

        val user =
            firebaseAuth.currentUser


        if (user == null) {

            driverReady =
                false

            btnDuty.isEnabled =
                false

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


                    loadDriverProfile()


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

            "Firebase Error:\n${e.message}"

    driverId = null

    driverReady = false

    btnDuty.isEnabled = false

    tvTrackingStatus.text =
        "Pairing ID:\n$uid\n\n" +
        "Driver verification failed\n\n" +
        "Firebase Error:\n${e.message}"

    ensurePairingQrButton()
            }
    }


    // ============================================================
    // SERIAL NO. 17A — DRIVER PROFILE DATA
    // ============================================================

    private fun loadDriverProfile() {

        val currentDriverId =
            driverId


        if (currentDriverId.isNullOrBlank()) {

            driverPhotoUrl = null
            carPhotoUrl = null
            carNumber = null

            updateProfileUi()

            return
        }


        FirebaseFirestore
            .getInstance()
            .collection("drivers")
            .document(currentDriverId)
            .get()
            .addOnSuccessListener { document ->

                if (document.exists()) {

                    driverPhotoUrl =
                        firstNonBlank(
                            document.getString(
                                "profilePhoto"
                            ),

                            document.getString(
                                "driverPhotoUrl"
                            ),

                            document.getString(
                                "photoUrl"
                            ),

                            document.getString(
                                "profilePhotoUrl"
                            )
                        )


                    carPhotoUrl =
                        firstNonBlank(
                            document.getString(
                                "carPhoto"
                            ),

                            document.getString(
                                "carPhotoUrl"
                            ),

                            document.getString(
                                "vehiclePhotoUrl"
                            ),

                            document.getString(
                                "vehiclePhoto"
                            )
                        )


                    carNumber =
                        firstNonBlank(
                            document.getString(
                                "vehicle"
                            ),

                            document.getString(
                                "carNumber"
                            ),

                            document.getString(
                                "vehicleNumber"
                            ),

                            document.getString(
                                "registrationNumber"
                            )
                        )

                } else {

                    driverPhotoUrl = null
                    carPhotoUrl = null
                    carNumber = null
                }


                updateProfileUi()
            }
            .addOnFailureListener {

                driverPhotoUrl = null
                carPhotoUrl = null
                carNumber = null

                updateProfileUi()
            }
    }


    private fun firstNonBlank(
        vararg values: String?
    ): String? {

        for (value in values) {

            if (!value.isNullOrBlank()) {

                return value.trim()
            }
        }

        return null
    }


    // ============================================================
    // SERIAL NO. 18 — START DUTY
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

            setDutyUi(false)


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


                setDutyUi(false)

                return

            } else {

                setDutyUi(false)


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


        setDutyUi(true)


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
    // SERIAL NO. 19 — FOREGROUND LOCATION SERVICE
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
    // SERIAL NO. 20 — LOCATION UPDATES
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
    // SERIAL NO. 21 — SEND LOCATION TO FIREBASE
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
    // SERIAL NO. 22 — STOP DUTY
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


        setDutyUi(false)


        tvGpsStatus.text =
            "GPS: Not Started"


        tvTrackingStatus.text =
            "Driver ID: $driverId\n" +
            "Tracking: Stopped"


        ensurePairingQrButton()
    }


    // ============================================================
    // SERIAL NO. 23 — PERMISSION RESULT
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
    // SERIAL NO. 24 — RESUME
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
    // SERIAL NO. 25 — DESTROY
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
