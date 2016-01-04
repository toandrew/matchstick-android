package com.infthink.flint.home.apk;

import java.io.File;
import java.net.HttpURLConnection;

import org.json.JSONObject;

import tv.matchstick.flintreceiver.FlintReceiverManager;
import tv.matchstick.flintreceiver.ReceiverMessageBus;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Window;
import android.widget.TextView;

import com.infthink.flint.home.Globals;
import com.infthink.flint.home.R;
import com.infthink.libs.common.os.ApkInstall;
import com.infthink.libs.common.os.AsyncFiloTask;
import com.infthink.libs.network.HttpFileDownload;

public class ApkInstallActivity extends Activity {
    private static final String TAG = "ApkInstallActivity";

    private static final String STOP_RECEIVER_CMD = "fling.action.stop_receiver";

    public static final String FLINT_INSTALL_APK_ACTION = "flint_install_apk_action";

    public static final String FLINT_INSTALL_APK_ACTION_PATH = "install_path";

    public static final String FLINT_DOWNLOAD_INSTALL_APK_ACTION = "flint_download_install_apk_action";

    public static final String FLINT_DOWNLOAD_INSTALL_APK_ACTION_URL = "flint_download_url";

    public static final String FLINT_DOWNLOAD_INSTALL_APK_INTERNAL_ACTION = "flint_download_install_apk_internal_action";
    public static final String FLINT_DOWNLOAD_INSTALL_APK_INTERNAL_ACTION_URL = "flint_download_internal_url";

    public static final String GET_APK_BY_URL_REQUEST = "/apkurl";

    public static final int MAX_CACHE_APK_NUM = 10;

    private static final int MSG_APK_BEGIN_DOWNLOAD = 101;
    private static final int MSG_APK_DOWNLOADING = 102;
    private static final int MSG_APK_DOWNLOADED = 103;
    private static final int MSG_APK_DOWNLOADED_FAILED = 104;

    private static final int MSG_APK_INSTALLING = 110;
    private static final int MSG_APK_INSTALLED = 111;
    private static final int MSG_APK_INSTALL_FAILED = 112;

    private static final int MSG_EXIT = 300;

    private static final int MSG_DOWNLOAD_INSTALL_APK = 400;

    private static final String CMD_INSTALL = "install";

    private static final String CMD_CANCEL_INSTALL = "cancel_install";

    // flint related
    private static final String APPID = "~flintplayer";

    private static final String CUST_APK_MESSAGE_NAMESPACE = "urn:x-cast:com.connectsdk"; // connect
                                                                                          // sdk
                                                                                          // default
    private FileDownloadTask mTask;

    private FlintReceiverManager mFlintReceiverManager = null;

    private ReceiverMessageBus mCustMessageReceiverMessageBus = null;

    private Handler mHandler;

    private TextView mDownloadingPercentView;

    private TextView mDownloadingHintView;

    private String mInstallApkUrl;

    /**
     * Process "install apk" command from user!
     */
    BroadcastReceiver mFlintInstallAppReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            Log.e(TAG, "onReceive!!!!");

