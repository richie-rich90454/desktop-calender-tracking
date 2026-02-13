// ==================== audio_player.cpp ====================
#include "audio_player.h"
#include <shlobj.h>
#include <shlwapi.h>
#include <comdef.h>
#include <algorithm>
#include <sstream>
#include <iomanip>
#include <debugapi.h>
#include <mutex>
#include <mmsystem.h> // for MCI only (volume‑related midiOut calls removed)
#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "winmm.lib") // for MCI

namespace CalendarOverlay::Audio
{
    // ---------------------------------------------------------------------
    // AudioTrack helpers
    // ---------------------------------------------------------------------
    std::wstring AudioTrack::getFormattedDuration() const
    {
        if (duration <= 0)
            return L"00:00";
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        std::wstringstream ss;
        ss << std::setw(2) << std::setfill(L'0') << minutes << L":"
           << std::setw(2) << std::setfill(L'0') << seconds;
        return ss.str();
    }

    bool AudioTrack::isSupportedFormat() const
    {
        std::wstring ext = fs::path(filePath).extension().wstring();
        for (wchar_t &c : ext)
            c = towlower(c);
        return ext == L".wav" || ext == L".mp3" || ext == L".m4a" || ext == L".wma" || ext == L".mid" || ext == L".midi";
    }

    // ---------------------------------------------------------------------
    // AudioPlayerEngine
    // ---------------------------------------------------------------------
    AudioPlayerEngine::AudioPlayerEngine()
        : state(PlaybackState::STOPPED), m_isMidi(false), m_midiAlias(L"")
    {
        HRESULT hr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        if (SUCCEEDED(hr))
            MFStartup(MF_VERSION);
    }

    AudioPlayerEngine::~AudioPlayerEngine()
    {
        cleanup();
        MFShutdown();
        CoUninitialize();
    }

    void AudioPlayerEngine::SetError(const std::wstring &err)
    {
        std::lock_guard<std::mutex> lock(m_errorMutex);
        m_lastError = err;
        OutputDebugStringW((L"[Audio] " + err + L"\n").c_str());
    }

    std::wstring AudioPlayerEngine::getLastError() const
    {
        std::lock_guard<std::mutex> lock(m_errorMutex);
        return m_lastError;
    }

    // ---------------------------------------------------------------------
    // MIDI detection
    // ---------------------------------------------------------------------
    bool AudioPlayerEngine::IsMidiFile(const std::wstring &filePath) const
    {
        std::wstring ext = fs::path(filePath).extension().wstring();
        for (wchar_t &c : ext)
            c = towlower(c);
        return ext == L".mid" || ext == L".midi";
    }

