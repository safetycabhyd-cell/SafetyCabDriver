package com.safetycab.driver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST = 1001

    private lateinit var btnDuty: Button
    private lateinit var tvDutyStatus: TextView
    private lateinit var tvGpsStatus: TextView
    private lateinit var tvTrackingStatus: TextView

    private var dutyOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        btnDuty = findViewById(R.id.btnDuty)
        tvDutyStatus = findViewById(R.id.tvDutyStatus)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        tvTrackingStatus = findViewById(R.id.tvTrackingStatus)

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
        tvGpsStatus.text = "GPS: Ready"
        tvTrackingStatus.text = "Tracking: Ready"
    }

    private fun stopDuty() {

        dutyOn = false

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
                tvGpsStatus.text = "GPS: Permission Denied"
                tvTrackingStatus.text = "Tracking: Stopped"
            }
        }
    }
}
