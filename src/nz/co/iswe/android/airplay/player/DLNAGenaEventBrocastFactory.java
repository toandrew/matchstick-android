package nz.co.iswe.android.airplay.player;

import nz.co.iswe.android.airplay.AirPlayServer;
import nz.co.iswe.android.airplay.network.airplay.AirplayVideoInfoListener;
import android.content.Context;

import com.example.airplay.util.CommonLog;
import com.example.airplay.util.LogFactory;

public class DLNAGenaEventBrocastFactory {

    private static final CommonLog log = LogFactory.createLog();

    private Context mContext;

    public DLNAGenaEventBrocastFactory(Context context) {
        mContext = context;
    }

    public void sendTranstionEvent(Context context) {
        log.e("sendTranstionEvent");
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(mContext);
        AirplayVideoInfoListener listener = airPlayServer
                .getAirplayVideoInfoListener();
        if (listener != null) {
            log.e("sendTranstionEvent:onPause");
            listener.onPause();
        }
    }

    public void sendDurationEvent(Context context, int duration) {
        log.e("sendDurationEvent");
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(mContext);
        AirplayVideoInfoListener listener = airPlayServer
                .getAirplayVideoInfoListener();
        if (listener != null) {
            log.e("sendDurationEvent:onDuration");
            listener.onDuration(duration);
        }
    }

    public void sendSeekEvent(Context context, int time) {
        log.e("sendSeekEvent");
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(mContext);
        AirplayVideoInfoListener listener = airPlayServer
                .getAirplayVideoInfoListener();
        if (listener != null) {
            log.e("sendSeekEvent:onSeek");
            listener.onSeek(time);
        }
    }

    public void sendPlayStateEvent(Context context) {
        log.e("sendPlayStateEvent");
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(mContext);
        AirplayVideoInfoListener listener = airPlayServer
                .getAirplayVideoInfoListener();
        if (listener != null) {
            log.e("sendPlayStateEvent:onPlay");
            listener.onPlay();
        }
    }

    public void sendPauseStateEvent(Context context) {
        log.e("sendPauseStateEvent");
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(mContext);
        AirplayVideoInfoListener listener = airPlayServer
                .getAirplayVideoInfoListener();
        if (listener != null) {
            log.e("sendPauseStateEvent:onPause");
            listener.onPause();
        }
    }

    public void sendStopStateEvent(Context context) {
        log.e("sendStopStateEvent");
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(mContext);
        AirplayVideoInfoListener listener = airPlayServer
                .getAirplayVideoInfoListener();
        if (listener != null) {
            log.e("sendStopStateEvent:onStop");
            listener.onStop();
        }
    }
}
