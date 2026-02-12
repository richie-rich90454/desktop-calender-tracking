#include "audio_player.h"
#include <windows.h>
#include <mmsystem.h>
#include <mmreg.h>
#include <dsound.h>
#include <shlobj.h>
#include <shlwapi.h>
#include <comdef.h>
#include <comutil.h>
#include <wrl/client.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <algorithm>
#include <chrono>
#include <thread>
#include <filesystem>
#include <cstdlib>

#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "comsuppw.lib")
#pragma comment(lib, "strmiids.lib")

namespace fs = std::filesystem;

namespace CalendarOverlay {
namespace Audio {

// ---------------------------------------------------------------------
// AudioPlayerEngine
// ---------------------------------------------------------------------
AudioPlayerEngine::AudioPlayerEngine()
    : playbackState(PlaybackState::STOPPED),
      volume(0.8f),
      muted(false),
      hWaveOut(nullptr),
      pMediaSession(nullptr),
      hMidiOut(nullptr),
      hMidiStream(nullptr),
      midiHeader(nullptr),
      running(true) {
}

AudioPlayerEngine::~AudioPlayerEngine() {
    cleanup();
}

// ---------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------
bool AudioPlayerEngine::play(const AudioTrack& track) {
    if (!track.isSupportedFormat()) return false;
    stop();

    std::lock_guard<std::mutex> lock(stateMutex);
    currentTrack = track;

    std::wstring ext = track.fileExtension;
    if (ext.empty() && !track.filePath.empty()) {
        size_t dotPos = track.filePath.find_last_of(L'.');
        if (dotPos != std::wstring::npos)
            ext = track.filePath.substr(dotPos + 1);
    }
    for (wchar_t& c : ext) c = towlower(c);

    bool success = false;
    if (ext == L"wav") {
        success = playWav(track);
    } else if (ext == L"mp3") {
        success = playMp3(track);
    } else if (ext == L"mid" || ext == L"midi") {
        success = playMidi(track);
    }

    if (success) {
        playbackState = PlaybackState::PLAYING;
        currentTrack.playing = true;
        startPositionUpdater();
    }
    return success;
}

bool AudioPlayerEngine::pause() {
    std::lock_guard<std::mutex> lock(stateMutex);
    if (playbackState != PlaybackState::PLAYING) return false;

    std::wstring ext = currentTrack.fileExtension;
    for (wchar_t& c : ext) c = towlower(c);

    if (ext == L"wav" && hWaveOut) {
        waveOutPause(hWaveOut);
    } else if (ext == L"mp3" && pMediaSession) {
        pMediaSession->Pause();
    } else if ((ext == L"mid" || ext == L"midi") && hMidiStream) {
        midiStreamPause(hMidiStream);
    } else {
        return false;
    }

    playbackState = PlaybackState::PAUSED;
    currentTrack.playing = false;
    return true;
}

bool AudioPlayerEngine::resume() {
    std::lock_guard<std::mutex> lock(stateMutex);
    if (playbackState != PlaybackState::PAUSED) return false;

    std::wstring ext = currentTrack.fileExtension;
    for (wchar_t& c : ext) c = towlower(c);

    if (ext == L"wav" && hWaveOut) {
        waveOutRestart(hWaveOut);
    } else if (ext == L"mp3" && pMediaSession) {
        PROPVARIANT varStart;
        PropVariantInit(&varStart);
        varStart.vt = VT_I8;
        varStart.hVal.QuadPart = 0;
        pMediaSession->Start(NULL, &varStart);
        PropVariantClear(&varStart);
    } else if ((ext == L"mid" || ext == L"midi") && hMidiStream) {
        midiStreamRestart(hMidiStream);
    } else {
        return play(currentTrack);
    }

    playbackState = PlaybackState::PLAYING;
    currentTrack.playing = true;
    return true;
}

bool AudioPlayerEngine::stop() {
    std::lock_guard<std::mutex> lock(stateMutex);
    stopPositionUpdater();

    std::wstring ext = currentTrack.fileExtension;
    for (wchar_t& c : ext) c = towlower(c);

    if (ext == L"wav") {
        cleanupWav();
    } else if (ext == L"mp3") {
        cleanupMp3();
    } else if (ext == L"mid" || ext == L"midi") {
        cleanupMidi();
    }

    playbackState = PlaybackState::STOPPED;
    currentTrack.playing = false;
    currentTrack.currentPosition = 0;
    return true;
}

bool AudioPlayerEngine::seek(long positionMillis) {
    std::lock_guard<std::mutex> lock(stateMutex);
    if (playbackState == PlaybackState::STOPPED) return false;

    // For WAV: simple byte‑offset seek (single header)
    std::wstring ext = currentTrack.fileExtension;
    for (wchar_t& c : ext) c = towlower(c);

    if (ext == L"wav" && hWaveOut) {
        DWORD bytePosition = (DWORD)((double)positionMillis / 1000.0 * waveFormat.nAvgBytesPerSec);
        if (bytePosition > waveBuffer.size()) bytePosition = (DWORD)waveBuffer.size();
        waveOutReset(hWaveOut);

        // Allocate new header
        WAVEHDR* pHeader = new WAVEHDR;
        ZeroMemory(pHeader, sizeof(WAVEHDR));
        pHeader->lpData = waveBuffer.data() + bytePosition;
        pHeader->dwBufferLength = (DWORD)waveBuffer.size() - bytePosition;
        pHeader->dwFlags = 0;

        MMRESULT res = waveOutPrepareHeader(hWaveOut, pHeader, sizeof(WAVEHDR));
        if (res == MMSYSERR_NOERROR) {
            res = waveOutWrite(hWaveOut, pHeader, sizeof(WAVEHDR));
            if (res == MMSYSERR_NOERROR) {
                currentTrack.currentPosition = positionMillis;
                return true;
            }
            waveOutUnprepareHeader(hWaveOut, pHeader, sizeof(WAVEHDR));
        }
        delete pHeader;
        return false;
    }

    // For other formats we simply restart (no proper seeking yet)
    AudioTrack track = currentTrack;
    stop();
    return play(track);
}

bool AudioPlayerEngine::setVolume(float newVolume) {
    std::lock_guard<std::mutex> lock(stateMutex);
    volume = std::max(0.0f, std::min(1.0f, newVolume));
    if (muted) return true;

    std::wstring ext = currentTrack.fileExtension;
    for (wchar_t& c : ext) c = towlower(c);

    if (ext == L"wav" && hWaveOut) {
        DWORD dwVolume = (DWORD)(volume * 0xFFFF);
        waveOutSetVolume(hWaveOut, MAKELONG(dwVolume, dwVolume));
    } else if (ext == L"mp3" && pMediaSession) {
        IMFSimpleAudioVolume* pAudioVolume = nullptr;
        HRESULT hr = pMediaSession->QueryInterface(IID_PPV_ARGS(&pAudioVolume));
        if (SUCCEEDED(hr)) {
            pAudioVolume->SetMasterVolume(volume);
            pAudioVolume->Release();
        }
    }
    return true;
}

bool AudioPlayerEngine::setMuted(bool newMuted) {
    std::lock_guard<std::mutex> lock(stateMutex);
    muted = newMuted;

    std::wstring ext = currentTrack.fileExtension;
    for (wchar_t& c : ext) c = towlower(c);

    if (ext == L"wav" && hWaveOut) {
        waveOutSetVolume(hWaveOut, muted ? 0 : (DWORD)(volume * 0xFFFF));
    } else if (ext == L"mp3" && pMediaSession) {
        IMFSimpleAudioVolume* pAudioVolume = nullptr;
        HRESULT hr = pMediaSession->QueryInterface(IID_PPV_ARGS(&pAudioVolume));
        if (SUCCEEDED(hr)) {
            pAudioVolume->SetMute(muted ? TRUE : FALSE);
            if (!muted) pAudioVolume->SetMasterVolume(volume);
            pAudioVolume->Release();
        }
    }
    return true;
}

long AudioPlayerEngine::getCurrentPosition() const {
    std::lock_guard<std::mutex> lock(stateMutex);   // 依赖头文件中 mutable 声明
    if (playbackState == PlaybackState::STOPPED)
        return 0;

    std::wstring ext = currentTrack.fileExtension;
    for (wchar_t& c : ext) c = towlower(c);

    if (ext == L"wav" && hWaveOut) {
        MMTIME mmt;
        mmt.wType = TIME_MS;
        if (waveOutGetPosition(hWaveOut, &mmt, sizeof(MMTIME)) == MMSYSERR_NOERROR)
            return mmt.u.ms;
    }
    else if (ext == L"mp3" && pMediaSession) {
        IMFPresentationClock* pClock = nullptr;
        IMFClock* pClockBase = nullptr;
        HRESULT hr = pMediaSession->GetClock(&pClockBase);
        if (SUCCEEDED(hr)) {
            hr = pClockBase->QueryInterface(IID_PPV_ARGS(&pClock));
            pClockBase->Release();
            if (SUCCEEDED(hr)) {
                MFTIME time;
                if (SUCCEEDED(pClock->GetTime(&time))) {
                    pClock->Release();
                    return (long)(time / 10000);   // 100 ns → ms
                }
                pClock->Release();
            }
        }
    }
    else if ((ext == L"mid" || ext == L"midi") && hMidiStream) {
        // MIDI 没有标准位置查询，返回存储的值
    }

    return currentTrack.currentPosition;
}

// ---------------------------------------------------------------------
// WAV implementation
// ---------------------------------------------------------------------
void CALLBACK AudioPlayerEngine::WaveOutCallback(HWAVEOUT hwo, UINT uMsg,
                                                 DWORD_PTR dwInstance,
                                                 DWORD_PTR dwParam1,
                                                 DWORD_PTR dwParam2) {
    if (uMsg == WOM_DONE) {
        WAVEHDR* pHeader = (WAVEHDR*)dwParam1;
        waveOutUnprepareHeader(hwo, pHeader, sizeof(WAVEHDR));
        delete[] pHeader->lpData;   // free the buffer (allocated in playWav)
        delete pHeader;            // free the header itself
    }
}

bool AudioPlayerEngine::playWav(const AudioTrack& track) {
    cleanupWav();

    HMMIO hmmio = mmioOpen(const_cast<LPWSTR>(track.filePath.c_str()), NULL, MMIO_READ);
    if (!hmmio) return false;

    MMCKINFO ckRiff;
    ckRiff.fccType = mmioFOURCC('W', 'A', 'V', 'E');
    if (mmioDescend(hmmio, &ckRiff, NULL, MMIO_FINDRIFF)) {
        mmioClose(hmmio, 0);
        return false;
    }

    MMCKINFO ckInfo;
    ckInfo.ckid = mmioFOURCC('f', 'm', 't', ' ');
    if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK)) {
        mmioClose(hmmio, 0);
        return false;
    }