    // ---------------------------------------------------------------------
    // Session management (Media Foundation)
    // ---------------------------------------------------------------------
    bool AudioPlayerEngine::CreateMediaSession(const AudioTrack &track)
    {
        DestroyMediaSession();

        ComPtr<IMFSourceResolver> spResolver;
        HRESULT hr = MFCreateSourceResolver(&spResolver);
        if (FAILED(hr))
        {
            SetError(L"MFCreateSourceResolver failed");
            return false;
        }

        MF_OBJECT_TYPE objectType;
        ComPtr<IUnknown> spSourceUnk;
        hr = spResolver->CreateObjectFromURL(
            track.filePath.c_str(),
            MF_RESOLUTION_MEDIASOURCE,
            nullptr,
            &objectType,
            &spSourceUnk);
        if (FAILED(hr))
        {
            SetError(L"Cannot open media file");
            return false;
        }

        hr = spSourceUnk.As(&m_spSource);
        if (FAILED(hr))
        {
            SetError(L"Not a media source");
            return false;
        }

        ComPtr<IMFPresentationDescriptor> spPD;
        hr = m_spSource->CreatePresentationDescriptor(&spPD);
        if (FAILED(hr))
        {
            SetError(L"Cannot get presentation descriptor");
            return false;
        }

        // Get duration
        UINT64 duration = 0;
        spPD->GetUINT64(MF_PD_DURATION, &duration);
        currentTrack.duration = (long)(duration / 10000);

        // Create session
        hr = MFCreateMediaSession(nullptr, &m_spSession);
        if (FAILED(hr))
        {
            SetError(L"Cannot create media session");
            return false;
        }

        hr = m_spSession.As(&m_spEventGen);
        // non‑fatal if fails

        // Create topology
        ComPtr<IMFTopology> spTopology;
        hr = MFCreateTopology(&spTopology);
        if (FAILED(hr))
        {
            SetError(L"Cannot create topology");
            return false;
        }

        DWORD cStreams = 0;
        spPD->GetStreamDescriptorCount(&cStreams);
        bool audioConnected = false;
        for (DWORD i = 0; i < cStreams; ++i)
        {
            BOOL fSelected = FALSE;
            ComPtr<IMFStreamDescriptor> spSD;
            hr = spPD->GetStreamDescriptorByIndex(i, &fSelected, &spSD);
            if (FAILED(hr))
                continue;

            if (!fSelected)
                continue;

            ComPtr<IMFMediaTypeHandler> spHandler;
            hr = spSD->GetMediaTypeHandler(&spHandler);
            if (FAILED(hr))
                continue;

            GUID majorType;
            hr = spHandler->GetMajorType(&majorType);
            if (FAILED(hr))
                continue;

            if (majorType == MFMediaType_Audio)
            {
                // Source node
                ComPtr<IMFTopologyNode> spNodeSource;
                hr = MFCreateTopologyNode(MF_TOPOLOGY_SOURCESTREAM_NODE, &spNodeSource);
                if (FAILED(hr))
                    continue;

                spNodeSource->SetUnknown(MF_TOPONODE_SOURCE, m_spSource.Get());
                spNodeSource->SetUnknown(MF_TOPONODE_PRESENTATION_DESCRIPTOR, spPD.Get());
                spNodeSource->SetUnknown(MF_TOPONODE_STREAM_DESCRIPTOR, spSD.Get());

                // Output node
                ComPtr<IMFTopologyNode> spNodeOutput;
                hr = MFCreateTopologyNode(MF_TOPOLOGY_OUTPUT_NODE, &spNodeOutput);
                if (FAILED(hr))
                    continue;

                ComPtr<IMFActivate> spRenderer;
                hr = MFCreateAudioRendererActivate(&spRenderer);
                if (FAILED(hr))
                    continue;

                spNodeOutput->SetObject(spRenderer.Get());

                spTopology->AddNode(spNodeSource.Get());
                spTopology->AddNode(spNodeOutput.Get());
                spNodeSource->ConnectOutput(0, spNodeOutput.Get(), 0);

                audioConnected = true;
            }
        }

        if (!audioConnected)
        {
            SetError(L"No audio stream found");
            return false;
        }

        hr = m_spSession->SetTopology(0, spTopology.Get());
        if (FAILED(hr))
        {
            SetError(L"SetTopology failed");
            return false;
        }

        // Volume interface is no longer obtained or used
        return true;
    }

    void AudioPlayerEngine::DestroyMediaSession()
    {
        if (m_spSession)
        {
            m_spSession->Stop();
            m_spSession->Close();
            m_spSession.Reset();
        }
        m_spEventGen.Reset();
        if (m_spSource)
        {
            m_spSource->Shutdown();
            m_spSource.Reset();
        }
    }

    // ---------------------------------------------------------------------
    // MIDI playback implementation (MCI only – volume removed)
    // ---------------------------------------------------------------------
    bool AudioPlayerEngine::PlayMidi(const AudioTrack &track)
    {
        // Generate unique alias
        static int midiCounter = 0;
        m_midiAlias = L"CalendarMIDI_" + std::to_wstring(++midiCounter);

        // Open MIDI device
        std::wstring openCmd = L"open \"" + track.filePath + L"\" type sequencer alias " + m_midiAlias;
        if (mciSendStringW(openCmd.c_str(), nullptr, 0, nullptr) != 0)
        {
            SetError(L"MCI: cannot open MIDI file");
            return false;
        }

        // Set time format to milliseconds
        std::wstring setTimeCmd = L"set " + m_midiAlias + L" time format milliseconds";
        if (mciSendStringW(setTimeCmd.c_str(), nullptr, 0, nullptr) != 0)
        {
            CloseMidi();
            SetError(L"MCI: cannot set time format");
            return false;
        }

        // Get duration
        wchar_t buf[64];
        std::wstring statusLenCmd = L"status " + m_midiAlias + L" length";
        if (mciSendStringW(statusLenCmd.c_str(), buf, 64, nullptr) == 0)
        {
            currentTrack.duration = _wtoi(buf);
        }

        // No volume/mute handling – plays at system default

        // Start playback
        std::wstring playCmd = L"play " + m_midiAlias;
        if (mciSendStringW(playCmd.c_str(), nullptr, 0, nullptr) != 0)
        {
            CloseMidi();
            SetError(L"MCI: cannot play MIDI");
            return false;
        }

        m_isMidi = true;
        state = PlaybackState::PLAYING;
        return true;
    }

