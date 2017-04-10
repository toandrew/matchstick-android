/**
 * Copyright (C) 2013-2015, Infthink (Beijing) Technology Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.infthink.flint.home;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.geniusgithub.mediarender.BaseActivity;
import com.geniusgithub.mediarender.util.DlnaUtils;
import com.umeng.analytics.MobclickAgent;

/**
 * Used to display UI related things.
 */
public class FlintActivity extends BaseActivity {
    private static final String TAG = "FlintActivity";

    public static final String UPDATE_UI_ACTION = "FLING_UPDATE_UI_ACTION";

    private View mFailedPageView;
    private View mReadyPageView;
    private TextView mConnectErrorDetailInfoTextView;

    private TextView mDeviceNameText;
    private TextView mReadyWifiNameTextView;
    private TextView mFlingInfoTextView;
    private TextView mTimeTextView;

    private ImageView mFlingStateImageView;
    private ImageView mReadyWifiSignalImageView;
    private ImageView mInternetImageView;

    private static final int MSG_ID_UPDATE = 0;
    private static final int MSG_ID_START_STOP_SERVICE = 1;

    static final private int MENU_START_FLING_SERVER = Menu.FIRST;
    static final private int MENU_STOP_FLING_SERVER = Menu.FIRST + 1;

    private int mDisplayWidth = 0;
    private int mDisplayHeight = 0;

    private String mDeviceFriendName;

    private ImageView mCorsskrImageView;

    private ImageView mLogoImageView;
    private TextView mKeyCodeTextView;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_ID_UPDATE:
                removeMessages(MSG_ID_UPDATE);
                updateUi();
                break;
            case MSG_ID_START_STOP_SERVICE:
                Context context = getApplicationContext();
                Intent intent = new Intent(context, FlintServerService.class);

                if (!FlintServerService.isRunning()) {
                    context.startService(intent);
                } else {
                    context.stopService(intent);
                }

