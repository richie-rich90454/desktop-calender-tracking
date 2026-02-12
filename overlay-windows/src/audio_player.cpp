#include "audio_player.h"
#include <shlobj.h>
#include <shlwapi.h>
#include <comdef.h>
#include <wrl/client.h>
#include <algorithm>
#include <chrono>
#include <thread>

#pragma comment(lib, "shlwapi.lib")

namespace fs = std::filesystem;
using Microsoft::WRL::ComPtr;

namespace CalendarOverlay::Audio
{
    // ---------------------------------------------------------------------
    // Media Foundation global refcount – thread‑safe, shared across instances
    // ---------------------------------------------------------------------
    static std::mutex g_mfMutex;
    static int g_mfRefCount = 0;

    static bool InitializeMediaFoundationGlobal()
    {
        std::lock_guard<std::mutex> lock(g_mfMutex);
        if (g_mfRefCount++ == 0)
        {
            HRESULT hr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
            if (FAILED(hr)) { g_mfRefCount--; return false; }
            hr = MFStartup(MF_VERSION, MFSTARTUP_LITE);
            if (FAILED(hr))
            {
                CoUninitialize();
                g_mfRefCount--;
                return false;
            }
        }
        return true;
    }

    static void ShutdownMediaFoundationGlobal()
    {
        std::lock_guard<std::mutex> lock(g_mfMutex);
        if (--g_mfRefCount == 0)
        {
            MFShutdown();
            CoUninitialize();
        }
    }

    // ---------------------------------------------------------------------
    // AudioTrack helper
    // ---------------------------------------------------------------------
    bool AudioTrack::isSupportedFormat() const
    {
        std::wstring ext = fs::path(filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);
        return ext == L".wav" || ext == L".mp3";
    }

    // ---------------------------------------------------------------------
    // AudioPlayerEngine
    // ---------------------------------------------------------------------
    AudioPlayerEngine::AudioPlayerEngine()
        : state(PlaybackState::STOPPED)
        , volume(0.8f)
        , muted(false)
        , hWaveOut(nullptr)
        , pMediaSession(nullptr)
        , pMediaSource(nullptr)
        , positionThreadRunning(false)
        , cleanedUp(false)
    {
        waveFormat = {};
    }