    PCMWAVEFORMAT pcmWaveFormat;
    if (mmioRead(hmmio, (HPSTR)&pcmWaveFormat, sizeof(pcmWaveFormat)) != sizeof(pcmWaveFormat)) {
        mmioClose(hmmio, 0);
        return false;
    }
    if (pcmWaveFormat.wf.wFormatTag != WAVE_FORMAT_PCM) {
        mmioClose(hmmio, 0);
        return false;
    }

    mmioAscend(hmmio, &ckInfo, 0);

    ckInfo.ckid = mmioFOURCC('d', 'a', 't', 'a');
    if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK)) {
        mmioClose(hmmio, 0);
        return false;
    }

    // Allocate buffer and read data
    std::unique_ptr<char[]> buffer = std::make_unique<char[]>(ckInfo.cksize);
    if (mmioRead(hmmio, buffer.get(), ckInfo.cksize) != ckInfo.cksize) {
        mmioClose(hmmio, 0);
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
                               (DWORD_PTR)WaveOutCallback, (DWORD_PTR)this,
                               CALLBACK_FUNCTION);
    if (res != MMSYSERR_NOERROR) return false;

    WAVEHDR* pHeader = new WAVEHDR;
    ZeroMemory(pHeader, sizeof(WAVEHDR));
    pHeader->lpData = buffer.release();   // transfer ownership
    pHeader->dwBufferLength = (DWORD)ckInfo.cksize;
    pHeader->dwFlags = 0;

    res = waveOutPrepareHeader(hWaveOut, pHeader, sizeof(WAVEHDR));
    if (res != MMSYSERR_NOERROR) {
        delete[] pHeader->lpData;
        delete pHeader;
        waveOutClose(hWaveOut);
        hWaveOut = nullptr;
        return false;
    }

    res = waveOutWrite(hWaveOut, pHeader, sizeof(WAVEHDR));
    if (res != MMSYSERR_NOERROR) {
        waveOutUnprepareHeader(hWaveOut, pHeader, sizeof(WAVEHDR));
        delete[] pHeader->lpData;
        delete pHeader;
        waveOutClose(hWaveOut);
        hWaveOut = nullptr;
        return false;
    }

    currentTrack.duration = (long)((double)ckInfo.cksize / waveFormat.nAvgBytesPerSec * 1000.0);
    // Store buffer for later use (seek, etc.)
    waveBuffer.assign(pHeader->lpData, pHeader->lpData + pHeader->dwBufferLength);
    return true;
}

