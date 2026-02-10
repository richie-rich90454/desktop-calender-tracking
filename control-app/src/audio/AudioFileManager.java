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
public class AudioFileManager {
    private static final String AUDIO_DIR_NAME = "audio";
    private static final Pattern TRACK_PATTERN = Pattern.compile("^(\\d{3})_(.*)\\.(wav|mid|midi|ogg|flac)$");
    private static final String PREFIX_FORMAT = "%03d";
    
    // Supported formats (WAV is native, others converted to WAV on upload)
    private static final String[] SUPPORTED_INPUT_EXTENSIONS = {"mp3", "wav", "mid", "midi", "ogg", "flac"};
    private static final String OUTPUT_EXTENSION = "wav"; // Always convert to WAV

    private final Path audioDirectory;

    public AudioFileManager() {
        String userHome = System.getProperty("user.home");
        this.audioDirectory = Paths.get(userHome, ".calendarapp", AUDIO_DIR_NAME);
        ensureAudioDirectory();
    }

    public AudioFileManager(String customPath) {
        this.audioDirectory = Paths.get(customPath);
        ensureAudioDirectory();
    }

    private void ensureAudioDirectory() {
        try {
            if (!Files.exists(audioDirectory)) {
                Files.createDirectories(audioDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create audio directory: " + e.getMessage(), e);
        }
    }

    public Path getAudioDirectory() {
        return audioDirectory;
    }

    public List<AudioTrack> scanAudioFiles() {
        List<AudioTrack> tracks = new ArrayList<>();
        if (!Files.exists(audioDirectory)) {
            return tracks;
        }
        try {
            List<Path> files = Files.list(audioDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        Matcher matcher = TRACK_PATTERN.matcher(fileName);
                        return matcher.matches();
                    })
                    .collect(Collectors.toList());

            for (Path filePath : files) {
                String fileName = filePath.getFileName().toString();
                Matcher matcher = TRACK_PATTERN.matcher(fileName);
                if (matcher.matches()) {
                    try {
                        int trackNumber = Integer.parseInt(matcher.group(1));
                        String displayName = matcher.group(2);
                        String extension = matcher.group(3);
                        
                        // All files are now WAV after conversion
                        AudioTrack track = new AudioTrack(filePath.toFile(), trackNumber, displayName, 0);
                        tracks.add(track);
                    } catch (NumberFormatException ignored) {
                        // Skip invalid numbered files
                    }
                }
            }
            tracks.sort(Comparator.comparingInt(AudioTrack::getTrackNumber));
        } catch (IOException e) {
            System.err.println("Error scanning audio files: " + e.getMessage());
        }
        return tracks;
    }

    public int getNextTrackNumber() {
        List<AudioTrack> tracks = scanAudioFiles();
        if (tracks.isEmpty()) {
            return 1;
        }
        return tracks.stream().mapToInt(AudioTrack::getTrackNumber).max().orElse(0) + 1;
    }

    public AudioTrack uploadAudioFile(File sourceFile) throws IOException {
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            throw new IOException("Source file does not exist: " + sourceFile.getPath());
        }

        String originalName = sourceFile.getName();
        String extension = getFileExtension(originalName);
        
        // Check if format is supported
        if (!isSupportedAudioExtension(extension)) {
            throw new IOException("Unsupported audio format: " + extension);
        }

        int trackNumber = getNextTrackNumber();
        String baseName = removeExtension(originalName);
        String prefixedName = String.format(PREFIX_FORMAT + "_%s." + OUTPUT_EXTENSION, trackNumber, baseName);
        Path destinationPath = audioDirectory.resolve(prefixedName);

        // Convert MP3 to WAV if needed, otherwise copy directly
        if ("mp3".equalsIgnoreCase(extension) || 
            "ogg".equalsIgnoreCase(extension) || 
            "flac".equalsIgnoreCase(extension)) {
            convertToWav(sourceFile, destinationPath);
        } else if ("mid".equalsIgnoreCase(extension) || "midi".equalsIgnoreCase(extension)) {
            // MIDI files can be kept as-is since Java supports them natively
            Files.copy(sourceFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            // WAV files can be copied directly
            Files.copy(sourceFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return new AudioTrack(destinationPath.toFile(), trackNumber);
    }

    /**
     * Convert an audio file to WAV format using FFmpeg.
     * This provides lossless conversion for MP3, OGG, FLAC files.
     */
    private void convertToWav(File sourceFile, Path destinationPath) throws IOException {
        // Check if FFmpeg is available
        if (!isFfmpegAvailable()) {
            throw new IOException("FFmpeg is required for audio conversion but not found in system PATH");
        }

        // Build FFmpeg command
        ProcessBuilder processBuilder = new ProcessBuilder(
            "ffmpeg",
            "-i", sourceFile.getAbsolutePath(),      // Input file
            "-acodec", "pcm_s16le",                  // 16-bit PCM (standard WAV)
            "-ar", "44100",                          // Sample rate: 44.1kHz
            "-ac", "2",                              // Stereo
            "-y",                                    // Overwrite output file
            destinationPath.toAbsolutePath().toString()
        );

        processBuilder.redirectErrorStream(true);
        
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                // Read error output
                String errorOutput = new String(process.getInputStream().readAllBytes());
                throw new IOException("FFmpeg conversion failed (exit code " + exitCode + "): " + errorOutput);
            }
            
            // Verify the output file was created
            if (!Files.exists(destinationPath) || Files.size(destinationPath) == 0) {
                throw new IOException("Conversion completed but output file was not created");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Conversion interrupted", e);
        }
    }

    /**
     * Check if FFmpeg is available on the system.
     */
    private boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Get FFmpeg installation instructions for the user.
     */
    public String getFfmpegInstallInstructions() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return "Please install FFmpeg from: https://ffmpeg.org/download.html\n" +
                   "Or using Chocolatey: choco install ffmpeg";
        } else if (os.contains("mac")) {
            return "Install FFmpeg using Homebrew: brew install ffmpeg";
        } else if (os.contains("nix") || os.contains("nux")) {
            return "Install FFmpeg using your package manager:\n" +
                   "Ubuntu/Debian: sudo apt-get install ffmpeg\n" +
                   "Fedora: sudo dnf install ffmpeg\n" +
                   "Arch: sudo pacman -S ffmpeg";
        } else {
            return "Please install FFmpeg from: https://ffmpeg.org/download.html";
        }
    }

