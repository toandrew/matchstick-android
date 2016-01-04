package com.infthink.flint.home.video;

import org.json.JSONObject;

import tv.matchstick.flintreceiver.FlintReceiverManager;
import tv.matchstick.flintreceiver.ReceiverMessageBus;
import tv.matchstick.flintreceiver.media.FlintMediaPlayer;
import tv.matchstick.flintreceiver.media.FlintVideo;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.geniusgithub.mediarender.util.CommonLog;
import com.geniusgithub.mediarender.util.CommonUtil;
import com.geniusgithub.mediarender.util.DlnaUtils;
import com.geniusgithub.mediarender.util.LogFactory;
import com.infthink.flint.home.R;
import com.infthink.flint.home.player.AbstractTimer;
import com.infthink.flint.home.player.CheckDelayTimer;
import com.infthink.flint.home.player.MediaModel;
import com.infthink.flint.home.player.PlayerEngineListener;
import com.infthink.flint.home.player.SingleSecondTimer;
import com.infthink.flint.home.player.VideoPlayEngineImpl;

public class FlintVideoActivity extends Activity implements
        OnBufferingUpdateListener, OnSeekCompleteListener, OnErrorListener,
        OnInfoListener {

    private static final String TAG = "FlintVideoActivity";

    private static final CommonLog log = LogFactory.createLog();
    private final static int REFRESH_CURPOS = 0x0001;
    private final static int HIDE_TOOL = 0x0002;
    private final static int EXIT_ACTIVITY = 0x0003;
    private final static int REFRESH_SPEED = 0x0004;
    private final static int CHECK_DELAY = 0x0005;

    private final static int EXIT_DELAY_TIME = 5000;
    private final static int HIDE_DELAY_TIME = 3000;

    private UIManager mUIManager;
    private VideoPlayEngineImpl mPlayerEngineImpl;
    private VideoPlayEngineListener mPlayEngineListener;

    private Handler mHandler;

    private AbstractTimer mPlayPosTimer;
    private AbstractTimer mNetWorkTimer;
    private CheckDelayTimer mCheckDelayTimer;

    private boolean isSurfaceCreate = false;
    private boolean isDestroy = false;

    // flint related
    private static final String APPID = "~flintplayer";

    private static final String CUST_MESSAGE_NAMESPACE = "urn:flint:com.infthink.flintreceiver.receiver";

    private static final String STOP_RECEIVER_CMD = "fling.action.stop_receiver";

    // custom message which will be send back to Sender Apps.
    private JSONObject mCustMessage;

    private ReceiverMessageBus mCustMessageReceiverMessageBus = null;

    private static final int PLAYER_MSG_LOAD = 10;
    private static final int PLAYER_MSG_PLAY = 20;
    private static final int PLAYER_MSG_PAUSE = 30;
    private static final int PLAYER_MSG_SEEK = 40;
    private static final int PLAYER_MSG_CHANGE_VOLUME = 50;
    private static final int PLAYER_MSG_SEND_MESSAGE = 60;
    private static final int PLAYER_MSG_STOP = 70;
    private static final int PLAYER_MSG_FINISHED = 80;

    private FlintReceiverManager mFlintReceiverManager;

    private MyFlintVideo mFlintVideo;

    private FlintMediaPlayer mFlintMediaPlayer;

    private double mCurrentTime = 0;

    private boolean mMuted = false;

    private double mVolume = 0; // please note the category: "0.0" ~ "1.0"

    private boolean mMediaLoaded = false;

    private MediaModel mMediaInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.e("onCreate");

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        setContentView(R.layout.video_player_layout);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        setupsView();
        initData();

        // register flint stop receiver.
        IntentFilter filter = new IntentFilter(STOP_RECEIVER_CMD);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mFlintFinishReceiver, filter);

        // init volume
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mVolume = (float) am.getStreamVolume(AudioManager.STREAM_MUSIC)
                / (float) maxVolume;

        if (mVolume == 0) {
            mMuted = true;
        } else {
            mMuted = false;
        }

        // init falint related
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onNewIntent(Intent intent) {
        log.e("onNewIntent");
        // refreshIntent(intent);

        super.onNewIntent(intent);
    }

    @Override
    protected void onStop() {
        log.e("onStop");
        super.onStop();

        onDestroy();
    }

    @Override
    protected void onDestroy() {
        log.e("onDestroy");

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow()
                .clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        isDestroy = true;

        mUIManager.unInit();
        mCheckDelayTimer.stopTimer();
        mNetWorkTimer.stopTimer();
        mPlayPosTimer.stopTimer();
        mPlayerEngineImpl.exit();

        // flint related
        try {
            if (mFlintFinishReceiver != null) {
                unregisterReceiver(mFlintFinishReceiver);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mFlintMediaPlayer.stop(null);

            mFlintReceiverManager.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        super.onDestroy();
    }

    private void setupsView() {
        mUIManager = new UIManager();
    }

    private void initData() {
        mPlayPosTimer = new SingleSecondTimer(this);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case REFRESH_CURPOS:
                    refreshCurPos();
                    break;
                case HIDE_TOOL:
                    if (!mPlayerEngineImpl.isPause()) {
                        mUIManager.showControlView(false);
                    }
                    break;
                case EXIT_ACTIVITY:
                    finish();
                    break;
                case REFRESH_SPEED:
                    refreshSpeed();
                    break;
                case CHECK_DELAY:
                    checkDelay();
                    break;

                // flint related
                case PLAYER_MSG_LOAD:
                    doLoad();
                    break;

                case PLAYER_MSG_PLAY:
                    doPlay();
                    break;

                case PLAYER_MSG_PAUSE:
                    doPause();
                    break;

                case PLAYER_MSG_SEEK:
                    doSeek(msg.arg1);
                    break;

                case PLAYER_MSG_CHANGE_VOLUME:
                    doChangeVolume();
                    break;

                case PLAYER_MSG_SEND_MESSAGE:
                    doSendMessage();
                    break;

                case PLAYER_MSG_STOP:
                    doStop();
                    break;

                case PLAYER_MSG_FINISHED:
                    doFinished();
                    break;

                }
            }

        };

        mPlayPosTimer.setHandler(mHandler, REFRESH_CURPOS);

        mNetWorkTimer = new SingleSecondTimer(this);
        mNetWorkTimer.setHandler(mHandler, REFRESH_SPEED);
        mCheckDelayTimer = new CheckDelayTimer(this);
        mCheckDelayTimer.setHandler(mHandler, CHECK_DELAY);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        mPlayerEngineImpl = new VideoPlayEngineImpl(this, mUIManager.holder,
                mUIManager.mSurfaceView, dm);
        mPlayerEngineImpl.setOnBuffUpdateListener(this);
        mPlayerEngineImpl.setOnSeekCompleteListener(this);
        mPlayerEngineImpl.setOnInfoListener(this);
        mPlayerEngineImpl.setOnErrorListener(this);

        mPlayEngineListener = new VideoPlayEngineListener();
        mPlayerEngineImpl.setPlayerListener(mPlayEngineListener);

        mNetWorkTimer.startTimer();
        mCheckDelayTimer.startTimer();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {

        int action = ev.getAction();
        int actionIdx = ev.getActionIndex();

        if (actionIdx == 0 && action == MotionEvent.ACTION_UP) {
            if (!mUIManager.isControlViewShow()) {
                mUIManager.showControlView(true);
                return true;
            } else {
                delayToHideControlPanel();
            }
        }

        return super.dispatchTouchEvent(ev);
    }

    private void delayToPlayMedia(final MediaModel mMediaInfo) {

        mHandler.postDelayed(new Runnable() {

            @Override
            public void run() {
                if (!isDestroy) {
                    boolean result = mPlayerEngineImpl.playMedia(mMediaInfo);
                    if (!result) {
                        playFailed();
                    }
                } else {
                    log.e("activity destroy...so don't playMedia...");
                }
            }
        }, 1000);
    }

    private void removeHideMessage() {
        mHandler.removeMessages(HIDE_TOOL);
    }

    private void delayToHideControlPanel() {
        removeHideMessage();
        mHandler.sendEmptyMessageDelayed(HIDE_TOOL, HIDE_DELAY_TIME);
    }

    private void removeExitMessage() {
        mHandler.removeMessages(EXIT_ACTIVITY);
    }

    private void delayToExit() {

        removeExitMessage();
        mHandler.sendEmptyMessageDelayed(EXIT_ACTIVITY, EXIT_DELAY_TIME);
    }

    private void refreshCurPos() {
        int pos = mPlayerEngineImpl.getCurPosition();

        mUIManager.setSeekbarProgress(pos);
    }

    private void refreshSpeed() {
        if (mUIManager.isLoadViewShow()) {
            float speed = CommonUtil.getSysNetworkDownloadSpeed();
            mUIManager.setSpeed(speed);
        }
    }

    private void checkDelay() {
        int pos = mPlayerEngineImpl.getCurPosition();

        boolean ret = mCheckDelayTimer.isDelay(pos);
        if (ret) {
            mUIManager.showLoadView(true);
        } else {
            mUIManager.showLoadView(false);
        }

        mCheckDelayTimer.setPos(pos);

    }

    private void seek(int pos) {
        isSeekComplete = false;
        mPlayerEngineImpl.skipTo(pos);
        mUIManager.setSeekbarProgress(pos);

    }

    private class VideoPlayEngineListener implements PlayerEngineListener {

        @Override
        public void onTrackPlay(MediaModel itemInfo) {

            mPlayPosTimer.startTimer();

            mUIManager.showPlay(false);
            mUIManager.showControlView(true);

            // notify playing?
            mFlintVideo.notifyEvents(FlintVideo.PLAYING, "PLAYING MEDIA?");
            mUIManager.showPrepareLoadView(false, false);
        }

        @Override
        public void onTrackStop(MediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            // DLNAGenaEventBrocastFactory.sendStopStateEvent(mContext);

            // //mUIManager.showPrepareLoadView(true, true);
            // mUIManager.showLoadView(false);
            // mUIManager.showControlView(false);
            //
            // //mUIManager.showPlay(true);
            // mUIManager.updateMediaInfoView(mMediaInfo);
            // //mUIManager.showControlView(true);
            // //mUIManager.showLoadView(false);
            isSeekComplete = true;

            // notify that media player is stopped!
            mFlintVideo.notifyEvents(FlintVideo.ENDED, "Media ENDED");

            // STOP received? treated it as FINISHED!
            mHandler.sendEmptyMessage(PLAYER_MSG_FINISHED);

            // finish???
            delayToExit();
        }

        @Override
        public void onTrackPause(MediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            // DLNAGenaEventBrocastFactory.sendPauseStateEvent(mContext);
            mUIManager.showPlay(true);
            mUIManager.showControlView();

            mFlintVideo.notifyEvents(FlintVideo.PAUSE, "Media PAUSED");
        }

        @Override
        public void onTrackPrepareSync(MediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            // DLNAGenaEventBrocastFactory.sendTranstionEvent(mContext);
        }

        @Override
        public void onTrackPrepareComplete(MediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            int duration = mPlayerEngineImpl.getDuration();

            // All media file should first be loaded.
            mMediaLoaded = true;

            // set Flint related
            mFlintVideo.setDuration(mPlayerEngineImpl.getDuration());

            mFlintVideo.setPlaybackRate(1); // TODO

            mFlintVideo.setCurrentTime(mPlayerEngineImpl.getCurPosition());

            mFlintVideo.notifyEvents(FlintVideo.LOADEDMETADATA,
                    "Media is LOADEDMETADATA"); // READY

            mUIManager.setSeekbarMax(duration);
            mUIManager.setTotalTime(duration);

            mUIManager.showPrepareLoadView(false, false);
        }

        @Override
        public void onTrackStreamError(MediaModel itemInfo) {
            log.e("onTrackStreamError");
            mPlayPosTimer.stopTimer();
            mPlayerEngineImpl.stop();
            mUIManager.showPlayErrorTip();

            // notify that media player is stopped!
            mFlintVideo.notifyEvents(FlintVideo.ENDED, "Media ENDED");

            // STOP received? treated it as FINISHED!
            mHandler.sendEmptyMessage(PLAYER_MSG_FINISHED);
        }

        @Override
        public void onTrackPlayComplete(MediaModel itemInfo) {
            log.e("onTrackPlayComplete");
            mPlayerEngineImpl.stop();
        }

    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        int duration = mPlayerEngineImpl.getDuration();
        int time = duration * percent / 100;
        mUIManager.setSeekbarSecondProgress(time);
    }

    private boolean isSeekComplete = false;

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        isSeekComplete = true;
        log.e("onSeekComplete ...");

        mFlintVideo.notifyEvents(FlintVideo.SEEKED, "Media SEEKED");

        // notify playing?
        mFlintVideo.notifyEvents(FlintVideo.PLAYING, "PLAYING MEDIA?");
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mUIManager.showPlayErrorTip();
        log.e("onError what = " + what + ", extra = " + extra);

        Log.e(TAG, "OnErrorListener:what[" + what + "]extra[" + extra + "]");

        // notify ERROR to sender app when error happened?
        mFlintVideo.notifyEvents(FlintVideo.ERROR, "Media ERROR");

        mHandler.sendEmptyMessage(PLAYER_MSG_FINISHED);

        return false;
    }

    /*---------------------------------------------------------------------------*/
    class UIManager implements OnClickListener, SurfaceHolder.Callback,
            OnSeekBarChangeListener {

        public View mPrepareView;
        public TextView mTVPrepareSpeed;

        public View mLoadView;
        public TextView mTVLoadSpeed;

        public View mControlView;
        public View mUpToolView;
        public View mDownToolView;

        public ImageButton mBtnPlay;
        public ImageButton mBtnPause;
        public SeekBar mSeekBar;
        public TextView mTVCurTime;
        public TextView mTVTotalTime;
        public TextView mTVTitle;

        private SurfaceView mSurfaceView;
        private SurfaceHolder holder = null;

        private TranslateAnimation mHideDownTransformation;
        private TranslateAnimation mHideUpTransformation;
        private AlphaAnimation mAlphaHideTransformation;

        private ProgressBar mPrepareProgressBar;

        public UIManager() {
            initView();
        }

        private void initView() {
            mPrepareProgressBar = (ProgressBar) findViewById(R.id.tv_prepare_progressbar);

            mPrepareView = findViewById(R.id.prepare_panel);
            mTVPrepareSpeed = (TextView) findViewById(R.id.tv_prepare_speed);

            mLoadView = findViewById(R.id.loading_panel);
            mTVLoadSpeed = (TextView) findViewById(R.id.tv_speed);

            mControlView = findViewById(R.id.control_panel);
            mUpToolView = findViewById(R.id.up_toolview);
            mDownToolView = findViewById(R.id.down_toolview);

            mTVTitle = (TextView) findViewById(R.id.tv_title);

            mBtnPlay = (ImageButton) findViewById(R.id.btn_play);
            mBtnPause = (ImageButton) findViewById(R.id.btn_pause);
            mBtnPlay.setOnClickListener(this);
            mBtnPause.setOnClickListener(this);
            mSeekBar = (SeekBar) findViewById(R.id.playback_seeker);
            mTVCurTime = (TextView) findViewById(R.id.tv_curTime);
            mTVTotalTime = (TextView) findViewById(R.id.tv_totalTime);

            setSeekbarListener(this);

            mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
            holder = mSurfaceView.getHolder();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            mHideDownTransformation = new TranslateAnimation(0.0f, 0.0f, 0.0f,
                    200.0f);
            mHideDownTransformation.setDuration(1000);

            mAlphaHideTransformation = new AlphaAnimation(1, 0);
            mAlphaHideTransformation.setDuration(1000);

            mHideUpTransformation = new TranslateAnimation(0.0f, 0.0f, 0.0f,
                    -124.0f);
            mHideUpTransformation.setDuration(1000);

        }

        private void unInit() {

        }

        public void showPrepareLoadView(boolean isShow, boolean finished) {
            if (isShow) {
                Log.e(TAG, "Black showPrepareLoadView:" + isShow);

                if (finished) {
                    mTVPrepareSpeed.setVisibility(View.GONE);
                    mPrepareProgressBar.setVisibility(View.GONE);
                } else {
                    mTVPrepareSpeed.setVisibility(View.VISIBLE);
                    mPrepareProgressBar.setVisibility(View.VISIBLE);
                }
                mSurfaceView.setBackgroundColor(Color.BLACK);
                mPrepareView.setVisibility(View.VISIBLE);
            } else {
                Log.e(TAG, "!TRANSPARENT showPrepareLoadView:" + isShow);
                mSurfaceView.setBackgroundColor(Color.TRANSPARENT);
                mPrepareView.setVisibility(View.GONE);
            }
        }

        public void showControlView(boolean isShow) {
            if (isShow) {
                mUpToolView.setVisibility(View.VISIBLE);
                mDownToolView.setVisibility(View.VISIBLE);
                mPrepareView.setVisibility(View.GONE);
                delayToHideControlPanel();
            } else {
                if (mDownToolView.isShown()) {
                    mDownToolView.startAnimation(mHideDownTransformation);
                    mUpToolView.startAnimation(mHideUpTransformation);

                    mUpToolView.setVisibility(View.GONE);
                    mDownToolView.setVisibility(View.GONE);
                }
            }
        }

        public void showControlView() {
            removeHideMessage();
            mUpToolView.setVisibility(View.VISIBLE);
            mDownToolView.setVisibility(View.VISIBLE);
        }

        public void showLoadView(boolean isShow) {
            if (isShow) {
                mLoadView.setVisibility(View.VISIBLE);
            } else {
                if (mLoadView.isShown()) {
                    mLoadView.startAnimation(mAlphaHideTransformation);
                    mLoadView.setVisibility(View.GONE);
                }
            }
        }

        private boolean isSeekbarTouch = false;

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            isSurfaceCreate = true;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                int height) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            isSurfaceCreate = false;
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
            case R.id.btn_play:
                mPlayerEngineImpl.play();
                break;
            case R.id.btn_pause:
                mPlayerEngineImpl.pause();
                break;
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser) {
            mUIManager.setcurTime(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            isSeekbarTouch = true;

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            isSeekbarTouch = false;
            seek(seekBar.getProgress());
            mUIManager.showControlView(true);
        }

        public void showPlay(boolean bShow) {
            if (bShow) {
                mBtnPlay.setVisibility(View.VISIBLE);
                mBtnPause.setVisibility(View.INVISIBLE);
            } else {
                mBtnPlay.setVisibility(View.INVISIBLE);
                mBtnPause.setVisibility(View.VISIBLE);
            }
        }

        public void togglePlayPause() {
            if (mBtnPlay.isShown()) {
                mPlayerEngineImpl.play();
            } else {
                mPlayerEngineImpl.pause();
            }
        }

        public void setSeekbarProgress(int time) {
            if (!isSeekbarTouch) {
                mSeekBar.setProgress(time);
            }
        }

        public void setSeekbarSecondProgress(int time) {
            mSeekBar.setSecondaryProgress(time);
        }

        public void setSeekbarMax(int max) {
            mSeekBar.setMax(max);
        }

        public void setcurTime(int curTime) {
            String timeString = DlnaUtils.formateTime(curTime);
            mTVCurTime.setText(timeString);
        }

        public void setTotalTime(int totalTime) {
            String timeString = DlnaUtils.formateTime(totalTime);
            mTVTotalTime.setText(timeString);
        }

        public void updateMediaInfoView(MediaModel mediaInfo) {
            setcurTime(0);
            setTotalTime(0);
            setSeekbarMax(100);
            setSeekbarProgress(0);
            mTVTitle.setText(mediaInfo.getTitle());
        }

        public void setSpeed(float speed) {
            String showString = (int) speed + "KB/"
                    + getResources().getString(R.string.second);
            mTVPrepareSpeed.setText(showString);
            mTVLoadSpeed.setText(showString);
        }

        public void setSeekbarListener(OnSeekBarChangeListener listener) {
            mSeekBar.setOnSeekBarChangeListener(listener);
        }

        public boolean isControlViewShow() {
            return mDownToolView.getVisibility() == View.VISIBLE ? true : false;
        }

        public boolean isLoadViewShow() {
            if (mLoadView.getVisibility() == View.VISIBLE
                    || mPrepareView.getVisibility() == View.VISIBLE) {
                return true;
            }

            return false;
        }

        public void showPlayErrorTip() {
            Toast.makeText(FlintVideoActivity.this,
                    R.string.toast_videoplay_fail, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Process "STOP_RECEIVER" command from Flingd
     */
    BroadcastReceiver mFlintFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent
                    .getAction())) {
                Parcelable parcelableExtra = intent
                        .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (null != parcelableExtra) {
                    NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;
                    State state = networkInfo.getState();
                    Log.d(TAG, "state = " + state);
                    if (state == State.DISCONNECTED) {
                        Toast.makeText(FlintVideoActivity.this, R.string.wifi_disconnected, Toast.LENGTH_SHORT).show();
                    }
                }

                return;
            }

            // TODO Auto-generated method stub
            Log.e(TAG, "Ready to call finish!!!");
            finish();
            Log.e(TAG, "End to call finish!!!");
        }
    };

    /**
     * Concrete Flint Video, which will receive all media events.
     */
    private class MyFlintVideo extends FlintVideo {

        @Override
        public void load() {
            Log.e(TAG, "load!");

            mHandler.sendEmptyMessage(PLAYER_MSG_LOAD);
        }

        @Override
        public void pause() {
            Log.e(TAG, "pause!");

            mHandler.sendEmptyMessage(PLAYER_MSG_PAUSE);
        }

        @Override
        public void play() {
            Log.e(TAG, "play!");

            mHandler.sendEmptyMessage(PLAYER_MSG_PLAY);
        }

        @Override
        public void stop(JSONObject custData) {
            Log.e(TAG, "stop!");

            mHandler.sendEmptyMessage(PLAYER_MSG_STOP);
        }

        @Override
        public void seek(double time) {
            mCurrentTime = time;

            Message msg = mHandler.obtainMessage();
            msg.what = PLAYER_MSG_SEEK;
            msg.arg1 = (int) time;
            mHandler.sendMessage(msg);
        }

        @Override
        public void setCurrentTime(double time) {
            // TODO Auto-generated method stub

            mCurrentTime = time;
        }

        @Override
        public double getCurrentTime() {
            // use real concrete media object to getCurrent position!

            try {
                mCurrentTime = mPlayerEngineImpl.getCurPosition();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return mCurrentTime;
        }

        @Override
        public void setVolume(double volume) {
            if (volume <= 0) {
                mVolume = 0;
            } else if (volume >= 1) {
                mVolume = 1;
            } else {
                mVolume = volume; // save this volume.
            }

            // change muted status
            if (mVolume == 0) {
                mMuted = true;
            } else {
                mMuted = false;
            }

            Message msg = mHandler.obtainMessage();
            msg.what = PLAYER_MSG_CHANGE_VOLUME;
            mHandler.sendMessage(msg);
        }

        @Override
        public double getVolume() {
            // TODO Auto-generated method stub

            return mVolume;
        }

        @Override
        public boolean isMuted() {
            // TODO Auto-generated method stub

            return mMuted;
        }

        @Override
        public void notifyEvents(String type, String data) {
            if (!mMediaLoaded) {
                Log.e(TAG, "The media is in finished state. So ignore event["
                        + type + "][" + data + "]");
                return;
            }

            // ready to process events.
            super.notifyEvents(type, data);
        }
    };

    /**
     * Init all Flint related objects
     */
    private void init() {
        // whether enable receiver log.
        FlintReceiverManager.setLogEnabled(true);

        mFlintReceiverManager = new FlintReceiverManager(APPID);

        mFlintVideo = new MyFlintVideo();

        mFlintMediaPlayer = new FlintMediaPlayer(mFlintReceiverManager,
                mFlintVideo) {

            @Override
            public boolean onMediaMessage(final String payload) {
                // TODO, here you can process all media messages.

                Log.e(TAG, "onMediaMessage: " + payload);
                return false;
            }
        };

        // used to receive cust message from sender app.
        mCustMessageReceiverMessageBus = new ReceiverMessageBus(
                CUST_MESSAGE_NAMESPACE) {

            @Override
            public void onPayloadMessage(final String payload,
                    final String senderId) {
                // TODO Auto-generated method stub

                // process CUSTOM messages received from sender apps.
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        Toast.makeText(getApplicationContext(),
                                "Got user messages![" + payload + "]",
                                Toast.LENGTH_SHORT).show();
                    }

                });

            }
        };

        mFlintReceiverManager.setMessageBus(CUST_MESSAGE_NAMESPACE,
                mCustMessageReceiverMessageBus);

        mFlintReceiverManager.open();

        mUIManager.showPrepareLoadView(true, true);
        mUIManager.showLoadView(false);
        mUIManager.showControlView(false);
    }

    /**
     * Process LOAD media player event
     */
    private void doLoad() {
        Log.e(TAG, "doLoad!");

        // remove exit messages.
        removeExitMessage();

        // All media file should first be loaded.
        mMediaLoaded = true;

        loadPlayInfo(mFlintVideo.getUrl(), mFlintVideo.getTitle());
    }

    /**
     * Process PLAY media player event.
     */
    private void doPlay() {
        try {
            mPlayerEngineImpl.play();

            mFlintVideo.notifyEvents(FlintVideo.PLAY, "PLAY Media");

            // Here show how to send custom message to sender apps.
            mCustMessage = new JSONObject();
            mCustMessage.put("hello", "PLAY Media!");
            mHandler.sendEmptyMessage(PLAYER_MSG_SEND_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Process PAUSE media player event.
     */
    private void doPause() {
        try {
            mPlayerEngineImpl.pause();

            mFlintVideo.notifyEvents(FlintVideo.PAUSE, "PAUSE Media");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Process SEEK media player event.
     */
    private void doSeek(int msec) {
        Log.e(TAG, "seek![" + msec);

        try {
            mUIManager.showControlView(true);
            seek(msec);

            // notify seeking event to sender apps!!!
            mFlintVideo.notifyEvents(FlintVideo.WAITING, "Media VOLUMECHANGED");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Process CHANGE VOLUME media player event.
     */
    private void doChangeVolume() {
        try {
            if (mPlayerEngineImpl != null) {
                double volume = mFlintVideo.getVolume(); // 0.0 ~ 1.0
                Log.e(TAG, "doChangeVolume:volume:" + volume);

                // change system volume?
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (volume > 0) {
                    am.setStreamMute(AudioManager.STREAM_MUSIC, false);
                }

                int maxVolume = am
                        .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                Log.e(TAG, "maxVolume:" + maxVolume);
                am.setStreamVolume(AudioManager.STREAM_MUSIC,
                        (int) (maxVolume * volume),
                        AudioManager.FLAG_PLAY_SOUND
                                | AudioManager.FLAG_SHOW_UI);

                // hard code this
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

                // notify volume changed event to sender apps!!!
                mFlintVideo.notifyEvents(FlintVideo.VOLUMECHANGE,
                        "Media VOLUMECHANGED");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Send CUSTOM MESSAGE events to Sender Apps.
     */
    private void doSendMessage() {
        if (mCustMessageReceiverMessageBus != null && mCustMessage != null) {
            Log.e(TAG, "doSendMessage!" + mCustMessage);

            mCustMessageReceiverMessageBus.send(mCustMessage.toString(), null); // null:
                                                                                // send
                                                                                // to
                                                                                // all.
        }
    }

    /**
     * Process STOP media player event.
     */
    private void doStop() {
        try {
            mPlayerEngineImpl.stop();

            // notify that media player is stopped!
            mFlintVideo.notifyEvents(FlintVideo.ENDED, "Media ENDED");

            // STOP received? treated it as FINISHED!
            mHandler.sendEmptyMessage(PLAYER_MSG_FINISHED);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Do something when video is finished!
     */
    private void doFinished() {
        mMediaLoaded = false;

        mUIManager.showPrepareLoadView(true, true);

        mUIManager.showLoadView(false);
        mUIManager.showControlView(false);
        mUIManager.updateMediaInfoView(mMediaInfo);
    }

    private void loadPlayInfo(String url, String title) {
        removeExitMessage();

        mMediaInfo = new MediaModel();
        mMediaInfo.setUrl(url);
        mMediaInfo.setObjectClass("");
        mMediaInfo.setTitle(title);
        mMediaInfo.setArtist("");
        mMediaInfo.setAlbum("");
        mMediaInfo.setAlbumUri("");

        mUIManager.updateMediaInfoView(mMediaInfo);
        if (isSurfaceCreate) {
            boolean result = mPlayerEngineImpl.playMedia(mMediaInfo);
            if (!result) {
                playFailed();
            }
        } else {
            delayToPlayMedia(mMediaInfo);
        }

        mUIManager.showPrepareLoadView(true, false);
        mUIManager.showLoadView(false);
        mUIManager.showControlView(false);
    }

    /**
     * Play failed.
     */
    private void playFailed() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                Toast.makeText(getApplicationContext(),
                        "Sorry, this video cannot be played!",
                        Toast.LENGTH_SHORT).show();
            }

        });

        // notify sender app that some error happened?
        mFlintVideo.notifyEvents(FlintVideo.ERROR, "Media ERROR");

        mHandler.sendEmptyMessage(PLAYER_MSG_FINISHED);
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        // TODO Auto-generated method stub
        Log.e(TAG, "OnInfoListener:what[" + what + "]extra[" + extra + "]");

        mFlintVideo.setCurrentTime(mp.getCurrentPosition());

        switch (what) {
        case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
            Toast.makeText(FlintVideoActivity.this,
                    "The media cannot be seeked!", Toast.LENGTH_SHORT).show();
            break;

        case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
            Log.e(TAG, "MEDIA_INFO_VIDEO_RENDERING_START!");

            mFlintVideo.notifyEvents(FlintVideo.PLAYING, "Media is PLAYING");
            break;

        case MediaPlayer.MEDIA_INFO_BUFFERING_START:
            mFlintVideo.notifyEvents(FlintVideo.WAITING, "Media is WAITING");
            break;

        case MediaPlayer.MEDIA_INFO_BUFFERING_END:
            if (mPlayerEngineImpl.isPlaying()) {
                mFlintVideo
                        .notifyEvents(FlintVideo.PLAYING, "Media is PLAYING");
            } else {
                Log.e(TAG, "MEDIA_INFO_BUFFERING_END: waiting!!!?!");

                // this should be a workaround for the seek issue of
                // VideoView in PAUSE state.
                mFlintVideo
                        .notifyEvents(FlintVideo.SEEKED, "Media is WAITING?");
                mFlintVideo.notifyEvents(FlintVideo.PAUSE, "Media is PAUSED?");
            }
            break;
        }

        return false;
    }
}
