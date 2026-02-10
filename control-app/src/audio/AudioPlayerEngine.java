package audio;

import javax.sound.sampled.*;
import javax.sound.midi.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Core audio playback engine with proper pause/resume functionality.
 * When paused, playback remembers the exact position and resumes from there.
 * Only restarting from beginning when explicitly stopped.
 */
public class AudioPlayerEngine implements AutoCloseable {
    public enum PlaybackState { STOPPED, PLAYING, PAUSED }

    private static final float MIN_VOLUME = -80.0f;
    private static final float MAX_VOLUME = 6.0f;

    private AudioTrack currentTrack;
    private PlaybackState playbackState;
    private float volume;
    private boolean muted;
    private Clip audioClip;
    private Sequencer midiSequencer;
    private Process mp3Process;
    private long resumePosition; // Position to resume from when paused
    private long trackDuration;
    private Thread positionUpdater;
    private boolean running;
    private boolean isSameTrackResume; // Flag to track if we're resuming same track

    private CompletableFuture<Void> mp3ProcessFuture;

    public AudioPlayerEngine() {
        this.playbackState = PlaybackState.STOPPED;
        this.volume = 0.0f;
        this.running = true;
        this.mp3ProcessFuture = null;
        this.resumePosition = 0;
        this.isSameTrackResume = false;
    }

    /**
     * Start or resume playback of a track
     */
    public boolean play(AudioTrack track) {
        if (track == null || !track.isSupportedFormat()) {
            return false;
        }
        
        // If we're already playing the same track and it's paused, just resume
        if (currentTrack != null && currentTrack.equals(track) && 
            playbackState == PlaybackState.PAUSED) {
            return resume();
        }
        
        // Otherwise stop current playback and start new
        stop();
        currentTrack = track;
        resumePosition = 0; // Reset resume position for new track
        isSameTrackResume = false;
        
        try {
            String extension = track.getFileExtension();
            return switch (extension) {
                case "wav" -> playWav(track);
                case "mid", "midi" -> playMidi(track);
                case "mp3" -> playMp3(track);
                default -> false;
            };
        } catch (Exception e) {
            throw new RuntimeException("Error playing audio track: " + e.getMessage(), e);
        }
    }

    /**
     * Pause playback - remembers current position for resume
     */
    public boolean pause() {
        if (playbackState != PlaybackState.PLAYING || currentTrack == null) {
            return false;
        }
        
        try {
            String extension = currentTrack.getFileExtension();
            
            switch (extension) {
                case "mp3":
                    if (audioClip != null && audioClip.isRunning()) {
                        // Store position before stopping
                        resumePosition = audioClip.getMicrosecondPosition() / 1000;
                        audioClip.stop();
                        playbackState = PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                        currentTrack.setCurrentPosition(resumePosition);
                        return true;
                    } else if (mp3Process != null && mp3Process.isAlive()) {
                        // For system commands, we can't truly pause, so we stop
                        // The position updater should have stored the position
                        resumePosition = currentTrack.getCurrentPosition();
                        mp3Process.destroy();
                        mp3Process = null;
                        playbackState = PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                        return true;
                    }
                    break;
                    
                case "wav":
                    if (audioClip != null && audioClip.isRunning()) {
                        resumePosition = audioClip.getMicrosecondPosition() / 1000;
                        audioClip.stop();
                        playbackState = PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                        currentTrack.setCurrentPosition(resumePosition);
                        return true;
                    }
                    break;
                    
                case "mid":
                case "midi":
                    if (midiSequencer != null && midiSequencer.isRunning()) {
                        resumePosition = midiSequencer.getMicrosecondPosition() / 1000;
                        midiSequencer.stop();
                        playbackState = PlaybackState.PAUSED;
                        currentTrack.setPlaying(false);
                        currentTrack.setCurrentPosition(resumePosition);
                        return true;
                    }
                    break;
            }
            
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Error pausing playback: " + e.getMessage(), e);
        }
    }