void AudioPlayerEngine::cleanupWav() {
    if (hWaveOut) {
        waveOutReset(hWaveOut);
        waveOutClose(hWaveOut);   // closing frees all pending headers (callback may still run)
        hWaveOut = nullptr;
    }
    waveBuffer.clear();
}

// ---------------------------------------------------------------------
// MP3 implementation (Media Foundation)
// ---------------------------------------------------------------------
static int mfInitCounter = 0;   // ensure balanced MFStartup/MFShutdown

bool AudioPlayerEngine::playMp3(const AudioTrack& track) {
    cleanupMp3();

    HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);
    if (FAILED(hr)) return false;

    hr = MFStartup(MF_VERSION, MFSTARTUP_LITE);
    if (FAILED(hr)) {
        CoUninitialize();
        return false;
    }
    mfInitCounter++;

    hr = MFCreateMediaSession(NULL, &pMediaSession);
    if (FAILED(hr)) {
        cleanupMp3();
        return false;
    }

    IMFSourceReader* pReader = nullptr;
    hr = MFCreateSourceReaderFromURL(track.filePath.c_str(), NULL, &pReader);
    if (FAILED(hr)) {
        cleanupMp3();
        return false;
    }

    // Set output type to PCM (decode)
    IMFMediaType* pMediaType = nullptr;
    hr = MFCreateMediaType(&pMediaType);
    if (SUCCEEDED(hr)) {
        pMediaType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
        pMediaType->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_PCM);
        hr = pReader->SetCurrentMediaType((DWORD)MF_SOURCE_READER_FIRST_AUDIO_STREAM,
                                          NULL, pMediaType);
        pMediaType->Release();
    }
    if (FAILED(hr)) {
        pReader->Release();
        cleanupMp3();
        return false;
    }

    // Create topology
    IMFTopology* pTopology = nullptr;
    hr = MFCreateTopology(&pTopology);
    if (SUCCEEDED(hr)) {
        IMFPresentationDescriptor* pPD = nullptr;
        hr = ((IMFMediaSource*)pReader)->CreatePresentationDescriptor(&pPD);
        if (SUCCEEDED(hr)) {
            DWORD streamCount = 0;
            hr = pPD->GetStreamDescriptorCount(&streamCount);
            for (DWORD i = 0; i < streamCount && SUCCEEDED(hr); ++i) {
                BOOL selected = FALSE;
                IMFStreamDescriptor* pSD = nullptr;
                hr = pPD->GetStreamDescriptorByIndex(i, &selected, &pSD);
                if (SUCCEEDED(hr) && selected) {
                    IMFTopologyNode* pSourceNode = nullptr;
                    IMFTopologyNode* pOutputNode = nullptr;
                    hr = MFCreateTopologyNode(MF_TOPOLOGY_SOURCESTREAM_NODE, &pSourceNode);
                    if (SUCCEEDED(hr)) hr = pSourceNode->SetUnknown(MF_TOPONODE_SOURCE, (IMFMediaSource*)pReader);
                    if (SUCCEEDED(hr)) hr = pSourceNode->SetUnknown(MF_TOPONODE_PRESENTATION_DESCRIPTOR, pPD);
                    if (SUCCEEDED(hr)) hr = pSourceNode->SetUnknown(MF_TOPONODE_STREAM_DESCRIPTOR, pSD);
                    if (SUCCEEDED(hr)) hr = MFCreateTopologyNode(MF_TOPOLOGY_OUTPUT_NODE, &pOutputNode);
                    if (SUCCEEDED(hr)) {
                        IMFActivate* pActivate = nullptr;
                        hr = MFCreateAudioRendererActivate(&pActivate);
                        if (SUCCEEDED(hr)) {
                            hr = pOutputNode->SetObject(pActivate);
                            pActivate->Release();
                        }
                    }
                    if (SUCCEEDED(hr)) hr = pTopology->AddNode(pSourceNode);
                    if (SUCCEEDED(hr)) hr = pTopology->AddNode(pOutputNode);
                    if (SUCCEEDED(hr)) hr = pSourceNode->ConnectOutput(0, pOutputNode, 0);
                    if (pSourceNode) pSourceNode->Release();
                    if (pOutputNode) pOutputNode->Release();
                }
                if (pSD) pSD->Release();
            }
            pPD->Release();
        }
    }

    if (SUCCEEDED(hr)) {
        hr = pMediaSession->SetTopology(0, pTopology);
    }
    if (SUCCEEDED(hr)) {
        PROPVARIANT varStart;
        PropVariantInit(&varStart);
        varStart.vt = VT_I8;
        varStart.hVal.QuadPart = 0;
        hr = pMediaSession->Start(NULL, &varStart);
        PropVariantClear(&varStart);
    }

    pReader->Release();
    if (pTopology) pTopology->Release();

    if (SUCCEEDED(hr)) {
        // Get duration
        IMFPresentationDescriptor* pPD = nullptr;
        if (SUCCEEDED(((IMFMediaSource*)pReader)->CreatePresentationDescriptor(&pPD))) {
            UINT64 duration = 0;
            pPD->GetUINT64(MF_PD_DURATION, &duration);
            currentTrack.duration = (long)(duration / 10000); // 100‑ns → ms
            pPD->Release();
        }
        return true;
    }

    cleanupMp3();
    return false;
}

