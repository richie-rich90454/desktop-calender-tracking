// ==================== audio_player.h ====================
// Public interface for the audio playback system in Calendar Overlay.
// Provides:
//   - AudioTrack: data structure for a single audio file.
//   - AudioPlayerEngine: core playback engine (supports MP3, WAV, M4A, WMA via MF; MIDI via MCI).
//   - AudioFileManager: file management for the user's audio library.
//
// Volume control is intentionally omitted – system defaults are used.
// All playback state is tracked; call processEvents() regularly from the UI thread.

#pragma once

// Ensure Windows headers don't pull in unnecessary cruft or min/max macros.
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

#include <windows.h>
#include <string>
#include <vector>
#include <memory>
#include <functional>
#include <filesystem>
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <wrl/client.h>
#include <mutex>  

// Link necessary Media Foundation libraries.
#pragma comment(lib, "mf.lib")
#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mfreadwrite.lib")
#pragma comment(lib, "mfuuid.lib")

namespace fs = std::filesystem;
using Microsoft::WRL::ComPtr;

namespace CalendarOverlay::Audio
{
    // ============================================================================
    // Playback state enumeration
    // ============================================================================
    enum class PlaybackState
    {
        STOPPED,   // No track loaded or playback finished.
        PLAYING,   // Currently playing.
        PAUSED     // Paused – can resume.
    };

    // ============================================================================
    // AudioTrack – represents a single audio file in the library.
    // ============================================================================
    struct AudioTrack
    {
        std::wstring filePath;      // Full path to the audio file.
        std::wstring fileName;      // Just the file name (extracted from path).
        std::wstring displayName;   // Name shown in UI (may be edited by user).
        int trackNumber;            // Order number in the playlist (1‑based).
        long duration;              // Total length in milliseconds.
        mutable long currentPosition; // Last known playback position (ms). Mutable because it may be updated during const queries.

        AudioTrack() : trackNumber(0), duration(0), currentPosition(0) {}

        // Returns duration formatted as "MM:SS".
        std::wstring getFormattedDuration() const;

        // Checks whether the file extension is one of the supported types.
        bool isSupportedFormat() const;
    };

    // ============================================================================
    // AudioPlayerEngine – handles actual audio playback.
    // Uses Media Foundation for most formats and MCI for MIDI.
    // ============================================================================
    class AudioPlayerEngine
    {
    public:
        AudioPlayerEngine();
        ~AudioPlayerEngine();

        // ------------------------------------------------------------------------
        // Playback control
        // ------------------------------------------------------------------------
        bool play(const AudioTrack& track);   // Start playing the given track (stops current).
        bool pause();                          // Pause current playback.
        bool resume();                         // Resume from pause.
        bool stop();                            // Stop playback and rewind.
        bool seek(long positionMillis);         // Jump to a position (in milliseconds).

        // ------------------------------------------------------------------------
        // State queries
        // ------------------------------------------------------------------------
        PlaybackState getPlaybackState() const { return state; }
        AudioTrack getCurrentTrack() const { return currentTrack; }
        long getCurrentPosition() const;        // Current position in milliseconds.
        long getDuration() const { return currentTrack.duration; }
        bool isPlaying() const { return state == PlaybackState::PLAYING; }
        bool isPaused() const { return state == PlaybackState::PAUSED; }
        bool isStopped() const { return state == PlaybackState::STOPPED; }

        // ------------------------------------------------------------------------
        // Callback and cleanup
        // ------------------------------------------------------------------------
        void setOnTrackEnd(std::function<void()> callback) { onTrackEnd = callback; }
        void cleanup();                         // Release all resources (called automatically in destructor).

        // ------------------------------------------------------------------------
        // Error reporting
        // ------------------------------------------------------------------------
        std::wstring getLastError() const;      // Retrieve the last error message.

        // ------------------------------------------------------------------------
        // Event processing – MUST be called periodically from the main UI thread
        // (e.g., on a timer) to handle end-of-track events and update MIDI status.
        // ------------------------------------------------------------------------
        void processEvents();

    private:
        // Media Foundation session management (for non-MIDI files)
        bool CreateMediaSession(const AudioTrack& track);
        void DestroyMediaSession();
        void ProcessSessionEvents();   // Internal event pump for Media Foundation.

        // MIDI playback helpers (MCI only – volume control not supported)
        bool IsMidiFile(const std::wstring& filePath) const;
        bool PlayMidi(const AudioTrack& track);
        bool PauseMidi();
        bool ResumeMidi();
        bool StopMidi();
        bool SeekMidi(long positionMillis);
        long GetMidiPosition() const;
        long GetMidiDuration() const;
        void CloseMidi();              // Close MCI device and clear alias.
        void CheckMidiStatus();        // Poll MCI to detect end-of-track.

        // Internal error setter (thread‑safe via mutex).
        void SetError(const std::wstring& err);

        // Current state
        AudioTrack currentTrack;
        PlaybackState state;
        std::function<void()> onTrackEnd;   // Called when a track finishes naturally.

        // Media Foundation interfaces
        ComPtr<IMFMediaSession>       m_spSession;        // The media session.
        ComPtr<IMFMediaSource>        m_spSource;         // Source for current non-MIDI track.
        ComPtr<IMFMediaEventGenerator> m_spEventGen;      // For non‑blocking event polling.

        // MIDI‑specific members
        bool            m_isMidi;      // True if currently playing a MIDI file.
        std::wstring    m_midiAlias;   // Unique alias used for MCI commands.

        // Last error string (protected by mutex for thread safety).
        std::wstring m_lastError;
        mutable std::mutex m_errorMutex;
    };

    // ============================================================================
    // AudioFileManager – manages the folder where audio files are stored.
    // Handles scanning, copying, deleting, and generating unique filenames.
    // ============================================================================
    class AudioFileManager
    {
    public:
        AudioFileManager();
        ~AudioFileManager();

        // Returns a list of all supported audio files found in the managed directory.
        std::vector<AudioTrack> scanAudioFiles();

        // Copies a user‑selected file into the audio directory and returns an AudioTrack for it.
        AudioTrack uploadAudioFile(const std::wstring& filePath);

        // Deletes the physical file associated with the track.
        bool deleteAudioTrack(const AudioTrack& track);

        // Deletes all audio files in the managed directory (resets track numbering).
        bool clearAllAudioFiles();

        // Returns the full path to the audio directory.
        std::wstring getAudioDirectory() const;

    private:
        std::wstring audioDirectory;      // Path where audio files are stored.
        int nextTrackNumber;               // Next track number to assign when uploading.

        // Generates a unique filename inside the audio directory (appends " (n)" if needed).
        std::wstring getUniqueFileName(const std::wstring& originalName);

        // Copies sourcePath into the audio directory; returns destination path in destPath.
        bool copyFileToAudioDir(const std::wstring& sourcePath, std::wstring& destPath);

        // Creates an AudioTrack object from a file path and assigns a track number.
        AudioTrack createTrackFromFile(const std::wstring& filePath, int trackNumber);

        // Queries the duration of an audio file (in milliseconds) using Media Foundation.
        long getAudioDuration(const std::wstring& filePath);
    };
}