    /**
     * Resume playback from paused position
     */
    public boolean resume() {
        if (playbackState != PlaybackState.PAUSED || currentTrack == null || resumePosition < 0) {
            return false;
        }
        
        try {
            String extension = currentTrack.getFileExtension();
            boolean success = false;
            
            switch (extension) {
                case "mp3":
                    if (audioClip != null) {
                        // Java Sound MP3 - set position and start
                        long microPosition = Math.min(resumePosition * 1000, audioClip.getMicrosecondLength());
                        audioClip.setMicrosecondPosition(microPosition);
                        audioClip.start();
                        playbackState = PlaybackState.PLAYING;
                        currentTrack.setPlaying(true);
                        isSameTrackResume = true;
                        success = true;
                    } else {
                        // System command MP3 - need to restart from position
                        // This is tricky with system commands
                        AudioTrack track = currentTrack;
                        stop(); // Clean current state
                        currentTrack = track;
                        success = playMp3FromPosition(track, resumePosition);
                        isSameTrackResume = success;
                    }
                    break;
                    
                case "wav":
                    if (audioClip != null) {
                        long microPosition = Math.min(resumePosition * 1000, audioClip.getMicrosecondLength());
                        audioClip.setMicrosecondPosition(microPosition);
                        audioClip.start();
                        playbackState = PlaybackState.PLAYING;
                        currentTrack.setPlaying(true);
                        isSameTrackResume = true;
                        success = true;
                    }
                    break;
                    
                case "mid":
                case "midi":
                    if (midiSequencer != null) {
                        long microPosition = Math.min(resumePosition * 1000, midiSequencer.getMicrosecondLength());
                        midiSequencer.setMicrosecondPosition(microPosition);
                        midiSequencer.start();
                        playbackState = PlaybackState.PLAYING;
                        currentTrack.setPlaying(true);
                        isSameTrackResume = true;
                        success = true;
                    }
                    break;
            }
            
            if (success) {
                startPositionUpdater();
            }
            
            return success;
        } catch (Exception e) {
            throw new RuntimeException("Error resuming playback: " + e.getMessage(), e);
        }
    }

    /**
     * Stop playback completely - resets to beginning
     */
    public boolean stop() {
        if (playbackState == PlaybackState.STOPPED) {
            return true;
        }
        
        try {
            cleanupResources();
            playbackState = PlaybackState.STOPPED;
            resumePosition = 0;
            isSameTrackResume = false;
            
            if (currentTrack != null) {
                currentTrack.setPlaying(false);
                currentTrack.setCurrentPosition(0);
            }
            
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Error stopping playback: " + e.getMessage(), e);
        }
    }

    private boolean playMp3FromPosition(AudioTrack track, long positionMillis) {
        try {
            return playMp3WithJavaSoundFromPosition(track, positionMillis);
        } catch (Exception e1) {
            // Can't resume from position with system commands
            return false;
        }
    }

    private boolean playMp3WithJavaSoundFromPosition(AudioTrack track, long startPositionMillis) throws Exception {
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(track.getAudioFile());
            AudioFormat baseFormat = audioStream.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );
            AudioInputStream decodedStream = AudioSystem.getAudioInputStream(decodedFormat, audioStream);
            DataLine.Info info = new DataLine.Info(Clip.class, decodedFormat);
            audioClip = (Clip) AudioSystem.getLine(info);
            audioClip.open(decodedStream);

            if (audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(volume);
            }

            // Set start position
            if (startPositionMillis > 0) {
                long microPosition = Math.min(startPositionMillis * 1000, audioClip.getMicrosecondLength());
                audioClip.setMicrosecondPosition(microPosition);
            }

