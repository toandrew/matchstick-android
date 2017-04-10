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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nz.co.iswe.android.airplay.AirPlayServer;
import tv.matchstick.Flint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.PowerManager;
import android.util.Log;

import com.geniusgithub.mediarender.DeviceInfo;
import com.geniusgithub.mediarender.DeviceUpdateBrocastFactory;
import com.geniusgithub.mediarender.RenderApplication;
import com.geniusgithub.mediarender.center.MediaRenderProxy;
import com.geniusgithub.mediarender.util.DlnaUtils;
import com.infthink.flint.home.apk.ApkInstallActivity;

import fi.iki.elonen.SimpleWebServer;

/**
 * Flint service
 * 
 * This service will start Flint daemon and wait there for commands.
 */
public class FlintServerService extends Service implements Runnable,
        Flint.Callback, DeviceUpdateBrocastFactory.IDevUpdateListener {
    private static final String TAG = "FlintServerService";

    private static final String WAKE_LOCK_TAG = "FlintServer";

    private static final int WAKE_INTERVAL_MS = 1000; // milliseconds

    private static final int DLNA_AIRPLAY_WAKE_INTERVAL_MS = 10000; // milliseconds

    private static final int NOTIFICATION_ID = 0x2d32d;

    protected static Thread sServerThread = null;

    protected static boolean sShouldExit = false;

    protected static WifiLock sWifiLock = null;

    protected static boolean sAcceptWifi = false;

    protected static boolean sFullWake = false;

    NotificationManager mNotificationMgr = null;

    PowerManager.WakeLock mWakeLock;

    protected static String mDeviceFriendName;

    private Flint mFlint;

    public static final String ACTION_STOP_RECEIVER = "fling.action.stop_receiver";

    public static final String FLINT_DEFAULT_MEDIA_APP_URL = "http://openflint.github.io/flint-player/player.html";

    public static final String FLINT_DEFAULT_INSTALL_APK_APP_URL = "http://openflint.github.io/install-app/index.html";

    public static final String FLINT_DEVICE_SETUP_WIFI = "setup_wifi";

    // DLNA related.
    private MediaRenderProxy mRenderProxy;
    private RenderApplication mApplication;
    private DeviceUpdateBrocastFactory mBrocastFactory;

    //protected static Thread sAirplayServerThread = null;

    public static final int DEFAULT_PORT = 9527;

    private SimpleWebServer mNanoHTTPD;

    private String mRootDir = "/";

    private String mIpAddress;

    /**
     * Process "install apk" command from user!
     */
    BroadcastReceiver mFlintInstallAppReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            Log.e(TAG, "onReceive!!!!");
            if (ApkInstallActivity.FLINT_INSTALL_APK_ACTION.equals(intent
                    .getAction())) {
                String path = intent
                        .getStringExtra(ApkInstallActivity.FLINT_INSTALL_APK_ACTION_PATH);
                Log.e(TAG, "onReceive!!!![" + path + "]");
                if (path != null) {
                    File apkFile = new File(path);
                    if (apkFile.exists()) {
                        changeApkMode(apkFile);
                        Log.e(TAG, "onReceive!!!![" + path
                                + "] ready to install!");
                        Intent installIntent = new Intent(Intent.ACTION_VIEW);
                        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        installIntent.setDataAndType(Uri.fromFile(apkFile),
                                "application/vnd.android.package-archive");
                        startActivity(installIntent);
                    }
                }
            }
        }
    };

    BroadcastReceiver mSetupDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // TODO. need do more for DLNA and AirPlay
            if (intent != null && intent.getStringExtra("dev_name") != null) {
                String deviceName = intent.getStringExtra("dev_name");
                if (deviceName == null || deviceName.trim().length() == 0 || (deviceName != null && deviceName.equals(mDeviceFriendName))) {
                    Log.e(TAG, "Ignore set flint device name:" + deviceName);
                    return;
                }
                
                mDeviceFriendName = deviceName;
                
                SharedPreferences settings = getSharedPreferences(
                        Globals.getSettingsName(), Globals.getSettingsMode());
                settings.edit()
                        .putString("DeviceFriendName", mDeviceFriendName)
                        .commit();

                Log.e(TAG, "set flint device name:" + mDeviceFriendName);

                mFlint.setDeviceName(mDeviceFriendName);

                // set device name.
                DlnaUtils.setDevName(FlintServerService.this, mDeviceFriendName
                        + "-DLNA");

                AirPlayServer.getIstance(FlintServerService.this)
                        .setDeviceName(mDeviceFriendName + "-AirPlay");
                
                stopOtherServices();

                refreshUi();
            }
        }
    };

    /**
     * Process "start/stop wifi tethering" commands!
     */
    BroadcastReceiver mFlintWIfiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            Log.e(TAG, "mFlintWIfiReceiver: action:" + action + " ip:"
                    + Globals.getWifiIp());
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int wifiState = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE, 0);
                Log.e(TAG, "wifiState" + wifiState);
                switch (wifiState) {
                case WifiManager.WIFI_STATE_DISABLED:
                    break;
                case WifiManager.WIFI_STATE_DISABLING:
                    break;
                case WifiManager.WIFI_STATE_ENABLED:
                    if (Globals.getWifiIp() != null) {
                    }
                    break;
                }
            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                Parcelable parcelableExtra = intent
                        .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                Log.e(TAG, "WifiManager.NETWORK_STATE_CHANGED_ACTION"
                        + parcelableExtra.toString());
                if (null != parcelableExtra) {
                    NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;
                    if (networkInfo != null) {
                        State state = networkInfo.getState();
                        boolean isConnected = state == State.CONNECTED;// 当然，这边可以更精确的确定状态
                        Log.e(TAG, "isConnected" + isConnected);
                        if (isConnected) {
                        } else {
                        }
                    }
                }
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                Log.e(TAG, "ConnectivityManager.CONNECTIVITY_ACTION");

                ConnectivityManager manager = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = manager
                        .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (networkInfo != null && networkInfo.isConnected()) {
                    Log.e(TAG,
                            "ConnectivityManager.CONNECTIVITY_ACTION:connected["
                                    + networkInfo + "]");
                } else {
                    Log.e(TAG,
                            "ConnectivityManager.CONNECTIVITY_ACTION:not connected["
                                    + networkInfo + "]");
                }
            } else {
                Log.e(TAG, "unknown action[" + action + "]");
            }

            /*
             * Parcelable extra = intent
             * .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO); if
             * (null != extra) { NetworkInfo networkInfo = (NetworkInfo) extra;
             * State state = networkInfo.getState(); boolean isConnected = state
             * == State.CONNECTED; if (isConnected) { } else { } }
             * 
             * if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
             * NetworkInfo networkInfo = intent
             * .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO); if
             * (networkInfo != null && networkInfo.isConnected() ||
             * Globals.getWifiIp() != null) { Log.e(TAG,
             * "Ready to start DLNA and Airplay!"); loadSettings();
             * 
             * // set device name. DlnaUtils.setDevName(FlintServerService.this,
             * mDeviceFriendName + "-DLNA");
             * 
             * // restart dlna service restartDlnaService();
             * 
             * AirPlayServer.getIstance(FlintServerService.this)
             * .setDeviceName(mDeviceFriendName + "-AirPlay");
             * 
             * sAirplayServerThread = new Thread(
             * AirPlayServer.getIstance(FlintServerService.this));
             * sAirplayServerThread.start(); } else { Log.e(TAG, "info[" +
             * (networkInfo != null ? networkInfo .isConnected() : "false") +
             * "]"); stopDlanService(); stopAirplayService(); }
             * 
             * } else { Log.e(TAG, "unknown action[" +
             * WifiManager.NETWORK_STATE_CHANGED_ACTION + "]action[" + action +
             * "]ip[" + Globals.getWifiIp().toString() + "]"); }
             */
        }
    };

    public IBinder onBind(Intent intent) {
        // We don't implement this functionality, so ignore it
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Fling server created");

        // Set the application-wide context global, if not already set
        Context myContext = Globals.getContext();
        if (myContext == null) {
            myContext = getApplicationContext();
            if (myContext != null) {
                Globals.setContext(myContext);
            }
        }

        mFlint = new Flint(this);
        mFlint.setCallback(this);

        IntentFilter filter = new IntentFilter(
                ApkInstallActivity.FLINT_INSTALL_APK_ACTION);
        registerReceiver(mFlintInstallAppReceiver, filter);

        IntentFilter setupFilter = new IntentFilter(FLINT_DEVICE_SETUP_WIFI);
        registerReceiver(mSetupDeviceReceiver, setupFilter);

        IntentFilter networkFilter = new IntentFilter(
                WifiManager.WIFI_STATE_CHANGED_ACTION);

        registerReceiver(mFlintWIfiReceiver, networkFilter);

        // init dlna related!
        initData();

        //startWetServer(DEFAULT_PORT);

        new Thread(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                while (!sShouldExit) {
                    boolean gotIpAddress = (Globals.getWifiIp() != null);
                    if (gotIpAddress && !RenderApplication.getInstance().isDlanServiceStarted()) {
                        Log.e(TAG, "ready to start DLNA and Airplay service!");
                        runOtherServices();
                    } else if (!gotIpAddress) {
                        Log.e(TAG, "ready to stop DLNA and Airplay services!");
                        stopOtherServices();
                    }
                    try {
                        Thread.sleep(DLNA_AIRPLAY_WAKE_INTERVAL_MS);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void initData() {
        mApplication = RenderApplication.getInstance();
        mRenderProxy = MediaRenderProxy.getInstance();
        mBrocastFactory = new DeviceUpdateBrocastFactory(this);

        updateDevInfo(mApplication.getDevInfo());
        mBrocastFactory.register(this);

        runOtherServices();
    }

    /**
     * run DLNA or Airplay related service
     */
    private synchronized void runOtherServices() {
        loadSettings();

        // set device name.
        DlnaUtils.setDevName(this, mDeviceFriendName + "-DLNA");

        boolean gotIpAddress = (Globals.getWifiIp() != null);

//        AirPlayServer.getIstance(this).setDeviceName(
//                mDeviceFriendName + "-AirPlay");

        // restart dlna service
        if (gotIpAddress) {
            restartDlnaService();

//            sAirplayServerThread = new Thread(AirPlayServer.getIstance(this));
//            sAirplayServerThread.start();
        } else {
            Log.e(TAG,
                    "airplay and dlna does not started for wifi ip is empty!!");
        }
    }

    /**
     * stop dlan and airplay services
     */
    private synchronized void stopOtherServices() {
        // TODO Auto-generated method stub

        //stopAirplayService();

        stopDlanService();
    }

    private void unInitData() {
        mBrocastFactory.unregister();
    }

    private void updateDevInfo(DeviceInfo object) {
        String status = object.status ? "open" : "close";
        String text = "status: " + status + "\n" + "friend name: "
                + object.dev_name + "\n" + "uuid: " + object.uuid;
        // Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT)
        // .show();
        Log.e(TAG, "updateDevInfo:" + text);
    }

    private void startDlnaService() {
        mRenderProxy.startEngine();

        RenderApplication.getInstance().setDlanServiceStatus(true);
    }

    private void restartDlnaService() {
        mRenderProxy.restartEngine();

        RenderApplication.getInstance().setDlanServiceStatus(true);
    }

    private void stopDlanService() {
        mRenderProxy.stopEngine();

        RenderApplication.getInstance().setDlanServiceStatus(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, START_STICKY, startId);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);

        sShouldExit = false;
        int attempts = 10;

        // The previous server thread may still be cleaning up, wait for it
        // to finish.
        while (sServerThread != null) {
            Log.w(TAG, "Won't start, server thread exists");
            if (attempts > 0) {
                attempts--;
                Globals.sleepIgnoreInterupt(1000);
            } else {
                Log.e(TAG, "Server thread already exists");
                return;
            }
        }

        Log.d(TAG, "Creating server thread");

        sServerThread = new Thread(this);
        sServerThread.start();
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "onDestroy() Stopping server");

        //stopWebServer();

        sShouldExit = true;

        if (sServerThread == null) {
            Log.w(TAG, "Stopping with null serverThread");
            return;
        } else {

            sServerThread.interrupt();

            try {
                sServerThread.join(10000); // wait 10 sec for server thread to
                                           // finish
            } catch (InterruptedException e) {
            }

            if (sServerThread.isAlive()) {
                Log.w(TAG, "Server thread failed to exit! force to NULL");
                // it may still exit eventually if we just leave the
                // shouldExit flag set
                sServerThread = null;
            } else {
                Log.w(TAG, "serverThread join()ed ok");
                sServerThread = null;
            }
        }

        cleanAll();

        refreshUi();

        super.onDestroy();
        Log.w(TAG, "FlintServerService.onDestroy() finished");

        RenderApplication.getInstance().setDlanServiceStatus(false);

        Intent localIntent = new Intent();
        localIntent.setClass(this, FlintServerService.class); // 销毁时重新启动Service
        this.startService(localIntent);
    }

    /**
     * Ready to run Flint daemon.
     */
    @Override
    public void run() {
        // The UI will want to check the server status to update its
        // start/stop server button
        Log.w(TAG, "Server thread running");

        // set our members according to user preferences
        loadSettings();

        takeWakeLock();

        setupNotification();

        // set current device name.
        mFlint.setDeviceName(mDeviceFriendName);

        // disable flingd log to fix flingd crash issue.FLD-36
        mFlint.enableLog(false);

        while (!sShouldExit) {
            try {
                if (isDameoneRunning()) {
                    killFlingdDaemon();
                }

                //runFlingDaemon();

                // Log.e(TAG, "Error: Flingd Daemon stopped?!! restart it!["
                //         + sShouldExit + "]");

                Thread.sleep(WAKE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Log.w(TAG, "Thread interrupted");
            }
        }

        sShouldExit = false; // we handled the exit flag, so reset it to
                             // acknowledge

        cleanAll();

        Log.w(TAG, "Exiting cleanly, returning from run()");
    }

    private void cleanAll() {
        try {
            terminateAllSessions();

            clearNotification();

            releaseWakeLock();

            // clean dlna related
            unInitData();

            //stopAirplayService();

            stopDlanService();

            try {
                if (mFlintInstallAppReceiver != null) {
                    unregisterReceiver(mFlintInstallAppReceiver);
                    mFlintInstallAppReceiver = null;
                }
            } catch (Exception e) {
            }

            try {
                if (mSetupDeviceReceiver != null) {
                    unregisterReceiver(mSetupDeviceReceiver);
                    mSetupDeviceReceiver = null;
                }
            } catch (Exception e) {
            }

            try {
                if (mFlintWIfiReceiver != null) {
                    unregisterReceiver(mFlintWIfiReceiver);
                    mFlintWIfiReceiver = null;
                }
            } catch (Exception e) {
            }

        } catch (Exception e) {
        }
    }

    //
    /**
     * Check whether the server is running
     * 
     * @return true if and only if a server Thread is running
     */
    public static boolean isRunning() {
        if (sServerThread == null) {
            Log.e(TAG, "Server is not running (null serverThread)");
            return false;
        }

        if (!sServerThread.isAlive()) {
            Log.e(TAG, "serverThread non-null but !isAlive()");
        } else {
            Log.e(TAG, "Server is alive!");
        }

        return true;
    }

    /**
     * Load device name
     * 
     * @return
     */
    private void loadSettings() {
        SharedPreferences settings = getSharedPreferences(
                Globals.getSettingsName(), Globals.getSettingsMode());
        String dName = settings.getString("DeviceFriendName",
                "MatchStick-Android");
        if (mDeviceFriendName != null && !mDeviceFriendName.equals(dName)) {
            Log.e(TAG, "Current using device name " + mDeviceFriendName);
            settings.edit().putString("DeviceFriendName", mDeviceFriendName)
                    .commit();
            return;
        }

        mDeviceFriendName = dName;

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
     * Setup status bar's notification.
     */
    private void setupNotification() {
        String ns = Context.NOTIFICATION_SERVICE;
        mNotificationMgr = (NotificationManager) getSystemService(ns);

        String contentText = getString(R.string.instruction)
                + mDeviceFriendName;

        int network = Globals.checkNet();
        if (network == ConnectivityManager.TYPE_ETHERNET) {

        } else {
            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            String ssid = "";
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                ssid = wifiInfo.getSSID();
            }
            if (ssid != null) {
                contentText += "(SSID:" + ssid + ")";
            }
        }

        // Instantiate a Notification
        int icon = R.drawable.notification;
        CharSequence tickerText = getString(R.string.notif_server_starting);
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);

        // Define Notification's message and Intent
        CharSequence contentTitle = getString(R.string.notif_title);

        Intent notificationIntent = new Intent(this, FlintActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        notification.setLatestEventInfo(getApplicationContext(), contentTitle,
                contentText, contentIntent);
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        // notification.flags |= Notification.FLAG_NO_CLEAR; 
        // notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;  

        startForeground(NOTIFICATION_ID, notification);

        refreshUi();

        Log.w(TAG, "Notication setup done");
    }

    /**
     * Clear notifications.
     */
    private void clearNotification() {
        if (mNotificationMgr == null) {
            // Get NotificationManager reference
            String ns = Context.NOTIFICATION_SERVICE;
            mNotificationMgr = (NotificationManager) getSystemService(ns);
        }
        mNotificationMgr.cancelAll();
        Log.d(TAG, "Cleared notification");

        stopForeground(true);
    }

    /**
     * Run Fling daemon
     * 
     * Need make sure Fling daemon is not running.
     */
    private void runFlingDaemon() {
        boolean isRunning = isDameoneRunning();

        Log.e(TAG, "Daemon is Running?[" + isRunning + "]");
        if (isRunning) {
            Log.e(TAG, "daemon is running!ignore it!");
            return;
        }

        // run flingd?
        Log.e(TAG, "Ready to run Fling daemone!");
        mFlint.start();
        Log.e(TAG, "Quit from Fling daemone???");
    }

    /**
     * Kill all
     */
    private void terminateAllSessions() {
        if (isDameoneRunning()) {
            killFlingdDaemon();
        }
    }

    /**
     * Get wake lock.
     */
    private void takeWakeLock() {
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

            // Many (all?) devices seem to not properly honor a
            // PARTIAL_WAKE_LOCK,
            // which should prevent CPU throttling. This has been
            // well-complained-about on android-developers.
            // For these devices, we have a config option to force the phone
            // into a
            // full wake lock.
            if (sFullWake) {
                mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK,
                        WAKE_LOCK_TAG);
            } else {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        WAKE_LOCK_TAG);
            }
            mWakeLock.setReferenceCounted(false);
        }
        Log.w(TAG, "Acquiring wake lock");
        mWakeLock.acquire();
    }

    /**
     * Release wake lock.
     */
    private void releaseWakeLock() {
        Log.w(TAG, "Releasing wake lock");
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
            Log.w(TAG, "Finished releasing wake lock");
        } else {
            Log.w(TAG, "Couldn't release null wake lock");
        }
    }

    /**
     * Kill Fling daemon.
     */
    private void killFlingdDaemon() {
        Log.e(TAG, "Stop Daemon!");

        mFlint.stop();
    }

    /**
     * Refresh current UI from service.
     */
    private void refreshUi() {
        Intent intent = new Intent(FlintActivity.UPDATE_UI_ACTION);
        sendBroadcast(intent);
    }

    @Override
    public void onWebAppStart(String appInfo) {
        // TODO Auto-generated method stub

        doLaunchWebApp(appInfo);
    }

    @Override
    public void onNativeAppStart(String appInfo) {
        // TODO Auto-generated method stub

        doLaunchNativeApp(appInfo);
    }

    @Override
    public void onWebAppStop(String appInfo) {
        // TODO Auto-generated method stub

        // stop web app
        doStop();
    }

    @Override
    public void onNativeAppStop(String appInfo) {
        // TODO Auto-generated method stub

        // stop native app
        doStop();
    }

    /**
     * Check whether current Flingd is running
     * 
     * @return true if Flingd is running.
     */
    public boolean isDameoneRunning() {
        return mFlint.isRunning();
    }

    /**
     * Whether daemon is started!
     * 
     * @return
     */
    public boolean isConnected() {
        return mFlint.isRunning();
    }

    /**
     * start web app
     * 
     * @param url
     */
    void doLaunchWebApp(String url) {
        Log.e(TAG, "ur:" + url);
        Intent intent = new Intent();
        intent.setAction("android.intent.action.VIEW");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Uri CONTENT_URI_BROWSERS = Uri.parse(url);
        intent.setData(CONTENT_URI_BROWSERS);

        if (FLINT_DEFAULT_MEDIA_APP_URL.equals(url)) {
            intent.setClassName("com.infthink.flint.home",
                    "com.infthink.flint.home.video.FlintVideoActivity");
        } else if (FLINT_DEFAULT_INSTALL_APK_APP_URL.equals(url)) {
            intent.setClassName("com.infthink.flint.home",
                    "com.infthink.flint.home.apk.ApkInstallActivity");
        } else {
            intent.setClassName("com.infthink.flint.home",
                    "com.infthink.flint.home.FlintContainerActivity");
        }

        if (Globals.getContext() != null) {
            Globals.getContext().startActivity(intent);
        } else {
            Log.e(TAG, "doStart: Globals.getContext() is null???!!!!ignore it!");
        }
    }

    /**
     * start native app
     * 
     * @param url
     */
    void doLaunchNativeApp(String url) {
        // TODO
    }

    /**
     * Stop receiver application.
     */
    void doStop() {
        Log.e(TAG, "Stop receiver!");
        Intent intent = new Intent();
        intent.setAction(ACTION_STOP_RECEIVER);
        if (Globals.getContext() != null) {
            Globals.getContext().sendBroadcast(intent);
        } else {
            Log.e(TAG, "doStop: Globals.getContext() is null???!!!!ignore it!");
        }
    }

    @Override
    public void onUpdate() {
        // TODO Auto-generated method stub

        updateDevInfo(mApplication.getDevInfo());
    }

