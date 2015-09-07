package co.slzr.cephlo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.List;

/**
 * Created by as on 29/08/15.
 */
public class WifiInfoFragment extends Fragment {
    WifiManager wifiManager;
    Button scanButton;
    ListView wifiView;
    BroadcastReceiver wifiUpdateReceiver;

    ArrayAdapter<String> wifiListAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(
                R.layout.fragment_wifi_info, container, false);

        scanButton = (Button) rootView.findViewById(R.id.wifiScan);
        wifiView =  (ListView) rootView.findViewById(R.id.wifiList);

        wifiListAdapter = new ArrayAdapter<String>(getActivity().getBaseContext(), android.R.layout.simple_list_item_1);
        wifiView.setAdapter(wifiListAdapter);

        Activity a = getActivity();
        wifiManager = (WifiManager) a.getSystemService(a.WIFI_SERVICE);

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!wifiManager.isWifiEnabled()) {
                    Toast.makeText(getActivity().getBaseContext(), "WiFi is disabled", Toast.LENGTH_SHORT).show();
                }

                wifiManager.startScan();
            }
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        wifiUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                List<ScanResult> scanResults = wifiManager.getScanResults();

                wifiListAdapter.clear();

                for (int i = 0; i < scanResults.size(); i++) {
                    ScanResult scanResult = scanResults.get(i);
                    wifiListAdapter.add(scanResult.SSID + ", " + scanResult.frequency + "(channel "
                            + getChannel(scanResult.frequency) +  ", " + scanResult.BSSID);
                }
                wifiListAdapter.notifyDataSetChanged();
            }
        };

        getActivity().registerReceiver(wifiUpdateReceiver,
            new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    public void onStop() {
        super.onStop();

        getActivity().unregisterReceiver(wifiUpdateReceiver);
    }

    static int getChannel(int frequency) {
        return (frequency - 2415) / 5 + 1;
    }
}