            audioClip.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                    playbackState = PlaybackState.STOPPED;
                    if (currentTrack != null) {
                        currentTrack.setPlaying(false);
                    }
                    cleanupClip();
                }
            });

            trackDuration = audioClip.getMicrosecondLength() / 1000;
            if (currentTrack != null) {
                currentTrack.setDuration(trackDuration);
            }
            audioClip.start();
            playbackState = PlaybackState.PLAYING;
            if (currentTrack != null) {
                currentTrack.setPlaying(true);
                currentTrack.setCurrentPosition(startPositionMillis);
            }
            startPositionUpdater();
            return true;
        } catch (UnsupportedAudioFileException e) {
            throw new Exception("MP3 format not supported by Java Sound API", e);
        }
    }

    private boolean playMp3(AudioTrack track) {
        try {
            return playMp3WithJavaSound(track);
        } catch (Exception e1) {
            try {
                return playMp3WithSystemCommand(track);
            } catch (Exception e2) {
                throw new RuntimeException("MP3 playback failed: " + e2.getMessage(), e2);
            }
        }
    }

    private boolean playMp3WithJavaSound(AudioTrack track) throws Exception {
        return playMp3WithJavaSoundFromPosition(track, 0);
    }

    private boolean playMp3WithSystemCommand(AudioTrack track) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        
        if (os.contains("win")) {
            String path = track.getAudioFile().getAbsolutePath().replace("\\", "\\\\");
            pb = new ProcessBuilder("powershell", "-c",
                    "$player = New-Object -ComObject WMPlayer.OCX; " +
                            "$player.URL = '" + path + "'; " +
                            "while ($player.playState -ne 1) { Start-Sleep -Milliseconds 100 }; " +
                            "$player.close()");
        } else if (os.contains("mac")) {
            pb = new ProcessBuilder("afplay", track.getAudioFile().getAbsolutePath());
        } else {
            pb = new ProcessBuilder("mpg123", track.getAudioFile().getAbsolutePath());
        }
        
        mp3Process = pb.start();

        mp3ProcessFuture = CompletableFuture.runAsync(() -> {
            try {
                mp3Process.waitFor();
                if (playbackState == PlaybackState.PLAYING) {
                    playbackState = PlaybackState.STOPPED;
                    if (currentTrack != null) {
                        currentTrack.setPlaying(false);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                mp3Process.destroy();
            }
        });

        playbackState = PlaybackState.PLAYING;
        if (currentTrack != null) {
            currentTrack.setPlaying(true);
        }
        startPositionUpdater();
        return true;
    }

    private boolean playWav(AudioTrack track) throws Exception {
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(track.getAudioFile());
        AudioFormat format = audioStream.getFormat();
        DataLine.Info info = new DataLine.Info(Clip.class, format);
        audioClip = (Clip) AudioSystem.getLine(info);
        audioClip.open(audioStream);

        if (audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
            gainControl.setValue(volume);
        }

        audioClip.addLineListener(event -> {
            if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                if (playbackState != PlaybackState.PAUSED) {
                    playbackState = PlaybackState.STOPPED;
                    if (currentTrack != null) {
                        currentTrack.setPlaying(false);
                    }
                    cleanupClip();
                }
            }
        });

        trackDuration = audioClip.getMicrosecondLength() / 1000;
        if (currentTrack != null) {
            currentTrack.setDuration(trackDuration);
        }
        audioClip.start();
        playbackState = PlaybackState.PLAYING;
        if (currentTrack != null) {
            currentTrack.setPlaying(true);
        }
        startPositionUpdater();
        return true;
    }

    private boolean playMidi(AudioTrack track) throws Exception {
        Sequence sequence = MidiSystem.getSequence(track.getAudioFile());
        midiSequencer = MidiSystem.getSequencer();
        midiSequencer.open();
        midiSequencer.setSequence(sequence);

        if (midiSequencer instanceof Synthesizer synthesizer) {
            MidiChannel[] channels = synthesizer.getChannels();
            for (MidiChannel channel : channels) {
                if (channel != null) {
                    int midiVolume = (int) ((volume - MIN_VOLUME) / (MAX_VOLUME - MIN_VOLUME) * 127);
                    midiVolume = Math.max(0, Math.min(127, midiVolume));
                    channel.controlChange(7, midiVolume);
                }
            }
        }

        trackDuration = midiSequencer.getMicrosecondLength() / 1000;
        if (currentTrack != null) {
            currentTrack.setDuration(trackDuration);
        }
        midiSequencer.start();
        playbackState = PlaybackState.PLAYING;
        if (currentTrack != null) {
            currentTrack.setPlaying(true);
        }
        startPositionUpdater();
        return true;
    }

    private void startPositionUpdater() {
        if (positionUpdater != null && positionUpdater.isAlive()) {
            positionUpdater.interrupt();
            try {
                positionUpdater.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        positionUpdater = new Thread(() -> {
            while (running && playbackState == PlaybackState.PLAYING) {
                try {
                    Thread.sleep(100);
                    if (currentTrack != null) {
                        long position = getCurrentPosition();
                        currentTrack.setCurrentPosition(position);
                        // Update resume position as we play
                        if (!isSameTrackResume) {
                            resumePosition = position;
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        positionUpdater.start();
    }

    public boolean seek(long positionMillis) {
        if (currentTrack == null || positionMillis < 0) {
            return false;
        }
        
        try {
            String extension = currentTrack.getFileExtension();
            boolean wasPlaying = playbackState == PlaybackState.PLAYING;
            
            switch (extension) {
                case "mp3":
                    if (audioClip != null) {
                        long microPosition = Math.min(positionMillis * 1000, audioClip.getMicrosecondLength());
                        
                        if (wasPlaying) {
                            audioClip.stop();
                        }
                        
                        audioClip.setMicrosecondPosition(microPosition);
                        resumePosition = positionMillis;
                        
                        if (wasPlaying) {
                            audioClip.start();
                        }
                        
                        currentTrack.setCurrentPosition(positionMillis);
                        return true;
                    }
                    return false;
                    
                case "wav":
                    if (audioClip != null) {
                        long microPosition = Math.min(positionMillis * 1000, audioClip.getMicrosecondLength());
                        
                        if (wasPlaying) {
                            audioClip.stop();
                        }
                        
                        audioClip.setMicrosecondPosition(microPosition);
                        resumePosition = positionMillis;
                        
                        if (wasPlaying) {
                            audioClip.start();
                        }
                        
                        currentTrack.setCurrentPosition(positionMillis);
                        return true;
                    }
                    break;
                    
                case "mid":
                case "midi":
                    if (midiSequencer != null) {
                        long microPosition = Math.min(positionMillis * 1000, midiSequencer.getMicrosecondLength());
                        
                        if (wasPlaying) {
                            midiSequencer.stop();
                        }
                        
                        midiSequencer.setMicrosecondPosition(microPosition);
                        resumePosition = positionMillis;
                        
                        if (wasPlaying) {
                            midiSequencer.start();
                        }
                        
                        currentTrack.setCurrentPosition(positionMillis);
                        return true;
                    }
                    break;
            }
            
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Error seeking: " + e.getMessage(), e);
        }
    }

    private void cleanupResources() {
        cleanupMp3();
        cleanupClip();
        cleanupMidi();
        
        if (positionUpdater != null) {
            positionUpdater.interrupt();
        }
    }

    private void cleanupMp3() {
        if (mp3Process != null) {
            mp3Process.destroy();
            if (mp3ProcessFuture != null) {
                 try {
                     mp3ProcessFuture.get(2, TimeUnit.SECONDS);
                 } catch (InterruptedException | ExecutionException | TimeoutException e) {
                     mp3Process.destroyForcibly();
                     Thread.currentThread().interrupt();
                 }
                 mp3ProcessFuture = null;
             }
            mp3Process = null;
        }
        cleanupClip();
    }

    private void cleanupClip() {
        if (audioClip != null) {
            audioClip.stop();
            audioClip.close();
            audioClip = null;
        }
    }

    private void cleanupMidi() {
        if (midiSequencer != null) {
            midiSequencer.stop();
            midiSequencer.close();
            midiSequencer = null;
        }
    }

    // Keep all other methods (setVolume, getCurrentPosition, etc.) as they were
    public boolean setVolume(float newVolume) {
        volume = Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, newVolume));
        try {
            if (muted) return true;
            
            if (currentTrack != null) {
                switch (currentTrack.getFileExtension()) {
                    case "wav":
                    case "mp3":
                        if (audioClip != null && audioClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                            FloatControl gainControl = (FloatControl) audioClip.getControl(FloatControl.Type.MASTER_GAIN);
                            gainControl.setValue(volume);
                        }
                        break;
                    case "mid":
                    case "midi":
                        if (midiSequencer instanceof Synthesizer synthesizer) {
                            MidiChannel[] channels = synthesizer.getChannels();
                            for (MidiChannel channel : channels) {
                                if (channel != null) {
                                    int midiVolume = (int) ((volume - MIN_VOLUME) / (MAX_VOLUME - MIN_VOLUME) * 127);
                                    midiVolume = Math.max(0, Math.min(127, midiVolume));
                                    channel.controlChange(7, midiVolume);
                                }
                            }
                        }
                        break;
                }
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Error setting volume: " + e.getMessage(), e);
        }
    }

    public float getVolume() {
        return volume;
    }

    public boolean setMuted(boolean muted) {
        this.muted = muted;
        try {
            if (muted) {
                return setVolume(MIN_VOLUME);
            } else {
                return setVolume(volume);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error setting mute: " + e.getMessage(), e);
        }
    }

    public boolean isMuted() {
        return muted;
    }

    public AudioTrack getCurrentTrack() {
        return currentTrack;
    }

    public PlaybackState getPlaybackState() {
        return playbackState;
    }

    public long getCurrentPosition() {
        if (currentTrack == null) {
            return 0;
        }
        
        if (playbackState == PlaybackState.PAUSED) {
            return resumePosition;
        }
        
        try {
            return switch (currentTrack.getFileExtension()) {
                case "mp3" -> audioClip != null ? audioClip.getMicrosecondPosition() / 1000 : 
                       (playbackState == PlaybackState.PAUSED ? resumePosition : 0);
                case "wav" -> audioClip != null ? audioClip.getMicrosecondPosition() / 1000 : 0;
                case "mid", "midi" -> midiSequencer != null ? midiSequencer.getMicrosecondPosition() / 1000 : 0;
                default -> 0;
            };
        } catch (Exception e) {
            return resumePosition;
        }
    }

    public long getDuration() {
        if (currentTrack == null) {
            return 0;
        }
        return trackDuration;
    }

    public double getPlaybackProgress() {
        long duration = getDuration();
        if (duration <= 0) {
            return 0.0;
        }
        long position = getCurrentPosition();
        return (double) position / duration;
    }

    public boolean isPlaying() {
        return playbackState == PlaybackState.PLAYING;
    }

    public boolean isPaused() {
        return playbackState == PlaybackState.PAUSED;
    }

    public boolean isStopped() {
        return playbackState == PlaybackState.STOPPED;
    }

    public void cleanup() {
        running = false;
        stop();
        cleanupResources();
        
        if (positionUpdater != null) {
            positionUpdater.interrupt();
            try {
                positionUpdater.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        cleanup();
    }
}