                mHandler.sendEmptyMessageDelayed(MSG_ID_UPDATE, 1000);
                break;
            }
        }
    };

    BroadcastReceiver mUiReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wifiManager.getConnectionInfo();
                SupplicantState state = info.getSupplicantState();

                String wifiInfo = getResources().getString(
                        R.string.wifi_setup_hint)
                        + "(" + state + ")";
                mConnectErrorDetailInfoTextView.setText(wifiInfo);
            } else {
                mHandler.sendEmptyMessage(MSG_ID_UPDATE);
            }
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.e(TAG, "onCreate!");

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (Globals.getContext() == null) {
            Context context = getApplicationContext();
            if (context == null)
                throw new NullPointerException("Null context!?!?!?");
            Globals.setContext(context);
        }

        setContentView(R.layout.server_control_activity);

        Intent intent = getIntent();
        if (intent != null && intent.getStringExtra("msg") != null) {
            mDeviceFriendName = intent.getStringExtra("msg");
        }

        Log.e(TAG, "mDeviceFriendName:" + mDeviceFriendName);

        mFailedPageView = (View) findViewById(R.id.failed_page);
        mReadyPageView = (View) findViewById(R.id.ready_page);
        mConnectErrorDetailInfoTextView = (TextView) findViewById(R.id.connect_error_detail_info);

        mDeviceNameText = (TextView) findViewById(R.id.fling_device_name);
        mReadyWifiNameTextView = (TextView) findViewById(R.id.ready_wifi_name);
        mFlingInfoTextView = (TextView) findViewById(R.id.fling_sw_version);

        mTimeTextView = (TextView) findViewById(R.id.fling_time_widget);

        mFlingStateImageView = (ImageView) findViewById(R.id.fling_state_image);
        mReadyWifiSignalImageView = (ImageView) findViewById(R.id.ready_wifi_signal_icon);
        mInternetImageView = (ImageView) findViewById(R.id.internet_status);

        mCorsskrImageView = (ImageView) findViewById(R.id.crosskr_img);
        mLogoImageView = (ImageView) findViewById(R.id.logo);
        mKeyCodeTextView = (TextView) findViewById(R.id.key_code);

        init();
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        MobclickAgent.onResume(this);

        updateUi();

        IntentFilter filter = new IntentFilter(
                WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(UPDATE_UI_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        filter.addAction(Intent.ACTION_TIME_CHANGED); // monitor time
        filter.addAction(Intent.ACTION_TIME_TICK); // monitor time

        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);

        registerReceiver(mUiReceiver, filter);

        Log.e(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();

        MobclickAgent.onPause(this);

        if (mUiReceiver != null) {
            unregisterReceiver(mUiReceiver);
        }

        Log.e(TAG, "onPause");
    }

    @Override
    protected void onStart() {
        super.onStart();

        updateUi();

        Log.e(TAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.e(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.e(TAG, "onDestroy!");
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();

        if (!FlintServerService.isRunning()) {
            menu.add(0, MENU_START_FLING_SERVER, Menu.NONE,
                    R.string.start_server);
        } else {
            menu.add(0, MENU_STOP_FLING_SERVER, Menu.NONE, R.string.stop_server);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
        case MENU_START_FLING_SERVER:
        case MENU_STOP_FLING_SERVER:
            mHandler.sendEmptyMessage(MSG_ID_START_STOP_SERVICE);
            break;
        }

        return true;
    }

    /**
     * Init something and start service.
     */
    private void init() {
        mFailedPageView.setVisibility(View.GONE);
        mReadyPageView.setVisibility(View.GONE);

        Context context = getApplicationContext();
        Intent intent = new Intent(context, FlintServerService.class);

        if (!FlintServerService.isRunning()) {
            Log.e(TAG, "Ready to start fling service!");
            context.startService(intent);
        }

        mHandler.sendEmptyMessage(MSG_ID_UPDATE);
    }

    /**
     * Load device name
     * 
     * @return
     */
    private void loadSettings() {
        SharedPreferences settings = getSharedPreferences(
                Globals.getSettingsName(), Globals.getSettingsMode());
        mDeviceFriendName = settings.getString("DeviceFriendName",
                "MatchStick-Android");

        if (mDeviceFriendName.equals("MatchStick-Android")) {
            String deviceName = "MatchStick-Android-";
            for (int i = 0; i < 4; i++) {
                deviceName += (int) Math.floor(Math.random() * 10);
            }

            mDeviceFriendName = deviceName;
            settings.edit().putString("DeviceFriendName", deviceName).commit();
        }

        Log.e(TAG, "Current using device name " + mDeviceFriendName);
    }

    /**
     * Update ui
     */
    private void updateUi() {
        loadSettings();

        // update dlna device name.
        DlnaUtils.setDevName(this, mDeviceFriendName + "-DLNA");

        updateTime();

        int network = Globals.checkNet();

        updateDisplayResolution(network, false, 0);

        if (network == ConnectivityManager.TYPE_ETHERNET) {
            updateEthernetUi();
        } else {
            updateWifiUi();
        }
    }

    /**
     * Update ui if current network is Ethernet
     */
    private void updateEthernetUi() {
        String ipAddress = Globals.getLocalIpAddress();
        Log.e(TAG, "Ip Address:" + ipAddress);

        boolean gotIpAddress = (ipAddress != null);

        if (gotIpAddress) {
            mReadyWifiNameTextView.setText(R.string.ethernet_connected_status);
        } else {
            mReadyWifiNameTextView
                    .setText(R.string.ethernet_disconnected_status);
        }

        if (!gotIpAddress) {
            mConnectErrorDetailInfoTextView
                    .setText(R.string.error_detail_no_ip);
        }

        if (mDeviceFriendName != null) {
            mDeviceNameText.setText(mDeviceFriendName);
        } else {
            mDeviceNameText.setText("");
        }

        if (FlintServerService.isRunning()) {
            mFlingStateImageView.setImageResource(R.drawable.cast_on);
        } else {
            mFlingStateImageView.setImageResource(R.drawable.cast_off);
        }

        boolean flingInternetServerOk = true; // default to true;

        mReadyWifiSignalImageView.setVisibility(View.GONE);

        if (!gotIpAddress) {
            mReadyWifiSignalImageView
                    .setImageResource(R.drawable.wifi_signal_0); // should use
                                                                 // different
                                                                 // icon
            mFailedPageView.setVisibility(View.VISIBLE);
            mReadyPageView.setVisibility(View.GONE);
            mInternetImageView.setImageResource(R.drawable.internet_off);
        } else {
            mReadyWifiSignalImageView
                    .setImageResource(R.drawable.wifi_signal_4); // should use
                                                                 // different
                                                                 // icon
            mFailedPageView.setVisibility(View.GONE);
            mReadyPageView.setVisibility(View.VISIBLE);

            if (!flingInternetServerOk) {
                mInternetImageView.setImageResource(R.drawable.internet_off);
            } else {
                mInternetImageView.setImageResource(R.drawable.internet_on);
            }
        }
    }

    /**
     * Update UI if current network is WIFI.
     */
    private void updateWifiUi() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        String ssid = "";
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            ssid = wifiInfo.getSSID();
            if (ssid != null) {
                ssid = ssid.replace("\"", "");
            }
        }

        Log.e(TAG, "Ip Address:" + Globals.getWifiIp());

        boolean wifiEnabled = Globals.isWifiEnabled();
        boolean gotIpAddress = (Globals.getWifiIp() != null);

        if (!wifiEnabled) {
            mCorsskrImageView.setVisibility(View.GONE);
            mReadyWifiNameTextView.setText(getString(R.string.no_wifi));
        } else {
            if (gotIpAddress) {
                mCorsskrImageView.setVisibility(View.VISIBLE);
                mReadyWifiNameTextView.setText(ssid);

                // hide key code
                mLogoImageView.setVisibility(View.VISIBLE);
                mKeyCodeTextView.setVisibility(View.GONE);
            } else {
                mCorsskrImageView.setVisibility(View.GONE);
                mReadyWifiNameTextView.setText("");
            }
        }

        if (!wifiEnabled) {
            mConnectErrorDetailInfoTextView
                    .setText(R.string.error_detail_no_wifi);

            // show key code
            mLogoImageView.setVisibility(View.GONE);
            mKeyCodeTextView.setVisibility(View.VISIBLE);
        } else if (!gotIpAddress) {
            mConnectErrorDetailInfoTextView.setText(R.string.wifi_setup_hint);
        }

        if (!wifiEnabled) {
            if (FlintServerService.isRunning()) {
                Log.e(TAG, "stop Flint service???! ignore!");
                // Context context = getApplicationContext();
                // Intent intent = new Intent(context,
                // FlintServerService.class);
                // context.stopService(intent);
            }
        }

        if (mDeviceFriendName != null) {
            mDeviceNameText.setText(mDeviceFriendName);
        } else {
            mDeviceNameText.setText("");
        }

        if (FlintServerService.isRunning()) {
            mFlingStateImageView.setImageResource(R.drawable.cast_on);
        } else {
            mFlingStateImageView.setImageResource(R.drawable.cast_off);
        }

        boolean flingInternetServerOk = true; // default to true;

        mReadyWifiSignalImageView.setVisibility(View.VISIBLE);

        if (!wifiEnabled || !gotIpAddress) {
            mReadyWifiSignalImageView
                    .setImageResource(R.drawable.wifi_signal_0);
            mFailedPageView.setVisibility(View.VISIBLE);
            mReadyPageView.setVisibility(View.GONE);
            mInternetImageView.setImageResource(R.drawable.internet_off);
        } else {
            mReadyWifiSignalImageView
                    .setImageResource(R.drawable.wifi_signal_4); // WiFi singal
                                                                 // level
                                                                 // should be
                                                                 // calculated.
            mFailedPageView.setVisibility(View.GONE);
            mReadyPageView.setVisibility(View.VISIBLE);

            if (!flingInternetServerOk) {
                mInternetImageView.setImageResource(R.drawable.internet_off);
            } else {
                mInternetImageView.setImageResource(R.drawable.internet_on);
            }
        }
    }

    /**
     * Get Current app's version.
     * 
     * @return
     */
    private String getVersion() {
        PackageManager manager;

        PackageInfo info = null;
        String version = "";

        manager = this.getPackageManager();

        try {
            info = manager.getPackageInfo(this.getPackageName(), 0);
            version = info.versionName;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        return version;
    }

    /**
     * Update current time.
     */
    private void updateTime() {
        long time = System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm");
        Date d = new Date(time);
        String t = format.format(d);
        mTimeTextView.setText(t);
    }

    /**
     * Update current display's resolution.
     * 
     * @param network
     */
    private void updateDisplayResolution(int network, boolean downloading,
            int percent) {
        DisplayMetrics mDisplayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(mDisplayMetrics);
        mDisplayWidth = mDisplayMetrics.widthPixels;
        mDisplayHeight = mDisplayMetrics.heightPixels;

        String info = "";

        String version = getVersion();
        if (version != null && !version.isEmpty()) {
            info = getResources().getString(R.string.fling_info)
                    + getResources().getString(R.string.current_version) + "("
                    + version + "), "
                    + getResources().getString(R.string.resolution) + "("
                    + mDisplayWidth + "X" + mDisplayHeight + ")";
        } else {
            info = getResources().getString(R.string.fling_info)
                    + getResources().getString(R.string.resolution) + "("
                    + mDisplayWidth + "X" + mDisplayHeight + ")";
        }

        String ipAddress = null;
        if (network == ConnectivityManager.TYPE_ETHERNET) {
            ipAddress = Globals.getLocalIpAddress();
        } else {
            if (Globals.getWifiIp() != null) {
                ipAddress = Globals.getWifiIp().getHostAddress().toString();
            }
        }

        // if (ipAddress != null) {
        //     String apkInstallHint = getResources().getString(
        //             R.string.install_apk_hint);
        //     info = info + ", " + apkInstallHint + "(" + "http://" + ipAddress
        //             + ":" + FlintServerService.DEFAULT_PORT + ")";
        // }

        mFlingInfoTextView.setText(info);

        if (downloading) {
            mFlingInfoTextView.setText(info + "("
                    + getString(R.string.sw_updating) + ": " + percent + "%)");
        }
    }
}
