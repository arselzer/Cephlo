package co.slzr.cephlo.location;

/**
 * Created by as on 29/08/15.
 */
public class WifiApObservation {
    public String ssid;
    public String bssid;
    public int rssi;
    public int frequency;
    public int lat;
    public int lon;

    public WifiApObservation(String ssid, String bssid, int frequency, int rssi, int lat, int lon) {
        ssid = ssid;
        bssid = bssid;
        frequency = frequency;
        rssi = rssi;
        lat = lat;
        lon = lon;
    }
}
