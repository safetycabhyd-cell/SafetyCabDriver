<?xml version="1.0" encoding="utf-8"?>

<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    android:background="#F7FBFF">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="18dp">

        <!-- ================================================= -->
        <!-- SAFETY CAB HEADER -->
        <!-- ================================================= -->

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="🛡️ SAFETY CAB"
            android:textSize="28sp"
            android:textStyle="bold"
            android:textColor="#0756A6"
            android:gravity="center"
            android:paddingTop="8dp"
            android:paddingBottom="2dp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Driver Panel"
            android:textSize="17sp"
            android:textColor="#167BC5"
            android:gravity="center"
            android:paddingBottom="18dp" />


        <!-- ================================================= -->
        <!-- DUTY ON / OFF — TOP -->
        <!-- ================================================= -->

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:background="#EAF4FF"
            android:padding="14dp">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Duty Status"
                android:textSize="18sp"
                android:textStyle="bold"
                android:textColor="#0756A6" />

            <Button
                android:id="@+id/btnDuty"
                android:layout_width="150dp"
                android:layout_height="50dp"
                android:text="OFF DUTY"
                android:textSize="15sp"
                android:textStyle="bold"
                android:textColor="#FFFFFF"
                android:backgroundTint="#0756A6"
                android:gravity="center" />

        </LinearLayout>


        <!-- Existing MainActivity status reference -->
        <TextView
            android:id="@+id/tvDutyStatus"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="OFF DUTY"
            android:textSize="1sp"
            android:textColor="#FFFFFF"
            android:visibility="gone" />


        <!-- ================================================= -->
        <!-- DRIVER PHOTO -->
        <!-- ================================================= -->

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:background="#FFFFFF"
            android:padding="16dp"
            android:layout_marginTop="16dp">

            <ImageView
                android:id="@+id/ivDriverPhoto"
                android:layout_width="120dp"
                android:layout_height="120dp"
                android:src="@android:drawable/ic_menu_camera"
                android:scaleType="centerInside"
                android:contentDescription="Driver Photo" />

            <TextView
                android:id="@+id/tvDriverName"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Driver"
                android:textSize="18sp"
                android:textStyle="bold"
                android:textColor="#0756A6"
                android:gravity="center"
                android:paddingTop="8dp" />

        </LinearLayout>


        <!-- ================================================= -->
        <!-- DRIVER ID -->
        <!-- ================================================= -->

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:background="#EAF4FF"
            android:padding="16dp"
            android:layout_marginTop="12dp">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Driver ID"
                android:textSize="14sp"
                android:textColor="#4D6B82" />

            <TextView
                android:id="@+id/tvDriverId"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Not Registered"
                android:textSize="26sp"
                android:textStyle="bold"
                android:textColor="#0756A6"
                android:paddingTop="4dp" />

        </LinearLayout>


        <!-- ================================================= -->
        <!-- HELP & SUPPORT -->
        <!-- ================================================= -->

        <Button
            android:id="@+id/btnHelpSupport"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:text="📞 Help &amp; Support"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="#FFFFFF"
            android:backgroundTint="#0756A6"
            android:layout_marginTop="12dp" />


        <!-- ================================================= -->
        <!-- CAR PHOTO -->
        <!-- ================================================= -->

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:background="#FFFFFF"
            android:padding="16dp"
            android:layout_marginTop="16dp">

            <ImageView
                android:id="@+id/ivCarPhoto"
                android:layout_width="match_parent"
                android:layout_height="160dp"
                android:src="@android:drawable/ic_menu_gallery"
                android:scaleType="centerInside"
                android:contentDescription="Car Photo" />

            <TextView
                android:id="@+id/tvCarNumber"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Car Number: --"
                android:textSize="18sp"
                android:textStyle="bold"
                android:textColor="#0756A6"
                android:gravity="center"
                android:paddingTop="10dp" />

        </LinearLayout>


        <!-- ================================================= -->
        <!-- SETTINGS -->
        <!-- ================================================= -->

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="⚙️ Settings"
            android:textSize="20sp"
            android:textStyle="bold"
            android:textColor="#0756A6"
            android:paddingTop="24dp"
            android:paddingBottom="10dp" />


        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:background="#FFFFFF"
            android:padding="14dp">

            <!-- Firebase Status -->

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Firebase"
                android:textSize="15sp"
                android:textStyle="bold"
                android:textColor="#0756A6" />

            <TextView
                android:id="@+id/tvConnectionStatus"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Firebase: Not Connected"
                android:textSize="15sp"
                android:textColor="#555555"
                android:paddingTop="5dp"
                android:paddingBottom="14dp" />


            <!-- Scanner / Pairing -->

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Driver Scanner"
                android:textSize="15sp"
                android:textStyle="bold"
                android:textColor="#0756A6" />

            <TextView
                android:id="@+id/tvTrackingStatus"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Pairing ID not available"
                android:textSize="14sp"
                android:textColor="#555555"
                android:paddingTop="5dp" />

        </LinearLayout>


        <!-- ================================================= -->
        <!-- HIDDEN EXISTING FIREBASE/GPS REFERENCES -->
        <!-- Firebase logic remains unchanged -->
        <!-- ================================================= -->

        <TextView
            android:id="@+id/tvGpsStatus"
            android:layout_width="1dp"
            android:layout_height="1dp"
            android:text="GPS: Not Started"
            android:visibility="gone" />

        <TextView
            android:id="@+id/tvLastLocation"
            android:layout_width="1dp"
            android:layout_height="1dp"
            android:text="Latitude: --&#10;Longitude: --&#10;Last Update: --"
            android:visibility="gone" />


        <!-- ================================================= -->
        <!-- FOOTER -->
        <!-- ================================================= -->

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Safety Cab"
            android:textSize="14sp"
            android:textColor="#6B8295"
            android:gravity="center"
            android:paddingTop="25dp"
            android:paddingBottom="20dp" />

    </LinearLayout>

</ScrollView>
