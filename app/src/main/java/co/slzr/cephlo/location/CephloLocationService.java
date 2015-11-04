package co.slzr.cephlo.location;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by as on 01/09/15.
 */
public class CephloLocationService extends Service {
    static boolean DEBUG = false;

    public static String LOCATION_DATA_INTENT = "CEPHLO_LOCATION_DATA_INTENT";

    static final int CAPTURE_PERIODICALLY = 1;
    static final int CAPTURE_WHEN_SIGNAL_CHANGED = 2; // more effective but uses more battery
    static String SERVER = "ssh.alexselzer.com:7898";

    int dataUploadInterval = 2; // should be 2 minutes when not testing
    int cellCaptureMethod = CAPTURE_WHEN_SIGNAL_CHANGED;
    int cellCapturePeriod = 4; // seconds
    int wifiCaptureMethod = CAPTURE_PERIODICALLY;
    int wifiScanInterval = 12; // should usually be > 60 sec, but less when collecting data
    long minGpsUpdatePeriod = 400; // ms
    int cellListUpdateInterval = 60;

    boolean dataCollectionEnabled = true;

    // current GPS state
    double latitude;
    double longitude;
    double altitude;
    float gpsSpeed = 0.0f; // or bad stuff happens
    float gpsAccuracy; // observations however bad are uploaded. server can decide.
    Date lastTimeGpsUpdated = null;

    Date lastTimeCellsUploaded = new Date();

    ArrayList<WifiApObservation> wifiApObservations = new ArrayList<WifiApObservation>();
    ArrayList<CellTowerObservation> cellTowerObservations = new ArrayList<CellTowerObservation>();

    // cell towers as downloaded from the Server
    ArrayList<CellTower> cellTowers = new ArrayList<CellTower>();
    ArrayList<WifiAp> wifiAps = new ArrayList<WifiAp>();

    Thread wifiScanThread;
    Thread dataUploadThread;
    Thread cellListUpdaterThread;
    Thread locationDataDistributorThread;

    PhoneStateListener phoneStateListener;
    BroadcastReceiver wifiListener;
    LocationListener locationListener;