//    private void stopAirplayService() {
//        try {
//            if (sAirplayServerThread == null) {
//                Log.w(TAG, "Stopping with null airplay serverThread");
//                return;
//            } else {
//                AirPlayServer.getIstance(this).stopService();
//
//                sAirplayServerThread.interrupt();
//
//                try {
//                    sAirplayServerThread.join(10000); // wait 10 sec for server
//                                                      // thread
//                    // to
//                    // finish
//                } catch (InterruptedException e) {
//                }
//
//                if (sAirplayServerThread.isAlive()) {
//                    Log.w(TAG, "Server thread failed to exit! force to NULL");
//                    // it may still exit eventually if we just leave the
//                    // shouldExit flag set
//                    sAirplayServerThread = null;
//                } else {
//                    Log.w(TAG, "serverThread join()ed ok");
//                    sAirplayServerThread = null;
//                }
//            }
//        } catch (Exception e) {
//
//        }
//    }

    /**
     * change apk mode
     * 
     * @param apkFile
     */
    private void changeApkMode(File apkFile) {
        String path = apkFile.getAbsolutePath();
        String permission = "666";

        try {
            String command = "chmod " + permission + " " + path;
            Runtime runtime = Runtime.getRuntime();
            runtime.exec(command);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startWetServer(int port) {
        try {
            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();

            mIpAddress = intToIp(wifiInfo.getIpAddress());

            if (wifiInfo.getSupplicantState() != SupplicantState.COMPLETED) {
                new AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage(
                                "Please connect to a WIFI-network for starting the webserver.")
                        .setPositiveButton("OK", null).show();
                throw new Exception("Please connect to a WIFI-network.");
            }

            Log.e(TAG, "Starting server " + mIpAddress + ":" + port + ".");

            List<File> rootDirs = new ArrayList<File>();
            boolean quiet = false;
            Map<String, String> options = new HashMap<String, String>();
            rootDirs.add(new File(mRootDir).getAbsoluteFile());

            // mNanoHTTPD
            try {
                mNanoHTTPD = new SimpleWebServer(mIpAddress, port, rootDirs,
                        quiet);
                mNanoHTTPD.start();
            } catch (IOException ioe) {
                Log.e(TAG, "Couldn't start server:\n" + ioe);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void stopWebServer() {
        if (mNanoHTTPD != null) {
            mNanoHTTPD.stop();
            Log.e(TAG, "Server was killed.");
        } else {
            Log.e(TAG, "Cannot kill server!? Please restart your phone.");
        }
    }

    private static String intToIp(int i) {
        return ((i) & 0xFF) + "." + ((i >> 8) & 0xFF) + "."
                + ((i >> 16) & 0xFF) + "." + (i >> 24 & 0xFF);
    }
}
