package com.geniusgithub.mediarender.video;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnErrorListener;
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
import android.view.KeyEvent;
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

import com.geniusgithub.mediarender.BaseActivity;
import com.geniusgithub.mediarender.center.DLNAGenaEventBrocastFactory;
import com.geniusgithub.mediarender.center.DlnaMediaModel;
import com.geniusgithub.mediarender.center.DlnaMediaModelFactory;
import com.geniusgithub.mediarender.center.MediaControlBrocastFactory;
import com.geniusgithub.mediarender.player.AbstractTimer;
import com.geniusgithub.mediarender.player.CheckDelayTimer;
import com.geniusgithub.mediarender.player.PlayerEngineListener;
import com.geniusgithub.mediarender.player.SingleSecondTimer;
import com.geniusgithub.mediarender.player.VideoPlayEngineImpl;
import com.geniusgithub.mediarender.util.CommonLog;
import com.geniusgithub.mediarender.util.CommonUtil;
import com.geniusgithub.mediarender.util.DlnaUtils;
import com.geniusgithub.mediarender.util.LogFactory;
import com.infthink.flint.home.R;

public class VideoActivity extends BaseActivity implements
        MediaControlBrocastFactory.IMediaControlListener,
        OnBufferingUpdateListener, OnSeekCompleteListener, OnErrorListener {

    private static final CommonLog log = LogFactory.createLog();
    private final static int REFRESH_CURPOS = 0x0001;
    private final static int HIDE_TOOL = 0x0002;
    private final static int EXIT_ACTIVITY = 0x0003;
    private final static int REFRESH_SPEED = 0x0004;
    private final static int CHECK_DELAY = 0x0005;

    private final static int EXIT_DELAY_TIME = 2000;
    private final static int HIDE_DELAY_TIME = 3000;

    private static final int PLAYER_MSG_FINISHED = 80;

    private static final int HIDE_NAV_BAR = 90;

    private UIManager mUIManager;
    private VideoPlayEngineImpl mPlayerEngineImpl;
    private VideoPlayEngineListener mPlayEngineListener;
    private MediaControlBrocastFactory mMediaControlBorcastFactory;

    private Context mContext;
    private DlnaMediaModel mMediaInfo = new DlnaMediaModel();
    private Handler mHandler;

    private AbstractTimer mPlayPosTimer;
    private AbstractTimer mNetWorkTimer;
    private CheckDelayTimer mCheckDelayTimer;

    private boolean isSurfaceCreate = false;
    private boolean isDestroy = false;

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

        refreshIntent(getIntent());

        registerVolumeReceiver();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        log.e("onNewIntent");
        refreshIntent(intent);

        super.onNewIntent(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();

        onDestroy();
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow()
                .clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        log.e("onDestroy");
        isDestroy = true;
        mUIManager.unInit();
        mCheckDelayTimer.stopTimer();
        mNetWorkTimer.stopTimer();
        mMediaControlBorcastFactory.unregister();
        mPlayPosTimer.stopTimer();
        mPlayerEngineImpl.exit();

        unRegisterVolumeReceiver();

        super.onDestroy();

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        log.e(" onKeyDown " + keyCode );

        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                preSeekPosition(true);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                preSeekPosition(false);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (mPlayerEngineImpl.isPause()) {
                    play();
                } else {
                    pause();
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                setVolume(true);
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                setVolume(false);
                break;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        log.e(" onKeyUp " +  keyCode);

        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                seekPosition(true);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seekPosition(false);
                break;
        }

        mSeekTime = 0;

        return super.onKeyUp(keyCode, event);
    }

    private int mSeekTime = 0;
    private void preSeekPosition(boolean left) {
        pause();

        if (!isSeekComplete) {
            return;
        }
        int totalTime = mPlayerEngineImpl.getDuration();
        if (totalTime > 0) {
            int step = 5 * 1000; // 5s
            if (left) {
                step = -step;
            }
            if (mSeekTime == 0) {
                mSeekTime = mPlayerEngineImpl.getCurPosition();
            }
            mSeekTime += step;
            if (mSeekTime >= 0 && mSeekTime <= totalTime) {
                log.e("onKey: seekPosition: mSeekTime[" + mSeekTime + "]totalTime[" + totalTime + "]pos[" + mSeekTime + "]");
                mUIManager.showControlView(true);
                mUIManager.setSeekbarProgress(mSeekTime);
            }
        }
    }

    private void seekPosition(boolean left) {
        play();
        if (mSeekTime > 0 && mSeekTime <= mPlayerEngineImpl.getDuration()) {
            onSeekCommand(mSeekTime);
        }
    }

    private void setVolume(boolean up) {
        try {
            AudioManager am = (AudioManager) this
                    .getSystemService(Context.AUDIO_SERVICE);
            int currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxvolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

            int delta =  1;
            if (!up) {
                delta = -delta;
            }
            int percent = (currentVolume + delta) * 100 / maxvolume;

            log.e("onKey: setVolume currentVolume[" + currentVolume + "]maxvolume[" + maxvolume + "]percent[" + percent + "]");

            if(percent < 101 & percent >= 0){
                CommonUtil.setCurrentVolume(percent, this);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setupsView() {
        mContext = this;
        mUIManager = new UIManager();
    }

    public void initData() {
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

                case PLAYER_MSG_FINISHED:
                    doFinished();
                    break;

                case HIDE_NAV_BAR:
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
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
        mPlayEngineListener = new VideoPlayEngineListener();
        mPlayerEngineImpl.setPlayerListener(mPlayEngineListener);
        mPlayerEngineImpl.setOnErrorListener(this);

        mMediaControlBorcastFactory = new MediaControlBrocastFactory(mContext);
        mMediaControlBorcastFactory.register(this);

        mNetWorkTimer.startTimer();
        mCheckDelayTimer.startTimer();

    }

    private void refreshIntent(Intent intent) {
        removeExitMessage();
        if (intent != null) {
            mMediaInfo = DlnaMediaModelFactory.createFromIntent(intent);
        }

        mUIManager.updateMediaInfoView(mMediaInfo);
        if (isSurfaceCreate) {
            boolean result = mPlayerEngineImpl.playMedia(mMediaInfo);
            if (!result) {
                playFailed();
            }
        } else {
            delayToPlayMedia(mMediaInfo);
        }

        mUIManager.showPrepareLoadView(true, true);
        mUIManager.showLoadView(false);
        mUIManager.showControlView(false);
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {

        int action = ev.getAction();
        int actionIdx = ev.getActionIndex();
        int actionMask = ev.getActionMasked();

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

    private void delayToPlayMedia(final DlnaMediaModel mMediaInfo) {

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

    public void play() {
        mPlayerEngineImpl.play();
    }

    public void pause() {
        mPlayerEngineImpl.pause();
    }

    public void stop() {
        mPlayerEngineImpl.stop();
    }

    public void refreshCurPos() {
        int pos = mPlayerEngineImpl.getCurPosition();

        mUIManager.setSeekbarProgress(pos);
        DLNAGenaEventBrocastFactory.sendSeekEvent(mContext, pos);
    }

    public void refreshSpeed() {
        if (mUIManager.isLoadViewShow()) {
            float speed = CommonUtil.getSysNetworkDownloadSpeed();
            mUIManager.setSpeed(speed);
        }
    }

    public void checkDelay() {
        int pos = mPlayerEngineImpl.getCurPosition();

        boolean ret = mCheckDelayTimer.isDelay(pos);
        if (ret) {
            mUIManager.showLoadView(true);
        } else {
            mUIManager.showLoadView(false);
        }

        mCheckDelayTimer.setPos(pos);

    }

    public void seek(int pos) {
        isSeekComplete = false;
        mPlayerEngineImpl.skipTo(pos);
        mUIManager.setSeekbarProgress(pos);

    }

    private class VideoPlayEngineListener implements PlayerEngineListener {

        @Override
        public void onTrackPlay(DlnaMediaModel itemInfo) {

            mPlayPosTimer.startTimer();
            DLNAGenaEventBrocastFactory.sendPlayStateEvent(mContext);
            mUIManager.showPlay(false);
            mUIManager.showControlView(true);
        }

        @Override
        public void onTrackStop(DlnaMediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            DLNAGenaEventBrocastFactory.sendStopStateEvent(mContext);
            mUIManager.showPlay(true);
            mUIManager.updateMediaInfoView(mMediaInfo);
            mUIManager.showControlView(true);
            mUIManager.showLoadView(false);
            isSeekComplete = true;

            // STOP received? treated it as FINISHED!
            mHandler.sendEmptyMessage(PLAYER_MSG_FINISHED);

            delayToExit();
        }

        @Override
        public void onTrackPause(DlnaMediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            DLNAGenaEventBrocastFactory.sendPauseStateEvent(mContext);
            mUIManager.showPlay(true);
            mUIManager.showControlView();
        }

        @Override
        public void onTrackPrepareSync(DlnaMediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            DLNAGenaEventBrocastFactory.sendTranstionEvent(mContext);

            mUIManager.showPrepareLoadView(true, false);
            mUIManager.showLoadView(false);
            mUIManager.showControlView(false);
        }

        @Override
        public void onTrackPrepareComplete(DlnaMediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            int duration = mPlayerEngineImpl.getDuration();
            DLNAGenaEventBrocastFactory.sendDurationEvent(mContext, duration);
            mUIManager.setSeekbarMax(duration);
            mUIManager.setTotalTime(duration);

            mUIManager.showPrepareLoadView(false, false);

        }

        @Override
        public void onTrackStreamError(DlnaMediaModel itemInfo) {
            log.e("onTrackStreamError");
            mPlayPosTimer.stopTimer();
            mPlayerEngineImpl.stop();
            mUIManager.showPlayErrorTip();

            // STOP received? treated it as FINISHED!
            mHandler.sendEmptyMessage(PLAYER_MSG_FINISHED);
        }

        @Override
        public void onTrackPlayComplete(DlnaMediaModel itemInfo) {
            log.e("onTrackPlayComplete");
            mPlayerEngineImpl.stop();
        }

    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        // log.e("onBufferingUpdate --> percen = " + percent + ", curPos = " +
        // mp.getCurrentPosition());

        int duration = mPlayerEngineImpl.getDuration();
        int time = duration * percent / 100;
        mUIManager.setSeekbarSecondProgress(time);
    }

    private boolean isSeekComplete = true;

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        isSeekComplete = true;
        log.e("onSeekComplete ...");
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mUIManager.showPlayErrorTip();
        log.e("onError what = " + what + ", extra = " + extra);

        mHandler.sendEmptyMessage(PLAYER_MSG_FINISHED);

        return false;
    }

    @Override
    public void onPlayCommand() {
        play();
    }

    @Override
    public void onPauseCommand() {
        pause();
    }

    @Override
    public void onStopCommand() {
        stop();
    }

    @Override
    public void onSeekCommand(int time) {
        log.e("onKey: onSeekCommand[" + time + "]total[" + mPlayerEngineImpl.getDuration() + "]current[" + mPlayerEngineImpl.getCurPosition() + "]");
        mUIManager.showControlView(true);
        seek(time);
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

        public void initView() {

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

        public void unInit() {

        }

        public void showPrepareLoadView(boolean isShow, boolean finished) {
            if (isShow) {
                Log.e("VideoActivity", "Black showPrepareLoadView:" + isShow);

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
                Log.e("VideoActivity", "TRANSPARENT showPrepareLoadView:"
                        + isShow);
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
                play();
                break;
            case R.id.btn_pause:
                pause();
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
                play();
            } else {
                pause();
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

        public void updateMediaInfoView(DlnaMediaModel mediaInfo) {
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
            Toast.makeText(VideoActivity.this, R.string.toast_videoplay_fail,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Do something when video is finished!
     */
    private void doFinished() {
        mUIManager.showPrepareLoadView(true, true);

        mUIManager.showLoadView(false);
        mUIManager.showControlView(false);
        mUIManager.updateMediaInfoView(mMediaInfo);
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

        mHandler.sendEmptyMessage(PLAYER_MSG_FINISHED);
    }

    MyVolumeReceiver mVolumeReceiver = null;

    /**
     * register volume change receiver
     */
    private void registerVolumeReceiver() {
        mVolumeReceiver = new MyVolumeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mVolumeReceiver, filter);
    }

    /**
     * unregister volume change receiver
     */
    private void unRegisterVolumeReceiver() {
        if (mVolumeReceiver != null) {
            try {
                unregisterReceiver(mVolumeReceiver);
                mVolumeReceiver = null;
            } catch (Exception e) {
            }
        }
    }

    /**
     * when volume changed, hide nav bar
     * 
     * @author long
     */
    private class MyVolumeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            log.e("MyVolumeReceiver![" + intent.getAction() + "]");
            if (intent.getAction()
                    .equals("android.media.VOLUME_CHANGED_ACTION")) {

                // hide nav bar!
                mHandler.sendEmptyMessageDelayed(HIDE_NAV_BAR, 100);
            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent
                    .getAction())) {
                Parcelable parcelableExtra = intent
                        .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (null != parcelableExtra) {
                    NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;
                    State state = networkInfo.getState();
                    log.e("wifi state = " + state);
                    if (state == State.DISCONNECTED) {
                        Toast.makeText(VideoActivity.this,
                                R.string.wifi_disconnected, Toast.LENGTH_SHORT)
                                .show();
                    }
                }
            }
        }
    }
}
