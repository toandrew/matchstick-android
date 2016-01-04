package com.geniusgithub.mediarender.player;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.geniusgithub.mediarender.util.CommonLog;
import com.geniusgithub.mediarender.util.CommonUtil;
import com.geniusgithub.mediarender.util.LogFactory;

public class VideoPlayEngineImpl extends AbstractMediaPlayEngine {

    private final CommonLog log = LogFactory.createLog();
    private SurfaceHolder mHolder = null;
    private OnBufferingUpdateListener mBufferingUpdateListener;
    private OnSeekCompleteListener mSeekCompleteListener;
    private OnErrorListener mOnErrorListener;
    private OnInfoListener mOnInfoListener;

    private SurfaceHolder mSurfaceHolder;
    private SurfaceView mSurfaceView;
    
    private Context mContext;
    
    DisplayMetrics mDisplayMetrics;
    
    public VideoPlayEngineImpl(Context context, SurfaceHolder holder, SurfaceView surfaceView, DisplayMetrics dm) {
        super(context);

        mContext = context;
        
        mSurfaceView = surfaceView;
        
        mDisplayMetrics = dm;
        
        mSurfaceHolder = holder;
        
        setHolder(holder);
    }

    public void setHolder(SurfaceHolder holder) {
        mHolder = holder;
    }

    public void setOnBuffUpdateListener(OnBufferingUpdateListener listener) {
        mBufferingUpdateListener = listener;
    }

    public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
        mSeekCompleteListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    public void setOnInfoListener(OnInfoListener listener) {
        mOnInfoListener = listener;
    }

    @Override
    protected boolean prepareSelf() {

        mMediaPlayer.reset();

        try {
            mMediaPlayer.setDataSource(mMediaInfo.getUrl());
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            if (mHolder != null) {
                mMediaPlayer.setDisplay(mHolder);
            }

            if (mBufferingUpdateListener != null) {
                mMediaPlayer
                        .setOnBufferingUpdateListener(mBufferingUpdateListener);
            }
            if (mSeekCompleteListener != null) {
                mMediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
            }
            if (mOnErrorListener != null) {
                mMediaPlayer.setOnErrorListener(mOnErrorListener);
            }

            if (mOnInfoListener != null) {
                mMediaPlayer.setOnInfoListener(mOnInfoListener);
            }

            mMediaPlayer
                    .setOnVideoSizeChangedListener(new OnVideoSizeChangedListener() {

                        @Override
                        public void onVideoSizeChanged(MediaPlayer mp,
                                int width, int height) {

                            changeVideoSizes(width, height);
                        }
                    });

            mMediaPlayer.prepareAsync();
            log.e("mMediaPlayer.prepareAsync path = " + mMediaInfo.getUrl());
            mPlayState = PlayState.MPS_PARESYNC;
            performPlayListener(mPlayState);
        } catch (Exception e) {
            e.printStackTrace();
            mPlayState = PlayState.MPS_INVALID;
            performPlayListener(mPlayState);
            return false;
        }

        return true;
    }

    @Override
    protected boolean prepareComplete(MediaPlayer mp) {

        mPlayState = PlayState.MPS_PARECOMPLETE;
        if (mPlayerEngineListener != null) {
            mPlayerEngineListener.onTrackPrepareComplete(mMediaInfo);
        }

        if (mHolder != null) {
            CommonUtil.ViewSize viewSize = CommonUtil.getFitSize(mContext, mp);
            mHolder.setFixedSize(viewSize.width, viewSize.height);
        }

        mMediaPlayer.start();

        mPlayState = PlayState.MPS_PLAYING;
        performPlayListener(mPlayState);

        return true;
    }

    /**
     * Change current video surface's width and height.
     *
     * @param width
     * @param height
     */
    private void changeVideoSizes(int width, int height) {
        if (width == 0 || height == 0) {
            log.e("invalid video width(" + width + ") or height(" + height
                    + ")");
            return;
        }

        int displayWith = mDisplayMetrics.widthPixels;
        int displayHeight = mDisplayMetrics.heightPixels;

        if (width != 0 && height != 0) {
            // LayoutParams params = mSurface.getLayoutParams();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    displayWith, displayHeight);

            if (width * displayHeight > displayWith * height) {
                params.height = displayWith * height / width;
            } else if (width * displayHeight < displayWith * height) {
                params.width = displayHeight * width / height;
            } else {
                params.width = displayWith;
                params.height = displayHeight;
            }

            log.e( "displayWith: " + displayWith + " displayHeight:"
                    + displayHeight + " params.width:" + params.width
                    + " params.height:" + params.height);
            int marginLeft = (displayWith - params.width) / 2;
            int marginTop = (displayHeight - params.height) / 2;

            log.e( "marginLeft:" + marginLeft + " marginTop:" + marginTop);

            params.setMargins(marginLeft, marginTop, marginLeft, marginTop);
            mSurfaceView.setLayoutParams(params);

            mSurfaceHolder.setFixedSize(params.width, params.height);

            // clear background color.
            mSurfaceView.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
