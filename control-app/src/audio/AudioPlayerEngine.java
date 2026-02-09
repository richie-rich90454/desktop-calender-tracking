package audio;

import javax.sound.sampled.*;
import javax.sound.midi.*;
import java.io.IOException;

/**
 * Core audio playback engine supporting multiple audio formats (WAV, MIDI, MP3)
 * with playback control, volume management, and position tracking. Implements
 * AutoCloseable for resource management and provides cross-platform audio
 * playback with Java Sound API and fallback system commands.
 */
public class AudioPlayerEngine implements AutoCloseable {
    public enum PlaybackState{STOPPED, PLAYING, PAUSED}
    private static final float MIN_VOLUME=-80.0f;
    private static final float MAX_VOLUME=6.0f;
    private AudioTrack currentTrack;
    private PlaybackState playbackState;
    private float volume;
    private boolean muted;
    private Clip audioClip;
    private Sequencer midiSequencer;
    private Process mp3Process;
    private long pausePosition;
    private long trackDuration;
    private Thread positionUpdater;
    private boolean running;
    public AudioPlayerEngine(){
        this.playbackState=PlaybackState.STOPPED;
        this.volume=0.0f;
        this.running=true;
    }
    public boolean play(AudioTrack track){
        if (track == null || !track.isSupportedFormat()){
            return false;
        }
        stop();
        try{
            currentTrack=track;
            String extension=track.getFileExtension();
            return switch (extension){
                case "wav" -> playWav(track);
                case "mid", "midi" -> playMidi(track);
                case "mp3" -> playMp3(track);
                default -> false;
            };
        }
        catch (Exception e){
            throw new RuntimeException("Error playing audio track: " + e.getMessage(), e);
        }
    }
    private boolean playMp3(AudioTrack track){
        try{
            return playMp3WithJavaSound(track);
        }
        catch (Exception e1){
            try{
                return playMp3WithSystemCommand(track);
            }
            catch (Exception e2){
                throw new RuntimeException("MP3 playback failed: " + e2.getMessage(), e2);
            }
        }
    }
    private boolean playMp3WithJavaSound(AudioTrack track) throws Exception{
        try{
            AudioInputStream audioStream=AudioSystem.getAudioInputStream(track.getAudioFile());
            AudioFormat baseFormat=audioStream.getFormat();
            AudioFormat decodedFormat=new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );
            AudioInputStream decodedStream=AudioSystem.getAudioInputStream(decodedFormat, audioStream);
            DataLine.Info info=new DataLine.Info(Clip.class, decodedFormat);
            audioClip=(Clip) AudioSystem.getLine(info);
            audioClip.open(decodedStream);
            if (audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)){
                FloatControl gainControl=(FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(volume);
            }
            audioClip.addLineListener(event ->{
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP){
                    playbackState=PlaybackState.STOPPED;
                    track.setPlaying(false);
                    cleanupClip();
                }
            });
            trackDuration=audioClip.getMicrosecondLength() / 1000;
            track.setDuration(trackDuration);
            audioClip.start();
            playbackState=PlaybackState.PLAYING;
            track.setPlaying(true);
            startPositionUpdater();
            return true;
        }
        catch (UnsupportedAudioFileException e){
            throw new Exception("MP3 format not supported by Java Sound API");
        }
    }
    private boolean playMp3WithSystemCommand(AudioTrack track) throws IOException{
        String os=System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        if (os.contains("win")){
            pb=new ProcessBuilder("powershell", "-c",
                    "$player=New-Object -ComObject WMPlayer.OCX;" +
                            "$player.URL='" + track.getAudioFile().getAbsolutePath().replace("\\", "\\\\") + "';" +
                            "while($player.playState -ne 1){Start-Sleep -Milliseconds 100}");
        }
        else if (os.contains("mac")){
            pb=new ProcessBuilder("afplay", track.getAudioFile().getAbsolutePath());
        }
        else{
            pb=new ProcessBuilder("mpg123", track.getAudioFile().getAbsolutePath());
        }
        mp3Process=pb.start();
        playbackState=PlaybackState.PLAYING;
        track.setPlaying(true);
        startPositionUpdater();
        return true;
    }
    private boolean playWav(AudioTrack track) throws Exception{
        AudioInputStream audioStream=AudioSystem.getAudioInputStream(track.getAudioFile());
        AudioFormat format=audioStream.getFormat();
        DataLine.Info info=new DataLine.Info(Clip.class, format);
        audioClip=(Clip) AudioSystem.getLine(info);
        audioClip.open(audioStream);
        if (audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)){
            FloatControl gainControl=(FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
            gainControl.setValue(volume);
        }
        audioClip.addLineListener(event ->{
            if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP){
                playbackState=PlaybackState.STOPPED;
                track.setPlaying(false);
                cleanupClip();
            }
        });
        trackDuration=audioClip.getMicrosecondLength() / 1000;
        track.setDuration(trackDuration);
        audioClip.start();
        playbackState=PlaybackState.PLAYING;
        track.setPlaying(true);
        startPositionUpdater();
        return true;
    }
    private boolean playMidi(AudioTrack track) throws Exception{
        Sequence sequence=MidiSystem.getSequence(track.getAudioFile());
        midiSequencer=MidiSystem.getSequencer();
        midiSequencer.open();
        midiSequencer.setSequence(sequence);
        if (midiSequencer instanceof Synthesizer){
            Synthesizer synthesizer=(Synthesizer) midiSequencer;
            MidiChannel[] channels=synthesizer.getChannels();
            for (MidiChannel channel : channels){
                if (channel != null){
                    channel.controlChange(7, (int) ((volume + 80.0f) / 86.0f * 127));
                }
            }
        }
        trackDuration=midiSequencer.getMicrosecondLength() / 1000;
        track.setDuration(trackDuration);
        midiSequencer.start();
        playbackState=PlaybackState.PLAYING;
        track.setPlaying(true);
        startPositionUpdater();
        return true;
    }
    private void startPositionUpdater(){
        if (positionUpdater != null && positionUpdater.isAlive()){
            positionUpdater.interrupt();
        }
        positionUpdater=new Thread(() ->{
            while (running && playbackState == PlaybackState.PLAYING){
                try{
                    Thread.sleep(100);
                    if (currentTrack != null){
                        long position=getCurrentPosition();
                        currentTrack.setCurrentPosition(position);
                    }
                }
                catch (InterruptedException e){
                    break;
                }
            }
        });
        positionUpdater.start();
    }
    public boolean pause(){
        if (playbackState != PlaybackState.PLAYING || currentTrack == null){
            return false;
        }
        try{
            switch (currentTrack.getFileExtension()){
                case "mp3":
                    if (mp3Process != null && mp3Process.isAlive()){
                        mp3Process.destroy();
                        playbackState=PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                        pausePosition=currentTrack.getCurrentPosition();
                    }
                    break;
                case "wav":
                    if (audioClip != null && audioClip.isRunning()){
                        pausePosition=audioClip.getMicrosecondPosition() / 1000;
                        audioClip.stop();
                        playbackState=PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                        currentTrack.setCurrentPosition(pausePosition);
                    }
                    break;
                case "mid", "midi":
                    if (midiSequencer != null && midiSequencer.isRunning()){
                        pausePosition=midiSequencer.getMicrosecondPosition() / 1000;
                        midiSequencer.stop();
                        playbackState=PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                        currentTrack.setCurrentPosition(pausePosition);
                    }
                    break;
            }
            return true;
        }
        catch (Exception e){
            throw new RuntimeException("Error pausing playback: " + e.getMessage(), e);
        }
    }
    public boolean resume(){
        if (playbackState != PlaybackState.PAUSED || currentTrack == null){
            return false;
        }
        try{
            switch (currentTrack.getFileExtension()){
                case "mp3":
                    currentTrack.setCurrentPosition(pausePosition);
                    return play(currentTrack);
                case "wav":
                    if (audioClip != null){
                        audioClip.setMicrosecondPosition(pausePosition * 1000);
                        audioClip.start();
                        playbackState=PlaybackState.PLAYING;
                        currentTrack.setPlaying(true);
                        return true;
                    }
                    break;
                case "mid", "midi":
                    if (midiSequencer != null){
                        midiSequencer.setMicrosecondPosition(pausePosition * 1000);
                        midiSequencer.start();
                        playbackState=PlaybackState.PLAYING;
                        currentTrack.setPlaying(true);
                        return true;
                    }
                    break;
            }
            return false;
        }
        catch (Exception e){
            throw new RuntimeException("Error resuming playback: " + e.getMessage(), e);
        }
    }
    public boolean stop(){
        if (playbackState == PlaybackState.STOPPED){
            return true;
        }
        try{
            if (currentTrack != null){
                switch (currentTrack.getFileExtension()){
                    case "mp3":
                        cleanupMp3();
                        break;
                    case "wav":
                        cleanupClip();
                        break;
                    case "mid", "midi":
                        cleanupMidi();
                        break;
                }
                currentTrack.setPlaying(false);
                currentTrack.setCurrentPosition(0);
            }
            playbackState=PlaybackState.STOPPED;
            pausePosition=0;
            if (positionUpdater != null){
                positionUpdater.interrupt();
            }
            return true;
        }
        catch (Exception e){
            throw new RuntimeException("Error stopping playback: " + e.getMessage(), e);
        }
    }
    public boolean seek(long positionMillis){
        if (currentTrack == null || positionMillis < 0){
            return false;
        }
        try{
            switch (currentTrack.getFileExtension()){
                case "mp3":
                    return false;
                case "wav":
                    if (audioClip != null){
                        long microPosition=Math.min(positionMillis * 1000, audioClip.getMicrosecondLength());
                        if (playbackState == PlaybackState.PLAYING){
                            audioClip.stop();
                            audioClip.setMicrosecondPosition(microPosition);
                            audioClip.start();
                        }
                        else{
                            audioClip.setMicrosecondPosition(microPosition);
                        }
                        currentTrack.setCurrentPosition(positionMillis);
                        return true;
                    }
                    break;
                case "mid", "midi":
                    if (midiSequencer != null){
                        long microPosition=Math.min(positionMillis * 1000, midiSequencer.getMicrosecondLength());
                        if (playbackState == PlaybackState.PLAYING){
                            midiSequencer.stop();
                            midiSequencer.setMicrosecondPosition(microPosition);
                            midiSequencer.start();
                        }
                        else{
                            midiSequencer.setMicrosecondPosition(microPosition);
                        }
                        currentTrack.setCurrentPosition(positionMillis);
                        return true;
                    }
                    break;
            }
            return false;
        }
        catch (Exception e){
            throw new RuntimeException("Error seeking: " + e.getMessage(), e);
        }
    }
    public boolean setVolume(float newVolume){
        volume=Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, newVolume));
        try{
            if (muted){
                return true;
            }
            if (currentTrack != null){
                switch (currentTrack.getFileExtension()){
                    case "wav":
                        if (audioClip != null && audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)){
                            FloatControl gainControl=(FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
                            gainControl.setValue(volume);
                        }
                        break;
                    case "mid", "midi":
                        if (midiSequencer instanceof Synthesizer){
                            Synthesizer synthesizer=(Synthesizer) midiSequencer;
                            MidiChannel[] channels=synthesizer.getChannels();
                            for (MidiChannel channel : channels){
                                if (channel != null){
                                    channel.controlChange(7, (int) ((volume + 80.0f) / 86.0f * 127));
                                }
                            }
                        }
                        break;
                }
            }
            return true;
        }
        catch (Exception e){
            throw new RuntimeException("Error setting volume: " + e.getMessage(), e);
        }
    }
    public float getVolume(){
        return volume;
    }
    public boolean setMuted(boolean muted){
        this.muted=muted;
        try{
            if (muted){
                return setVolume(MIN_VOLUME);
            }
            else{
                return setVolume(volume);
            }
        }
        catch (Exception e){
            throw new RuntimeException("Error setting mute: " + e.getMessage(), e);
        }
    }
    public boolean isMuted(){
        return muted;
    }
    public AudioTrack getCurrentTrack(){
        return currentTrack;
    }
    public PlaybackState getPlaybackState(){
        return playbackState;
    }
    public long getCurrentPosition(){
        if (currentTrack == null){
            return 0;
        }
        try{
            return switch (currentTrack.getFileExtension()){
                case "mp3" -> currentTrack.getCurrentPosition();
                case "wav" -> audioClip != null ? audioClip.getMicrosecondPosition() / 1000 : 0;
                case "mid", "midi" -> midiSequencer != null ? midiSequencer.getMicrosecondPosition() / 1000 : 0;
                default -> 0;
            };
        }
        catch (Exception e){
            throw new RuntimeException("Error getting position: " + e.getMessage(), e);
        }
    }
    public long getDuration(){
        if (currentTrack == null){
            return 0;
        }
        return trackDuration;
    }
    public double getPlaybackProgress(){
        long duration=getDuration();
        if (duration <= 0){
            return 0.0;
        }
        long position=getCurrentPosition();
        return (double) position / duration;
    }
    public boolean isPlaying(){
        return playbackState == PlaybackState.PLAYING;
    }
    public boolean isPaused(){
        return playbackState == PlaybackState.PAUSED;
    }
    public boolean isStopped(){
        return playbackState == PlaybackState.STOPPED;
    }
    private void cleanupMp3(){
        if (mp3Process != null){
            mp3Process.destroy();
            mp3Process=null;
        }
    }
    private void cleanupClip(){
        if (audioClip != null){
            audioClip.stop();
            audioClip.close();
            audioClip=null;
        }
    }
    private void cleanupMidi(){
        if (midiSequencer != null){
            midiSequencer.stop();
            midiSequencer.close();
            midiSequencer=null;
        }
    }
    public void cleanup(){
        running=false;
        stop();
        cleanupMp3();
        cleanupClip();
        cleanupMidi();
        if (positionUpdater != null){
            positionUpdater.interrupt();
        }
    }
    @Override
    public void close(){
        cleanup();
    }
}