    AudioPlayerEngine::~AudioPlayerEngine()
    {
        cleanup();
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------
    bool AudioPlayerEngine::play(const AudioTrack& track)
    {
        if (!track.isSupportedFormat())
            return false;

        stop();

        std::lock_guard<std::mutex> lock(mutex);
        if (cleanedUp) return false;

        currentTrack = track;
        currentTrack.currentPosition = 0;

        std::wstring ext = fs::path(track.filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);

        bool success = false;
        if (ext == L".wav")
            success = playWav(track);
        else if (ext == L".mp3")
            success = playMp3(track);

        if (success)
        {
            state = PlaybackState::PLAYING;
            startPositionUpdater();
        }
        return success;
    }

    bool AudioPlayerEngine::pause()
    {
        std::lock_guard<std::mutex> lock(mutex);
        if (state != PlaybackState::PLAYING || cleanedUp)
            return false;

        std::wstring ext = fs::path(currentTrack.filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);

        if (ext == L".wav" && hWaveOut)
            waveOutPause(hWaveOut);
        else if (ext == L".mp3" && pMediaSession)
            pMediaSession->Pause();

        state = PlaybackState::PAUSED;
        return true;
    }

    bool AudioPlayerEngine::resume()
    {
        std::lock_guard<std::mutex> lock(mutex);
        if (state != PlaybackState::PAUSED || cleanedUp)
            return false;

        std::wstring ext = fs::path(currentTrack.filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);

        if (ext == L".wav" && hWaveOut)
            waveOutRestart(hWaveOut);
        else if (ext == L".mp3" && pMediaSession)
        {
            PROPVARIANT varStart;
            PropVariantInit(&varStart);
            varStart.vt = VT_I8;
            varStart.hVal.QuadPart = 0;
            pMediaSession->Start(nullptr, &varStart);
            PropVariantClear(&varStart);
        }

        state = PlaybackState::PLAYING;
        return true;
    }

    bool AudioPlayerEngine::stop()
    {
        stopPositionUpdater();

        std::lock_guard<std::mutex> lock(mutex);
        if (cleanedUp) return false;

        std::wstring ext = fs::path(currentTrack.filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);

        if (ext == L".wav")
            cleanupWav();
        else if (ext == L".mp3")
            cleanupMp3();

        state = PlaybackState::STOPPED;
        currentTrack.currentPosition = 0;
        return true;
    }

    bool AudioPlayerEngine::seek(long positionMillis)
    {
        std::lock_guard<std::mutex> lock(mutex);
        if (state == PlaybackState::STOPPED || cleanedUp)
            return false;

        std::wstring ext = fs::path(currentTrack.filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);

        if (ext == L".wav" && hWaveOut && !waveData.empty())
        {
            DWORD bytePos = (DWORD)((double)positionMillis / 1000.0 * waveFormat.nAvgBytesPerSec);
            if (bytePos >= waveData.size())
                bytePos = (DWORD)waveData.size() - 1;

            waveOutReset(hWaveOut);

            auto newHeader = std::make_unique<WAVEHDR>();
            ZeroMemory(newHeader.get(), sizeof(WAVEHDR));
            newHeader->lpData = reinterpret_cast<LPSTR>(waveData.data() + bytePos);
            newHeader->dwBufferLength = (DWORD)waveData.size() - bytePos;
            newHeader->dwFlags = 0;

            MMRESULT res = waveOutPrepareHeader(hWaveOut, newHeader.get(), sizeof(WAVEHDR));
            if (res == MMSYSERR_NOERROR)
            {
                res = waveOutWrite(hWaveOut, newHeader.get(), sizeof(WAVEHDR));
                if (res == MMSYSERR_NOERROR)
                {
                    waveHeader = std::move(newHeader);
                    currentTrack.currentPosition = positionMillis;
                    return true;
                }
                waveOutUnprepareHeader(hWaveOut, newHeader.get(), sizeof(WAVEHDR));
            }
            return false;
        }
        else if (ext == L".mp3" && pMediaSession)
        {
            PROPVARIANT varPosition;
            PropVariantInit(&varPosition);
            varPosition.vt = VT_I8;
            varPosition.hVal.QuadPart = positionMillis * 10000;
            HRESULT hr = pMediaSession->Start(nullptr, &varPosition);
            PropVariantClear(&varPosition);
            if (SUCCEEDED(hr))
            {
                currentTrack.currentPosition = positionMillis;
                return true;
            }
        }
        return false;
    }

    bool AudioPlayerEngine::setVolume(float newVolume)
    {
        std::lock_guard<std::mutex> lock(mutex);
        if (cleanedUp) return false;

        if (newVolume < 0.0f) newVolume = 0.0f;
        if (newVolume > 1.0f) newVolume = 1.0f;
        volume = newVolume;
        if (muted)
            return true;

        std::wstring ext = fs::path(currentTrack.filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);

        if (ext == L".wav" && hWaveOut)
        {
            DWORD dwVol = (DWORD)(volume * 0xFFFF);
            waveOutSetVolume(hWaveOut, MAKELONG(dwVol, dwVol));
        }
        else if (ext == L".mp3" && pMediaSession)
        {
            ComPtr<IMFSimpleAudioVolume> pAudioVolume;
            if (SUCCEEDED(pMediaSession->QueryInterface(IID_PPV_ARGS(&pAudioVolume))))
                pAudioVolume->SetMasterVolume(volume);
        }
        return true;
    }

    bool AudioPlayerEngine::setMuted(bool newMuted)
    {
        std::lock_guard<std::mutex> lock(mutex);
        if (cleanedUp) return false;
        muted = newMuted;

        std::wstring ext = fs::path(currentTrack.filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);

        if (ext == L".wav" && hWaveOut)
        {
            waveOutSetVolume(hWaveOut, muted ? 0 : (DWORD)(volume * 0xFFFF));
        }
        else if (ext == L".mp3" && pMediaSession)
        {
            ComPtr<IMFSimpleAudioVolume> pAudioVolume;
            if (SUCCEEDED(pMediaSession->QueryInterface(IID_PPV_ARGS(&pAudioVolume))))
            {
                pAudioVolume->SetMute(muted ? TRUE : FALSE);
                if (!muted)
                    pAudioVolume->SetMasterVolume(volume);
            }
        }
        return true;
    }

    long AudioPlayerEngine::getCurrentPosition() const
    {
        std::lock_guard<std::mutex> lock(mutex);
        if (state == PlaybackState::STOPPED || cleanedUp)
            return 0;

        std::wstring ext = fs::path(currentTrack.filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);

        if (ext == L".wav" && hWaveOut)
        {
            MMTIME mmt;
            mmt.wType = TIME_MS;
            if (waveOutGetPosition(hWaveOut, &mmt, sizeof(MMTIME)) == MMSYSERR_NOERROR)
                return mmt.u.ms;
        }
        else if (ext == L".mp3" && pMediaSession)
        {
            ComPtr<IMFPresentationClock> pClock;
            ComPtr<IMFClock> pClockBase;
            if (SUCCEEDED(pMediaSession->GetClock(&pClockBase)) &&
                SUCCEEDED(pClockBase.As(&pClock)))
            {
                MFTIME time;
                if (SUCCEEDED(pClock->GetTime(&time)))
                    return (long)(time / 10000);
            }
        }
        return currentTrack.currentPosition;
    }

    // ---------------------------------------------------------------------
    // WAV implementation
    // ---------------------------------------------------------------------
    void CALLBACK AudioPlayerEngine::waveOutCallback(HWAVEOUT hwo, UINT uMsg,
                                                     DWORD_PTR dwInstance,
                                                     DWORD_PTR dwParam1,
                                                     DWORD_PTR dwParam2)
    {
        auto pEngine = reinterpret_cast<AudioPlayerEngine*>(dwInstance);
        if (pEngine && !pEngine->cleanedUp.load() && uMsg == WOM_DONE)
        {
            if (pEngine->onTrackEnd)
                pEngine->onTrackEnd();
        }
    }

    bool AudioPlayerEngine::playWav(const AudioTrack& track)
    {
        cleanupWav();

        HMMIO hmmio = mmioOpen(const_cast<LPWSTR>(track.filePath.c_str()), nullptr, MMIO_READ);
        if (!hmmio) return false;

        MMCKINFO ckRiff;
        ckRiff.fccType = mmioFOURCC('W', 'A', 'V', 'E');
        if (mmioDescend(hmmio, &ckRiff, nullptr, MMIO_FINDRIFF))
        {
            mmioClose(hmmio, 0);
            return false;
        }

        MMCKINFO ckInfo;
        ckInfo.ckid = mmioFOURCC('f', 'm', 't', ' ');
        if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK))
        {
            mmioClose(hmmio, 0);
            return false;
        }

