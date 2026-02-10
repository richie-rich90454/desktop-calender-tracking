package audio;

import java.io.File;
import java.util.Objects;

/**
 * Represents a single audio track with metadata, playback state, and file
 * information. Provides lightweight encapsulation of audio files for playlist
 * management with track numbering, duration tracking, and format validation.
 */

public class AudioTrack{
    private static final String PREFIX_FORMAT="%03d";
    private File audioFile;
    private String displayName;
    private int trackNumber;
    private long duration;
    private boolean isPlaying;
    private long currentPosition;
    public AudioTrack(File audioFile, int trackNumber){
        if (audioFile==null){
            throw new IllegalArgumentException("Audio file cannot be null");
        }
        this.audioFile=audioFile;
        this.trackNumber=trackNumber;
        this.displayName=extractDisplayName(audioFile.getName());
    }
    public AudioTrack(File audioFile, int trackNumber, String displayName, long duration){
        if (audioFile==null){
            throw new IllegalArgumentException("Audio file cannot be null");
        }
        this.audioFile=audioFile;
        this.trackNumber=trackNumber;
        this.displayName=displayName!=null?displayName:extractDisplayName(audioFile.getName());
        this.duration=duration;
    }
    private String extractDisplayName(String fileName){
        if (fileName.matches("^\\d{3}_.*")){
            return fileName.substring(4);
        }
        return fileName;
    }
    public File getAudioFile(){
        return audioFile;
    }
    public void setAudioFile(File audioFile){
        this.audioFile=audioFile;
    }
    public String getDisplayName(){
        return displayName;
    }
    public void setDisplayName(String displayName){
        this.displayName=displayName;
    }
    public int getTrackNumber(){
        return trackNumber;
    }
    public void setTrackNumber(int trackNumber){
        this.trackNumber=trackNumber;
    }
    public String getFormattedTrackNumber(){
        return String.format(PREFIX_FORMAT, trackNumber);
    }
    public long getDuration(){
        return duration;
    }
    public void setDuration(long duration){
        this.duration=duration;
    }
    public String getFormattedDuration(){
        if (duration <=0){
            return "--:--";
        }
        long minutes=(duration/1000)/60;
        long seconds=(duration/1000)%60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    public boolean isPlaying(){
        return isPlaying;
    }
    public void setPlaying(boolean playing){
        isPlaying=playing;
    }
    public long getCurrentPosition(){
        return currentPosition;
    }
    public void setCurrentPosition(long position){
        this.currentPosition=position;
    }
    public String getFormattedPosition(){
        if (currentPosition <=0){
            return "00:00";
        }
        long minutes=(currentPosition/1000)/60;
        long seconds=(currentPosition/1000)%60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    public double getPlaybackProgress(){
        if (duration <=0){
            return 0.0;
        }
        return (double) currentPosition/duration;
    }
    public boolean isSupportedFormat(){
        String name=audioFile.getName().toLowerCase();
        return name.endsWith(".mp3")||name.endsWith(".wav")||name.endsWith(".mid")||name.endsWith(".midi")||name.endsWith(".ogg")||name.endsWith(".flac");
    }
    public String getFileExtension(){
        String name=audioFile.getName();
        int dotIndex=name.lastIndexOf('.');
        if (dotIndex >0&&dotIndex < name.length() -1){
            return name.substring(dotIndex+1).toLowerCase();
        }
        return "";
    }
    public long getFileSize(){
        return audioFile.length();
    }
    public String getFormattedFileSize(){
        long size=getFileSize();
        if (size <1024){
            return size+" B";
        }
        else if (size <1024*1024){
            return String.format("%.1f KB", size/1024.0);
        }
        else{
            return String.format("%.1f MB", size/(1024.0*1024.0));
        }
    }
    @Override
    public boolean equals(Object o){
        if (this==o) return true;
        if (o==null||getClass()!=o.getClass()) return false;
        AudioTrack that=(AudioTrack) o;
        return trackNumber==that.trackNumber&&Objects.equals(audioFile.getAbsolutePath(), that.audioFile.getAbsolutePath());
    }
    @Override
    public int hashCode(){
        return Objects.hash(audioFile.getAbsolutePath(), trackNumber);
    }
    @Override
    public String toString(){
        return String.format("AudioTrack{number=%03d, name='%s', file='%s', duration=%dms}",trackNumber, displayName, audioFile.getName(), duration);
    }
    public String toDisplayString(){
        return String.format("%03d. %s (%s)", trackNumber, displayName, getFormattedDuration());
    }
}