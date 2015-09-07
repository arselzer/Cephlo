package co.slzr.cephlo;

import android.app.ActionBar;
import android.app.ActivityManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

import co.slzr.cephlo.location.CephloLocationService;

/**
 * Created by as on 27/08/15.
 */
public class MainActivity extends FragmentActivity implements ActionBar.TabListener {
    MainActivityPagerAdapter mainActivityPagerAdapter;
    ViewPager viewPager;
    String[] tabs = {"Cells", "WiFi", "location"};

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mainActivityPagerAdapter = new MainActivityPagerAdapter(getSupportFragmentManager());
        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(mainActivityPagerAdapter);

        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        for (int i = 0; i < tabs.length; i++)  {
            actionBar.addTab(actionBar.newTab().setText(tabs[i])
                    .setTabListener(this));
        }

        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                getActionBar().setSelectedNavigationItem(position);
            }
        });
    }

    public class MainActivityPagerAdapter extends FragmentPagerAdapter {
        public MainActivityPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            Bundle args = new Bundle();
            // no arguments

            Fragment wifiInfoFragment = new WifiInfoFragment();
            Fragment cellInfoFragment = new CellInfoFragment();
            Fragment locationFragment = new LocationFragment();
            wifiInfoFragment.setArguments(args);
            cellInfoFragment.setArguments(args);
            locationFragment.setArguments(args);

            switch (i) {
                case 0:
                    return cellInfoFragment;
                case 1:
                    return wifiInfoFragment;
                case 2:
                    return locationFragment;
            }
                return null;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "OBJECT " + (position + 1);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!locationServiceIsRunning()) {
            startService(new Intent(this, CephloLocationService.class));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();


    }

    boolean locationServiceIsRunning() {
        // http://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android

        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (CephloLocationService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        viewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {

    }
}
