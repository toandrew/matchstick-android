package nz.co.iswe.android.airplay.network.airplay;

public interface AirplayVideoInfoListener {
    public void onSeek(int time);

    public void onPlay();

    public void onPause();

    public void onStop();

    public void onPrepare();

    public void onDuration(int duration);
}
