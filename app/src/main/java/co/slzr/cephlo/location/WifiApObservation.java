package co.slzr.cephlo.location;

import java.util.Date;

/**
 * Created by as on 29/08/15.
 */
public class WifiApObservation {
    public String ssid;
    public String bssid;
    public int rssi;
    public int frequency;
    Date timestamp;

    public double lat;
    public double lon;
    public double alt;
    public double accuracy;
}