void AudioPlayerEngine::cleanupMp3() {
    if (pMediaSession) {
        pMediaSession->Stop();
        pMediaSession->Close();
        pMediaSession->Release();
        pMediaSession = nullptr;
        mfInitCounter--;
        if (mfInitCounter == 0) {
            MFShutdown();
            CoUninitialize();
        }
    }
}

// ---------------------------------------------------------------------
// MIDI implementation
// ---------------------------------------------------------------------
bool AudioPlayerEngine::playMidi(const AudioTrack& track) {
    cleanupMidi();

    UINT deviceId = MIDI_MAPPER;
    MMRESULT res = midiStreamOpen(&hMidiStream, &deviceId, 1, 0, 0, CALLBACK_NULL);
    if (res != MMSYSERR_NOERROR) return false;

    // Set time division (common value)
    MIDIPROPTIMEDIV prop;
    prop.cbStruct = sizeof(MIDIPROPTIMEDIV);
    prop.dwTimeDiv = 96;
    res = midiStreamProperty(hMidiStream, (LPBYTE)&prop, MIDIPROP_SET | MIDIPROP_TIMEDIV);
    if (res != MMSYSERR_NOERROR) {
        midiStreamClose(hMidiStream);
        hMidiStream = nullptr;
        return false;
    }

    // Read file into buffer
    std::ifstream file(track.filePath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        midiStreamClose(hMidiStream);
        hMidiStream = nullptr;
        return false;
    }
    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);
    midiBuffer.resize(size);
    if (!file.read(midiBuffer.data(), size)) {
        midiStreamClose(hMidiStream);
        hMidiStream = nullptr;
        return false;
    }

    // Prepare header
    midiHeader = new MIDIHDR;
    ZeroMemory(midiHeader, sizeof(MIDIHDR));
    midiHeader->lpData = midiBuffer.data();
    midiHeader->dwBufferLength = (DWORD)midiBuffer.size();
    midiHeader->dwFlags = 0;

    res = midiOutPrepareHeader((HMIDIOUT)hMidiStream, midiHeader, sizeof(MIDIHDR));
    if (res != MMSYSERR_NOERROR) {
        delete midiHeader;
        midiHeader = nullptr;
        midiStreamClose(hMidiStream);
        hMidiStream = nullptr;
        return false;
    }

    res = midiStreamOut(hMidiStream, midiHeader, sizeof(MIDIHDR));
    if (res != MMSYSERR_NOERROR) {
        midiOutUnprepareHeader((HMIDIOUT)hMidiStream, midiHeader, sizeof(MIDIHDR));
        delete midiHeader;
        midiHeader = nullptr;
        midiStreamClose(hMidiStream);
        hMidiStream = nullptr;
        return false;
    }

    res = midiStreamRestart(hMidiStream);
    if (res != MMSYSERR_NOERROR) {
        midiOutUnprepareHeader((HMIDIOUT)hMidiStream, midiHeader, sizeof(MIDIHDR));
        delete midiHeader;
        midiHeader = nullptr;
        midiStreamClose(hMidiStream);
        hMidiStream = nullptr;
        return false;
    }

    currentTrack.duration = 120000; // placeholder
    return true;
}

