package audio;

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
    private Process mp3Process;
    private long pausePosition;
    private long trackDuration;
    private Thread positionUpdater;
    private boolean running;
    public AudioPlayerEngine(){
        this.currentTrack=null;
        this.playbackState=PlaybackState.STOPPED;
        this.volume=0.0f;
        this.muted=false;
        this.pausePosition=0;
        this.trackDuration=0;
        this.running=true;
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
                case "wav":
                    return playWav(track);
                case "mid":
                case "midi":
                    return playMidi(track);
                case "mp3":
                    return playMp3(track);
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
    private boolean playMp3(AudioTrack track){
        try{
            return playMp3WithJavaSound(track);
        }
        catch (Exception e1){
            System.err.println("Java Sound MP3 failed: "+e1.getMessage());
            try{
                return playMp3WithSystemCommand(track);
            }
            catch (Exception e2){
                System.err.println("System command MP3 failed: "+e2.getMessage());
                return false;
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
                baseFormat.getChannels()*2,
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
                "$player=New-Object -ComObject WMPlayer.OCX;"+
                "$player.URL='"+track.getAudioFile().getAbsolutePath().replace("\\", "\\\\")+"';"+
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
        startPositionUpdater();
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
        startPositionUpdater();
        return true;
    }
    private void startPositionUpdater(){
        if (positionUpdater!=null&&positionUpdater.isAlive()){
            positionUpdater.interrupt();
        }
        positionUpdater=new Thread(()->{
            while (running&&playbackState==PlaybackState.PLAYING){
                try{
                    Thread.sleep(100);
                    if (currentTrack!=null){
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
        if (playbackState!=PlaybackState.PLAYING||currentTrack==null){
            return false;
        }
        try{
            switch (currentTrack.getFileExtension()){
                case "mp3":
                    if (mp3Process!=null&&mp3Process.isAlive()){
                        mp3Process.destroy();
                        playbackState=PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                        pausePosition=currentTrack.getCurrentPosition();
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
                    currentTrack.setCurrentPosition(pausePosition);
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
            if (positionUpdater!=null){
                positionUpdater.interrupt();
            }
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
        if (mp3Process!=null){
            mp3Process.destroy();
            mp3Process=null;
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
        running=false;
        stop();
        cleanupMp3();
        cleanupClip();
        cleanupMidi();
        if (positionUpdater!=null){
            positionUpdater.interrupt();
            positionUpdater=null;
        }
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