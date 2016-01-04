package nz.co.iswe.android.airplay.music;

import nz.co.iswe.android.airplay.AirPlayServer;
import nz.co.iswe.android.airplay.network.airplay.AirplayMediaController;
import nz.co.iswe.android.airplay.player.AbstractTimer;
import nz.co.iswe.android.airplay.player.CheckDelayTimer;
import nz.co.iswe.android.airplay.player.MediaModel;
import nz.co.iswe.android.airplay.player.SingleSecondTimer;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.example.airplay.util.CommonLog;
import com.example.airplay.util.CommonUtil;
import com.example.airplay.util.DlnaUtils;
import com.example.airplay.util.LogFactory;
import com.infthink.flint.home.R;

public class MusicActivity extends Activity implements AirplayMediaController {

    private static final CommonLog log = LogFactory.createLog();

    private final static int REFRESH_CURPOS = 0x0001;
    private final static int EXIT_ACTIVITY = 0x0003;
    private final static int REFRESH_SPEED = 0x0004;
    private final static int CHECK_DELAY = 0x0005;
    private final static int LOAD_DRAWABLE_COMPLETE = 0x0006;
    private final static int UPDATE_LRC_VIEW = 0x0007;

    private final static int EXIT_DELAY_TIME = 0;

    private UIManager mUIManager;

