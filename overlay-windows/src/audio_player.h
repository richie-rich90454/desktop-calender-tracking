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
#include <condition_variable>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <filesystem>
#include <mmsystem.h>
#include <mmreg.h>
#include <dsound.h>
#pragma comment(lib, "winmm.lib")
#pragma comment(lib, "dsound.lib")

namespace CalendarOverlay{
    namespace Audio{
        enum class PlaybackState{
            STOPPED,
            PLAYING,
            PAUSED
        };
        struct AudioTrack{
            std::wstring filePath;
            std::wstring fileName;
            std::wstring displayName;
            std::wstring fileExtension;
            int trackNumber;
            long duration;
            long currentPosition;
            bool playing;
            AudioTrack() : trackNumber(0), duration(0), currentPosition(0), playing(false){}
            std::wstring getFormattedDuration() const{
                if (duration<=0) return L"00:00";
                long seconds=duration/1000;
                long minutes=seconds/60;
                seconds=seconds%60;
                std::wstringstream ss;
                ss<<std::setw(2)<<std::setfill(L'0')<<minutes<<L":"<<std::setw(2)<<std::setfill(L'0')<<seconds;
                return ss.str();
            }
            bool isSupportedFormat() const{
                std::wstring ext=fileExtension;
                if (ext.empty()&&!filePath.empty()){
                    size_t dotPos=filePath.find_last_of(L'.');
                    if (dotPos!=std::wstring::npos){
                        ext=filePath.substr(dotPos+1);
                    }
                }
                for (wchar_t& c : ext) c=towlower(c);
                return ext==L"mp3"||ext==L"wav"||ext==L"mid"||ext==L"midi";
            }
        };
        class AudioPlayerEngine{
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
            PlaybackState getPlaybackState() const{
                return playbackState;
            }
            AudioTrack getCurrentTrack() const{
                return currentTrack;
            }
            long getCurrentPosition() const;
            long getDuration() const{
                return currentTrack.duration;
            }
            float getVolume() const{
                return volume;
            }
            bool isMuted() const{
                return muted;
            }
            bool isPlaying() const{
                return playbackState==PlaybackState::PLAYING;
            }
            bool isPaused() const{
                return playbackState==PlaybackState::PAUSED;
            }
            bool isStopped() const{
                return playbackState==PlaybackState::STOPPED;
            }
            void cleanup();
        private:
            bool playWav(const AudioTrack& track);
            bool playMp3(const AudioTrack& track);
            bool playMidi(const AudioTrack& track);
            void cleanupWav();
            void cleanupMp3();
            void cleanupMidi();
            void startPositionUpdater();
            void stopPositionUpdater();
            AudioTrack currentTrack;
            PlaybackState playbackState;
            float volume;
            bool muted;
            HWAVEOUT hWaveOut;
            WAVEFORMATEX waveFormat;
            std::vector<char> waveBuffer;
            HMIDIOUT hMidiOut;
            IUnknown* pMediaPlayer;
            std::thread positionUpdater;
            std::atomic<bool> running;
            std::mutex stateMutex;
            std::condition_variable stateCV;
        };
        class AudioFileManager{
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
}