void AudioPlayerEngine::cleanupMidi() {
    if (hMidiStream) {
        midiStreamStop(hMidiStream);
        if (midiHeader) {
            midiOutUnprepareHeader((HMIDIOUT)hMidiStream, midiHeader, sizeof(MIDIHDR));
            delete midiHeader;
            midiHeader = nullptr;
        }
        midiStreamClose(hMidiStream);
        hMidiStream = nullptr;
    }
    midiBuffer.clear();
}

// ---------------------------------------------------------------------
// Position updater (simple polling)
// ---------------------------------------------------------------------
void AudioPlayerEngine::startPositionUpdater() {
    stopPositionUpdater();
    running = true;
    positionUpdater = std::thread([this]() {
        while (running && playbackState == PlaybackState::PLAYING) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
            std::lock_guard<std::mutex> lock(stateMutex);
            currentTrack.currentPosition = getCurrentPosition();
        }
    });
}

void AudioPlayerEngine::stopPositionUpdater() {
    running = false;
    if (positionUpdater.joinable())
        positionUpdater.join();
}

// ---------------------------------------------------------------------
// Global cleanup
// ---------------------------------------------------------------------
void AudioPlayerEngine::cleanup() {
    stop();
    cleanupWav();
    cleanupMp3();
    cleanupMidi();
    stopPositionUpdater();
}