    private Context mContext;
    
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case REFRESH_CURPOS:
                refreshCurPos();
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
            case LOAD_DRAWABLE_COMPLETE:
                onLoadDrawableComplete();
                break;
            case UPDATE_LRC_VIEW:
                // mUIManager.updateLyricView(mMediaInfo);
                break;
            }
        }

    };

    private AbstractTimer mPlayPosTimer;
    private AbstractTimer mNetWorkTimer;
    private CheckDelayTimer mCheckDelayTimer;

    private boolean isDestroy = false;

    MusicGenaEventBrocastFactory mMusicGenaEventBrocastFactory;

    private float mDuration = 0;
    private float mCurrentPos = 0;

    MediaModel mMediaInfo;

    private boolean isSeekComplete = false;

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
                        Toast.makeText(MusicActivity.this,
                                R.string.wifi_disconnected, Toast.LENGTH_SHORT)
                                .show();
                    }
                }

                return;
            }

            // TODO Auto-generated method stub
            Log.e("MusicActivity", "Ready to call finish!!!");
            finish();
            Log.e("MusicActivity", "End to call finish!!!");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.e("onCreate");

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        setContentView(R.layout.airplay_music_player_layout);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(this);
        if (airPlayServer != null) {
            Log.e("MusicActivity", "onCreate:setAirplayMusicController[" + this + "]");
            airPlayServer.setAirplayMusicController(this);
        }

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
        super.onStop();

        mUIManager.unInit();

        mCheckDelayTimer.stopTimer();
        mNetWorkTimer.stopTimer();
        mPlayPosTimer.stopTimer();

        // finish();
    }

    @Override
    protected void onDestroy() {
        log.e("onDestroy");
        isDestroy = true;

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(this);
        if (airPlayServer != null) {
            airPlayServer.setAirplayMusicController(null);
        }

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow()
                .clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

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

    private void setupsView() {
        mContext = this;
        mUIManager = new UIManager();
    }

    private void initData() {
        mPlayPosTimer = new SingleSecondTimer(this);

        mPlayPosTimer.setHandler(mHandler, REFRESH_CURPOS);

        mNetWorkTimer = new SingleSecondTimer(this);
        mNetWorkTimer.setHandler(mHandler, REFRESH_SPEED);
        mCheckDelayTimer = new CheckDelayTimer(this);
        mCheckDelayTimer.setHandler(mHandler, CHECK_DELAY);

        mNetWorkTimer.startTimer();
        mCheckDelayTimer.startTimer();

        mMusicGenaEventBrocastFactory = new MusicGenaEventBrocastFactory(this);
    }

    private void refreshIntent(Intent intent) {
        log.e("refreshIntent");

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(this);
        if (airPlayServer != null) {
            Log.e("MusicActivity", "refreshIntent:setAirplayMusicController[" + this + "]");
            airPlayServer.setAirplayMusicController(this);
        }

        removeExitMessage();

        mHandler.sendEmptyMessageDelayed(LOAD_DRAWABLE_COMPLETE, 1000);

        mUIManager.showPrepareLoadView(true);
        mUIManager.showLoadView(false);
        mUIManager.showControlView(false);
    }

    private void removeExitMessage() {
        mHandler.removeMessages(LOAD_DRAWABLE_COMPLETE);
        mHandler.removeMessages(EXIT_ACTIVITY);
    }

    private void delayToExit() {
        log.e("delayToExit");
        removeExitMessage();
        mHandler.sendEmptyMessageDelayed(EXIT_ACTIVITY, EXIT_DELAY_TIME);
    }

    private void play() {
        Log.e("MusicActivity", "play!");
        
        int duration = (int) getDuration();
        mMusicGenaEventBrocastFactory.sendDurationEvent(mContext, duration);
        mUIManager.setSeekbarMax(duration);
        mUIManager.setTotalTime(duration);

        mPlayPosTimer.startTimer();
        mMusicGenaEventBrocastFactory.sendPlayStateEvent(mContext);
        mUIManager.showPlay(false);
        mUIManager.showPrepareLoadView(false);
        mUIManager.showControlView(true);
    }

    private void pause() {
        mPlayPosTimer.stopTimer();
        mMusicGenaEventBrocastFactory.sendPauseStateEvent(mContext);
        mUIManager.showPlay(true);
    }

    private void stop() {
        mPlayPosTimer.stopTimer();
        mMusicGenaEventBrocastFactory.sendStopStateEvent(mContext);
        mUIManager.showPlay(true);
        mUIManager.updateMediaInfoView(mMediaInfo);
        mUIManager.showLoadView(false);
        isSeekComplete = true;
        delayToExit();
    }

    private void refreshCurPos() {
        int duration = (int) getDuration();
        mUIManager.setSeekbarMax(duration);
        mUIManager.setTotalTime(duration);

        int pos = (int) getPosition();

        mUIManager.setSeekbarProgress(pos);
        mMusicGenaEventBrocastFactory.sendSeekEvent(mContext, pos);

        mCurrentPos += 1 * 1000; // 1s
        if (mCurrentPos >= mDuration) {
            mCurrentPos = mDuration;
        }
    }

    private void refreshSpeed() {
        if (mUIManager.isLoadViewShow()) {
            float speed = CommonUtil.getSysNetworkDownloadSpeed();
            mUIManager.setSpeed(speed);
        }
    }

    private void checkDelay() {
        int pos = (int) getPosition();

        boolean ret = mCheckDelayTimer.isDelay(pos);
        if (ret) {
            mUIManager.showLoadView(true);
        } else {
            mUIManager.showLoadView(false);
        }

        mCheckDelayTimer.setPos(pos);
    }

    private Bitmap decodeOptionsFile(byte[] data) {
        try {
            // File file = new File(filePath);
            // BitmapFactory.Options o = new BitmapFactory.Options();
            // o.inJustDecodeBounds = true;
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private void onLoadDrawableComplete() {
        log.e("onLoadDrawableComplete!");

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(this);
        byte data[] = airPlayServer.getCurrentImageData();

        if (isDestroy || data == null) {
            Drawable drawable = mContext.getResources().getDrawable(
                    R.drawable.mp_music_default);
            mUIManager.updateAlbumPIC(drawable);
            return;
        }

        log.e("onLoadDrawableComplete! 1!len[" + data.length + "]");
        Bitmap bitmap = decodeOptionsFile(data);
        if (bitmap == null) {
            return;
        }
        log.e("onLoadDrawableComplete! 2!");
        mUIManager.updateAlbumPIC(bitmap);
    }

    private void seek(int pos) {
        isSeekComplete = false;
        mUIManager.setSeekbarProgress(pos);
    }

    @Override
    public void onPlayCommand() {
        log.e("onPlayCommand!");
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
        log.e("onPauseCommand!");
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
        log.e("onStopCommand!");
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                stop();
            }

        });
    }

    @Override
    public void onSeekCommand(final float time) {
        log.e("onSeekCmd time = " + time);
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                seek((int) time);
            }

        });
    }

    /*---------------------------------------------------------------------------*/
    class UIManager implements OnClickListener, OnSeekBarChangeListener {

        public View mPrepareView;
        public TextView mTVPrepareSpeed;

        public View mLoadView;
        public TextView mTVLoadSpeed;

        public View mControlView;
        public TextView mTVSongName;
        public TextView mTVArtist;
        public TextView mTVAlbum;

        public ImageButton mBtnPlay;
        public ImageButton mBtnPause;
        public SeekBar mSeekBar;
        public TextView mTVCurTime;
        public TextView mTVTotalTime;

        public ImageView mIVAlbum;

        public TranslateAnimation mHideDownTransformation;
        public AlphaAnimation mAlphaHideTransformation;

        public View mSongInfoView;

        public boolean lrcShow = false;

        public boolean mIsScalBitmap = false;

        public UIManager() {
            initView();
        }

        public void initView() {
            mPrepareView = findViewById(R.id.prepare_panel);
            mTVPrepareSpeed = (TextView) findViewById(R.id.tv_prepare_speed);

            mLoadView = findViewById(R.id.loading_panel);
            mTVLoadSpeed = (TextView) findViewById(R.id.tv_speed);

            mControlView = findViewById(R.id.control_panel);
            mTVSongName = (TextView) findViewById(R.id.tv_title);
            mTVArtist = (TextView) findViewById(R.id.tv_artist);
            mTVAlbum = (TextView) findViewById(R.id.tv_album);

            mBtnPlay = (ImageButton) findViewById(R.id.btn_play);
            mBtnPause = (ImageButton) findViewById(R.id.btn_pause);
            mBtnPlay.setOnClickListener(this);
            mBtnPause.setOnClickListener(this);
            mSeekBar = (SeekBar) findViewById(R.id.playback_seeker);
            mTVCurTime = (TextView) findViewById(R.id.tv_curTime);
            mTVTotalTime = (TextView) findViewById(R.id.tv_totalTime);
            mIVAlbum = (ImageView) findViewById(R.id.iv_album);
            setSeekbarListener(this);

            mSongInfoView = findViewById(R.id.song_info_view);

            mHideDownTransformation = new TranslateAnimation(0.0f, 0.0f, 0.0f,
                    200.0f);
            mHideDownTransformation.setDuration(1000);

            mAlphaHideTransformation = new AlphaAnimation(1, 0);
            mAlphaHideTransformation.setDuration(1000);

            updateAlbumPIC(getResources().getDrawable(
                    R.drawable.mp_music_default));
        }

        public void unInit() {

        }

        public void updateAlbumPIC(Drawable drawable) {
            Bitmap img = ImageUtils
                    .createRotateReflectedMap(mContext, drawable);
            if (img != null) {
                mIVAlbum.setImageBitmap(img);
            }
        }

        public void updateAlbumPIC(Bitmap bitmap) {
            if (bitmap != null) {
                mIVAlbum.setImageBitmap(bitmap);
            }
        }

        public void showPrepareLoadView(boolean isShow) {
            if (isShow) {
                mPrepareView.setVisibility(View.VISIBLE);
            } else {
                mPrepareView.setVisibility(View.GONE);
            }
        }

        public void showControlView(boolean show) {
            if (show) {
                mControlView.setVisibility(View.VISIBLE);
            } else {
                mControlView.setVisibility(View.GONE);
            }
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
        }

        public void showPlay(boolean bShow) {
            Log.e("MusicActivity!", "showPlay[" + bShow + "]");
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

            if (mediaInfo != null) {
                mTVSongName.setText(mediaInfo.getTitle());
                mTVArtist.setText(mediaInfo.getArtist());
                mTVAlbum.setText(mediaInfo.getAlbum());
            }
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
            return mControlView.getVisibility() == View.VISIBLE ? true : false;
        }

        public boolean isLoadViewShow() {
            if (mLoadView.getVisibility() == View.VISIBLE
                    || mPrepareView.getVisibility() == View.VISIBLE) {
                return true;
            }

            return false;
        }

        public void showPlayErrorTip() {
        }
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
        return mCurrentPos;
    }

    @Override
    public float getDuration() {
        // TODO Auto-generated method stub
        return mDuration;
    }

    @Override
    public boolean isPlaying() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onUpdateImageCommand() {
        // TODO Auto-generated method stub

        mHandler.sendEmptyMessageDelayed(LOAD_DRAWABLE_COMPLETE, 1000);
    }

    @Override
    public void onSetPosCommand(float time) {
        // TODO Auto-generated method stub
        log.e("onSetPosCommand: " + time);
        mCurrentPos = time * 1000;

        // playing????
        onPlayCommand();
    }

    @Override
    public void onSetDurationCommand(float duration) {
        // TODO Auto-generated method stub
        log.e("onSetDurationCommand: " + duration);

        mDuration = duration * 1000;
    }
}