        PCMWAVEFORMAT pcmWaveFormat;
        if (mmioRead(hmmio, (HPSTR)&pcmWaveFormat, sizeof(pcmWaveFormat)) != sizeof(pcmWaveFormat))
        {
            mmioClose(hmmio, 0);
            return false;
        }

        if (pcmWaveFormat.wf.wFormatTag != WAVE_FORMAT_PCM)
        {
            mmioClose(hmmio, 0);
            return false;
        }

        mmioAscend(hmmio, &ckInfo, 0);

        ckInfo.ckid = mmioFOURCC('d', 'a', 't', 'a');
        if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK))
        {
            mmioClose(hmmio, 0);
            return false;
        }

        waveData.resize(ckInfo.cksize);
        if (mmioRead(hmmio, (HPSTR)waveData.data(), ckInfo.cksize) != ckInfo.cksize)
        {
            mmioClose(hmmio, 0);
            waveData.clear();
            return false;
        }
        mmioClose(hmmio, 0);

        waveFormat.wFormatTag = WAVE_FORMAT_PCM;
        waveFormat.nChannels = pcmWaveFormat.wf.nChannels;
        waveFormat.nSamplesPerSec = pcmWaveFormat.wf.nSamplesPerSec;
        waveFormat.nAvgBytesPerSec = pcmWaveFormat.wf.nAvgBytesPerSec;
        waveFormat.nBlockAlign = pcmWaveFormat.wf.nBlockAlign;
        waveFormat.wBitsPerSample = pcmWaveFormat.wBitsPerSample;
        waveFormat.cbSize = 0;

        MMRESULT res = waveOutOpen(&hWaveOut, WAVE_MAPPER, &waveFormat,
                                   (DWORD_PTR)waveOutCallback, (DWORD_PTR)this,
                                   CALLBACK_FUNCTION);
        if (res != MMSYSERR_NOERROR)
        {
            waveData.clear();
            return false;
        }

        waveHeader = std::make_unique<WAVEHDR>();
        ZeroMemory(waveHeader.get(), sizeof(WAVEHDR));
        waveHeader->lpData = reinterpret_cast<LPSTR>(waveData.data());
        waveHeader->dwBufferLength = (DWORD)waveData.size();
        waveHeader->dwFlags = 0;

        res = waveOutPrepareHeader(hWaveOut, waveHeader.get(), sizeof(WAVEHDR));
        if (res != MMSYSERR_NOERROR)
        {
            waveHeader.reset();
            waveOutClose(hWaveOut);
            hWaveOut = nullptr;
            waveData.clear();
            return false;
        }

        res = waveOutWrite(hWaveOut, waveHeader.get(), sizeof(WAVEHDR));
        if (res != MMSYSERR_NOERROR)
        {
            waveOutUnprepareHeader(hWaveOut, waveHeader.get(), sizeof(WAVEHDR));
            waveHeader.reset();
            waveOutClose(hWaveOut);
            hWaveOut = nullptr;
            waveData.clear();
            return false;
        }

        currentTrack.duration = (long)((double)waveData.size() / waveFormat.nAvgBytesPerSec * 1000.0);
        return true;
    }

    void AudioPlayerEngine::cleanupWav()
    {
        if (hWaveOut)
        {
            waveOutReset(hWaveOut);
            if (waveHeader)
            {
                waveOutUnprepareHeader(hWaveOut, waveHeader.get(), sizeof(WAVEHDR));
                waveHeader.reset();
            }
            waveOutClose(hWaveOut);
            hWaveOut = nullptr;
        }
        waveData.clear();
    }

    // ---------------------------------------------------------------------
    // Media Foundation MP3 implementation – uses global refcount, no event pump
    // ---------------------------------------------------------------------
    bool AudioPlayerEngine::playMp3(const AudioTrack& track)
    {
        cleanupMp3();

        if (!InitializeMediaFoundationGlobal())
            return false;

        HRESULT hr = S_OK;
        ComPtr<IMFSourceResolver> pResolver;
        ComPtr<IMFPresentationDescriptor> pPD;
        ComPtr<IMFTopology> pTopology;
        IUnknown* pSourceUnk = nullptr;
        MF_OBJECT_TYPE objectType = MF_OBJECT_INVALID;

        hr = MFCreateSourceResolver(&pResolver);
        if (FAILED(hr)) { cleanupMp3(); return false; }

        hr = pResolver->CreateObjectFromURL(track.filePath.c_str(),
                                            MF_RESOLUTION_MEDIASOURCE,
                                            nullptr, &objectType, &pSourceUnk);
        if (FAILED(hr)) { cleanupMp3(); return false; }

        hr = pSourceUnk->QueryInterface(IID_PPV_ARGS(&pMediaSource));
        pSourceUnk->Release();
        if (FAILED(hr)) { cleanupMp3(); return false; }

        hr = pMediaSource->CreatePresentationDescriptor(&pPD);
        if (FAILED(hr)) { cleanupMp3(); return false; }

        hr = CreatePlaybackTopology(pMediaSource, pPD.Get(), &pTopology);
        if (FAILED(hr)) { cleanupMp3(); return false; }

        hr = MFCreateMediaSession(nullptr, &pMediaSession);
        if (FAILED(hr)) { cleanupMp3(); return false; }

        hr = pMediaSession->SetTopology(0, pTopology.Get());
        if (FAILED(hr)) { cleanupMp3(); return false; }

        UINT64 duration = 0;
        pPD->GetUINT64(MF_PD_DURATION, &duration);
        currentTrack.duration = (long)(duration / 10000);

        PROPVARIANT varStart;
        PropVariantInit(&varStart);
        varStart.vt = VT_I8;
        varStart.hVal.QuadPart = 0;
        hr = pMediaSession->Start(nullptr, &varStart);
        PropVariantClear(&varStart);
        if (FAILED(hr)) { cleanupMp3(); return false; }

        return true;
    }

    void AudioPlayerEngine::cleanupMp3()
    {
        if (pMediaSession)
        {
            pMediaSession->Stop();
            pMediaSession->Close();
            pMediaSession->Release();
            pMediaSession = nullptr;
        }
        if (pMediaSource)
        {
            pMediaSource->Shutdown();
            pMediaSource->Release();
            pMediaSource = nullptr;
        }
        ShutdownMediaFoundationGlobal();
    }

    HRESULT AudioPlayerEngine::CreatePlaybackTopology(IMFMediaSource* pSource,
                                                      IMFPresentationDescriptor* pPD,
                                                      IMFTopology** ppTopology)
    {
        IMFTopology* pTopology = nullptr;
        HRESULT hr = MFCreateTopology(&pTopology);
        if (FAILED(hr)) return hr;

        DWORD cStreams = 0;
        hr = pPD->GetStreamDescriptorCount(&cStreams);
        if (SUCCEEDED(hr))
        {
            for (DWORD i = 0; i < cStreams; i++)
            {
                BOOL fSelected = FALSE;
                IMFStreamDescriptor* pSD = nullptr;
                hr = pPD->GetStreamDescriptorByIndex(i, &fSelected, &pSD);
                if (SUCCEEDED(hr))
                {
                    if (fSelected)
                    {
                        IMFTopologyNode* pNode1 = nullptr;
                        IMFTopologyNode* pNode2 = nullptr;
                        hr = MFCreateTopologyNode(MF_TOPOLOGY_SOURCESTREAM_NODE, &pNode1);
                        if (SUCCEEDED(hr))
                        {
                            hr = pNode1->SetUnknown(MF_TOPONODE_SOURCE, pSource);
                            hr = pNode1->SetUnknown(MF_TOPONODE_PRESENTATION_DESCRIPTOR, pPD);
                            hr = pNode1->SetUnknown(MF_TOPONODE_STREAM_DESCRIPTOR, pSD);
                        }
                        if (SUCCEEDED(hr))
                        {
                            hr = MFCreateTopologyNode(MF_TOPOLOGY_OUTPUT_NODE, &pNode2);
                        }
                        if (SUCCEEDED(hr))
                        {
                            IMFActivate* pRendererActivate = nullptr;
                            hr = MFCreateAudioRendererActivate(&pRendererActivate);
                            if (SUCCEEDED(hr))
                            {
                                hr = pNode2->SetObject(pRendererActivate);
                                pRendererActivate->Release();
                            }
                        }
                        if (SUCCEEDED(hr))
                            hr = pTopology->AddNode(pNode1);
                        if (SUCCEEDED(hr))
                            hr = pTopology->AddNode(pNode2);
                        if (SUCCEEDED(hr))
                            hr = pNode1->ConnectOutput(0, pNode2, 0);
                        if (pNode1) pNode1->Release();
                        if (pNode2) pNode2->Release();
                    }
                    pSD->Release();
                }
                if (FAILED(hr)) break;
            }
        }

        if (SUCCEEDED(hr))
            *ppTopology = pTopology;
        else
            if (pTopology) pTopology->Release();
        return hr;
    }

    // ---------------------------------------------------------------------
    // Position updater thread – uses stored duration for end detection
    // ---------------------------------------------------------------------
    void AudioPlayerEngine::startPositionUpdater()
    {
        stopPositionUpdater();
        positionThreadRunning = true;
        positionThread = std::thread([this]()
        {
            while (positionThreadRunning && !cleanedUp)
            {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
                std::lock_guard<std::mutex> lock(mutex);
                if (state == PlaybackState::PLAYING && !cleanedUp)
                {
                    currentTrack.currentPosition = getCurrentPosition();

                    if (currentTrack.duration > 0 &&
                        currentTrack.currentPosition >= currentTrack.duration - 200)
                    {
                        if (onTrackEnd)
                            onTrackEnd();
                    }
                }
            }
        });
    }

    void AudioPlayerEngine::stopPositionUpdater()
    {
        positionThreadRunning = false;
        if (positionThread.joinable())
            positionThread.join();
    }

    void AudioPlayerEngine::cleanup()
    {
        cleanedUp = true;
        stopPositionUpdater();

        std::lock_guard<std::mutex> lock(mutex);
        cleanupWav();
        cleanupMp3();
        state = PlaybackState::STOPPED;
        currentTrack = AudioTrack();
    }

    // ---------------------------------------------------------------------
    // AudioFileManager – uses %USERPROFILE%\.calendarapp\audio
    // ---------------------------------------------------------------------
    AudioFileManager::AudioFileManager() : nextTrackNumber(1)
    {
        wchar_t userProfile[MAX_PATH];
        if (GetEnvironmentVariableW(L"USERPROFILE", userProfile, MAX_PATH))
        {
            audioDirectory = std::wstring(userProfile) + L"\\.calendarapp\\audio";
            CreateDirectoryW(audioDirectory.c_str(), nullptr);
        }
        else
        {
            wchar_t appDataPath[MAX_PATH];
            if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_APPDATA, nullptr, 0, appDataPath)))
            {
                audioDirectory = std::wstring(appDataPath) + L"\\DesktopCalendar\\Audio";
                CreateDirectoryW(audioDirectory.c_str(), nullptr);
            }
        }
    }

    AudioFileManager::~AudioFileManager() = default;

    std::vector<AudioTrack> AudioFileManager::scanAudioFiles()
    {
        std::vector<AudioTrack> tracks;
        if (audioDirectory.empty())
            return tracks;

        try
        {
            int num = 1;
            for (const auto& entry : fs::directory_iterator(audioDirectory))
            {
                if (!entry.is_regular_file())
                    continue;

                std::wstring ext = entry.path().extension().wstring();
                for (wchar_t& c : ext) c = towlower(c);
                if (ext == L".wav" || ext == L".mp3")
                {
                    tracks.push_back(createTrackFromFile(entry.path().wstring(), num++));
                }
            }
            nextTrackNumber = num;
        }
        catch (...) {}
        return tracks;
    }

    AudioTrack AudioFileManager::uploadAudioFile(const std::wstring& filePath)
    {
        std::wstring destPath;
        if (copyFileToAudioDir(filePath, destPath))
        {
            return createTrackFromFile(destPath, nextTrackNumber++);
        }
        return AudioTrack();
    }

    bool AudioFileManager::deleteAudioTrack(const AudioTrack& track)
    {
        return DeleteFileW(track.filePath.c_str()) == TRUE;
    }

    bool AudioFileManager::clearAllAudioFiles()
    {
        bool ok = true;
        for (const auto& entry : fs::directory_iterator(audioDirectory))
        {
            if (!DeleteFileW(entry.path().wstring().c_str()))
                ok = false;
        }
        nextTrackNumber = 1;
        return ok;
    }

    std::wstring AudioFileManager::getAudioDirectory() const
    {
        return audioDirectory;
    }

    std::wstring AudioFileManager::getUniqueFileName(const std::wstring& originalName)
    {
        std::wstring name = fs::path(originalName).filename().wstring();
        std::wstring stem = fs::path(name).stem().wstring();
        std::wstring ext = fs::path(name).extension().wstring();

        std::wstring newName = name;
        int counter = 1;
        while (fs::exists(audioDirectory + L"\\" + newName))
        {
            std::wstringstream ss;
            ss << stem << L" (" << counter++ << L")" << ext;
            newName = ss.str();
        }
        return newName;
    }

    bool AudioFileManager::copyFileToAudioDir(const std::wstring& sourcePath, std::wstring& destPath)
    {
        std::wstring fileName = getUniqueFileName(sourcePath);
        destPath = audioDirectory + L"\\" + fileName;
        return CopyFileW(sourcePath.c_str(), destPath.c_str(), FALSE) == TRUE;
    }

    AudioTrack AudioFileManager::createTrackFromFile(const std::wstring& filePath, int trackNumber)
    {
        AudioTrack track;
        track.filePath = filePath;
        track.fileName = fs::path(filePath).filename().wstring();
        track.displayName = track.fileName;
        track.trackNumber = trackNumber;
        track.duration = getAudioDuration(filePath);
        track.currentPosition = 0;
        return track;
    }

    long AudioFileManager::getAudioDuration(const std::wstring& filePath)
    {
        std::wstring ext = fs::path(filePath).extension().wstring();
        for (wchar_t& c : ext) c = towlower(c);

        if (ext == L".wav")
        {
            HMMIO hmmio = mmioOpen(const_cast<LPWSTR>(filePath.c_str()), nullptr, MMIO_READ);
            if (!hmmio) return 0;
            MMCKINFO ckRiff;
            ckRiff.fccType = mmioFOURCC('W', 'A', 'V', 'E');
            if (mmioDescend(hmmio, &ckRiff, nullptr, MMIO_FINDRIFF))
            {
                mmioClose(hmmio, 0);
                return 0;
            }
            MMCKINFO ckInfo;
            ckInfo.ckid = mmioFOURCC('f', 'm', 't', ' ');
            if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK))
            {
                mmioClose(hmmio, 0);
                return 0;
            }
            PCMWAVEFORMAT pcmWaveFormat;
            if (mmioRead(hmmio, (HPSTR)&pcmWaveFormat, sizeof(pcmWaveFormat)) != sizeof(pcmWaveFormat))
            {
                mmioClose(hmmio, 0);
                return 0;
            }
            mmioAscend(hmmio, &ckInfo, 0);
            ckInfo.ckid = mmioFOURCC('d', 'a', 't', 'a');
            if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK))
            {
                mmioClose(hmmio, 0);
                return 0;
            }
            long duration = (long)((double)ckInfo.cksize / pcmWaveFormat.wf.nAvgBytesPerSec * 1000.0);
            mmioClose(hmmio, 0);
            return duration;
        }
        else if (ext == L".mp3")
        {
            ComPtr<IMFSourceResolver> pResolver;
            ComPtr<IMFMediaSource> pSource;
            ComPtr<IMFPresentationDescriptor> pPD;
            UINT64 duration = 0;

            if (FAILED(MFCreateSourceResolver(&pResolver))) return 0;
            MF_OBJECT_TYPE objectType;
            IUnknown* pSourceUnk = nullptr;
            if (FAILED(pResolver->CreateObjectFromURL(filePath.c_str(),
                                                      MF_RESOLUTION_MEDIASOURCE,
                                                      nullptr, &objectType, &pSourceUnk)))
                return 0;
            if (FAILED(pSourceUnk->QueryInterface(IID_PPV_ARGS(&pSource))))
            {
                pSourceUnk->Release();
                return 0;
            }
            pSourceUnk->Release();
            if (FAILED(pSource->CreatePresentationDescriptor(&pPD))) return 0;
            pPD->GetUINT64(MF_PD_DURATION, &duration);
            pSource->Shutdown();
            return (long)(duration / 10000);
        }
        return 0;
    }
}