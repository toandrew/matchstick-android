package nz.co.iswe.android.airplay.image;

import nz.co.iswe.android.airplay.AirPlayServer;
import nz.co.iswe.android.airplay.network.airplay.AirplayImageController;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.airplay.util.CommonLog;
import com.example.airplay.util.CommonUtil;
import com.example.airplay.util.LogFactory;
import com.infthink.flint.home.R;

public class ImageActivity extends Activity implements AirplayImageController {

    private static final CommonLog log = LogFactory.createLog();

    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    private Handler mHandler;
    private UIManager mUIManager;

    private static final int EXIT_DELAY_TIME = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.e("onCreate");

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);  
        
        setContentView(R.layout.image_player_layout);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        initView();
        initData();

        refreshIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        log.e("onDestroy");

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow()
                .clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(this);
        if (airPlayServer != null) {
            airPlayServer.setAirplayImageController(null);
        }

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        log.e("onNewIntent!");

        refreshIntent(intent);
    }

    private void initView() {
        mUIManager = new UIManager();
    }

    private static final int EXIT_ACTIVITY = 0x0002;

    private void initData() {
        mScreenWidth = CommonUtil.getScreenWidth(this);
        mScreenHeight = CommonUtil.getScreenHeight(this);

        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case EXIT_ACTIVITY:
                    finish();
                    break;
                }
            }
        };
    }

    private void refreshIntent(Intent intent) {
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(this);
        if (airPlayServer != null) {
            airPlayServer.setAirplayImageController(this);
        }
        
        removeExitMessage();

        mUIManager.showProgress(true);

        onTransDelLoadResult(true, null);
    }

    private void removeExitMessage() {
        mHandler.removeMessages(EXIT_ACTIVITY);
    }

    private void delayToExit() {
        removeExitMessage();
        mHandler.sendEmptyMessageDelayed(EXIT_ACTIVITY, EXIT_DELAY_TIME);
    }

    class UIManager {
        public ImageView mImageView;
        public View mLoadView;

        public Bitmap recycleBitmap;
        public boolean mIsScalBitmap = false;

        public UIManager() {
            initView();
        }

        private void initView() {
            mImageView = (ImageView) findViewById(R.id.imageview);
            mLoadView = findViewById(R.id.show_load_progress);
        }

        public void setBitmap(Bitmap bitmap) {
            if (recycleBitmap != null && !recycleBitmap.isRecycled()) {
                mImageView.setImageBitmap(null);
                recycleBitmap.recycle();
                recycleBitmap = null;
            }

            if (mIsScalBitmap) {
                mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                mImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            }

            recycleBitmap = bitmap;
            mImageView.setImageBitmap(recycleBitmap);
        }

        public boolean isLoadViewShow() {
            if (mLoadView.getVisibility() == View.VISIBLE) {
                return true;
            }

            return false;
        }

        public void showProgress(boolean bShow) {
            if (bShow) {
                mLoadView.setVisibility(View.VISIBLE);
            } else {
                mLoadView.setVisibility(View.GONE);
            }
        }

        public void showLoadFailTip() {
            showToask(R.string.load_image_fail);
        }

        public void showParseFailTip() {
            showToask(R.string.parse_image_fail);
        }

        private void showToask(int tip) {
            Toast.makeText(ImageActivity.this, tip, Toast.LENGTH_SHORT).show();
        }
    }

    private void onTransDelLoadResult(final boolean isSuccess,
            final String savePath) {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mUIManager.showProgress(false);

                if (!isSuccess) {
                    mUIManager.showLoadFailTip();
                    return;
                }

                final AirPlayServer airPlayServer = AirPlayServer
                        .getIstance(ImageActivity.this);
                if (airPlayServer != null) {
                    final byte[] data = airPlayServer.getCurrentImageData();
                    if (data != null) {
                        Bitmap bitmap = decodeOptionsFile(data);
                        if (bitmap == null) {
                            mUIManager.showParseFailTip();
                            return;
                        }

                        mUIManager.setBitmap(bitmap);
                    }
                }
            }
        });
    }

    public Bitmap decodeOptionsFile(byte[] data) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int width_tmp = o.outWidth, height_tmp = o.outHeight;
            int scale = 1;
            if (width_tmp <= mScreenWidth && height_tmp <= mScreenHeight) {
                scale = 1;
                mUIManager.mIsScalBitmap = false;
            } else {
                double widthFit = width_tmp * 1.0 / mScreenWidth;
                double heightFit = height_tmp * 1.0 / mScreenHeight;
                double fit = widthFit > heightFit ? widthFit : heightFit;
                scale = (int) (fit + 0.5);
                mUIManager.mIsScalBitmap = true;
            }
            Bitmap bitmap = null;
            if (scale == 1) {
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bitmap != null) {
                    log.e("scale = 1 bitmap.size = " + bitmap.getRowBytes()
                            * bitmap.getHeight());
                }
            } else {
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = scale;
                bitmap = BitmapFactory
                        .decodeByteArray(data, 0, data.length, o2);
                if (bitmap != null) {
                    log.e("scale = " + o2.inSampleSize + " bitmap.size = "
                            + bitmap.getRowBytes() * bitmap.getHeight());
                }
            }

            return bitmap;

        } catch (Exception e) {
            log.e("e: " + e.toString());

        }
        return null;
    }

    @Override
    public void onShowImage(byte[] bytes) {
        // TODO Auto-generated method stub

        log.e("onShowImage");
    }

    @Override
    public void onStopImage() {
        // TODO Auto-generated method stub
        log.e("onStopImage");

        delayToExit();
    }

}
