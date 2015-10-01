package co.slzr.cephlo.location;

/**
 * Created by as on 22/04/15.
 */
public class CellTower {
    // this class represents cell towers as they are used
    // to trilaterate the position of the device.

    public double lat;
    public double lon;
    public int cid;
    public int rssi;

    public String toString() {
        return lat + " " + lon + " " + cid + " " + rssi;
    }
}