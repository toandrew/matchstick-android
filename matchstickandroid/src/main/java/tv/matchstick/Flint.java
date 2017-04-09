/*
 * Copyright (C) 2013-2015, The OpenFlint Open Source Project
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

package tv.matchstick;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

/**
 * Used to interact with Flint service.
 */
public class Flint {

    static {
        try {
            System.loadLibrary("flint-android");
        } catch (UnsatisfiedLinkError e) {
            Log.e("Flint", "UnsatisfiedLinkError:" + e.toString());
        }
    }

    /**
     * Callback interface which should be implemented in USER.
     */
    public interface Callback {
        /**
         * Called when Sender wants to start web receiver app.
         * 
         * @param appInfo
         *            the receiver app's address which is start with "http://"
         *            or "https://"
         */
        public void onWebAppStart(String appInfo);

        /**
         * Called when Sender wants to start native receiver app(Android
         * application,etc), which can be an Intent,or other data.
         * 
         * @param appInfo
         *            the android app's data
         */
        public void onNativeAppStart(String appInfo);

        /**
         * Called when Sender wants to stop Web receiver app.
         * 
         * @param appInfo
         */
        public void onWebAppStop(String appInfo);

        /**
         * Called when Sender wants to stop android native receiver app.
         * 
         * @param appInfo
         */
        public void onNativeAppStop(String appInfo);
    }

    private static final String LOG_TAG = "Flint";

    private static final int SOCKET_INIT_TIMES = 5;

    private boolean DEBUG = true;

    private int mCounter = 0;

    private int mFlintdaemonPtr = 0;

    private Context mContext = null;

    private ConnectionReceiver mConnectionReceiver = null;

    private VolumeReceiver mVolumeReceiver = null;

    private Callback mCallback = null;

    private StringBuilder mReaderBuffer = null;

    private TcpClient mTcpClient = null;

    private Looper mLooper = null;

    private Handler mHandler = null;

    public Flint(Context context) {
        mContext = context;
        mReaderBuffer = new StringBuilder();
        mFlintdaemonPtr = native_init();
    }

