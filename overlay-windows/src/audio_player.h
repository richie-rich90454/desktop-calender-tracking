// ==================== audio_player.h ====================
#pragma once

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
#pragma comment(lib, "mf.lib")
#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mfreadwrite.lib")
#pragma comment(lib, "mfuuid.lib")

namespace fs = std::filesystem;
using Microsoft::WRL::ComPtr;

namespace CalendarOverlay::Audio
{
    enum class PlaybackState
    {
        STOPPED,
        PLAYING,
        PAUSED
    };

    struct AudioTrack
    {
        std::wstring filePath;
        std::wstring fileName;
        std::wstring displayName;
        int trackNumber;
        long duration;                // milliseconds
        mutable long currentPosition; // milliseconds

        AudioTrack() : trackNumber(0), duration(0), currentPosition(0) {}
        std::wstring getFormattedDuration() const;
        bool isSupportedFormat() const;
    };

    class AudioPlayerEngine
    {
    public:
        AudioPlayerEngine();
        ~AudioPlayerEngine();

        // Playback control
        bool play(const AudioTrack& track);
        bool pause();
        bool resume();
        bool stop();
        bool seek(long positionMillis);
        bool setVolume(float volume);
        bool setMuted(bool muted);

        // State queries
        PlaybackState getPlaybackState() const { return state; }
        AudioTrack getCurrentTrack() const { return currentTrack; }
        long getCurrentPosition() const;
        long getDuration() const { return currentTrack.duration; }
        float getVolume() const { return volume; }
        bool isMuted() const { return muted; }
        bool isPlaying() const { return state == PlaybackState::PLAYING; }
        bool isPaused() const { return state == PlaybackState::PAUSED; }
        bool isStopped() const { return state == PlaybackState::STOPPED; }

        // Callback and cleanup
        void setOnTrackEnd(std::function<void()> callback) { onTrackEnd = callback; }
        void cleanup();

        // Error reporting
        std::wstring getLastError() const;

        // MUST be called periodically from main UI thread (e.g., timer)
        void processEvents();

    private:
        // Media Foundation session management
        bool CreateMediaSession(const AudioTrack& track);
        void DestroyMediaSession();
        void ProcessSessionEvents();   // internal event pump

        // MIDI playback helpers (MCI / midiOut)
        bool IsMidiFile(const std::wstring& filePath) const;
        bool PlayMidi(const AudioTrack& track);
        bool PauseMidi();
        bool ResumeMidi();
        bool StopMidi();
        bool SeekMidi(long positionMillis);
        long GetMidiPosition() const;
        long GetMidiDuration() const;
        bool SetMidiVolume(float vol);
        bool SetMidiMuted(bool mute);
        void CloseMidi();              // close device and clear alias
        void CheckMidiStatus();       // poll for track end

        // Error helper
        void SetError(const std::wstring& err);

        // State
        AudioTrack currentTrack;
        PlaybackState state;
        float volume;
        bool muted;
        std::function<void()> onTrackEnd;

        // Media Foundation COM pointers
        ComPtr<IMFMediaSession>       m_spSession;
        ComPtr<IMFMediaSource>        m_spSource;
        ComPtr<IMFMediaEventGenerator> m_spEventGen;
        ComPtr<IMFSimpleAudioVolume>  m_spAudioVolume;

        // MIDI specific members
        bool            m_isMidi;      // true if current track is MIDI
        std::wstring    m_midiAlias;   // unique alias for MCI device
        DWORD           m_midiVolumeBeforeMute; // for mute restore

        // Error string (thread‑safe for main thread calls)
        std::wstring m_lastError;
        mutable std::mutex m_errorMutex;
    };

    class AudioFileManager
    {
    public:
        AudioFileManager();
        ~AudioFileManager();

        std::vector<AudioTrack> scanAudioFiles();
        AudioTrack uploadAudioFile(const std::wstring& filePath);
        bool deleteAudioTrack(const AudioTrack& track);
        bool clearAllAudioFiles();
        std::wstring getAudioDirectory() const;

    private:
        std::wstring audioDirectory;
        int nextTrackNumber;
        std::wstring getUniqueFileName(const std::wstring& originalName);
        bool copyFileToAudioDir(const std::wstring& sourcePath, std::wstring& destPath);
        AudioTrack createTrackFromFile(const std::wstring& filePath, int trackNumber);
        long getAudioDuration(const std::wstring& filePath);
    };
}