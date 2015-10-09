package co.slzr.cephlo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.Fragment;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Handler;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.OverlayItem;

import co.slzr.cephlo.location.CephloLocationService;

/**
 * Created by as on 01/09/15.
 */
public class LocationFragment extends Fragment {
    IMapController mapController;
    LocationUpdateReceiver locationUpdateReceiver;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(
                R.layout.fragment_location, container, false);

        return rootView;
    }

    private class LocationUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(CephloLocationService.LOCATION_DATA_INTENT)) {
                final double lat = intent.getDoubleExtra("latitude", 0);
                final double lon = intent.getDoubleExtra("longitude", 0);

                mapController.setZoom(17);
                mapController.animateTo(new GeoPoint(lat, lon));

                        System.out.println("location data: " + intent.getDoubleExtra("latitude", 3.1415) + " "
                                + intent.getDoubleExtra("longitude", 3.1415));
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        MapView map = (MapView) getActivity().findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        mapController = map.getController();
        mapController.setZoom(9);
        GeoPoint startPoint = new GeoPoint(48.3210993, 16.2090994);
        mapController.setCenter(startPoint);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (locationUpdateReceiver != null)
            getActivity().unregisterReceiver(locationUpdateReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (locationUpdateReceiver == null) locationUpdateReceiver = new LocationUpdateReceiver();
        IntentFilter intentFilter = new IntentFilter(CephloLocationService.LOCATION_DATA_INTENT);
        getActivity().registerReceiver(locationUpdateReceiver, intentFilter);
    }
}