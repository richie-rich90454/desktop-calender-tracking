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
#include <wmp.h>
#include <algorithm>
#include <chrono>
#include <thread>
#include <filesystem>
#include <cstdlib>

#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "comsuppw.lib")
#pragma comment(lib, "strmiids.lib")

namespace fs=std::filesystem;

namespace CalendarOverlay{
    namespace Audio{
        AudioPlayerEngine::AudioPlayerEngine() 
            : playbackState(PlaybackState::STOPPED), 
              volume(0.8f), 
              muted(false),
              hWaveOut(nullptr),
              hMidiOut(nullptr),
              pMediaPlayer(nullptr),
              running(true){
            waveBuffer.clear();
        }
        AudioPlayerEngine::~AudioPlayerEngine(){
            cleanup();
        }
        bool AudioPlayerEngine::play(const AudioTrack& track){
            if (!track.isSupportedFormat()){
                return false;
            }
            stop();
            std::lock_guard<std::mutex> lock(stateMutex);
            currentTrack=track;
            std::wstring ext=track.fileExtension;
            if (ext.empty()&&!track.filePath.empty()){
                size_t dotPos=track.filePath.find_last_of(L'.');
                if (dotPos!=std::wstring::npos){
                    ext=track.filePath.substr(dotPos + 1);
                }
            }
            for (wchar_t& c : ext) c=towlower(c);
            bool success=false;
            if (ext==L"wav"){
                success=playWav(track);
            }
            else if (ext==L"mp3"){
                success=playMp3(track);
            }
            else if (ext==L"mid"||ext==L"midi"){
                success=playMidi(track);
            }
            if (success){
                playbackState=PlaybackState::PLAYING;
                currentTrack.playing=true;
                startPositionUpdater();
            }
            return success;
        }
        bool AudioPlayerEngine::playWav(const AudioTrack& track){
            cleanupWav();
            HMMIO hmmio=mmioOpen(const_cast<LPWSTR>(track.filePath.c_str()), NULL, MMIO_READ);
            if (!hmmio){
                return false;
            }
            MMCKINFO ckRiff;
            ckRiff.fccType=mmioFOURCC('W', 'A', 'V', 'E');
            if (mmioDescend(hmmio, &ckRiff, NULL, MMIO_FINDRIFF)){
                mmioClose(hmmio, 0);
                return false;
            }
            MMCKINFO ckInfo;
            ckInfo.ckid=mmioFOURCC('f', 'm', 't', ' ');
            if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK)){
                mmioClose(hmmio, 0);
                return false;
            }
            PCMWAVEFORMAT pcmWaveFormat;
            if (mmioRead(hmmio, (HPSTR)&pcmWaveFormat, sizeof(pcmWaveFormat))!=sizeof(pcmWaveFormat)){
                mmioClose(hmmio, 0);
                return false;
            }
            if (pcmWaveFormat.wf.wFormatTag!=WAVE_FORMAT_PCM){
                mmioClose(hmmio, 0);
                return false;
            }
            mmioAscend(hmmio, &ckInfo, 0);
            ckInfo.ckid=mmioFOURCC('d', 'a', 't', 'a');
            if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK)){
                mmioClose(hmmio, 0);
                return false;
            }
            waveBuffer.resize(ckInfo.cksize);
            if (mmioRead(hmmio, (HPSTR)waveBuffer.data(), ckInfo.cksize)!=ckInfo.cksize){
                mmioClose(hmmio, 0);
                return false;
            }
            mmioClose(hmmio, 0);
            waveFormat.wFormatTag=WAVE_FORMAT_PCM;
            waveFormat.nChannels=pcmWaveFormat.wf.nChannels;
            waveFormat.nSamplesPerSec=pcmWaveFormat.wf.nSamplesPerSec;
            waveFormat.nAvgBytesPerSec=pcmWaveFormat.wf.nAvgBytesPerSec;
            waveFormat.nBlockAlign=pcmWaveFormat.wf.nBlockAlign;
            waveFormat.wBitsPerSample=pcmWaveFormat.wBitsPerSample;
            waveFormat.cbSize=0;
            MMRESULT result=waveOutOpen(&hWaveOut, WAVE_MAPPER, &waveFormat, (DWORD_PTR)NULL, 0, CALLBACK_NULL);
            if (result!=MMSYSERR_NOERROR){
                return false;
            }
            WAVEHDR waveHeader;
            ZeroMemory(&waveHeader, sizeof(WAVEHDR));
            waveHeader.lpData=waveBuffer.data();
            waveHeader.dwBufferLength=(DWORD)waveBuffer.size();
            waveHeader.dwFlags=0;
            result=waveOutPrepareHeader(hWaveOut, &waveHeader, sizeof(WAVEHDR));
            if (result!=MMSYSERR_NOERROR){
                waveOutClose(hWaveOut);
                hWaveOut=nullptr;
                return false;
            }
            result=waveOutWrite(hWaveOut, &waveHeader, sizeof(WAVEHDR));
            if (result!=MMSYSERR_NOERROR){
                waveOutUnprepareHeader(hWaveOut, &waveHeader, sizeof(WAVEHDR));
                waveOutClose(hWaveOut);
                hWaveOut=nullptr;
                return false;
            }
            currentTrack.duration=(long)((double)waveBuffer.size()/waveFormat.nAvgBytesPerSec * 1000.0);
            return true;
        }
        bool AudioPlayerEngine::playMp3(const AudioTrack& track){
            cleanupMp3();
            HRESULT hr=CoCreateInstance(CLSID_WindowsMediaPlayer, NULL,  CLSCTX_INPROC_SERVER, IID_IWMPPlayer, (void**)&pMediaPlayer);
            if (FAILED(hr)){
                return false;
            }
            IWMPPlayer* pPlayer=(IWMPPlayer*)pMediaPlayer;
            _bstr_t bstrPath(track.filePath.c_str());
            hr=pPlayer->put_URL(bstrPath);
            if (FAILED(hr)){
                pPlayer->Release();
                pMediaPlayer=nullptr;
                return false;
            }
            IWMPControls* pControls=NULL;
            hr=pPlayer->get_controls(&pControls);
            if (SUCCEEDED(hr)){
                hr=pControls->play();
                pControls->Release();
            }
            IWMPSettings* pSettings=NULL;
            hr=pPlayer->get_settings(&pSettings);
            if (SUCCEEDED(hr)){
                pSettings->put_volume((long)(volume * 100));
                pSettings->Release();
            }
            if (FAILED(hr)){
                pPlayer->Release();
                pMediaPlayer=nullptr;
                return false;
            }
            currentTrack.duration=0;
            return true;
        }
        bool AudioPlayerEngine::playMidi(const AudioTrack& track){
            cleanupMidi();
            UINT deviceId=MIDI_MAPPER;
            MMRESULT result=midiOutOpen((LPHMIDIOUT)&hMidiOut, deviceId, 0, 0, CALLBACK_NULL);
            if (result!=MMSYSERR_NOERROR){
                return false;
            }
            HMIDISTRM hMidiStream;
            result=midiStreamOpen(&hMidiStream, (LPHMIDIOUT)&hMidiOut, 1, 0, 0, CALLBACK_NULL);
            if (result!=MMSYSERR_NOERROR){
                midiOutClose(hMidiOut);
                hMidiOut=nullptr;
                return false;
            }
            MIDIPROPTIMEDIV prop;
            prop.cbStruct=sizeof(MIDIPROPTIMEDIV);
            prop.dwTimeDiv=96;
            result=midiStreamProperty(hMidiStream, (LPBYTE)&prop, MIDIPROP_SET|MIDIPROP_TIMEDIV);
            if (result!=MMSYSERR_NOERROR){
                midiStreamClose(hMidiStream);
                midiOutClose(hMidiOut);
                hMidiOut=nullptr;
                return false;
            }
            std::ifstream file(track.filePath, std::ios::binary|std::ios::ate);
            if (!file.is_open()){
                midiStreamClose(hMidiStream);
                midiOutClose(hMidiOut);
                hMidiOut=nullptr;
                return false;
            }
            std::streamsize size=file.tellg();
            file.seekg(0, std::ios::beg);
            std::vector<char> buffer(size);
            if (!file.read(buffer.data(), size)){
                midiStreamClose(hMidiStream);
                midiOutClose(hMidiOut);
                hMidiOut=nullptr;
                return false;
            }
            MIDIHDR midiHeader;
            ZeroMemory(&midiHeader, sizeof(MIDIHDR));
            midiHeader.lpData=(LPSTR)buffer.data();
            midiHeader.dwBufferLength=(DWORD)buffer.size();
            midiHeader.dwFlags=0;
            result=midiOutPrepareHeader(hMidiOut, &midiHeader, sizeof(MIDIHDR));
            if (result!=MMSYSERR_NOERROR){
                midiStreamClose(hMidiStream);
                midiOutClose(hMidiOut);
                hMidiOut=nullptr;
                return false;
            }
            result=midiStreamOut(hMidiStream, &midiHeader, sizeof(MIDIHDR));
            if (result!=MMSYSERR_NOERROR){
                midiOutUnprepareHeader(hMidiOut, &midiHeader, sizeof(MIDIHDR));
                midiStreamClose(hMidiStream);
                midiOutClose(hMidiOut);
                hMidiOut=nullptr;
                return false;
            }
            result=midiStreamRestart(hMidiStream);
            if (result!=MMSYSERR_NOERROR){
                midiOutUnprepareHeader(hMidiOut, &midiHeader, sizeof(MIDIHDR));
                midiStreamClose(hMidiStream);
                midiOutClose(hMidiOut);
                hMidiOut=nullptr;
                return false;
            }
            currentTrack.duration=120000;
            
            return true;
        }
        bool AudioPlayerEngine::pause(){
            std::lock_guard<std::mutex> lock(stateMutex);
            if (playbackState!=PlaybackState::PLAYING){
                return false;
            }
            std::wstring ext=currentTrack.fileExtension;
            for (wchar_t& c : ext) c=towlower(c);
            if (ext==L"wav"&&hWaveOut){
                waveOutPause(hWaveOut);
            }
            else if ((ext==L"mid"||ext==L"midi")&&hMidiOut){
                midiOutClose(hMidiOut);
                hMidiOut=nullptr;
            }
            else if (ext==L"mp3"&&pMediaPlayer){
                IWMPPlayer* pPlayer=(IWMPPlayer*)pMediaPlayer;
                IWMPControls* pControls=NULL;
                HRESULT hr=pPlayer->get_controls(&pControls);
                if (SUCCEEDED(hr)){
                    pControls->pause();
                    pControls->Release();
                }
            }
            playbackState=PlaybackState::PAUSED;
            currentTrack.playing=false;
            return true;
        }
        bool AudioPlayerEngine::resume(){
            std::lock_guard<std::mutex> lock(stateMutex);
            if (playbackState!=PlaybackState::PAUSED){
                return false;
            }
            std::wstring ext=currentTrack.fileExtension;
            for (wchar_t& c : ext) c=towlower(c);
            if (ext==L"wav"&&hWaveOut){
                waveOutRestart(hWaveOut);
            }
            else if (ext==L"mp3"&&pMediaPlayer){
                IWMPPlayer* pPlayer=(IWMPPlayer*)pMediaPlayer;
                IWMPControls* pControls=NULL;
                HRESULT hr=pPlayer->get_controls(&pControls);
                if (SUCCEEDED(hr)){
                    pControls->play();
                    pControls->Release();
                }
            }
            else{
                return play(currentTrack);
            }
            playbackState=PlaybackState::PLAYING;
            currentTrack.playing=true;
            return true;
        }
        bool AudioPlayerEngine::stop(){
            std::lock_guard<std::mutex> lock(stateMutex);
            stopPositionUpdater();
            std::wstring ext=currentTrack.fileExtension;
            for (wchar_t& c : ext) c=towlower(c);
            if (ext==L"wav"){
                cleanupWav();
            }
            else if (ext==L"mp3"){
                cleanupMp3();
            }
            else if (ext==L"mid"||ext==L"midi"){
                cleanupMidi();
            }
            playbackState=PlaybackState::STOPPED;
            currentTrack.playing=false;
            currentTrack.currentPosition=0;
            return true;
        }
        bool AudioPlayerEngine::seek(long positionMillis){
            std::lock_guard<std::mutex> lock(stateMutex);
            if (playbackState==PlaybackState::STOPPED){
                return false;
            }
            std::wstring ext=currentTrack.fileExtension;
            for (wchar_t& c : ext) c=towlower(c);
            if (ext==L"wav"&&hWaveOut){
                DWORD bytePosition=(DWORD)((double)positionMillis/1000.0 * waveFormat.nAvgBytesPerSec);
                bytePosition=std::min(bytePosition, (DWORD)waveBuffer.size());
                waveOutReset(hWaveOut);
                WAVEHDR waveHeader;
                ZeroMemory(&waveHeader, sizeof(WAVEHDR));
                waveHeader.lpData=waveBuffer.data() + bytePosition;
                waveHeader.dwBufferLength=(DWORD)waveBuffer.size() - bytePosition;
                waveHeader.dwFlags=0;
                waveOutPrepareHeader(hWaveOut, &waveHeader, sizeof(WAVEHDR));
                waveOutWrite(hWaveOut, &waveHeader, sizeof(WAVEHDR));
                currentTrack.currentPosition=positionMillis;
                return true;
            }
            return false;
        }
        bool AudioPlayerEngine::setVolume(float newVolume){
            std::lock_guard<std::mutex> lock(stateMutex);
            volume=std::max(0.0f, std::min(1.0f, newVolume));
            if (muted){
                return true;
            }
            std::wstring ext=currentTrack.fileExtension;
            for (wchar_t& c : ext) c=towlower(c);
            if (ext==L"wav"&&hWaveOut){
                DWORD dwVolume=(DWORD)(volume * 0xFFFF);
                waveOutSetVolume(hWaveOut, MAKELONG(dwVolume, dwVolume));
            }
            else if (ext==L"mp3"&&pMediaPlayer){
                IWMPPlayer* pPlayer=(IWMPPlayer*)pMediaPlayer;
                IWMPSettings* pSettings=NULL;
                HRESULT hr=pPlayer->get_settings(&pSettings);
                if (SUCCEEDED(hr)){
                    pSettings->put_volume((long)(volume * 100));
                    pSettings->Release();
                }
            }
            return true;
        }
        bool AudioPlayerEngine::setMuted(bool newMuted){
            std::lock_guard<std::mutex> lock(stateMutex);
            muted=newMuted;
            if (muted){
                std::wstring ext=currentTrack.fileExtension;
                for (wchar_t& c : ext) c=towlower(c);
                if (ext==L"wav"&&hWaveOut){
                    waveOutSetVolume(hWaveOut, 0);
                }
                else if (ext==L"mp3"&&pMediaPlayer){
                    IWMPPlayer* pPlayer=(IWMPPlayer*)pMediaPlayer;
                    IWMPSettings* pSettings=NULL;
                    HRESULT hr=pPlayer->get_settings(&pSettings);
                    if (SUCCEEDED(hr)){
                        pSettings->put_volume(0);
                        pSettings->Release();
                    }
                }
            }
            else{
                return setVolume(volume);
            }
            return true;
        }
        long AudioPlayerEngine::getCurrentPosition() const{
            return currentTrack.currentPosition;
        }
        void AudioPlayerEngine::startPositionUpdater(){
            stopPositionUpdater();
            running=true;
            positionUpdater=std::thread([this](){
                while (running&&playbackState==PlaybackState::PLAYING){
                    std::this_thread::sleep_for(std::chrono::milliseconds(100));
                    std::lock_guard<std::mutex> lock(stateMutex);
                    std::wstring ext=currentTrack.fileExtension;
                    for (wchar_t& c : ext) c=towlower(c);
                    if (ext==L"wav"&&hWaveOut){
                        MMTIME mmt;
                        mmt.wType=TIME_MS;
                        if (waveOutGetPosition(hWaveOut, &mmt, sizeof(MMTIME))==MMSYSERR_NOERROR){
                            currentTrack.currentPosition=mmt.u.ms;
                        }
                    }
                    else if (ext==L"mp3"&&pMediaPlayer){
                        currentTrack.currentPosition+=100;
                    }
                    else if ((ext==L"mid"||ext==L"midi")&&hMidiOut){
                        currentTrack.currentPosition+=100;
                    }
                }
            });
        }
        void AudioPlayerEngine::stopPositionUpdater(){
            running=false;
            if (positionUpdater.joinable()){
                positionUpdater.join();
            }
        }
        void AudioPlayerEngine::cleanupWav(){
            if (hWaveOut){
                waveOutReset(hWaveOut);
                waveOutClose(hWaveOut);
                hWaveOut=nullptr;
            }
            waveBuffer.clear();
        }
        void AudioPlayerEngine::cleanupMp3(){
            if (pMediaPlayer){
                IWMPPlayer* pPlayer=(IWMPPlayer*)pMediaPlayer;
                IWMPControls* pControls=NULL;
                HRESULT hr=pPlayer->get_controls(&pControls);
                if (SUCCEEDED(hr)){
                    pControls->stop();
                    pControls->Release();
                }
                pPlayer->Release();
                pMediaPlayer=nullptr;
            }
        }
        void AudioPlayerEngine::cleanupMidi(){
            if (hMidiOut){
                midiOutReset(hMidiOut);
                midiOutClose(hMidiOut);
                hMidiOut=nullptr;
            }
        }
        void AudioPlayerEngine::cleanup(){
            stop();
            cleanupWav();
            cleanupMp3();
            cleanupMidi();
            stopPositionUpdater();
        }
        AudioFileManager::AudioFileManager() : nextTrackNumber(1){
            wchar_t appDataPath[MAX_PATH];
            if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_APPDATA, NULL, 0, appDataPath))){
                audioDirectory=std::wstring(appDataPath)+L"\\DesktopCalendar\\Audio";
                CreateDirectoryW(audioDirectory.c_str(), NULL);
            }
        }
        AudioFileManager::~AudioFileManager(){
        }
        std::vector<AudioTrack> AudioFileManager::scanAudioFiles(){
            std::vector<AudioTrack> tracks;
            if (audioDirectory.empty()){
                return tracks;
            }
            try{
                int trackNum=1;
                for (const auto& entry : fs::directory_iterator(audioDirectory)){
                    if (entry.is_regular_file()){
                        std::wstring filePath=entry.path().wstring();
                        std::wstring fileName=entry.path().filename().wstring();
                        size_t dotPos=fileName.find_last_of(L'.');
                        if (dotPos!=std::wstring::npos){
                            std::wstring ext=fileName.substr(dotPos+1);
                            for (wchar_t& c : ext) c=towlower(c);
                            if (ext==L"mp3"||ext==L"wav"||ext==L"mid"||ext==L"midi"){
                                AudioTrack track=createTrackFromFile(filePath, trackNum++);
                                tracks.push_back(track);
                            }
                        }
                    }
                }
                nextTrackNumber=trackNum;
            }
            catch (...){

            }
            return tracks;
        }
        AudioTrack AudioFileManager::uploadAudioFile(const std::wstring& filePath){
            std::wstring destPath;
            if (copyFileToAudioDir(filePath, destPath)){
                AudioTrack track=createTrackFromFile(destPath, nextTrackNumber++);
                return track;
            }
            return AudioTrack();
        }
        bool AudioFileManager::deleteAudioTrack(const AudioTrack& track){
            if (DeleteFileW(track.filePath.c_str())){
                return true;
            }
            return false;
        }
        bool AudioFileManager::clearAllAudioFiles(){
            bool success=true;
            auto tracks=scanAudioFiles();
            for (const auto& track : tracks){
                if (!DeleteFileW(track.filePath.c_str())){
                    success=false;
                }
            }
            nextTrackNumber=1;
            return success;
        }
        std::wstring AudioFileManager::getAudioDirectory() const{
            return audioDirectory;
        }
        std::wstring AudioFileManager::getUniqueFileName(const std::wstring& originalName){
            std::wstring baseName=originalName;
            size_t dotPos=baseName.find_last_of(L'.');
            std::wstring nameWithoutExt, ext;
            if (dotPos!=std::wstring::npos){
                nameWithoutExt=baseName.substr(0, dotPos);
                ext=baseName.substr(dotPos);
            }
            else{
                nameWithoutExt=baseName;
            }
            std::wstring newName=baseName;
            int counter=1;
            while (fs::exists(audioDirectory+L"\\"+newName)){
                std::wstringstream ss;
                ss<<nameWithoutExt<<L" ("<<counter++<<L")"<<ext;
                newName=ss.str();
            }
            return newName;
        }
        bool AudioFileManager::copyFileToAudioDir(const std::wstring& sourcePath, std::wstring& destPath){
            std::wstring fileName=fs::path(sourcePath).filename().wstring();
            std::wstring uniqueName=getUniqueFileName(fileName);
            destPath=audioDirectory+L"\\"+uniqueName;
            return CopyFileW(sourcePath.c_str(), destPath.c_str(), FALSE)!=0;
        }
        AudioTrack AudioFileManager::createTrackFromFile(const std::wstring& filePath, int trackNumber){
            AudioTrack track;
            track.filePath=filePath;
            track.fileName=fs::path(filePath).filename().wstring();
            track.displayName=track.fileName;
            track.trackNumber=trackNumber;
            size_t dotPos=track.fileName.find_last_of(L'.');
            if (dotPos!=std::wstring::npos){
                track.fileExtension=track.fileName.substr(dotPos+1);
            }
            track.duration=getAudioDuration(filePath);
            track.currentPosition=0;
            track.playing=false;
            return track;
        }
        long AudioFileManager::getAudioDuration(const std::wstring& filePath){
            std::wstring ext=fs::path(filePath).extension().wstring();
            for (wchar_t& c : ext) c=towlower(c);
            if (ext==L".wav"){
                HMMIO hmmio=mmioOpen(const_cast<LPWSTR>(filePath.c_str()), NULL, MMIO_READ);
                if (!hmmio) return 0;
                MMCKINFO ckRiff;
                ckRiff.fccType=mmioFOURCC('W', 'A', 'V', 'E');
                if (mmioDescend(hmmio, &ckRiff, NULL, MMIO_FINDRIFF)){
                    mmioClose(hmmio, 0);
                    return 0;
                }
                MMCKINFO ckInfo;
                ckInfo.ckid=mmioFOURCC('f', 'm', 't', ' ');
                if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK)){
                    mmioClose(hmmio, 0);
                    return 0;
                }
                PCMWAVEFORMAT pcmWaveFormat;
                if (mmioRead(hmmio, (HPSTR)&pcmWaveFormat, sizeof(pcmWaveFormat))!=sizeof(pcmWaveFormat)){
                    mmioClose(hmmio, 0);
                    return 0;
                }
                mmioAscend(hmmio, &ckInfo, 0);
                ckInfo.ckid=mmioFOURCC('d', 'a', 't', 'a');
                if (mmioDescend(hmmio, &ckInfo, &ckRiff, MMIO_FINDCHUNK)){
                    mmioClose(hmmio, 0);
                    return 0;
                }
                long duration=(long)((double)ckInfo.cksize/pcmWaveFormat.wf.nAvgBytesPerSec * 1000.0);
                mmioClose(hmmio, 0);
                return duration;
            }
            return 0;
        }
    }
}