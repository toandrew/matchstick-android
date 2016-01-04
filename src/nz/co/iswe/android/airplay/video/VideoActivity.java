package nz.co.iswe.android.airplay.video;

import nz.co.iswe.android.airplay.AirPlayServer;
import nz.co.iswe.android.airplay.network.airplay.AirplayMediaController;
import nz.co.iswe.android.airplay.player.AbstractTimer;
import nz.co.iswe.android.airplay.player.CheckDelayTimer;
import nz.co.iswe.android.airplay.player.DLNAGenaEventBrocastFactory;
import nz.co.iswe.android.airplay.player.MediaModel;
import nz.co.iswe.android.airplay.player.PlayerEngineListener;
import nz.co.iswe.android.airplay.player.SingleSecondTimer;
import nz.co.iswe.android.airplay.player.VideoPlayEngineImpl;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
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

import com.example.airplay.util.CommonLog;
import com.example.airplay.util.CommonUtil;
import com.example.airplay.util.DlnaMediaModelFactory;
import com.example.airplay.util.DlnaUtils;
import com.example.airplay.util.LogFactory;
import com.infthink.flint.home.R;

public class VideoActivity extends Activity implements AirplayMediaController,
        OnBufferingUpdateListener, OnSeekCompleteListener, OnErrorListener {

    private static final CommonLog log = LogFactory.createLog();
    private final static int REFRESH_CURPOS = 0x0001;
    private final static int HIDE_TOOL = 0x0002;
    private final static int EXIT_ACTIVITY = 0x0003;
    private final static int REFRESH_SPEED = 0x0004;
    private final static int CHECK_DELAY = 0x0005;

    private final static int EXIT_DELAY_TIME = 0;
    private final static int HIDE_DELAY_TIME = 3000;

    private static final int PLAYER_MSG_FINISHED = 80;

    private UIManager mUIManager;
    private VideoPlayEngineImpl mPlayerEngineImpl;
    private VideoPlayEngineListener mPlayEngineListener;

    private Context mContext;
    private MediaModel mMediaInfo = new MediaModel();
    private Handler mHandler;

    private AbstractTimer mPlayPosTimer;
    private AbstractTimer mNetWorkTimer;
    private CheckDelayTimer mCheckDelayTimer;

    private boolean isSurfaceCreate = false;
    private boolean isDestroy = false;

    DLNAGenaEventBrocastFactory mDLNAGenaEventBrocastFactory;
    
    private static final String STOP_RECEIVER_CMD = "fling.action.stop_receiver";


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
                    Log.d("MusicActivity", "state = " + state);
                    if (state == State.DISCONNECTED) {
                        Toast.makeText(VideoActivity.this, R.string.wifi_disconnected, Toast.LENGTH_SHORT).show();
                    }
                }

                return;
            }

            // TODO Auto-generated method stub
            Log.e("VideoActivity", "Ready to call finish!!!");
            finish();
            Log.e("VideoActivity", "End to call finish!!!");
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.e("onCreate");

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);  
        
        setContentView(R.layout.video_player_layout);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(this);
        airPlayServer.setAirplayVideoController(this);
        
        setupsView();
        initData();

        refreshIntent(getIntent());
        
        // register flint stop receiver.
        IntentFilter filter = new IntentFilter(STOP_RECEIVER_CMD);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mFlintFinishReceiver, filter);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        log.e("onNewIntent");
        refreshIntent(intent);

        super.onNewIntent(intent);
    }
    
    @Override
    protected void onStop() {
        log.e("onStop!");
        super.onStop();

        onDestroy();
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow()
                .clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(this);
        airPlayServer.setAirplayVideoController(null);

        log.e("onDestroy");
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

        super.onDestroy();
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
        
        mNetWorkTimer.startTimer();
        mCheckDelayTimer.startTimer();

        mDLNAGenaEventBrocastFactory = new DLNAGenaEventBrocastFactory(this);
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
        
        
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(this);
        airPlayServer.setAirplayVideoController(this);
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
        }, 0);
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
        mDLNAGenaEventBrocastFactory.sendSeekEvent(mContext, pos);
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
        public void onTrackPlay(MediaModel itemInfo) {

            mPlayPosTimer.startTimer();
            mDLNAGenaEventBrocastFactory.sendPlayStateEvent(mContext);
            mUIManager.showPlay(false);
            mUIManager.showControlView(true);
        }

        @Override
        public void onTrackStop(MediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            mDLNAGenaEventBrocastFactory.sendStopStateEvent(mContext);
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
        public void onTrackPause(MediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            mDLNAGenaEventBrocastFactory.sendPauseStateEvent(mContext);
            mUIManager.showPlay(true);
            mUIManager.showControlView();
        }

        @Override
        public void onTrackPrepareSync(MediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            mDLNAGenaEventBrocastFactory.sendTranstionEvent(mContext);

            mUIManager.showPrepareLoadView(true, false);
            mUIManager.showLoadView(false);
            mUIManager.showControlView(false);
        }

        @Override
        public void onTrackPrepareComplete(MediaModel itemInfo) {

            mPlayPosTimer.stopTimer();
            int duration = mPlayerEngineImpl.getDuration();
            mDLNAGenaEventBrocastFactory.sendDurationEvent(mContext, duration);
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
        // log.e("onBufferingUpdate --> percen = " + percent + ", curPos = " +
        // mp.getCurrentPosition());

        int duration = mPlayerEngineImpl.getDuration();
        int time = duration * percent / 100;
        mUIManager.setSeekbarSecondProgress(time);
    }

    private boolean isSeekComplete = false;

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

    @Override
    public void onPlayCommand() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                play();
            }

        });
    }

    @Override
    public void onPauseCommand() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                pause();
            }

        });
    }

    @Override
    public void onStopCommand() {
        Log.e("Video", "onStopCommand!");
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                
                //delayToExit();
                
                stop();
            }

        });
    }

    @Override
    public void onSeekCommand(final float time) {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                mUIManager.showControlView(true);
                seek((int) time);
            }

        });

    }

    @Override
    public void onChangeVolumeCommand(double volume) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onRewindCommand() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onFastForwardCommand() {
        // TODO Auto-generated method stub

    }

    @Override
    public float getPosition() {
        // TODO Auto-generated method stub

        if (mPlayerEngineImpl != null) {
            return mPlayerEngineImpl.getCurPosition() / 1000f;
        }

        return 0;
    }

    @Override
    public float getDuration() {
        // TODO Auto-generated method stub

        if (mPlayerEngineImpl != null) {
            return mPlayerEngineImpl.getDuration() / 1000f;
        }

        return 0;
    }

    @Override
    public boolean isPlaying() {
        // TODO Auto-generated method stub
        
        if (mPlayerEngineImpl != null) {
            return mPlayerEngineImpl.isPlaying();
        }

        return false;
    }

    @Override
    public void onUpdateImageCommand() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onSetPosCommand(float time) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onSetDurationCommand(float duration) {
        // TODO Auto-generated method stub
        
    }
}
