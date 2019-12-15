package io.mapwize.mapwizeuicomponents;

import android.net.wifi.ScanResult;
import static java.lang.Math.abs;
import static java.lang.Math.log10;
import static java.lang.Math.pow;

public class RouterScanned {
    int signalStrength; //level
    double frequency;
    String SSID;
    String BSSID;
    double distance;

    public RouterScanned(ScanResult scanResult){
            signalStrength = scanResult.level;
            frequency = Double.valueOf(scanResult.frequency);
            SSID = scanResult.SSID;
            BSSID = scanResult.BSSID;

            double exp = (27.55 - 20 * log10(frequency) + abs(signalStrength)) / 20.0;
            distance   = pow(10, exp);
    }

    public RouterScanned(double distance, double frequency, String BSSID){
        this.distance = distance;
        this.BSSID = BSSID;
        this.frequency = frequency;
        SSID = "No Name";
    }

}