// ---------------------------------------------------------------------
// AudioFileManager (unchanged – works as before)
// ---------------------------------------------------------------------
AudioFileManager::AudioFileManager() : nextTrackNumber(1) {
    wchar_t appDataPath[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_APPDATA, NULL, 0, appDataPath))) {
        audioDirectory = std::wstring(appDataPath) + L"\\DesktopCalendar\\Audio";
        CreateDirectoryW(audioDirectory.c_str(), NULL);
    }
}

AudioFileManager::~AudioFileManager() {}

std::vector<AudioTrack> AudioFileManager::scanAudioFiles() {
    std::vector<AudioTrack> tracks;
    if (audioDirectory.empty()) return tracks;
    try {
        int trackNum = 1;
        for (const auto& entry : fs::directory_iterator(audioDirectory)) {
            if (entry.is_regular_file()) {
                std::wstring filePath = entry.path().wstring();
                std::wstring fileName = entry.path().filename().wstring();
                size_t dotPos = fileName.find_last_of(L'.');
                if (dotPos != std::wstring::npos) {
                    std::wstring ext = fileName.substr(dotPos + 1);
                    for (wchar_t& c : ext) c = towlower(c);
                    if (ext == L"mp3" || ext == L"wav" || ext == L"mid" || ext == L"midi") {
                        AudioTrack track = createTrackFromFile(filePath, trackNum++);
                        tracks.push_back(track);
                    }
                }
            }
        }
        nextTrackNumber = trackNum;
    } catch (...) {}
    return tracks;
}