            if (STOP_RECEIVER_CMD.equals(intent.getAction())) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.apk_install);

        IntentFilter filter = new IntentFilter(STOP_RECEIVER_CMD);
        filter.addAction(FLINT_DOWNLOAD_INSTALL_APK_INTERNAL_ACTION);
        registerReceiver(mFlintInstallAppReceiver, filter);

        mDownloadingHintView = (TextView) findViewById(R.id.downloading_hint);
        mDownloadingPercentView = (TextView) findViewById(R.id.download_percent);

        init();

        refreshIntent(getIntent());
    }

    private void downloadApk(final String url, final String path) {
        if (mTask != null) {
            mTask.cancel(true);
        }

        mTask = new FileDownloadTask() {
            private File mFile;
            private long mDownloadLength;
            private long mContentLength;

            private long mPrePercent = 0;

            @Override
            protected Void doInBackground(Void... params) {
                Message msg = mHandler.obtainMessage();
                msg.what = MSG_APK_BEGIN_DOWNLOAD;
                mHandler.sendMessage(msg);

                if (!isCancelled()) {
                    HttpFileDownload.download(url, path, null,
                            new HttpFileDownload.IOnHttpFileDownload() {
                                @Override
                                public void onHttpFileDownload(String httpUrl,
                                        File file, long downloadLength,
                                        long contentLength,
                                        HttpURLConnection connection) {
                                    if (!isCancelled()) {
                                        mFile = file;
                                        mDownloadLength = downloadLength;
                                        mContentLength = contentLength;
                                        publishProgress();
                                    }
                                }

                                @Override
                                public boolean isAlreadyCancelled() {
                                    return isCancelled();
                                }

                                @Override
                                public void onHttpFileDownloaded(
                                        boolean sucessed) {
                                    // TODO Auto-generated method stub

                                }

                            });
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Void... values) {
                if (!isCancelled()) {
                    if (mFile == null) {
                        if (DEBUG)
                            Log.d(TAG, "showDownloadingFail");
                        // mUpgradeView.showDownloadingFail(Upgrade.this);
                    } else {
//                        if (DEBUG)
//                            Log.d(TAG,
//                                    String.format(
//                                            "showDownloading#mDownloadLength:%s, mContentLength:%s",
//                                            mDownloadLength, mContentLength));
                        if (mDownloadLength < mContentLength) {
                            long downloadPercent = mDownloadLength * 100
                                    / mContentLength;

                            if (mPrePercent != downloadPercent) {
                                
                                mPrePercent = downloadPercent;
                                
                                Message msg = mHandler.obtainMessage();
                                msg.what = MSG_APK_DOWNLOADING;
                                msg.arg1 = (int) downloadPercent;
                                mHandler.sendMessage(msg);
                            }

                            // update download progress bar
                            updateDownloadingPercent((int) downloadPercent);
                        } else {
                            Message msg = mHandler.obtainMessage();
                            msg.what = MSG_APK_DOWNLOADED;
                            mHandler.sendMessage(msg);

                            // finished?
                            updateDownloadingPercent(100);

                            // mUpgradeView.dismiss();
                            // 安装升级
                            ApkInstall.install(Globals.getContext(), mFile);
                        }
                    }
                }
            }

            @Override
            protected void onPostExecute(Void result) {
                mTask.setComplete(true);
            }

        };
        mTask.execute();
    }

    @Override
    public void onDestroy() {
        try {
            if (mFlintInstallAppReceiver != null) {
                unregisterReceiver(mFlintInstallAppReceiver);
                mFlintInstallAppReceiver = null;
            }
        } catch (Exception e) {
        }

        try {
            mFlintReceiverManager.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (mTask != null) {
            mTask.cancel(true);
        }

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.e(TAG, "onNewIntent");

        refreshIntent(intent);

        super.onNewIntent(intent);
    }

    private void refreshIntent(Intent intent) {
        if (intent != null
                && FLINT_DOWNLOAD_INSTALL_APK_ACTION.equals(intent.getAction())) {
            mInstallApkUrl = intent
                    .getStringExtra(FLINT_DOWNLOAD_INSTALL_APK_ACTION_URL);

            if (mInstallApkUrl != null) {
                mHandler.sendEmptyMessageDelayed(MSG_DOWNLOAD_INSTALL_APK, 1000);
            }
        }

    }

    public abstract static class FileDownloadTask extends
            AsyncFiloTask<Void, Void, Void> {
        String mHttpUrl;

        boolean mComplete;

        public FileDownloadTask() {
        }

        public boolean isComplete() {
            return mComplete;
        }

        public void setComplete(boolean complete) {
            mComplete = complete;
        }

        FileDownloadTask(FileDownloadTask task) {
            if (task != null) {
                mHttpUrl = task.mHttpUrl;
            }
        }
    }

    private void init() {
        // whether enable receiver log.
        FlintReceiverManager.setLogEnabled(true);

        mFlintReceiverManager = new FlintReceiverManager(APPID);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MSG_APK_BEGIN_DOWNLOAD:
                    doSendMessage(MSG_APK_BEGIN_DOWNLOAD, 0);
                    break;

                case MSG_APK_DOWNLOADING:
                    doSendMessage(MSG_APK_DOWNLOADING, msg.arg1);
                    break;

                case MSG_APK_DOWNLOADED:
                    doSendMessage(MSG_APK_DOWNLOADED, 100);

                    mHandler.sendEmptyMessageDelayed(MSG_EXIT, 1000);
                    break;

                case MSG_APK_DOWNLOADED_FAILED:
                    doSendMessage(MSG_APK_DOWNLOADED_FAILED, 0);

                    mHandler.sendEmptyMessageDelayed(MSG_EXIT, 1000);
                    break;

                case MSG_APK_INSTALLING:
                    doSendMessage(MSG_APK_INSTALLING, msg.arg1);
                    break;

                case MSG_APK_INSTALLED:
                    doSendMessage(MSG_APK_INSTALLED, 100);
                    break;

                case MSG_APK_INSTALL_FAILED:
                    doSendMessage(MSG_APK_INSTALL_FAILED, 0);
                    break;

                case MSG_EXIT:
                    finish();
                    break;

                case MSG_DOWNLOAD_INSTALL_APK:
                    installApkByUrl(mInstallApkUrl);
                    break;
                }
            }
        };

        // used to receive cust message from sender app.
        mCustMessageReceiverMessageBus = new ReceiverMessageBus(
                CUST_APK_MESSAGE_NAMESPACE) {

            @Override
            public void onPayloadMessage(final String payload,
                    final String senderId) {
                // TODO Auto-generated method stub

                // process CUSTOM messages received from sender apps.
                Log.e(TAG, "payload[" + payload + "]");
                try {
                    JSONObject obj = new JSONObject(payload);
                    String cmd = obj.getString("cmd");
                    if (CMD_INSTALL.equals(cmd)) {
                        final String url = obj.getString("url");
                        if (url != null) {
                            mInstallApkUrl = url;

                            mHandler.sendEmptyMessageDelayed(
                                    MSG_DOWNLOAD_INSTALL_APK, 0);
                        } else {
                            mHandler.sendEmptyMessage(MSG_APK_DOWNLOADED_FAILED);
                        }
                    } else { // CMD_CANCEL_INSTALL
                        mHandler.sendEmptyMessage(MSG_EXIT);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        mFlintReceiverManager.setMessageBus(CUST_APK_MESSAGE_NAMESPACE,
                mCustMessageReceiverMessageBus);

        mFlintReceiverManager.open();
    }

    /**
     * Send CUSTOM MESSAGE events to Sender Apps.
     */
    private void doSendMessage(int what, int value) {
        try {
            JSONObject message = new JSONObject();
            message.put("msg", what);
            message.put("value", value);
            if (mCustMessageReceiverMessageBus != null && message != null) {
                Log.e(TAG, "doSendMessage!" + message);

                mCustMessageReceiverMessageBus.send(message.toString(), null); // null:
                                                                               // send
                                                                               // to
                                                                               // all.
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateDownloadingPercent(int percent) {
        if (mDownloadingPercentView != null) {
            mDownloadingPercentView.setText(percent + "%");
        }
    }

    private void installApkByUrl(final String url) {
        File cacheDir;

        if (url == null) {
            Log.e(TAG, "installApkByUrl failed: url is null!");
            return;
        }
        if (android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED))
            cacheDir = Globals.getContext().getExternalCacheDir();
        else
            cacheDir = ApkInstallActivity.this.getCacheDir();

        if (cacheDir.listFiles() != null
                && cacheDir.listFiles().length > MAX_CACHE_APK_NUM) {
            cacheDir.delete();
        }

        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        Log.e(TAG, "url[" + url + "]cacheDir[" + cacheDir.getAbsolutePath()
                + "]");

        File file = new File(cacheDir.getAbsolutePath() + "/"
                + String.valueOf(Math.abs(url.toString().hashCode())) + ".apk");

        if (file.exists()) {
            file.delete();
        }

        downloadApk(
                url,
                cacheDir.getAbsolutePath() + "/"
                        + String.valueOf(url.toString().hashCode()) + ".apk");
    }

    private void broadInstallApkByUrl(final String url) {
        Log.e("httpd", "!!broadInstallApkByUrl:" + url);
        if (url == null) {
            return;
        }

        Context context = Globals.getContext();

        Intent intent = new Intent(
                ApkInstallActivity.FLINT_DOWNLOAD_INSTALL_APK_INTERNAL_ACTION);
        intent.putExtra(
                ApkInstallActivity.FLINT_DOWNLOAD_INSTALL_APK_INTERNAL_ACTION_URL,
                url);
        context.sendBroadcast(intent);
    }

}
