package audio;

/**
 * Audio playback engine for MP3, WAV, and MIDI files.
 *
 * Responsibilities:
 * - Play, pause, stop audio playback
 * - Control volume and playback position
 * - Handle multiple audio formats
 * - Manage playback state
 *
 * Java data types used:
 * - Clip (javax.sound.sampled)
 * - Sequencer (javax.sound.midi)
 * - Player (javazoom.jlayer)
 *
 * Design intent:
 * Unified audio playback interface for different formats.
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Sequence;
import javazoom.jl.player.Player;
import javazoom.jl.decoder.JavaLayerException;

public class AudioPlayerEngine {
    public enum PlaybackState{
        STOPPED,
        PLAYING,
        PAUSED
    }
    
    private static final float MIN_VOLUME=-80.0f;
    private static final float MAX_VOLUME=6.0f;
    private AudioTrack currentTrack;
    private PlaybackState playbackState;
    private float volume;
    private boolean muted;
    private Clip audioClip;
    private Sequencer midiSequencer;
    private Player mp3Player;
    private Thread mp3PlayerThread;
    private long pausePosition;
    private long trackDuration;
    public AudioPlayerEngine(){
        this.currentTrack=null;
        this.playbackState=PlaybackState.STOPPED;
        this.volume=0.0f;
        this.muted=false;
        this.pausePosition=0;
        this.trackDuration=0;
    }
    public boolean play(AudioTrack track){
        if (track==null||!track.isSupportedFormat()){
            return false;
        }
        stop();
        try{
            currentTrack=track;
            String extension=track.getFileExtension();
            switch (extension){
                case "mp3":
                    return playMp3(track);
                case "wav":
                    return playWav(track);
                case "mid":
                case "midi":
                    return playMidi(track);
                default:
                    System.err.println("Unsupported audio format: "+extension);
                    return false;
            }
        }
        catch (Exception e){
            System.err.println("Error playing audio track: "+e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    private boolean playMp3(AudioTrack track) throws JavaLayerException, IOException{
        FileInputStream fileInputStream=new FileInputStream(track.getAudioFile());
        mp3Player=new Player(fileInputStream);
        mp3PlayerThread=new Thread(()->{
            try{
                playbackState=PlaybackState.PLAYING;
                track.setPlaying(true);
                mp3Player.play();
            }
            catch (JavaLayerException e){
                System.err.println("MP3 playback error: "+e.getMessage());
            }
            finally{
                playbackState=PlaybackState.STOPPED;
                track.setPlaying(false);
                cleanupMp3();
            }
        });
        mp3PlayerThread.start();
        return true;
    }
    private boolean playWav(AudioTrack track) throws UnsupportedAudioFileException, IOException, LineUnavailableException{
        AudioInputStream audioStream=AudioSystem.getAudioInputStream(track.getAudioFile());
        AudioFormat format=audioStream.getFormat();
        DataLine.Info info=new DataLine.Info(Clip.class, format);
        audioClip=(Clip) AudioSystem.getLine(info);
        audioClip.open(audioStream);
        if (audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)){
            FloatControl gainControl=(FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
            gainControl.setValue(volume);
        }
        audioClip.addLineListener(event->{
            if (event.getType()==javax.sound.sampled.LineEvent.Type.STOP){
                playbackState=PlaybackState.STOPPED;
                track.setPlaying(false);
                cleanupClip();
            }
        });
        trackDuration=audioClip.getMicrosecondLength()/1000;
        track.setDuration(trackDuration);
        audioClip.start();
        playbackState=PlaybackState.PLAYING;
        track.setPlaying(true);
        return true;
    }
    private boolean playMidi(AudioTrack track) throws Exception{
        Sequence sequence=MidiSystem.getSequence(track.getAudioFile());
        midiSequencer=MidiSystem.getSequencer();
        midiSequencer.open();
        midiSequencer.setSequence(sequence);
        if (midiSequencer instanceof javax.sound.midi.Synthesizer){
            javax.sound.midi.Synthesizer synthesizer=(javax.sound.midi.Synthesizer) midiSequencer;
            javax.sound.midi.MidiChannel[] channels=synthesizer.getChannels();
            for (javax.sound.midi.MidiChannel channel:channels){
                if (channel!=null){
                    channel.controlChange(7, (int) ((volume+80.0f)/86.0f*127));
                }
            }
        }
        trackDuration=midiSequencer.getMicrosecondLength()/1000;
        track.setDuration(trackDuration);
        midiSequencer.start();
        playbackState=PlaybackState.PLAYING;
        track.setPlaying(true);
        return true;
    }
    public boolean pause(){
        if (playbackState!=PlaybackState.PLAYING||currentTrack==null){
            return false;
        }
        try{
            switch (currentTrack.getFileExtension()){
                case "mp3":
                    if (mp3Player!=null){
                        mp3Player.close();
                        playbackState=PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                    }
                    break;
                case "wav":
                    if (audioClip!=null&&audioClip.isRunning()){
                        pausePosition=audioClip.getMicrosecondPosition()/1000;
                        audioClip.stop();
                        playbackState=PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                        currentTrack.setCurrentPosition(pausePosition);
                    }
                    break;
                case "mid":
                case "midi":
                    if (midiSequencer!=null&&midiSequencer.isRunning()){
                        pausePosition=midiSequencer.getMicrosecondPosition()/1000;
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
            System.err.println("Error pausing playback: "+e.getMessage());
            return false;
        }
    }
    public boolean resume(){
        if (playbackState!=PlaybackState.PAUSED||currentTrack==null){
            return false;
        }
        try{
            switch (currentTrack.getFileExtension()){
                case "mp3":
                    return play(currentTrack);
                case "wav":
                    if (audioClip!=null){
                        audioClip.setMicrosecondPosition(pausePosition*1000);
                        audioClip.start();
                        playbackState=PlaybackState.PLAYING;
                        currentTrack.setPlaying(true);
                        return true;
                    }
                    break;
                case "mid":
                case "midi":
                    if (midiSequencer!=null){
                        midiSequencer.setMicrosecondPosition(pausePosition*1000);
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
            System.err.println("Error resuming playback: "+e.getMessage());
            return false;
        }
    }
    public boolean stop(){
        if (playbackState==PlaybackState.STOPPED){
            return true;
        }
        try{
            switch (currentTrack!=null?currentTrack.getFileExtension():""){
                case "mp3":
                    cleanupMp3();
                    break;
                case "wav":
                    cleanupClip();
                    break;
                case "mid":
                case "midi":
                    cleanupMidi();
                    break;
            }
            playbackState=PlaybackState.STOPPED;
            if (currentTrack!=null){
                currentTrack.setPlaying(false);
                currentTrack.setCurrentPosition(0);
            }
            pausePosition=0;
            return true;
        }
        catch (Exception e){
            System.err.println("Error stopping playback: "+e.getMessage());
            return false;
        }
    }
    public boolean seek(long positionMillis){
        if (currentTrack==null||positionMillis<0){
            return false;
        }
        try{
            switch (currentTrack.getFileExtension()){
                case "mp3":
                    System.err.println("Seek not supported for MP3 in basic implementation");
                    return false;
                case "wav":
                    if (audioClip!=null){
                        long microPosition=Math.min(positionMillis*1000, audioClip.getMicrosecondLength());
                        if (playbackState==PlaybackState.PLAYING){
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
                case "mid":
                case "midi":
                    if (midiSequencer!=null){
                        long microPosition=Math.min(positionMillis*1000, midiSequencer.getMicrosecondLength());
                        if (playbackState==PlaybackState.PLAYING){
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
            System.err.println("Error seeking: "+e.getMessage());
            return false;
        }
    }
    public boolean setVolume(float newVolume){
        volume=Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, newVolume));
        try{
            if (muted){
                return true;
            }
            switch (currentTrack!=null?currentTrack.getFileExtension():""){
                case "mp3":
                    break;
                case "wav":
                    if (audioClip!=null&&audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)){
                        FloatControl gainControl=(FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
                        gainControl.setValue(volume);
                    }
                    break;
                case "mid":
                case "midi":
                    if (midiSequencer!=null&&midiSequencer instanceof javax.sound.midi.Synthesizer){
                        javax.sound.midi.Synthesizer synthesizer=(javax.sound.midi.Synthesizer) midiSequencer;
                        javax.sound.midi.MidiChannel[] channels=synthesizer.getChannels();
                        for (javax.sound.midi.MidiChannel channel:channels){
                            if (channel!=null){
                                channel.controlChange(7, (int) ((volume+80.0f)/86.0f*127));
                            }
                        }
                    }
                    break;
            }
            return true;
        }
        catch (Exception e){
            System.err.println("Error setting volume: "+e.getMessage());
            return false;
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
            System.err.println("Error setting mute: "+e.getMessage());
            return false;
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
        if (currentTrack==null){
            return 0;
        }
        try{
            switch (currentTrack.getFileExtension()){
                case "mp3":
                    return currentTrack.getCurrentPosition();
                case "wav":
                    if (audioClip!=null){
                        return audioClip.getMicrosecondPosition()/1000;
                    }
                    break;
                case "mid":
                case "midi":
                    if (midiSequencer!=null){
                        return midiSequencer.getMicrosecondPosition()/1000;
                    }
                    break;
            }
            return 0;
        }
        catch (Exception e){
            System.err.println("Error getting position: "+e.getMessage());
            return 0;
        }
    }
    public long getDuration(){
        if (currentTrack==null){
            return 0;
        }
        return trackDuration;
    }
    public double getPlaybackProgress(){
        long duration=getDuration();
        if (duration<=0){
            return 0.0;
        }
        long position=getCurrentPosition();
        return (double) position/duration;
    }
    public boolean isPlaying(){
        return playbackState==PlaybackState.PLAYING;
    }
    public boolean isPaused(){
        return playbackState==PlaybackState.PAUSED;
    }
    public boolean isStopped(){
        return playbackState==PlaybackState.STOPPED;
    }
    private void cleanupMp3(){
        if (mp3Player!=null){
            mp3Player.close();
            mp3Player=null;
        }
        if (mp3PlayerThread!=null){
            mp3PlayerThread.interrupt();
            mp3PlayerThread=null;
        }
    }
    private void cleanupClip(){
        if (audioClip!=null){
            audioClip.stop();
            audioClip.close();
            audioClip=null;
        }
    }
    private void cleanupMidi(){
        if (midiSequencer!=null){
            midiSequencer.stop();
            midiSequencer.close();
            midiSequencer=null;
        }
    }
    public void cleanup(){
        stop();
        cleanupMp3();
        cleanupClip();
        cleanupMidi();
    }
    @Override
    protected void finalize() throws Throwable{
        try{
            cleanup();
        }
        finally{
            super.finalize();
        }
    }
}