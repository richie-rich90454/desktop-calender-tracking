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
 * centralized file system operations for the audio player system with 
 * automatic MP3-to-WAV conversion on upload.
 */

public class AudioFileManager{
    private static final String AUDIO_DIR_NAME="audio";
    private static final Pattern TRACK_PATTERN=Pattern.compile("^(\\d{3})_(.*)\\.(wav|mid|midi|ogg|flac)$");
    private static final String PREFIX_FORMAT="%03d";
    private static final String[] SUPPORTED_INPUT_EXTENSIONS={
"mp3", "wav", "mid", "midi", "ogg", "flac", "aac", "m4a", "aiff", "aif", "opus", "webm", "wma", "alac", "ape", "ac3", "dts", "caf", "3gp"};
    private static final String OUTPUT_EXTENSION="wav";
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
            throw new RuntimeException("Failed to create audio directory: "+e.getMessage(), e);
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
            List<Path> files=Files.list(audioDirectory).filter(Files::isRegularFile).filter(path ->{
                String fileName=path.getFileName().toString();
                Matcher matcher=TRACK_PATTERN.matcher(fileName);
                return matcher.matches();
            }).collect(Collectors.toList());
            for (Path filePath:files){
                String fileName=filePath.getFileName().toString();
                Matcher matcher=TRACK_PATTERN.matcher(fileName);
                if (matcher.matches()){
                    try{
                        int trackNumber=Integer.parseInt(matcher.group(1));
                        String displayName=matcher.group(2);
                        AudioTrack track=new AudioTrack(filePath.toFile(), trackNumber, displayName, 0);
                        tracks.add(track);
                    }
                    catch (NumberFormatException ignored){
                    }
                }
            }
            tracks.sort(Comparator.comparingInt(AudioTrack::getTrackNumber));
        }
        catch (IOException e){
            System.err.println("Error scanning audio files: "+e.getMessage());
        }
        return tracks;
    }
    public int getNextTrackNumber(){
        List<AudioTrack> tracks=scanAudioFiles();
        if (tracks.isEmpty()){
            return 1;
        }
        return tracks.stream().mapToInt(AudioTrack::getTrackNumber).max().orElse(0)+1;
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
        String baseName=removeExtension(originalName);
        boolean isMidi="mid".equalsIgnoreCase(extension)||"midi".equalsIgnoreCase(extension);
        String finalExtension=isMidi?extension.toLowerCase():OUTPUT_EXTENSION;
        String prefixedName=String.format(PREFIX_FORMAT+"_%s.%s", trackNumber, baseName, finalExtension);
        Path destinationPath=audioDirectory.resolve(prefixedName);
        if (isMidi){
            Files.copy(sourceFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("MIDI file uploaded as: "+prefixedName+" (kept original extension)");
        }
        else{
            convertToWav(sourceFile, destinationPath);
        }
        return new AudioTrack(destinationPath.toFile(), trackNumber, baseName, 0);
    }
    private void convertToWav(File sourceFile, Path destinationPath) throws IOException{
        if (!isFfmpegAvailable()){
            throw new IOException("FFmpeg is required for audio conversion but not found in system PATH");
        }
        ProcessBuilder processBuilder=new ProcessBuilder(
            "ffmpeg",
            "-i", sourceFile.getAbsolutePath(),
            "-map_metadata", "-1",
            "-f", "wav",
            "-acodec", "pcm_s16le",
            "-ar", "44100",
            "-ac", "2",
            "-y",
            destinationPath.toAbsolutePath().toString()
        );
        processBuilder.redirectErrorStream(true);
        try{
            Process process=processBuilder.start();
            int exitCode=process.waitFor();
            if (exitCode!=0){
                String errorOutput=new String(process.getInputStream().readAllBytes());
                throw new IOException("FFmpeg conversion failed (exit code "+exitCode+"): "+errorOutput);
            }
            if (!Files.exists(destinationPath)||Files.size(destinationPath) ==0){
                throw new IOException("Conversion completed but output file was not created");
            }
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IOException("Conversion interrupted", e);
        }
    }
    private boolean isFfmpegAvailable(){
        try{
            Process process=new ProcessBuilder("ffmpeg", "-version").start();
            int exitCode=process.waitFor();
            return exitCode==0;
        }
        catch (IOException|InterruptedException e){
            return false;
        }
    }
    public String getFfmpegInstallInstructions(){
        String os=System.getProperty("os.name").toLowerCase();
        if (os.contains("win")){
            return "Please install FFmpeg from: https://ffmpeg.org/download.html\n"+"Or using Chocolatey: choco install ffmpeg";
        }
        else if (os.contains("mac")){
            return "Install FFmpeg using Homebrew: brew install ffmpeg";
        }
        else if (os.contains("nix")||os.contains("nux")){
            return "Install FFmpeg using your package manager:\n"+"Ubuntu/Debian: sudo apt-get install ffmpeg\n"+"Fedora: sudo dnf install ffmpeg\n"+"Arch: sudo pacman -S ffmpeg";
        }
        else{
            return "Please install FFmpeg from: https://ffmpeg.org/download.html";
        }
    }
    public boolean deleteAudioTrack(AudioTrack track){
        File audioFile=track.getAudioFile();
        List<AudioTrack> currentTracks=scanAudioFiles();
        int deletedTrackNumber=track.getTrackNumber();
        if (audioFile.exists()&&audioFile.delete()){
            List<AudioTrack> tracksToRenumber=currentTracks.stream().filter(t -> t.getTrackNumber() >deletedTrackNumber).sorted(Comparator.comparingInt(AudioTrack::getTrackNumber)).collect(Collectors.toList());
            for (int i=0; i < tracksToRenumber.size(); i++){
                AudioTrack trackToRenumber=tracksToRenumber.get(i);
                int newNumber=deletedTrackNumber+i;
                try{
                    renameTrackFile(trackToRenumber, newNumber);
                }
                catch (IOException e){
                    System.err.println("Error renaming track "+trackToRenumber.getDisplayName()+": "+e.getMessage());
                }
            }
            return true;
        }
        return false;
    }
    public void reorderTracks(List<AudioTrack> newOrder){
        try{
            List<Path> tempPaths=new ArrayList<>();
            for (AudioTrack track:newOrder){
                String tempName="temp_"+System.currentTimeMillis()+"_"+track.getAudioFile().getName();
                Path tempPath=audioDirectory.resolve(tempName);
                Files.move(track.getAudioFile().toPath(), tempPath, StandardCopyOption.REPLACE_EXISTING);
                tempPaths.add(tempPath);
            }
            for (int i=0; i < newOrder.size(); i++){
                AudioTrack track=newOrder.get(i);
                Path tempPath=tempPaths.get(i);
                String currentName=track.getAudioFile().getName();
                String baseName=removeExtension(currentName);
                String extension=getFileExtension(currentName);
                if (baseName.contains("_")){
                    baseName=baseName.substring(baseName.indexOf("_")+1);
                }
                int newNumber=i+1;
                String newName=String.format(PREFIX_FORMAT+"_%s.%s", newNumber, baseName, extension);
                Path newPath=audioDirectory.resolve(newName);
                Files.move(tempPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                track.setTrackNumber(newNumber);
                track.setAudioFile(newPath.toFile());
            }
        }
        catch (IOException e){
            throw new RuntimeException("Error reordering tracks: "+e.getMessage(), e);
        }
    }
    private void renameTrackFile(AudioTrack track, int newNumber) throws IOException{
        File currentFile=track.getAudioFile();
        String currentName=currentFile.getName();
        String baseName=removeExtension(currentName);
        String extension=getFileExtension(currentName);
        if (baseName.contains("_")){
            baseName=baseName.substring(baseName.indexOf("_")+1);
        }
        String newName=String.format(PREFIX_FORMAT+"_%s.%s", newNumber, baseName, extension);
        Path newPath=audioDirectory.resolve(newName);
        Files.move(currentFile.toPath(), newPath, StandardCopyOption.REPLACE_EXISTING);
        track.setTrackNumber(newNumber);
        track.setAudioFile(newPath.toFile());
    }
    public boolean clearAllAudioFiles(){
        try{
            List<AudioTrack> tracks=scanAudioFiles();
            boolean allDeleted=true;
            for (AudioTrack track:tracks){
                if (!track.getAudioFile().delete()){
                    allDeleted=false;
                }
            }
            return allDeleted;
        }
        catch (Exception e){
            throw new RuntimeException("Error clearing audio files: "+e.getMessage(), e);
        }
    }
    public long getTotalAudioSize(){
        return scanAudioFiles().stream().mapToLong(AudioTrack::getFileSize).sum();
    }
    public String getFormattedTotalSize(){
        long size=getTotalAudioSize();
        if (size <1024){
            return size+" B";
        }
        else if (size <1024*1024){
            return String.format("%.1f KB", size/1024.0);
        }
        else if (size <1024*1024*1024){
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
        if (dotIndex >0&&dotIndex < fileName.length() -1){
            return fileName.substring(dotIndex+1).toLowerCase();
        }
        return "";
    }
    private String removeExtension(String fileName){
        int dotIndex=fileName.lastIndexOf('.');
        if (dotIndex >0){
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }
    private boolean isSupportedAudioExtension(String extension){
        if (extension==null||extension.isEmpty()){
            return false;
        }
        for (String supported:SUPPORTED_INPUT_EXTENSIONS){
            if (supported.equalsIgnoreCase(extension)){
                return true;
            }
        }
        return false;
    }
    public String getDirectoryInfo(){
        StringBuilder info=new StringBuilder();
        info.append("Audio Directory: ").append(audioDirectory).append("\n");
        info.append("Exists: ").append(Files.exists(audioDirectory)).append("\n");
        info.append("FFmpeg Available: ").append(isFfmpegAvailable()).append("\n");
        if (!isFfmpegAvailable()){
            info.append("\n").append(getFfmpegInstallInstructions()).append("\n");
        }
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