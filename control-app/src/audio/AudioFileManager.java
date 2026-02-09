package audio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages audio file operations including upload, deletion, scanning, and
 * maintaining playlist order via numeric filename prefixes. Provides
 * centralized
 * file system operations for the audio player system with track numbering and
 * reordering capabilities.
 */
public class AudioFileManager {
    private static final String AUDIO_DIR_NAME="audio";
    private static final Pattern TRACK_PATTERN=Pattern.compile("^(\\d{3})_(.*)$");
    private static final String PREFIX_FORMAT="%03d";
    private final Path audioDirectory;
    public AudioFileManager(){
        String userHome=System.getProperty("user.home");
        this.audioDirectory=Paths.get(userHome, ".calendarapp", AUDIO_DIR_NAME);
        ensureAudioDirectory();
    }
    public AudioFileManager(String customPath){
        this.audioDirectory=Paths.get(customPath);
        ensureAudioDirectory();
    }
    private void ensureAudioDirectory(){
        try{
            if (!Files.exists(audioDirectory)){
                Files.createDirectories(audioDirectory);
            }
        }
        catch (IOException e){
            throw new RuntimeException("Failed to create audio directory: " + e.getMessage(), e);
        }
    }
    public Path getAudioDirectory(){
        return audioDirectory;
    }
    public List<AudioTrack> scanAudioFiles(){
        List<AudioTrack> tracks=new ArrayList<>();
        if (!Files.exists(audioDirectory)){
            return tracks;
        }
        try{
            List<Path> files=Files.list(audioDirectory).filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path filePath : files){
                String fileName=filePath.getFileName().toString();
                Matcher matcher=TRACK_PATTERN.matcher(fileName);
                if (matcher.matches()){
                    try{
                        int trackNumber=Integer.parseInt(matcher.group(1));
                        String displayName=matcher.group(2);
                        AudioTrack track=new AudioTrack(filePath.toFile(), trackNumber, displayName, 0);
                        if (track.isSupportedFormat()){
                            tracks.add(track);
                        }
                    }
                    catch (NumberFormatException ignored){
                    }
                }
            }
            tracks.sort(Comparator.comparingInt(AudioTrack::getTrackNumber));
        }
        catch (IOException ignored){

        }
        return tracks;
    }
    public int getNextTrackNumber(){
        List<AudioTrack> tracks=scanAudioFiles();
        if (tracks.isEmpty()){
            return 0;
        }
        return tracks.stream().mapToInt(AudioTrack::getTrackNumber).max().orElse(-1) + 1;
    }
    public AudioTrack uploadAudioFile(File sourceFile) throws IOException{
        if (!sourceFile.exists() || !sourceFile.isFile()){
            throw new IOException("Source file does not exist: " + sourceFile.getPath());
        }
        String originalName=sourceFile.getName();
        String extension=getFileExtension(originalName);
        if (!isSupportedAudioExtension(extension)){
            throw new IOException("Unsupported audio format: " + extension);
        }
        int trackNumber=getNextTrackNumber();
        String prefixedName=String.format(PREFIX_FORMAT + "_%s", trackNumber, originalName);
        Path destinationPath=audioDirectory.resolve(prefixedName);
        Files.copy(sourceFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        return new AudioTrack(destinationPath.toFile(), trackNumber);
    }
    public boolean deleteAudioTrack(AudioTrack track){
        try{
            File audioFile=track.getAudioFile();
            if (audioFile.exists() && audioFile.delete()){
                renumberTracksAfterDeletion(track.getTrackNumber());
                return true;
            }
            return false;
        }
        catch (Exception e){
            throw new RuntimeException("Error deleting audio track: " + e.getMessage(), e);
        }
    }
    public void reorderTracks(List<AudioTrack> newOrder){
        try{
            for (int i=0; i < newOrder.size(); i++){
                AudioTrack track=newOrder.get(i);
                int newNumber=i;
                if (track.getTrackNumber() != newNumber){
                    renameTrackFile(track, newNumber);
                }
            }
        }
        catch (Exception e){
            throw new RuntimeException("Error reordering tracks: " + e.getMessage(), e);
        }
    }
    private void renameTrackFile(AudioTrack track, int newNumber) throws IOException{
        File currentFile=track.getAudioFile();
        String currentName=currentFile.getName();
        Matcher matcher=TRACK_PATTERN.matcher(currentName);
        if (!matcher.matches()){
            throw new IOException("Invalid track filename format: " + currentName);
        }
        String displayName=matcher.group(2);
        String newName=String.format(PREFIX_FORMAT + "_%s", newNumber, displayName);
        Path newPath=audioDirectory.resolve(newName);
        Files.move(currentFile.toPath(), newPath, StandardCopyOption.REPLACE_EXISTING);
        track.setTrackNumber(newNumber);
        track.setAudioFile(newPath.toFile());
    }
    private void renumberTracksAfterDeletion(int deletedNumber){
        try{
            List<AudioTrack> tracks=scanAudioFiles();
            List<AudioTrack> tracksToRenumber=tracks.stream().filter(track -> track.getTrackNumber() > deletedNumber).sorted(Comparator.comparingInt(AudioTrack::getTrackNumber)).collect(Collectors.toList());
            for (int i=0; i < tracksToRenumber.size(); i++){
                AudioTrack track=tracksToRenumber.get(i);
                int newNumber=deletedNumber + i;
                renameTrackFile(track, newNumber);
            }
        }
        catch (Exception e){
            throw new RuntimeException("Error renumbering tracks after deletion: " + e.getMessage(), e);
        }
    }
    public boolean clearAllAudioFiles(){
        try{
            List<AudioTrack> tracks=scanAudioFiles();
            boolean allDeleted=true;
            for (AudioTrack track : tracks){
                if (!track.getAudioFile().delete()){
                    allDeleted=false;
                }
            }
            return allDeleted;
        }
        catch (Exception e){
            throw new RuntimeException("Error clearing audio files: " + e.getMessage(), e);
        }
    }
    public long getTotalAudioSize(){
        return scanAudioFiles().stream().mapToLong(AudioTrack::getFileSize).sum();
    }
    public String getFormattedTotalSize(){
        long size=getTotalAudioSize();
        if (size < 1024){
            return size + " B";
        }
        else if (size < 1024 * 1024){
            return String.format("%.1f KB", size / 1024.0);
        }
        else if (size < 1024 * 1024 * 1024){
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
        else{
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
    public int getAudioFileCount(){
        return scanAudioFiles().size();
    }
    private String getFileExtension(String fileName){
        int dotIndex=fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1){
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }
    private boolean isSupportedAudioExtension(String extension){
        if (extension == null || extension.isEmpty()){
            return false;
        }
        return switch (extension.toLowerCase()){
            case "mp3", "wav", "mid", "midi", "ogg", "flac" -> true;
            default -> false;
        };
    }
    public String getDirectoryInfo(){
        StringBuilder info=new StringBuilder();
        info.append("Audio Directory: ").append(audioDirectory).append("\n");
        info.append("Exists: ").append(Files.exists(audioDirectory)).append("\n");
        if (Files.exists(audioDirectory)){
            try{
                long fileCount=Files.list(audioDirectory).filter(Files::isRegularFile).count();
                info.append("File Count: ").append(fileCount).append("\n");
                info.append("Total Size: ").append(getFormattedTotalSize()).append("\n");
            }
            catch (IOException e){
                info.append("Error counting files: ").append(e.getMessage()).append("\n");
            }
        }
        return info.toString();
    }
}