AudioTrack AudioFileManager::uploadAudioFile(const std::wstring& filePath) {
    std::wstring destPath;
    if (copyFileToAudioDir(filePath, destPath)) {
        return createTrackFromFile(destPath, nextTrackNumber++);
    }
    return AudioTrack();
}

bool AudioFileManager::deleteAudioTrack(const AudioTrack& track) {
    return DeleteFileW(track.filePath.c_str()) ? true : false;
}

bool AudioFileManager::clearAllAudioFiles() {
    bool success = true;
    auto tracks = scanAudioFiles();
    for (const auto& track : tracks) {
        if (!DeleteFileW(track.filePath.c_str())) success = false;
    }
    nextTrackNumber = 1;
    return success;
}

std::wstring AudioFileManager::getAudioDirectory() const {
    return audioDirectory;
}

std::wstring AudioFileManager::getUniqueFileName(const std::wstring& originalName) {
    std::wstring baseName = originalName;
    size_t dotPos = baseName.find_last_of(L'.');
    std::wstring nameWithoutExt, ext;
    if (dotPos != std::wstring::npos) {
        nameWithoutExt = baseName.substr(0, dotPos);
        ext = baseName.substr(dotPos);
    } else {
        nameWithoutExt = baseName;
    }
    std::wstring newName = baseName;
    int counter = 1;
    while (fs::exists(audioDirectory + L"\\" + newName)) {
        std::wstringstream ss;
        ss << nameWithoutExt << L" (" << counter++ << L")" << ext;
        newName = ss.str();
    }
    return newName;
}

bool AudioFileManager::copyFileToAudioDir(const std::wstring& sourcePath, std::wstring& destPath) {
    std::wstring fileName = fs::path(sourcePath).filename().wstring();
    std::wstring uniqueName = getUniqueFileName(fileName);
    destPath = audioDirectory + L"\\" + uniqueName;
    return CopyFileW(sourcePath.c_str(), destPath.c_str(), FALSE) != 0;
}

AudioTrack AudioFileManager::createTrackFromFile(const std::wstring& filePath, int trackNumber) {
    AudioTrack track;
    track.filePath = filePath;
    track.fileName = fs::path(filePath).filename().wstring();
    track.displayName = track.fileName;
    track.trackNumber = trackNumber;
    size_t dotPos = track.fileName.find_last_of(L'.');
    if (dotPos != std::wstring::npos)
        track.fileExtension = track.fileName.substr(dotPos + 1);
    track.duration = getAudioDuration(filePath);
    track.currentPosition = 0;
    track.playing = false;
    return track;
}

long AudioFileManager::getAudioDuration(const std::wstring& filePath) {
    std::wstring ext = fs::path(filePath).extension().wstring();
    for (wchar_t& c : ext) c = towlower(c);
    if (ext == L".wav") {
        HMMIO hmmio = mmioOpen(const_cast<LPWSTR>(filePath.c_str()), NULL, MMIO_READ);
        if (!hmmio) return 0;
        MMCKINFO ckRiff;
        ckRiff.fccType = mmioFOURCC('W', 'A', 'V', 'E');
        if (mmioDescend(hmmio, &ckRiff, NULL, MMIO_FINDRIFF)) {
            mmioClose(hmmio, 0);
            return 0;
        }
        MMCKINFO ckInfo;
        ckInfo.ckid = mmioFOURCC('f', 'm', 't', ' ');
        if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK)) {
            mmioClose(hmmio, 0);
            return 0;
        }
        PCMWAVEFORMAT pcmWaveFormat;
        if (mmioRead(hmmio, (HPSTR)&pcmWaveFormat, sizeof(pcmWaveFormat)) != sizeof(pcmWaveFormat)) {
            mmioClose(hmmio, 0);
            return 0;
        }
        mmioAscend(hmmio, &ckInfo, 0);
        ckInfo.ckid = mmioFOURCC('d', 'a', 't', 'a');
        if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK)) {
            mmioClose(hmmio, 0);
            return 0;
        }
        long duration = (long)((double)ckInfo.cksize / pcmWaveFormat.wf.nAvgBytesPerSec * 1000.0);
        mmioClose(hmmio, 0);
        return duration;
    }
    return 0;
}

} // namespace Audio
} // namespace CalendarOverlay