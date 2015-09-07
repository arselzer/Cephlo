package co.slzr.cephlo.location;

/**
 * Created by as on 22/04/15.
 */
public class CellTower {
    // this class is a cell tower with only the minimally
    // needed data included that can be used for trilateration

    public double lat;
    public double lon;
    public int cid;
    public int rssi;

    public String toString() {
        return lat + " " + lon + " " + cid + " " + rssi;
    }
}