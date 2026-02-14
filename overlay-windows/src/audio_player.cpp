// ==================== audio_player.cpp ====================
// Audio playback implementation for Calendar Overlay.
// Uses Windows Media Foundation for most formats, and MCI for MIDI
// (since Media Foundation doesn't support MIDI natively).
// Volume control is intentionally omitted; we rely on system defaults.
//
// This file is structured into three main parts:
//   1. AudioTrack helpers (formatting, validation)
//   2. AudioPlayerEngine – core playback logic
//   3. AudioFileManager – file operations for the audio library

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

    // Returns duration formatted as "MM:SS". If duration <= 0, returns "00:00".
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

    // Checks whether the file extension is in our supported list.
    // Extensions are compared case-insensitively.
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

    // Constructor: initializes COM and Media Foundation for the lifetime of the engine.
    AudioPlayerEngine::AudioPlayerEngine()
        : state(PlaybackState::STOPPED), m_isMidi(false), m_midiAlias(L"")
    {
        HRESULT hr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        if (SUCCEEDED(hr))
            MFStartup(MF_VERSION);
    }

    // Destructor: shuts down Media Foundation and uninitializes COM.
    AudioPlayerEngine::~AudioPlayerEngine()
    {
        cleanup();
        MFShutdown();
        CoUninitialize();
    }

    // Internal helper to store the last error message and also output to debug console.
    void AudioPlayerEngine::SetError(const std::wstring &err)
    {
        std::lock_guard<std::mutex> lock(m_errorMutex);
        m_lastError = err;
        OutputDebugStringW((L"[Audio] " + err + L"\n").c_str());
    }

    // Public getter for the last error (thread-safe via mutex).
    std::wstring AudioPlayerEngine::getLastError() const
    {
        std::lock_guard<std::mutex> lock(m_errorMutex);
        return m_lastError;
    }

    // ---------------------------------------------------------------------
    // MIDI detection
    // ---------------------------------------------------------------------

    // Simple extension check to decide whether a file should be played via MCI.
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

    // Sets up a Media Foundation session for a non-MIDI audio track.
    // This involves creating a source, building a topology, and preparing for playback.
    bool AudioPlayerEngine::CreateMediaSession(const AudioTrack &track)
    {
        DestroyMediaSession();

        // Step 1: Create a source resolver to open the file.
        ComPtr<IMFSourceResolver> spResolver;
        HRESULT hr = MFCreateSourceResolver(&spResolver);
        if (FAILED(hr))
        {
            SetError(L"MFCreateSourceResolver failed");
            return false;
        }

        // Step 2: Create a media source from the file URL.
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

        // Step 3: Get the IMFMediaSource interface.
        hr = spSourceUnk.As(&m_spSource);
        if (FAILED(hr))
        {
            SetError(L"Not a media source");
            return false;
        }

        // Step 4: Get the presentation descriptor – it contains stream info and duration.
        ComPtr<IMFPresentationDescriptor> spPD;
        hr = m_spSource->CreatePresentationDescriptor(&spPD);
        if (FAILED(hr))
        {
            SetError(L"Cannot get presentation descriptor");
            return false;
        }

        // Read the total duration (in 100-ns units) and store in ms.
        UINT64 duration = 0;
        spPD->GetUINT64(MF_PD_DURATION, &duration);
        currentTrack.duration = (long)(duration / 10000);

        // Step 5: Create the media session.
        hr = MFCreateMediaSession(nullptr, &m_spSession);
        if (FAILED(hr))
        {
            SetError(L"Cannot create media session");
            return false;
        }

        // Get the event generator interface (optional, used for non-blocking event polling).
        hr = m_spSession.As(&m_spEventGen);
        // non‑fatal if fails

        // Step 6: Build a topology that connects the audio stream to the default audio renderer.
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
                continue; // stream not selected by default, skip

            // Get the media type handler to discover the major type.
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
                // Create a source node for this audio stream.
                ComPtr<IMFTopologyNode> spNodeSource;
                hr = MFCreateTopologyNode(MF_TOPOLOGY_SOURCESTREAM_NODE, &spNodeSource);
                if (FAILED(hr))
                    continue;

                spNodeSource->SetUnknown(MF_TOPONODE_SOURCE, m_spSource.Get());
                spNodeSource->SetUnknown(MF_TOPONODE_PRESENTATION_DESCRIPTOR, spPD.Get());
                spNodeSource->SetUnknown(MF_TOPONODE_STREAM_DESCRIPTOR, spSD.Get());

                // Create an output node with the audio renderer activate object.
                ComPtr<IMFTopologyNode> spNodeOutput;
                hr = MFCreateTopologyNode(MF_TOPOLOGY_OUTPUT_NODE, &spNodeOutput);
                if (FAILED(hr))
                    continue;

                ComPtr<IMFActivate> spRenderer;
                hr = MFCreateAudioRendererActivate(&spRenderer);
                if (FAILED(hr))
                    continue;

                spNodeOutput->SetObject(spRenderer.Get());

                // Add both nodes to the topology and connect them.
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

        // Step 7: Set the topology on the session.
        hr = m_spSession->SetTopology(0, spTopology.Get());
        if (FAILED(hr))
        {
            SetError(L"SetTopology failed");
            return false;
        }

        // Volume interface is no longer obtained or used – we play at system volume.
        return true;
    }

    // Cleans up the Media Foundation session and source.
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

    // Starts MIDI playback using MCI commands.
    bool AudioPlayerEngine::PlayMidi(const AudioTrack &track)
    {
        // Generate a unique alias for this MIDI instance (MCI requires aliases).
        static int midiCounter = 0;
        m_midiAlias = L"CalendarMIDI_" + std::to_wstring(++midiCounter);

        // Open the MIDI device/sequencer with the file.
        std::wstring openCmd = L"open \"" + track.filePath + L"\" type sequencer alias " + m_midiAlias;
        if (mciSendStringW(openCmd.c_str(), nullptr, 0, nullptr) != 0)
        {
            SetError(L"MCI: cannot open MIDI file");
            return false;
        }

        // Set time format to milliseconds so we can seek and query position in ms.
        std::wstring setTimeCmd = L"set " + m_midiAlias + L" time format milliseconds";
        if (mciSendStringW(setTimeCmd.c_str(), nullptr, 0, nullptr) != 0)
        {
            CloseMidi();
            SetError(L"MCI: cannot set time format");
            return false;
        }

        // Query the total length (duration) of the MIDI file.
        wchar_t buf[64];
        std::wstring statusLenCmd = L"status " + m_midiAlias + L" length";
        if (mciSendStringW(statusLenCmd.c_str(), buf, 64, nullptr) == 0)
        {
            currentTrack.duration = _wtoi(buf);
        }

        // No volume/mute handling – plays at system default

        // Start playback.
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

    // Pauses MIDI playback.
    bool AudioPlayerEngine::PauseMidi()
    {
        if (!m_isMidi || m_midiAlias.empty())
            return false;
        std::wstring pauseCmd = L"pause " + m_midiAlias;
        return mciSendStringW(pauseCmd.c_str(), nullptr, 0, nullptr) == 0;
    }

    // Resumes paused MIDI playback.
    bool AudioPlayerEngine::ResumeMidi()
    {
        if (!m_isMidi || m_midiAlias.empty())
            return false;
        std::wstring playCmd = L"play " + m_midiAlias;
        return mciSendStringW(playCmd.c_str(), nullptr, 0, nullptr) == 0;
    }

    // Stops MIDI playback and closes the MCI device.
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

    // Closes the MCI device (internal, no state change).
    void AudioPlayerEngine::CloseMidi()
    {
        if (!m_midiAlias.empty())
        {
            std::wstring closeCmd = L"close " + m_midiAlias;
            mciSendStringW(closeCmd.c_str(), nullptr, 0, nullptr);
            m_midiAlias.clear();
        }
    }

    // Seeks to a position (in milliseconds) in the MIDI file.
    bool AudioPlayerEngine::SeekMidi(long positionMillis)
    {
        if (!m_isMidi || m_midiAlias.empty())
            return false;
        wchar_t posStr[32];
        swprintf_s(posStr, L"%ld", positionMillis);
        std::wstring seekCmd = L"seek " + m_midiAlias + L" to " + posStr;
        if (mciSendStringW(seekCmd.c_str(), nullptr, 0, nullptr) != 0)
            return false;

        // If currently playing, resume from new position (MCI seek stops playback).
        if (state == PlaybackState::PLAYING)
        {
            std::wstring playCmd = L"play " + m_midiAlias;
            mciSendStringW(playCmd.c_str(), nullptr, 0, nullptr);
        }
        currentTrack.currentPosition = positionMillis;
        return true;
    }

    // Gets current playback position of MIDI file (in ms).
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

    // Gets total duration of MIDI file (in ms).
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

    // Polls MCI to see if playback has finished, and updates state/position.
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
                // Playback finished naturally.
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

    // Plays the given audio track (handles both MIDI and non-MIDI).
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
            varStart.hVal.QuadPart = 0; // start from beginning
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

    // Pauses current playback (both MIDI and non-MIDI).
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

    // Resumes from paused state.
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
            varStart.hVal.QuadPart = 0; // 0 means "current position" for start
            m_spSession->Start(nullptr, &varStart);
            PropVariantClear(&varStart);
        }

        state = PlaybackState::PLAYING;
        return true;
    }

    // Stops playback and resets position.
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

    // Seeks to a position (in milliseconds).
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
            varPos.hVal.QuadPart = positionMillis * 10000; // convert ms to 100-ns units
            HRESULT hr = m_spSession->Start(nullptr, &varPos);
            PropVariantClear(&varPos);
            if (SUCCEEDED(hr))
                currentTrack.currentPosition = positionMillis;
            return SUCCEEDED(hr);
        }
    }

    // Returns current playback position in milliseconds.
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
                    return (long)(time / 10000); // convert 100-ns to ms
            }
            return currentTrack.currentPosition;
        }
    }

    // ---------------------------------------------------------------------
    // Event processing – must be called periodically from main thread
    // ---------------------------------------------------------------------

    // Polls Media Foundation session for events (e.g., end-of-stream).
    void AudioPlayerEngine::ProcessSessionEvents()
    {
        if (!m_spEventGen)
            return;
        IMFMediaEvent *pEvent = nullptr;
        // Use MF_EVENT_FLAG_NO_WAIT to avoid blocking – we just want to check for any pending events.
        while (m_spEventGen->GetEvent(MF_EVENT_FLAG_NO_WAIT, &pEvent) == S_OK)
        {
            MediaEventType met;
            pEvent->GetType(&met);
            if (met == MESessionEnded && onTrackEnd)
                onTrackEnd();
            pEvent->Release();
        }
    }

    // Unified event processing – calls the appropriate method based on current playback type.
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

    // Cleans up any active playback resources.
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

    // Constructor: determines a suitable directory under the user's profile.
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

    // Scans the audio directory for supported files and returns a list of AudioTrack objects.
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
            // If directory iteration fails, just return what we have (likely empty).
        }
        return tracks;
    }

    // Copies a user-selected file into the audio directory and returns an AudioTrack for it.
    AudioTrack AudioFileManager::uploadAudioFile(const std::wstring &filePath)
    {
        std::wstring destPath;
        if (copyFileToAudioDir(filePath, destPath))
            return createTrackFromFile(destPath, nextTrackNumber++);
        return AudioTrack();
    }

    // Deletes the physical file associated with an AudioTrack.
    bool AudioFileManager::deleteAudioTrack(const AudioTrack &track)
    {
        return DeleteFileW(track.filePath.c_str()) == TRUE;
    }

    // Deletes all audio files in the managed directory.
    bool AudioFileManager::clearAllAudioFiles()
    {
        bool ok = true;
        for (const auto &entry : fs::directory_iterator(audioDirectory))
            if (!DeleteFileW(entry.path().wstring().c_str()))
                ok = false;
        nextTrackNumber = 1;
        return ok;
    }

    // Returns the current audio directory path.
    std::wstring AudioFileManager::getAudioDirectory() const { return audioDirectory; }

    // Generates a unique filename in the audio directory (appends (n) if needed).
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

    // Copies a file from sourcePath into the audio directory, returning the destination path.
    bool AudioFileManager::copyFileToAudioDir(const std::wstring &sourcePath, std::wstring &destPath)
    {
        std::wstring fileName = getUniqueFileName(sourcePath);
        destPath = audioDirectory + L"\\" + fileName;
        return CopyFileW(sourcePath.c_str(), destPath.c_str(), FALSE) == TRUE;
    }

    // Creates an AudioTrack object from a file path and assigns a track number.
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

    // Helper to retrieve the duration of an audio file (in ms) using Media Foundation.
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