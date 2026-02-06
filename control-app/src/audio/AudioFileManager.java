package audio;

/**
 * Manages audio files in the audio directory.
 *
 * Responsibilities:
 * - Handle file upload and renaming with numeric prefixes
 * - Maintain playlist order via filename prefixes
 * - Scan directory for audio files
 * - Rename files during reordering
 *
 * Java data types used:
 * - File
 * - List<AudioTrack>
 * - Path
 *
 * Design intent:
 * Centralized file operations for the audio player system.
 */
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

public class AudioFileManager {
    private static final String AUDIO_DIR_NAME="audio";
    private static final Pattern TRACK_PATTERN=Pattern.compile("^(\\d{3})_(.*)$");
    private static final String PREFIX_FORMAT="%03d";
    private Path audioDirectory;
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
                System.out.println("Created audio directory: "+audioDirectory);
            }
        }
        catch (IOException e){
            System.err.println("Failed to create audio directory: "+e.getMessage());
        }
    }
    public Path getAudioDirectory(){
        return audioDirectory;
    }
    public List<AudioTrack> scanAudioFiles(){
        List<AudioTrack> tracks=new ArrayList<>();
        try{
            if (!Files.exists(audioDirectory)){
                return tracks;
            }
            List<Path> files=Files.list(audioDirectory).filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path filePath:files){
                String fileName=filePath.getFileName().toString();
                Matcher matcher=TRACK_PATTERN.matcher(fileName);
                if (matcher.matches()){
                    try{
                        int trackNumber=Integer.parseInt(matcher.group(1));
                        String displayName=matcher.group(2);
                        File audioFile=filePath.toFile();
                        AudioTrack track=new AudioTrack(audioFile, trackNumber, displayName, 0);
                        if (track.isSupportedFormat()){
                            tracks.add(track);
                        }
                    }
                    catch (NumberFormatException e){
                        System.err.println("Invalid track number in file: "+fileName);
                    }
                }
            }
            tracks.sort(Comparator.comparingInt(AudioTrack::getTrackNumber));
            return tracks;
        }
        catch (IOException e){
            System.err.println("Error scanning audio directory: "+e.getMessage());
            return new ArrayList<>();
        }
    }
    public int getNextTrackNumber(){
        List<AudioTrack> tracks=scanAudioFiles();
        if (tracks.isEmpty()){
            return 0;
        }
        int maxNumber=tracks.stream().mapToInt(AudioTrack::getTrackNumber).max().orElse(-1);
        return maxNumber+1;
    }
    public AudioTrack uploadAudioFile(File sourceFile) throws IOException{
        if (!sourceFile.exists()||!sourceFile.isFile()){
            throw new IOException("Source file does not exist: "+sourceFile.getPath());
        }
        String originalName=sourceFile.getName();
        String extension=getFileExtension(originalName);
        if (!isSupportedAudioExtension(extension)){
            throw new IOException("Unsupported audio format: "+extension);
        }
        int trackNumber=getNextTrackNumber();
        String prefixedName=String.format(PREFIX_FORMAT+"_%s", trackNumber, originalName);
        Path destinationPath=audioDirectory.resolve(prefixedName);
        Files.copy(sourceFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        File audioFile=destinationPath.toFile();
        AudioTrack track=new AudioTrack(audioFile, trackNumber);
        System.out.println("Uploaded audio file: "+originalName+" -> "+prefixedName);
        return track;
    }
    public boolean deleteAudioTrack(AudioTrack track){
        try{
            File audioFile=track.getAudioFile();
            if (audioFile.exists()){
                boolean deleted=audioFile.delete();
                if (deleted){
                    renumberTracksAfterDeletion(track.getTrackNumber());
                    return true;
                }
            }
            return false;
        }
        catch (Exception e){
            System.err.println("Error deleting audio track: "+e.getMessage());
            return false;
        }
    }
    public void reorderTracks(List<AudioTrack> newOrder){
        try{
            List<AudioTrack> currentTracks=scanAudioFiles();
            for (int i=0;i<newOrder.size();i++){
                AudioTrack track=newOrder.get(i);
                int newNumber=i;
                if (track.getTrackNumber()!=newNumber){
                    renameTrackFile(track, newNumber);
                }
            }
            verifyTrackOrder();
        }
        catch (Exception e){
            System.err.println("Error reordering tracks: "+e.getMessage());
        }
    }
    private void renameTrackFile(AudioTrack track, int newNumber) throws IOException{
        File currentFile=track.getAudioFile();
        String currentName=currentFile.getName();
        Matcher matcher=TRACK_PATTERN.matcher(currentName);
        if (!matcher.matches()){
            throw new IOException("Invalid track filename format: "+currentName);
        }
        String displayName=matcher.group(2);
        String newName=String.format(PREFIX_FORMAT+"_%s", newNumber, displayName);
        Path newPath=audioDirectory.resolve(newName);
        Files.move(currentFile.toPath(), newPath, StandardCopyOption.REPLACE_EXISTING);
        track.setTrackNumber(newNumber);
        track.setAudioFile(newPath.toFile());
    }
    private void renumberTracksAfterDeletion(int deletedNumber){
        try{
            List<AudioTrack> tracks=scanAudioFiles();
            List<AudioTrack> tracksToRenumber=tracks.stream().filter(track->track.getTrackNumber()>deletedNumber).sorted(Comparator.comparingInt(AudioTrack::getTrackNumber)).collect(Collectors.toList());
            for (int i=0;i<tracksToRenumber.size();i++){
                AudioTrack track=tracksToRenumber.get(i);
                int newNumber=deletedNumber+i;
                renameTrackFile(track, newNumber);
            }
        }
        catch (Exception e){
            System.err.println("Error renumbering tracks after deletion: "+e.getMessage());
        }
    }
    private void verifyTrackOrder(){
        List<AudioTrack> tracks=scanAudioFiles();
        for (int i=0;i<tracks.size();i++){
            AudioTrack track=tracks.get(i);
            if (track.getTrackNumber()!=i){
                System.err.println("Track order mismatch: "+track.getTrackNumber()+" != "+i);
            }
        }
    }
    public boolean clearAllAudioFiles(){
        try{
            List<AudioTrack> tracks=scanAudioFiles();
            boolean allDeleted=true;
            for (AudioTrack track:tracks){
                if (!track.getAudioFile().delete()){
                    allDeleted=false;
                    System.err.println("Failed to delete: "+track.getAudioFile().getName());
                }
            }
            return allDeleted;
        }
        catch (Exception e){
            System.err.println("Error clearing audio files: "+e.getMessage());
            return false;
        }
    }
    public long getTotalAudioSize(){
        List<AudioTrack> tracks=scanAudioFiles();
        return tracks.stream().mapToLong(AudioTrack::getFileSize).sum();
    }
    public String getFormattedTotalSize(){
        long size=getTotalAudioSize();
        if (size<1024){
            return size+" B";
        }
        else if (size<1024*1024){
            return String.format("%.1f KB", size/1024.0);
        }
        else if (size<1024*1024*1024){
            return String.format("%.1f MB", size/(1024.0*1024.0));
        }
        else{
            return String.format("%.1f GB", size/(1024.0*1024.0*1024.0));
        }
    }
    public int getAudioFileCount(){
        return scanAudioFiles().size();
    }
    private String getFileExtension(String fileName){
        int dotIndex=fileName.lastIndexOf('.');
        if (dotIndex>0&&dotIndex<fileName.length()-1){
            return fileName.substring(dotIndex+1).toLowerCase();
        }
        return "";
    }
    private boolean isSupportedAudioExtension(String extension){
        if (extension==null||extension.isEmpty()){
            return false;
        }
        switch (extension.toLowerCase()){
            case "mp3":
            case "wav":
            case "mid":
            case "midi":
            case "ogg":
            case "flac":
                return true;
            default:
                return false;
        }
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