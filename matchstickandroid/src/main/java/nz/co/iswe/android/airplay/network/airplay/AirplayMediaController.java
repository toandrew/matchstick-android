package nz.co.iswe.android.airplay.network.airplay;

public interface AirplayMediaController {
    public void onPlayCommand();

    public void onPauseCommand();

    public void onStopCommand();

    public void onSeekCommand(float time);

    public void onChangeVolumeCommand(double volume);

    public void onRewindCommand();

    public void onFastForwardCommand();

    public float getPosition();

    public float getDuration();

    public boolean isPlaying();

    public void onUpdateImageCommand();

    public void onSetPosCommand(float time);

    public void onSetDurationCommand(float duration);
}
