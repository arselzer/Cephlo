package co.slzr.cephlo.location;

import java.util.Date;

/**
 * Created by as on 22/04/15.
 */
public class CellTowerObservation {
    // this class is used to represent a cell tower sighting

    public double lat;
    public double lon;
    public double alt;
    public double accuracy;
    public String type; // "wcdma", "gsm", "lte" even though this is never accurate \(oo)/
    public int cid;
    public int mcc;
    public int lac; // except for LTE cells
    public int rssi; // dBm (debcibel milliwatt)
    Date timestamp;

    public CellTowerObservation(int cid, int rssi, double lat, double lon) {
        cid = cid;
        rssi = rssi;
        lat = lat;
        lon = lon;
    }

    public String toString() {
        return "[" + lat + ", " + lon + " / " + alt + "m +/- " + accuracy + "m] " + type + " cid: "
                + cid + " mcc: " + mcc + " lac: " + lac + " rssi: " + rssi + " time: " +
                timestamp.toString();
    }

    public CellTowerObservation() {}
}