    bool AudioPlayerEngine::PauseMidi()
    {
        if (!m_isMidi || m_midiAlias.empty())
            return false;
        std::wstring pauseCmd = L"pause " + m_midiAlias;
        return mciSendStringW(pauseCmd.c_str(), nullptr, 0, nullptr) == 0;
    }

    bool AudioPlayerEngine::ResumeMidi()
    {
        if (!m_isMidi || m_midiAlias.empty())
            return false;
        std::wstring playCmd = L"play " + m_midiAlias;
        return mciSendStringW(playCmd.c_str(), nullptr, 0, nullptr) == 0;
    }

    bool AudioPlayerEngine::StopMidi()
    {
        if (!m_isMidi || m_midiAlias.empty())
            return true; // already stopped

        std::wstring stopCmd = L"stop " + m_midiAlias;
        mciSendStringW(stopCmd.c_str(), nullptr, 0, nullptr); // ignore result
        CloseMidi();
        m_isMidi = false;
        return true;
    }

    void AudioPlayerEngine::CloseMidi()
    {
        if (!m_midiAlias.empty())
        {
            std::wstring closeCmd = L"close " + m_midiAlias;
            mciSendStringW(closeCmd.c_str(), nullptr, 0, nullptr);
            m_midiAlias.clear();
        }
    }

    bool AudioPlayerEngine::SeekMidi(long positionMillis)
    {
        if (!m_isMidi || m_midiAlias.empty())
            return false;
        wchar_t posStr[32];
        swprintf_s(posStr, L"%ld", positionMillis);
        std::wstring seekCmd = L"seek " + m_midiAlias + L" to " + posStr;
        if (mciSendStringW(seekCmd.c_str(), nullptr, 0, nullptr) != 0)
            return false;

        // If currently playing, resume from new position
        if (state == PlaybackState::PLAYING)
        {
            std::wstring playCmd = L"play " + m_midiAlias;
            mciSendStringW(playCmd.c_str(), nullptr, 0, nullptr);
        }
        currentTrack.currentPosition = positionMillis;
        return true;
    }

    long AudioPlayerEngine::GetMidiPosition() const
    {
        if (!m_isMidi || m_midiAlias.empty())
            return 0;
        wchar_t buf[64];
        std::wstring statusPosCmd = L"status " + m_midiAlias + L" position";
        if (mciSendStringW(statusPosCmd.c_str(), buf, 64, nullptr) == 0)
            return _wtoi(buf);
        return currentTrack.currentPosition;
    }

    long AudioPlayerEngine::GetMidiDuration() const
    {
        if (!m_isMidi)
            return 0;
        wchar_t buf[64];
        std::wstring statusLenCmd = L"status " + m_midiAlias + L" length";
        if (mciSendStringW(statusLenCmd.c_str(), buf, 64, nullptr) == 0)
            return _wtoi(buf);
        return currentTrack.duration;
    }