    /**
     * Called when GC.
     */
    protected void finalize() {
        try {
            stop();
            native_finalizer(mFlintdaemonPtr);
        } finally {
            try {
                super.finalize();
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * Start Flint service.
     */
    public void start() {
        if (isRunning()) {
            return;
        }

        initIp();
        registerReceiver();
        initHandler();
        native_start(mFlintdaemonPtr);
    }

    /**
     * Stop Flint service.
     */
    public void stop() {
        if (!isRunning()) {
            return;
        }

        if (mConnectionReceiver != null) {
            mContext.unregisterReceiver(mConnectionReceiver);
            mConnectionReceiver = null;
        }

        if (mVolumeReceiver != null) {
            mContext.unregisterReceiver(mVolumeReceiver);
            mVolumeReceiver = null;
        }

        if (mLooper != null) {
            mLooper.quit();
        }

        native_stop(mFlintdaemonPtr);
    }

    /**
     * Get Flint device name
     * 
     * @return
     */
    public String getDeviceName() {
        return native_getDeviceName();
    }

    /**
     * Set Flint device name
     * 
     * @param name
     */
    public void setDeviceName(String name) {
        native_setDeviceName(name);
    }

    /**
     * Set Flint model name
     *
     * @param name
     */
    public void setModelName(String name) {
        native_setModelName(name);
    }

    /**
     * Get Flint model name
     *
     * @return
     */
    public String getModelName() {
        return native_getModelName();
    }

    /**
     * Get current Flint service's running status.
     * 
     * @return
     */
    public boolean isRunning() {
        return native_isRunning(mFlintdaemonPtr);
    }

    /**
     * Set callback functions which will be notified when Flint related events
     * arrived.
     * 
     * @param callback
     */
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    /**
     * Flint log control.
     * 
     * @param enable
     */
    public void enableLog(boolean enable) {
        DEBUG = enable;

        native_enableLog(enable);
    }

    /**
     * Get Flint internal error code when some errors happened.
     * 
     * Current values are:
     * 
     * <p>
     * 0: NONE_ERROR
     * 
     * <p>
     * 1: FLINTSERVER_ERROR
     * 
     * <p>
     * 2: WEBSOCKETSERVER_ERROR
     * 
     * <p>
     * 3: FLINTDISCOVERY_ERROR
     * 
     * @return
     */
    public int getErrorCode() {
        return native_getErrorCode(mFlintdaemonPtr);
    }

    /**
     * Init IP.
     */
    private void initIp() {
        if (DEBUG) {
            Log.d(LOG_TAG, "set from init!");
        }
        setIp(mContext);
    }

    /**
     * Register system receivers.
     */
    private void registerReceiver() {
        mConnectionReceiver = new ConnectionReceiver();
        IntentFilter cFilter = new IntentFilter();
        cFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mConnectionReceiver, cFilter);

        mVolumeReceiver = new VolumeReceiver();
        IntentFilter vFilter = new IntentFilter();
        vFilter.addAction("android.media.VOLUME_CHANGED_ACTION");
        mContext.registerReceiver(mVolumeReceiver, vFilter);
    }

    /**
     * Init Tcp connection.
     * 
     * This Tcp connection will be used to get all Flint messages.
     */
    private void initTcpConnection() {
        if (mTcpClient != null) {
            mTcpClient.close();
        }
        mTcpClient = new TcpClient(mHandler, "127.0.0.1", 9440);
        mTcpClient.start();
    }

    /**
     * init Handler.
     */
    private void initHandler() {
        new Thread(new Runnable() {
            public void run() {
                // TODO Auto-generated method stub
                Looper.prepare();
                mLooper = Looper.myLooper();

                mHandler = new Handler() {
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        switch (msg.what) {
                        case TcpClient.SOCKET_CLOSE:
                            if (DEBUG) {
                                Log.d(LOG_TAG, "socket closed.");
                            }
                            if (isRunning()) {
                                if (DEBUG) {
                                    Log.d(LOG_TAG,
                                            "deamon is running, re-connect socket");
                                }
                                initTcpConnection();
                            }
                            break;
                        case TcpClient.SOCKET_INIT_FIALED:
                            if (mCounter < SOCKET_INIT_TIMES) {
                                mHandler.postDelayed(new Runnable() {
                                    public void run() {
                                        initTcpConnection();
                                    }
                                }, 1000);
                                mCounter++;
                            }
                            break;
                        case TcpClient.RECEIVE_MESSAGE:
                            parseMessage((String) msg.obj);
                            break;
                        default:
                            break;
                        }
                    }
                };

                initTcpConnection();

                if (DEBUG) {
                    Log.d(LOG_TAG, "Looper loop!!!");
                }
                Looper.loop();

                if (mTcpClient != null) {
                    mTcpClient.close();
                    mTcpClient = null;
                }
                if (DEBUG) {
                    Log.d(LOG_TAG, "Looper exit!!!");
                }
            }
        }).start();
    }

    /**
     * Set ip
     * 
     * @param context
     */
    private void setIp(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
		if (info == null) {
			return;
		}
        if (info.getType() == ConnectivityManager.TYPE_ETHERNET
                || info.getType() == ConnectivityManager.TYPE_WIFI) {
            try {
                List<NetworkInterface> nilist = Collections
                        .list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface ni : nilist) {
                    List<InetAddress> ialist = Collections.list(ni
                            .getInetAddresses());
                    for (InetAddress address : ialist) {
                        String ipv4 = address.getHostAddress();
                        if (!address.isLoopbackAddress()
                                && InetAddressUtils.isIPv4Address(ipv4)) {
                            native_setIpAddr(ipv4);
                            return;
                        }
                    }
                }
            } catch (SocketException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        native_setIpAddr("");
    }

    /**
     * Parse all Flint messages
     * 
     * @param message
     */
    private void parseMessage(String message) {
        mReaderBuffer.append(message);
        // Log.d(LOG_TAG, "buffer: \n" + mReaderBuffer.toString());

        int index = mReaderBuffer.indexOf(":");
        while (index > 0) {
            String length = mReaderBuffer.substring(0, index);
            mReaderBuffer.delete(0, index + 1);
            // Log.d(LOG_TAG, "remove header buffer: \n"
            // + mReaderBuffer.toString());
            int len = 0;
            try {
                String lenStr = length.substring(0, index);
                // Log.d(LOG_TAG, "length header : " + lenStr);
                len = Integer.parseInt(lenStr);
            } catch (NumberFormatException e) {
                // TODO Auto-generated catch block
                if (DEBUG) {
                    Log.e(LOG_TAG, "format message header error!");
                }
                len = 0;
            }
            if (len > 0) {
                String content = mReaderBuffer.substring(0, len);
                if (DEBUG) {
                    Log.d(LOG_TAG, "content : " + content);
                }
                processMessage(content);

                mReaderBuffer.delete(0, len);
                // Log.d(LOG_TAG, "remove content buffer: \n"
                // + mReaderBuffer.toString());
                if (mReaderBuffer.length() == 0) {
                    if (DEBUG) {
                        Log.d(LOG_TAG, "empty buffer!!!");
                    }
                }
            }

            index = mReaderBuffer.indexOf(":");
        }
    }

    /**
     * Process Flint message.
     * 
     * @param message
     */
    private void processMessage(String message) {
        if (DEBUG) {
            Log.d(LOG_TAG, "process : " + message);
        }
        try {
            JSONObject jObj = new JSONObject(message);
            if (jObj.isNull("type")) {
                if (DEBUG) {
                    Log.e(LOG_TAG, "process failed, missing type");
                }
                return;
            }

            String type = jObj.getString("type");
            // Log.d(LOG_TAG, "type ----> " + type);
            if (type.equals("SET_VOLUME")) {
                if (jObj.isNull("level")) {
                    if (DEBUG) {
                        Log.e(LOG_TAG, "process failed, missing level");
                    }
                } else {
                    double level = jObj.getDouble("level");
                    setVolume(level);
                }
            } else if (type.equals("SET_MUTED")) {
                if (jObj.isNull("muted")) {
                    if (DEBUG) {
                        Log.e(LOG_TAG, "process failed, missing muted");
                    }
                } else {
                    boolean muted = jObj.getBoolean("muted");
                    setMute(muted);
                }
            } else if (type.equals("LAUNCH_RECEIVER")
                    || type.equals("STOP_RECEIVER")) {
                if (jObj.isNull("app_info")) {
                    if (DEBUG) {
                        Log.e(LOG_TAG, "process failed, missing app_info");
                    }
                } else {
                    JSONObject appInfo = jObj.getJSONObject("app_info");
                    if (appInfo.isNull("url")) {
                        if (DEBUG) {
                            Log.e(LOG_TAG, "process failed, missing url");
                        }
                    } else {
                        String url = appInfo.getString("url");
                        // Log.d(LOG_TAG, "url -------> " + url);
                        if (url.toLowerCase().startsWith("http://")
                                || url.toLowerCase().startsWith("https://")) {
                            if (mCallback != null) {
                                if (type.equals("LAUNCH_RECEIVER")) {
                                    mCallback.onWebAppStart(url);
                                } else if (type.equals("STOP_RECEIVER")) {
                                    mCallback.onWebAppStop(url);
                                }
                            }
                        } else if (url.toLowerCase().startsWith("app:?")) {
                            if (mCallback != null) {
                                String _url = url.substring("app:?".length());
                                if (type.equals("LAUNCH_RECEIVER")) {
                                    mCallback.onNativeAppStart(_url);
                                } else if (type.equals("STOP_RECEIVER")) {
                                    mCallback.onNativeAppStop(_url);
                                }
                            }
                        } else {
                            if (DEBUG) {
                                Log.w(LOG_TAG, "unknow app url!");
                            }
                        }
                    }
                }
            } else {
                if (DEBUG) {
                    Log.w(LOG_TAG, "unsupport type: " + type);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * convert to IP format.
     * 
     * @param ip
     * @return
     */
    private String convertIp(int ip) {
        String ipStr = (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "." + (ip >> 24 & 0xFF);
        return ipStr;
    }

    /**
     * Set device's volume.
     * 
     * @param volume
     */
    private void setVolume(double volume) {
        AudioManager audioManager = (AudioManager) mContext
                .getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int v = (int) (maxVolume * volume);
        if (DEBUG) {
            Log.d(LOG_TAG, "set volume --------> " + v);
        }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v,
                AudioManager.FLAG_SHOW_UI);
    }

    private int mTempVolume = 0;

    /**
     * Set mute status
     * 
     * @param mute
     */
    private void setMute(boolean mute) {
        if (DEBUG) {
            Log.d(LOG_TAG, "set mute --------> " + mute);
        }
        AudioManager audioManager = (AudioManager) mContext
                .getSystemService(Context.AUDIO_SERVICE);
        if (mute) {
            mTempVolume = audioManager
                    .getStreamVolume(AudioManager.STREAM_MUSIC);
            setVolume(0);
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                    mTempVolume, AudioManager.FLAG_SHOW_UI);
        }
    }

    /**
     * Notify current device's volume status.
     * 
     * @param currVolume
     */
    private void notifyVolume(int currVolume) {
        if (mTcpClient != null) {
            try {
                AudioManager audioManager = (AudioManager) mContext
                        .getSystemService(Context.AUDIO_SERVICE);
                int maxVolume = audioManager
                        .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                double volume = ((double) currVolume) / maxVolume;

                JSONObject jObj = new JSONObject();
                jObj.put("volumeLevel", volume);
                if (volume < 0.1) {
                    jObj.put("volumeMuted", true);
                } else {
                    jObj.put("volumeMuted", false);
                }

                String msg = jObj.toString();
                mTcpClient.send(msg.length() + ":" + msg);
            } catch (JSONException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    // native methods
    private native int native_init();

    private native void native_finalizer(int ptr);

    private native void native_start(int ptr);

    private native void native_stop(int ptr);

    private native String native_getDeviceName();

    private native void native_setDeviceName(String deviceName);

    private native String native_getModelName();

    private native void native_setModelName(String modelName);

    private native boolean native_isRunning(int ptr);

    private native void native_setIpAddr(String ip);

    private native void native_enableLog(boolean enable);

    private native int native_getErrorCode(int ptr);

    /**
     * Volume receiver.
     */
    class VolumeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction()
                    .equals("android.media.VOLUME_CHANGED_ACTION")) {
                AudioManager audioManager = (AudioManager) mContext
                        .getSystemService(Context.AUDIO_SERVICE);
                int currVolume = audioManager
                        .getStreamVolume(AudioManager.STREAM_MUSIC);
                if (DEBUG) {
                    Log.d(LOG_TAG, "volume change to --------> " + currVolume);
                }
                notifyVolume(currVolume);
            }
        }
    }

    /**
     * Connection Receiver.
     */
    class ConnectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            Parcelable parcelableExtra = intent
                    .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (null != parcelableExtra) {
                NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;
                State state = networkInfo.getState();
                boolean isConnected = state == State.CONNECTED;
                if (isConnected) {
                    if (DEBUG) {
                        Log.d(LOG_TAG, "set from receiver!");
                    }
                    setIp(mContext);
                } else {
                    if (DEBUG) {
                        Log.d(LOG_TAG, "set from receiver! set null!");
                    }
                    native_setIpAddr("");
                }
            }
        }
    }

    /**
     * Tcp connection which send/receive all Flint messages from Flint service.
     */
    class TcpClient extends Thread {
        private static final String LOG_TAG = "Flint::TcpClient";

        public static final int SOCKET_CLOSE = 0;
        public static final int SOCKET_INIT_FIALED = 1;
        public static final int RECEIVE_MESSAGE = 2;
        public static final int SEND_MESSAGE = 3;

        private Handler mSocketHandler = null;

        private Socket mSocket = null;
        private String mSocketServerIp = "";
        private int mSocketServerPort = 0;

        private InputStream mInputStream = null;
        private OutputStream mOutputStream = null;

        public TcpClient(Handler handler, String serverIp, int serverPort) {
            mSocketHandler = handler;
            mSocketServerIp = serverIp;
            mSocketServerPort = serverPort;
        }

        /**
         * Close this connection.
         */
        public void close() {
            if (DEBUG) {
                Log.w(LOG_TAG, "close TcpClient!!!" + mSocketServerIp + ":"
                        + mSocketServerPort);
            }
            mSocketHandler = null;
            interrupt();
            closeInternal();
        }

        /**
         * Send message to Flint service
         * 
         * @param msg
         */
        public void send(final String msg) {
            if (DEBUG) {
                Log.d(LOG_TAG, "send -> " + msg);
            }
            if (mSocket != null && mSocket.isConnected()) {
                if (mOutputStream != null) {
                    if (mSocketHandler != null) {
                        mSocketHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // TODO Auto-generated method stub
                                try {
                                    mOutputStream.write(msg.getBytes());
                                    mOutputStream.flush();
                                } catch (Exception e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                }
            }
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub
            if (DEBUG) {
                Log.d(LOG_TAG, "TcpClient running!!!");
            }
            try {
                // 1.init socket
                mSocket = new Socket(mSocketServerIp, mSocketServerPort);
                mInputStream = mSocket.getInputStream();
                mOutputStream = mSocket.getOutputStream();
                if (mSocket == null || mInputStream == null
                        || mOutputStream == null) {
                    if (DEBUG) {
                        Log.e(LOG_TAG, "init failed!");
                    }
                    if (mSocketHandler != null) {
                        mSocketHandler.sendEmptyMessage(SOCKET_INIT_FIALED);
                    }
                    return;
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                if (DEBUG) {
                    Log.e(LOG_TAG, "init exception!");
                }
                e.printStackTrace();
                if (mSocketHandler != null) {
                    mSocketHandler.sendEmptyMessage(SOCKET_INIT_FIALED);
                }
                return;
            }

            // 2.
            final StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[4096];
            while (!isInterrupted()) {
                if (!mSocket.isConnected()) {
                    if (DEBUG) {
                        Log.w(LOG_TAG, "socket is disconnected!");
                    }
                    break;
                }
                if (mSocket.isInputShutdown()) {
                    if (DEBUG) {
                        Log.w(LOG_TAG, "socket input shutdown!");
                    }
                    break;
                }

                try {
                    int readSize = mInputStream.read(buffer);
                    if (DEBUG) {
                        Log.d(LOG_TAG, "readSize:" + readSize);
                    }

                    // If Server is stopping
                    if (readSize == -1) {
                        if (DEBUG) {
                            Log.e(LOG_TAG, "read error: -1");
                        }
                        break;
                    }
                    if (readSize == 0) {
                        if (DEBUG) {
                            Log.e(LOG_TAG, "read empty: continue");
                        }
                        continue;
                    }

                    sb.append(new String(buffer, 0, readSize));
                    Message msg = new Message();
                    msg.what = RECEIVE_MESSAGE;
                    msg.obj = sb.toString();
                    // Log.d(LOG_TAG, "data:" + msg.obj);
                    if (mSocketHandler != null) {
                        mSocketHandler.sendMessage(msg);
                    }
                    sb.setLength(0);
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    break;
                }
            }

            // clean jobs
            closeInternal();
            if (mSocketHandler != null) {
                mSocketHandler.sendEmptyMessage(SOCKET_CLOSE);
            }
            if (DEBUG) {
                Log.d(LOG_TAG, "socket loop exist!!!");
            }
        }

        /**
         * Close all streams.
         */
        private void closeInternal() {
            try {
                if (mInputStream != null) {
                    mInputStream.close();
                }
                if (mOutputStream != null) {
                    mOutputStream.close();
                }
                if (mSocket != null) {
                    if (!mSocket.isClosed()) {
                        mSocket.close();
                    }
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                mInputStream = null;
                mOutputStream = null;
                mSocket = null;
            }
        }
    }
}