    TelephonyManager telephonyManager;
    WifiManager wifiManager;
    LocationManager locationManager;
    PowerManager powerManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        System.out.println("CephloLocationService started");

        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        // GPS data is always as new as possible
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                altitude = location.getAltitude();
                gpsAccuracy = location.getAccuracy();
                gpsSpeed = location.getSpeed();
                lastTimeGpsUpdated = new Date();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onProviderDisabled(String provider) {}
        };

        Criteria gpsCriteria = new Criteria();
        gpsCriteria.setAccuracy(Criteria.ACCURACY_FINE);
        gpsCriteria.setAltitudeRequired(true); // might be useful

        locationManager.requestLocationUpdates(minGpsUpdatePeriod, 0.5f /* meters */, gpsCriteria, locationListener, null);

        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        // collect WiFi and cell observations periodically
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength ss) {
                collectCellTowerObservations();
            }
        };
        wifiListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                collectWifiApObservations();
            }
        };

        if (cellCaptureMethod == CAPTURE_WHEN_SIGNAL_CHANGED) {
            startListeningCells();
        }

        startListeningWifi();

        if (wifiCaptureMethod == CAPTURE_PERIODICALLY) {
            wifiScanThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(wifiScanInterval * 1000);
                        } catch (InterruptedException e) {
                            // happens when the service is killed
                        }

                        wifiManager.startScan();
                    }
                }
            });
            wifiScanThread.start();
        }

        dataUploadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(dataUploadInterval * 1000);
                    } catch (InterruptedException e) {}

                    sendCellTowerObservations();
                    sendWifiApObservations();
                }
            }
        });
        dataUploadThread.start();

        // Keeps the local cell list synchronised with the database
        cellListUpdaterThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    // getServerCellCount is 0 when there is no internet connection
                    if (getServerCellCount() > (cellTowers.size())) {
                        updateCellList();
                    }

                    try {
                        Thread.sleep(cellListUpdateInterval * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        cellListUpdaterThread.start();

        // Is supposed to send the location data to all subscribing clients
        locationDataDistributorThread = new Thread(new Runnable() {
            @Override
            public void run() {
            while (true) {
                CephloLocation location = getLocation();

                // only broadcast the location when there are actually cells...
                if (location != null) {

                    Intent locationDataIntent = new Intent(LOCATION_DATA_INTENT);

                    locationDataIntent.putExtra("latitude", location.latitide);
                    locationDataIntent.putExtra("longitude", location.longitude);
                    locationDataIntent.putExtra("usesWifi", location.usesWifi);
                    locationDataIntent.putExtra("usesCells", location.usesCells);

                    sendBroadcast(locationDataIntent);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            }
        });
        locationDataDistributorThread.start();

        return START_STICKY;
    }

    void startListeningCells() {
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    void stopListeningCells() {
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    void startListeningWifi() {
        registerReceiver(wifiListener, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    public void stopListeningWifi() {
        unregisterReceiver(wifiListener);
    }

    void collectCellTowerObservations() {
        // scale max time difference with slowness (the slower, the longer data is accurate)
        // it turned out this is actually a good way of rate limiting since android GPS update
        // intervals are not constant
        float maxDiff = 16000 / (gpsSpeed + 1); // no div by zero
        if (maxDiff <= 2000) maxDiff = 2000; // GPS update rate is usually 1Hz
        // only add cell tower observation to list if GPS data is recent enough
        if (lastTimeGpsUpdated != null
                && (new Date().getTime() - lastTimeGpsUpdated.getTime()) < maxDiff) {

            ArrayList<CellTowerObservation> newCellTowerObservations =
                    new ArrayList<CellTowerObservation>();

            List<CellInfo> cellInfo = telephonyManager.getAllCellInfo();
            for (int i = 0; i < cellInfo.size(); i++) {
                CellInfo cell = cellInfo.get(i);

                CellTowerObservation observation = new CellTowerObservation();

                observation.lat = latitude;
                observation.lon = longitude;
                observation.alt = altitude;
                observation.accuracy = gpsAccuracy;
                observation.timestamp = new Date();

                if (cell.getClass() == CellInfoWcdma.class) {
                    CellInfoWcdma c = (CellInfoWcdma) cell;
                    CellIdentityWcdma cellIdentity = c.getCellIdentity();

                    observation.type = "wcdma"; // umts or whatever hspa hspa+ hsdpa hsupa blablapa
                    observation.cid = cellIdentity.getCid();
                    observation.lac = cellIdentity.getLac();
                    observation.mcc = cellIdentity.getMcc();
                    observation.mnc = cellIdentity.getMnc();
                    observation.rssi = c.getCellSignalStrength().getDbm();
                } else if (cell.getClass() == CellInfoGsm.class) {
                    CellInfoGsm c = (CellInfoGsm) cell;
                    CellIdentityGsm cellIdentity = c.getCellIdentity();

                    observation.type = "gsm";
                    observation.cid = cellIdentity.getCid();
                    observation.lac = cellIdentity.getLac();
                    observation.mcc = cellIdentity.getMcc();
                    observation.mnc = cellIdentity.getMnc();
                    observation.rssi = c.getCellSignalStrength().getDbm();
                } else if (cell.getClass() == CellInfoLte.class) {
                    CellInfoLte c = (CellInfoLte) cell;
                    CellIdentityLte cellIdentity = c.getCellIdentity();

                    observation.type = "lte";
                    observation.cid = cellIdentity.getCi();
                    observation.lac = 0;
                    observation.mcc = cellIdentity.getMcc();
                    observation.mnc = cellIdentity.getMnc();
                    observation.rssi = c.getCellSignalStrength().getDbm();
                }

                // Filter out the weaker cells from the same BTS
                // this kind of assumes all cells are evenly spaced and
                // have the same gain, which they tend to do.
                boolean exists = false;
                for (int j = 0; j < newCellTowerObservations.size(); j++) {
                    if (newCellTowerObservations.get(j).cid == observation.cid) {
                        exists = true;
                        if (newCellTowerObservations.get(j).rssi <= observation.rssi) {
                            // replace the rssi value of the old cell observation
                            newCellTowerObservations.get(j).rssi = observation.rssi;
                        }
                    }
                }

                // only add this cell to the list if there is no other one in the same BTS
                if (!exists) {
                    newCellTowerObservations.add(observation);
                }
            }

            for (int i = 0; i < newCellTowerObservations.size(); i++) {
                CellTowerObservation observation = newCellTowerObservations.get(i);

                if (DEBUG) {
                    Toast.makeText(this, observation.toString(), Toast.LENGTH_SHORT).show();
                }
                System.out.println("observation: " + observation.toString());

                cellTowerObservations.add(newCellTowerObservations.get(i));
            }
        }
        else {
            System.out.println("bad GPS data, did not log cell data.");
        }
    }

    void sendCellTowerObservations() {
        for (int i = 0; i < cellTowerObservations.size(); i++) {
            try {
                CellTowerObservation observation = cellTowerObservations.get(i);

                // contains either WiFi AP or cell tower observations
                JSONObject data = new JSONObject();
                data.put("type", "cell");

                JSONObject cellData = new JSONObject();
                cellData.put("type", observation.type);
                cellData.put("cid", observation.cid);
                cellData.put("mcc", observation.mcc);
                cellData.put("mnc", observation.mnc);
                cellData.put("lac", observation.lac);
                cellData.put("rssi", observation.rssi);
                cellData.put("lat", observation.lat);
                cellData.put("lon", observation.lon);
                cellData.put("alt", observation.alt);
                cellData.put("accuracy", observation.accuracy);
                cellData.put("timestamp", observation.timestamp.getTime());

                data.put("data", cellData);

                HttpClient httpClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost("http://" + SERVER + "/observation");
                try {
                    httpPost.setEntity(new StringEntity(data.toString(), "UTF8"));
                    httpPost.setHeader("Content-Type", "application/json");

                    HttpResponse httpResponse = httpClient.execute(httpPost);
                    if (httpResponse.getStatusLine().getStatusCode() != 200) {
                        System.out.println("Sending failed");
                    }

                    lastTimeCellsUploaded = new Date();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        cellTowerObservations.clear();
    }

    void collectWifiApObservations() {
        List<ScanResult> scanResults = wifiManager.getScanResults();

        for (int i = 0; i < scanResults.size(); i++) {
            ScanResult scanResult = scanResults.get(i);

            WifiApObservation observation = new WifiApObservation();
            observation.bssid = scanResult.BSSID;
            observation.ssid = scanResult.SSID;
            observation.frequency = scanResult.frequency;
            observation.timestamp = new Date();
            observation.rssi = scanResult.level;
            observation.lat = latitude;
            observation.lon = longitude;
            observation.alt = altitude;
            observation.accuracy = gpsAccuracy;

            wifiApObservations.add(observation);
        }
    }

    void sendWifiApObservations() {
        for (int i = 0; i < wifiApObservations.size(); i++) {
            WifiApObservation apObservation = wifiApObservations.get(i);
            JSONObject data = new JSONObject();

            try {
                data.put("type", "wifi");

                JSONObject wifiApData = new JSONObject();
                wifiApData.put("rssi", apObservation.rssi);
                wifiApData.put("timestamp", apObservation.timestamp);
                wifiApData.put("frequency", apObservation.frequency);
                wifiApData.put("ssid", apObservation.ssid);
                wifiApData.put("bssid", apObservation.bssid);
                wifiApData.put("lat", apObservation.lat);
                wifiApData.put("lon", apObservation.lon);
                wifiApData.put("alt", apObservation.alt);
                wifiApData.put("accuracy", apObservation.accuracy);
                data.put("data", wifiApData);

                HttpClient httpClient = new DefaultHttpClient();
                HttpPost httpPost = new HttpPost("http://" + SERVER + "/observation");
                try {
                    httpPost.setEntity(new StringEntity(data.toString(), "UTF8"));
                    httpPost.setHeader("Content-Type", "application/json");

                    HttpResponse httpResponse = httpClient.execute(httpPost);
                    if (httpResponse.getStatusLine().getStatusCode() != 200) {
                        System.out.println("Sending failed");
                    }

                    lastTimeCellsUploaded = new Date();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        wifiApObservations.clear();
    }

    int getServerCellCount() {
        JSONObject result = null;
        try {
            String cellCountRes = apiRequest("/cellcount");
            if (cellCountRes == null)
                return 0;

            result = new JSONObject(apiRequest("/cellcount"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (result != null) {
            try {
                return result.getInt("count");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    void updateCellList() {
        System.out.println("updateCellList");
        JSONArray cells = null;
        try {
            cells = new JSONArray(apiRequest("/cells"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (cells != null) {
            cellTowers.clear();
            for (int i = 0; i < cells.length(); i++) {
                try {
                    JSONObject cell = cells.getJSONObject(i);
                    CellTower cellTower = new CellTower();
                    cellTower.cid = cell.getInt("cid");
                    cellTower.lat = cell.getDouble("lat");
                    cellTower.lon = cell.getDouble("lon");
                    cellTower.rssi = cell.getInt("rssi");

                    cellTowers.add(cellTower);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        else {
            System.out.println("cells are null");
        }
    }

    String apiRequest(String path) {
        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet("http://" + SERVER + path);
        HttpResponse httpResponse;

        try {
            httpResponse = httpClient.execute(httpGet);

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                return null;
            }

            HttpEntity entity = httpResponse.getEntity();

            if (entity == null) {
                return null;
            }

            InputStream inputStream = entity.getContent();

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();

            String line = null;

            try {
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            inputStream.close();

           return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    CephloLocation getLocation() {
        CephloLocation locationEstimate = new CephloLocation();

        List<CellTower> nearbyTowers = getNearbyCellTowers();

        System.out.println("nearby:" + nearbyTowers.toString() + " " + cellTowers.toString());

        double sumX = 0;
        double sumY = 0;
        int cellsMatch = 0; // There might be some cells not in the database (or bogus cells)

        for (int i = 0; i < cellTowers.size(); i++) {
            CellTower tower = cellTowers.get(i);
            int cid = cellTowers.get(i).cid;

            for (int j = 0; j < nearbyTowers.size(); j++) {
                if (nearbyTowers.get(j).cid == cid) {
                    sumX += tower.lat;
                    sumY += tower.lon;
                    cellsMatch++;
                }
            }
        }


        locationEstimate.usesCells = true;
        locationEstimate.usesWifi = false;

        locationEstimate.latitide = sumX / cellsMatch;
        locationEstimate.longitude = sumY / cellsMatch;

        if ((locationEstimate.latitide == 0 && locationEstimate.longitude == 0)
                || Double.isNaN(locationEstimate.latitide)
                || Double.isNaN(locationEstimate.longitude))
            return null;

        return locationEstimate;
    }

    ArrayList<CellTower> getNearbyCellTowers() {
        ArrayList<CellTower> towers = new ArrayList<CellTower>();
        List<CellInfo> cellInfo = telephonyManager.getAllCellInfo();

        for (int i = 0; i < cellInfo.size(); i++) {
            CellInfo cell = cellInfo.get(i);

            CellTower cellTower = new CellTower();

            if (cell.getClass() == CellInfoWcdma.class) {
                CellInfoWcdma c = (CellInfoWcdma) cell;
                CellIdentityWcdma cellIdentity = c.getCellIdentity();

                cellTower.cid = cellIdentity.getCid();
                cellTower.rssi = c.getCellSignalStrength().getDbm();
            } else if (cell.getClass() == CellInfoGsm.class) {
                CellInfoGsm c = (CellInfoGsm) cell;
                CellIdentityGsm cellIdentity = c.getCellIdentity();

                cellTower.cid = cellIdentity.getCid();
                cellTower.rssi = c.getCellSignalStrength().getDbm();
            } else if (cell.getClass() == CellInfoLte.class) {
                CellInfoLte c = (CellInfoLte) cell;
                CellIdentityLte cellIdentity = c.getCellIdentity();

                cellTower.cid = cellIdentity.getCi();
                cellTower.rssi = c.getCellSignalStrength().getDbm();
            }

            towers.add(cellTower);
        }

        return towers;
    };

    @Override
    public void onDestroy() {
        System.out.println("CephloLocationService is destroyed");
        if (wifiScanThread != null)
            wifiScanThread.interrupt();
        dataUploadThread.interrupt();
    }

    @Override
    public IBinder onBind(Intent intent) {

        return null; // = we don't care about binding
    }
}