    /**
     * Simple method for systems without FFmpeg (fallback - copies file as-is)
     */
    private void copyWithoutConversion(File sourceFile, Path destinationPath) throws IOException {
        System.err.println("Warning: FFmpeg not available, copying file without conversion");
        System.err.println("Audio playback may not work properly for MP3 files");
        Files.copy(sourceFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean deleteAudioTrack(AudioTrack track) {
        try {
            File audioFile = track.getAudioFile();
            if (audioFile.exists() && audioFile.delete()) {
                renumberTracksAfterDeletion(track.getTrackNumber());
                return true;
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Error deleting audio track: " + e.getMessage(), e);
        }
    }

    public void reorderTracks(List<AudioTrack> newOrder) {
        try {
            // First, move all files to temporary names
            List<Path> tempPaths = new ArrayList<>();
            for (AudioTrack track : newOrder) {
                String tempName = "temp_" + System.currentTimeMillis() + "_" + track.getAudioFile().getName();
                Path tempPath = audioDirectory.resolve(tempName);
                Files.move(track.getAudioFile().toPath(), tempPath, StandardCopyOption.REPLACE_EXISTING);
                tempPaths.add(tempPath);
            }
            
            // Then rename to final names with correct numbering
            for (int i = 0; i < newOrder.size(); i++) {
                AudioTrack track = newOrder.get(i);
                Path tempPath = tempPaths.get(i);
                
                String currentName = track.getAudioFile().getName();
                String baseName = removeExtension(currentName);
                if (baseName.contains("_")) {
                    baseName = baseName.substring(baseName.indexOf("_") + 1);
                }
                
                int newNumber = i + 1;
                String newName = String.format(PREFIX_FORMAT + "_%s." + OUTPUT_EXTENSION, newNumber, baseName);
                Path newPath = audioDirectory.resolve(newName);
                
                Files.move(tempPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                track.setTrackNumber(newNumber);
                track.setAudioFile(newPath.toFile());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reordering tracks: " + e.getMessage(), e);
        }
    }

    private void renameTrackFile(AudioTrack track, int newNumber) throws IOException {
        File currentFile = track.getAudioFile();
        String currentName = currentFile.getName();
        
        // Extract base name (remove number prefix and extension)
        String baseName = removeExtension(currentName);
        if (baseName.contains("_")) {
            baseName = baseName.substring(baseName.indexOf("_") + 1);
        }
        
        String newName = String.format(PREFIX_FORMAT + "_%s." + OUTPUT_EXTENSION, newNumber, baseName);
        Path newPath = audioDirectory.resolve(newName);

        Files.move(currentFile.toPath(), newPath, StandardCopyOption.REPLACE_EXISTING);
        track.setTrackNumber(newNumber);
        track.setAudioFile(newPath.toFile());
    }

    private void renumberTracksAfterDeletion(int deletedNumber) {
        try {
            List<AudioTrack> tracks = scanAudioFiles();
            List<AudioTrack> tracksToRenumber = tracks.stream()
                    .filter(track -> track.getTrackNumber() > deletedNumber)
                    .sorted(Comparator.comparingInt(AudioTrack::getTrackNumber))
                    .collect(Collectors.toList());

            for (int i = 0; i < tracksToRenumber.size(); i++) {
                AudioTrack track = tracksToRenumber.get(i);
                int newNumber = deletedNumber + i;
                if (newNumber != track.getTrackNumber()) {
                    renameTrackFile(track, newNumber);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error renumbering tracks after deletion: " + e.getMessage(), e);
        }
    }

    public boolean clearAllAudioFiles() {
        try {
            List<AudioTrack> tracks = scanAudioFiles();
            boolean allDeleted = true;
            for (AudioTrack track : tracks) {
                if (!track.getAudioFile().delete()) {
                    allDeleted = false;
                }
            }
            return allDeleted;
        } catch (Exception e) {
            throw new RuntimeException("Error clearing audio files: " + e.getMessage(), e);
        }
    }

    public long getTotalAudioSize() {
        return scanAudioFiles().stream().mapToLong(AudioTrack::getFileSize).sum();
    }

    public String getFormattedTotalSize() {
        long size = getTotalAudioSize();
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public int getAudioFileCount() {
        return scanAudioFiles().size();
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    private String removeExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private boolean isSupportedAudioExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        for (String supported : SUPPORTED_INPUT_EXTENSIONS) {
            if (supported.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    public String getDirectoryInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Audio Directory: ").append(audioDirectory).append("\n");
        info.append("Exists: ").append(Files.exists(audioDirectory)).append("\n");
        info.append("FFmpeg Available: ").append(isFfmpegAvailable()).append("\n");
        if (!isFfmpegAvailable()) {
            info.append("\n").append(getFfmpegInstallInstructions()).append("\n");
        }
        if (Files.exists(audioDirectory)) {
            try {
                long fileCount = Files.list(audioDirectory).filter(Files::isRegularFile).count();
                info.append("File Count: ").append(fileCount).append("\n");
                info.append("Total Size: ").append(getFormattedTotalSize()).append("\n");
            } catch (IOException e) {
                info.append("Error counting files: ").append(e.getMessage()).append("\n");
            }
        }
        return info.toString();
    }
}