    void AudioPlayerEngine::CheckMidiStatus()
    {
        if (!m_isMidi || m_midiAlias.empty() || state == PlaybackState::STOPPED)
            return;

        wchar_t buf[32];
        std::wstring statusModeCmd = L"status " + m_midiAlias + L" mode";
        if (mciSendStringW(statusModeCmd.c_str(), buf, 32, nullptr) == 0)
        {
            std::wstring mode(buf);
            if (mode == L"stopped" && state == PlaybackState::PLAYING)
            {
                // Playback finished
                state = PlaybackState::STOPPED;
                currentTrack.currentPosition = 0;
                if (onTrackEnd)
                    onTrackEnd();
            }
            else if (mode == L"paused")
            {
                // state should already be PAUSED; do nothing
            }
            else if (mode == L"playing")
            {
                // update current position for UI
                currentTrack.currentPosition = GetMidiPosition();
            }
        }
    }

    // ---------------------------------------------------------------------
    // Public playback control
    // ---------------------------------------------------------------------
    bool AudioPlayerEngine::play(const AudioTrack &track)
    {
        if (!track.isSupportedFormat())
        {
            SetError(L"Unsupported format");
            return false;
        }

        stop(); // clean up previous session or MIDI

        currentTrack = track;
        currentTrack.currentPosition = 0;

        if (IsMidiFile(track.filePath))
        {
            if (!PlayMidi(track))
                return false;
        }
        else
        {
            if (!CreateMediaSession(track))
                return false;

            PROPVARIANT varStart;
            PropVariantInit(&varStart);
            varStart.vt = VT_I8;
            varStart.hVal.QuadPart = 0;
            HRESULT hr = m_spSession->Start(nullptr, &varStart);
            PropVariantClear(&varStart);
            if (FAILED(hr))
            {
                SetError(L"Start failed");
                DestroyMediaSession();
                return false;
            }

            // Volume is not set – playback uses system default
        }

        state = PlaybackState::PLAYING;
        SetError(L"");
        return true;
    }

    bool AudioPlayerEngine::pause()
    {
        if (state != PlaybackState::PLAYING)
            return false;

        if (m_isMidi)
        {
            if (!PauseMidi())
                return false;
        }
        else
        {
            if (!m_spSession)
                return false;
            m_spSession->Pause();
        }

        state = PlaybackState::PAUSED;
        return true;
    }

    bool AudioPlayerEngine::resume()
    {
        if (state != PlaybackState::PAUSED)
            return false;

        if (m_isMidi)
        {
            if (!ResumeMidi())
                return false;
        }
        else
        {
            if (!m_spSession)
                return false;
            PROPVARIANT varStart;
            PropVariantInit(&varStart);
            varStart.vt = VT_I8;
            varStart.hVal.QuadPart = 0;
            m_spSession->Start(nullptr, &varStart);
            PropVariantClear(&varStart);
        }

        state = PlaybackState::PLAYING;
        return true;
    }

    bool AudioPlayerEngine::stop()
    {
        if (m_isMidi)
        {
            StopMidi();
        }
        else
        {
            DestroyMediaSession();
        }

        state = PlaybackState::STOPPED;
        currentTrack.currentPosition = 0;
        return true;
    }

    bool AudioPlayerEngine::seek(long positionMillis)
    {
        if (state == PlaybackState::STOPPED)
            return false;

        if (m_isMidi)
        {
            return SeekMidi(positionMillis);
        }
        else
        {
            if (!m_spSession)
                return false;
            PROPVARIANT varPos;
            PropVariantInit(&varPos);
            varPos.vt = VT_I8;
            varPos.hVal.QuadPart = positionMillis * 10000;
            HRESULT hr = m_spSession->Start(nullptr, &varPos);
            PropVariantClear(&varPos);
            if (SUCCEEDED(hr))
                currentTrack.currentPosition = positionMillis;
            return SUCCEEDED(hr);
        }
    }

    long AudioPlayerEngine::getCurrentPosition() const
    {
        if (state == PlaybackState::STOPPED)
            return 0;

        if (m_isMidi)
        {
            return GetMidiPosition();
        }
        else
        {
            ComPtr<IMFPresentationClock> spClock;
            ComPtr<IMFClock> spClockBase;
            if (SUCCEEDED(m_spSession->GetClock(&spClockBase)) &&
                SUCCEEDED(spClockBase.As(&spClock)))
            {
                MFTIME time;
                if (SUCCEEDED(spClock->GetTime(&time)))
                    return (long)(time / 10000);
            }
            return currentTrack.currentPosition;
        }
    }

