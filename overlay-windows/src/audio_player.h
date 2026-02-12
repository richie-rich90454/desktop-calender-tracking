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
#include <atomic>
#include <thread>
#include <mutex>
#include <functional>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <filesystem>
#include <mmsystem.h>
#include <mmreg.h>
#include <dsound.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>

#pragma comment(lib, "winmm.lib")
#pragma comment(lib, "dsound.lib")
#pragma comment(lib, "mf.lib")
#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mfreadwrite.lib")
#pragma comment(lib, "mfuuid.lib")

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

        std::wstring getFormattedDuration() const
        {
            if (duration <= 0) return L"00:00";
            long seconds = duration / 1000;
            long minutes = seconds / 60;
            seconds %= 60;
            std::wstringstream ss;
            ss << std::setw(2) << std::setfill(L'0') << minutes << L":"
               << std::setw(2) << std::setfill(L'0') << seconds;
            return ss.str();
        }

        bool isSupportedFormat() const;
    };

    class AudioPlayerEngine
    {
    public:
        AudioPlayerEngine();
        ~AudioPlayerEngine();

        bool play(const AudioTrack& track);
        bool pause();
        bool resume();
        bool stop();
        bool seek(long positionMillis);
        bool setVolume(float volume);
        bool setMuted(bool muted);

        PlaybackState getPlaybackState() const { return state; }
        AudioTrack getCurrentTrack() const { return currentTrack; }
        long getCurrentPosition() const;
        long getDuration() const { return currentTrack.duration; }
        float getVolume() const { return volume; }
        bool isMuted() const { return muted; }
        bool isPlaying() const { return state == PlaybackState::PLAYING; }
        bool isPaused() const { return state == PlaybackState::PAUSED; }
        bool isStopped() const { return state == PlaybackState::STOPPED; }

        void setOnTrackEnd(std::function<void()> callback) { onTrackEnd = callback; }
        void cleanup();

    private:
        bool playWav(const AudioTrack& track);
        bool playMp3(const AudioTrack& track);
        void cleanupWav();
        void cleanupMp3();
        static HRESULT CreatePlaybackTopology(IMFMediaSource* pSource,
                                              IMFPresentationDescriptor* pPD,
                                              IMFTopology** ppTopology);
        void startPositionUpdater();
        void stopPositionUpdater();
        static void CALLBACK waveOutCallback(HWAVEOUT hwo, UINT uMsg,
                                             DWORD_PTR dwInstance,
                                             DWORD_PTR dwParam1,
                                             DWORD_PTR dwParam2);

        AudioTrack currentTrack;
        PlaybackState state;
        float volume;
        bool muted;
        std::function<void()> onTrackEnd;

        // WAV
        HWAVEOUT hWaveOut;
        WAVEFORMATEX waveFormat;
        std::vector<BYTE> waveData;
        std::unique_ptr<WAVEHDR> waveHeader;

        // MP3
        IMFMediaSession* pMediaSession;
        IMFMediaSource* pMediaSource;

        // Threading
        std::thread positionThread;
        std::atomic<bool> positionThreadRunning;
        std::atomic<bool> cleanedUp;
        mutable std::mutex mutex;
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