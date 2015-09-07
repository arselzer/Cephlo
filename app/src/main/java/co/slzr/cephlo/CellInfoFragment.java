package co.slzr.cephlo;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


public class CellInfoFragment extends Fragment {
    Button searchButton = null;
    ListView cellList = null;
    ArrayAdapter<String> cellListAdapter;

    TelephonyManager telephonyManager;
    PhoneStateListener phoneStateListener;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        Activity a = getActivity();
        telephonyManager = (TelephonyManager) a.getSystemService(a.TELEPHONY_SERVICE);

        View rootView = inflater.inflate(
                R.layout.fragment_cell_info, container, false);

        searchButton = (Button) rootView.findViewById(R.id.searchButton);
        cellList = (ListView) rootView.findViewById(R.id.cellsList);

        cellListAdapter = new ArrayAdapter<String>(getActivity().getBaseContext(), android.R.layout.simple_list_item_1);
        cellList.setAdapter(cellListAdapter);

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateCellList();
            }
        });

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCellInfoChanged(List<CellInfo> cells) {
                updateCellList();
            }

            @Override
            public void onSignalStrengthsChanged(SignalStrength ss) {
                updateCellList();
            }
        };

        return rootView;
    }

    @Override
    public void onResume() {
        super.onStart();
        updateCellList();

        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_CELL_INFO);
    }

    @Override
    public void onPause() {
        super.onPause();

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    void updateCellList() {
        cellListAdapter.clear();
        List<CellInfo> cellInfo = telephonyManager.getAllCellInfo();
        for (int i = 0; i < cellInfo.size(); i++) {
            CellInfo cell = cellInfo.get(i);

            String info = null;

            if (cell.getClass() == CellInfoWcdma.class) {
                CellInfoWcdma c = (CellInfoWcdma) cell;
                CellIdentityWcdma cellIdentity = c.getCellIdentity();
                info = "type: WCDMA, "
                        + "CID: " + cellIdentity.getCid() + ", "
                        + "LAC: " + cellIdentity.getLac() + ", "
                        + "MCC: " + cellIdentity.getMcc() + ", "
                        + "MNC: " + cellIdentity.getMnc() + ", "
                        + "PSC: " + cellIdentity.getPsc() + ", "
                        + "RSSI: " + c.getCellSignalStrength().getDbm() + "dBm";
            }
            else if (cell.getClass() == CellInfoGsm.class) {
                CellInfoGsm c = (CellInfoGsm) cell;
                CellIdentityGsm cellIdentity = c.getCellIdentity();
                info = "type: GSM, "
                        + "CID: " + cellIdentity.getCid() + ", "
                        + "LAC: " + cellIdentity.getLac() + ", "
                        + "MCC: " + cellIdentity.getMcc() + ", "
                        + "MNC: " + cellIdentity.getMnc() + ", "
                        + "RSSI: " + c.getCellSignalStrength().getDbm() + "dBm";
            }
            else if (cell.getClass() == CellInfoLte.class) {
                CellInfoLte c = (CellInfoLte) cell;
                CellIdentityLte cellIdentity = c.getCellIdentity();
                info = "type: LTE, "
                        + "CI: " + cellIdentity.getCi() + ", "
                        + "PCI: " + cellIdentity.getPci() + ", "
                        + "TAC: " + cellIdentity.getTac() + ", "
                        + "MCC: " + cellIdentity.getMcc() + ", "
                        + "MNC: " + cellIdentity.getMnc() + ", "
                        + "RSSI: " + c.getCellSignalStrength().getDbm() + "dBm";
            }
            else {
                // too bad
            }

            cellListAdapter.add(info);
        }
        cellListAdapter.notifyDataSetChanged();
    }
}
