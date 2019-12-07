package io.mapwize.mapwizeuicomponents

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.indoorlocation.core.IndoorLocation
import io.mapwize.mapwizeui.MapwizeFragment
import io.mapwize.mapwizeui.MapwizeFragmentUISettings
import io.mapwize.mapwizesdk.api.Floor
import io.mapwize.mapwizesdk.api.MapwizeObject
import io.mapwize.mapwizesdk.map.MapOptions
import io.mapwize.mapwizesdk.map.MapwizeMap
import kotlinx.android.synthetic.main.activity_main.*

/**
 * NOTE:
 *      The app needs location permissions, but won't ask for them.
 *      You need to manually go to Settings->Apps->mapwize-ui-android->Access->Location Access and turn it on...
 *      TODO: Change this app so it asks for location permissions on startup.
 */


class MainActivity : AppCompatActivity(), MapwizeFragment.OnFragmentInteractionListener {

    private var mapwizeFragment: MapwizeFragment? = null    //Holds the fragment that creates the UI for the app (came with the code. Don't touch)
    private var mapwizeMap: MapwizeMap? = null
    private var wifiManager: WifiManager? = null            //Used to scan the network and get the list of nearby wifi devices.
    private var wifiIndoorLocationProvider: WifiIndoorLocationProvider? = null  //Class I made to handle al indoor location things.

    /**
     * Function that is run when the Activity is created (right at the start)
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Uncomment and fill place holder to test MapwizeUI on your venue
        val opts = MapOptions.Builder()
                //.restrictContentToOrganization("YOUR_ORGANIZATION_ID")
                //.restrictContentToVenue("YOUR_VENUE_ID")
                .centerOnVenue("5d795e935297bf003f71fc5a")
                //.centerOnPlace("YOUR_PLACE_ID")
                .build()

        // Uncomment and change value to test different settings configuration
        var uiSettings = MapwizeFragmentUISettings.Builder()
                //.menuButtonHidden(true)
                //.followUserButtonHidden(false)
                //.floorControllerHidden(false)
                //.compassHidden(true)
                .build()


        mapwizeFragment = MapwizeFragment.newInstance(opts, uiSettings)
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        ft.add(fragmentContainer.id, mapwizeFragment!!)
        ft.commit()

    }

    /**
     * Fragment listener. Called when the UI fragment is created.
     */
    override fun onFragmentReady(mapwizeMap: MapwizeMap) {
        this.mapwizeMap = mapwizeMap
        wifiIndoorLocationProvider = WifiIndoorLocationProvider(this)
        this.mapwizeMap!!.setIndoorLocationProvider(wifiIndoorLocationProvider!!)

        /** Manually assign a location as the users location. For debug purposes */
//        val indoorLocation = IndoorLocation(wifiIndoorLocationProvider!!.name, 25.131895991126456, 55.42006855817476, 2.0, System.currentTimeMillis())
//        wifiIndoorLocationProvider!!.setIndoorLocation(indoorLocation)

        /** Initialize everything needed for the Wifi scanner */
        initializeWifiScanner()

        /** Function that runs every second (1000 ms) to *try* and scan the wifi. */
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                scanWifi()
                mainHandler.postDelayed(this, 1000)
            }
        })

    }


    /** Initialize Everything necessary to scan Wifi Routers */
    fun initializeWifiScanner() {

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        /** Broadcast Receiver, that automatically calls the 'refreshApList()' function when ever the wifi is rescanned and new data is avaliable. */
        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    Log.e("Debug","Success!! Obtained new results")
                    refreshApList()
                } else {
                    Log.e("Debug","Scan Failed!! Using previous values...")
//                    refreshApList()
                }
            }
        }

        /** IDK what this is, but its important.. */
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        applicationContext.registerReceiver(wifiScanReceiver, intentFilter)

        scanWifi()
        refreshApList()

    }

    /**Actions to be performed when a new scan is successful.
     *
     * The function is called by the BroadcastReceiver automatically every time a successful scan occurs.
     * Get the list of all routers, and pass it to the UpdateLocation() function
     * This in turn updates the location on the map.
     */
    fun refreshApList() {
        var routerList: ArrayList<RouterScanned> = ArrayList()
        val wifiList = wifiManager!!.scanResults

        Log.e("Debug", wifiList.size.toString())
        for (scanResult in wifiList) {
            routerList.add(RouterScanned(scanResult))
            Log.e("Debug",scanResult.SSID)
        }
        wifiIndoorLocationProvider!!.UpdateLocation(routerList)
    }


    /**Function that is run every second
     * It runs the 'startScan()' function, to start a scan.
     * If it scan is successful (usually takes 4 seconds), the BroadcastReceiver is notified.
     */
    fun scanWifi() {
        @Suppress("DEPRECATION")
        val success = wifiManager!!.startScan()

        if(!success){
            Log.e("Scan Error", "wifiManager.startScan() Failed")
        }

    }


    override fun onMenuButtonClick() {

    }

    override fun onInformationButtonClick(mapwizeObject: MapwizeObject?) {

    }

    override fun onFollowUserButtonClickWithoutLocation() {
        Log.i("Debug", "onFollowUserButtonClickWithoutLocation")

    }

    /**Wether and when the app should show an information button on different locations.
     * I've turned it off just because i didn't have any info stored on the locations and it was annoying to look at.
     */
    override fun shouldDisplayInformationButton(mapwizeObject: MapwizeObject?): Boolean {
        Log.i("Debug", "shouldDisplayInformationButton")
//        when (mapwizeObject) {
//            is Place -> return true
//        }
        return false
    }

    override fun shouldDisplayFloorController(floors: MutableList<Floor>?): Boolean {
        Log.i("Debug", "shouldDisplayFloorController")
        if (floors == null || floors.size <= 1) {
            return false
        }
        return true
    }

}