    // ---------------------------------------------------------------------
    // Event processing – must be called periodically from main thread
    // ---------------------------------------------------------------------
    void AudioPlayerEngine::ProcessSessionEvents()
    {
        if (!m_spEventGen)
            return;
        IMFMediaEvent *pEvent = nullptr;
        while (m_spEventGen->GetEvent(0, &pEvent) == S_OK)
        {
            MediaEventType met;
            pEvent->GetType(&met);
            if (met == MESessionEnded && onTrackEnd)
                onTrackEnd();
            pEvent->Release();
        }
    }

    void AudioPlayerEngine::processEvents()
    {
        if (m_isMidi)
        {
            CheckMidiStatus();
        }
        else
        {
            ProcessSessionEvents();
        }
    }

    void AudioPlayerEngine::cleanup()
    {
        if (m_isMidi)
        {
            StopMidi();
        }
        else
        {
            DestroyMediaSession();
        }
        state = PlaybackState::STOPPED;
        currentTrack = AudioTrack();
    }

    // ---------------------------------------------------------------------
    // AudioFileManager – unchanged (no volume references)
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
            for (const auto &entry : fs::directory_iterator(audioDirectory))
            {
                if (!entry.is_regular_file())
                    continue;
                std::wstring ext = entry.path().extension().wstring();
                for (wchar_t &c : ext)
                    c = towlower(c);
                if (ext == L".wav" || ext == L".mp3" || ext == L".m4a" || ext == L".wma" || ext == L".mid" || ext == L".midi")
                    tracks.push_back(createTrackFromFile(entry.path().wstring(), num++));
            }
            nextTrackNumber = num;
        }
        catch (...)
        {
        }
        return tracks;
    }

    AudioTrack AudioFileManager::uploadAudioFile(const std::wstring &filePath)
    {
        std::wstring destPath;
        if (copyFileToAudioDir(filePath, destPath))
            return createTrackFromFile(destPath, nextTrackNumber++);
        return AudioTrack();
    }

    bool AudioFileManager::deleteAudioTrack(const AudioTrack &track)
    {
        return DeleteFileW(track.filePath.c_str()) == TRUE;
    }

    bool AudioFileManager::clearAllAudioFiles()
    {
        bool ok = true;
        for (const auto &entry : fs::directory_iterator(audioDirectory))
            if (!DeleteFileW(entry.path().wstring().c_str()))
                ok = false;
        nextTrackNumber = 1;
        return ok;
    }

    std::wstring AudioFileManager::getAudioDirectory() const { return audioDirectory; }

    std::wstring AudioFileManager::getUniqueFileName(const std::wstring &originalName)
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

    bool AudioFileManager::copyFileToAudioDir(const std::wstring &sourcePath, std::wstring &destPath)
    {
        std::wstring fileName = getUniqueFileName(sourcePath);
        destPath = audioDirectory + L"\\" + fileName;
        return CopyFileW(sourcePath.c_str(), destPath.c_str(), FALSE) == TRUE;
    }

    AudioTrack AudioFileManager::createTrackFromFile(const std::wstring &filePath, int trackNumber)
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

    long AudioFileManager::getAudioDuration(const std::wstring &filePath)
    {
        ComPtr<IMFSourceResolver> spResolver;
        if (FAILED(MFCreateSourceResolver(&spResolver)))
            return 0;

        MF_OBJECT_TYPE objectType;
        ComPtr<IUnknown> spSourceUnk;
        if (FAILED(spResolver->CreateObjectFromURL(filePath.c_str(),
                                                   MF_RESOLUTION_MEDIASOURCE, nullptr, &objectType, &spSourceUnk)))
            return 0;

        ComPtr<IMFMediaSource> spSource;
        if (FAILED(spSourceUnk.As(&spSource)))
            return 0;

        ComPtr<IMFPresentationDescriptor> spPD;
        if (FAILED(spSource->CreatePresentationDescriptor(&spPD)))
            return 0;

        UINT64 duration = 0;
        spPD->GetUINT64(MF_PD_DURATION, &duration);
        spSource->Shutdown();
        return (long)(duration / 10000);
    }
}