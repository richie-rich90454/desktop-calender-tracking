#include "event_manager.h"
#include "config.h"
#include <fstream>
#include <sstream>
#include <iostream>
#include <chrono>
#include <algorithm>
#include <sys/stat.h>
#define NOMINMAX
#include <windows.h>

namespace CalendarOverlay{
    EventManager::EventManager() : initialized(false), fileWatcherThread(NULL),
        sharedMemory(NULL), sharedMemoryPtr(nullptr){
        lastUpdate=std::chrono::system_clock::now();
        lastFileModification=std::chrono::system_clock::time_point::min();
        Config& config=Config::getInstance();
        dataFilePath=config.getDataPath()+"calendar_events.json";
        setupSharedMemory();
    }
    EventManager::~EventManager(){
        if (fileWatcherThread){
            TerminateThread(fileWatcherThread, 0);
            CloseHandle(fileWatcherThread);
        }
        if (sharedMemoryPtr){
            UnmapViewOfFile(sharedMemoryPtr);
        }
        if (sharedMemory){
            CloseHandle(sharedMemory);
        }
    }
    bool EventManager::initialize(){
        if (!loadEventsFromFile(dataFilePath)){
            std::cout<<"Could not load events from file. Will try Java IPC."<<std::endl;
        }
        fileWatcherThread=CreateThread(NULL, 0, fileWatcherProc, this, 0, NULL);
        if (!fileWatcherThread){
            std::cerr<<"Failed to create file watcher thread"<<std::endl;
            return false;
        }
        initialized=true;
        return true;
    }
    void EventManager::update(){
        checkFileUpdates();
        if (hasNewData()){
            std::lock_guard<std::mutex> lock(eventsMutex);
            lastUpdate=std::chrono::system_clock::now();
        }
    }
    bool EventManager::loadEventsFromFile(const std::string& filepath){
        std::ifstream file(filepath);
        if (!file.is_open()){
            return false;
        }
        std::string jsonContent((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());
        file.close();
        return parseEventsJson(jsonContent);
    }
    bool EventManager::parseEventsJson(const std::string& json){
        std::lock_guard<std::mutex> lock(eventsMutex);
        events.clear();
        size_t eventsPos=json.find("\"events\"");
        if (eventsPos==std::string::npos){
            return false;
        }
        size_t arrayStart=json.find('[', eventsPos);
        if (arrayStart==std::string::npos){
            return false;
        }
        size_t arrayEnd=json.find(']', arrayStart);
        if (arrayEnd==std::string::npos){
            return false;
        }
        std::string eventsArray=json.substr(arrayStart+1, arrayEnd-arrayStart-1);
        size_t pos=0;
        int eventCount=0;
        while (pos<eventsArray.length()){
            size_t objStart=eventsArray.find('{', pos);
            if (objStart==std::string::npos){
                break;
            }
            size_t objEnd=eventsArray.find('}', objStart);
            if (objEnd==std::string::npos){
                break;
            }
            std::string eventObj=eventsArray.substr(objStart+1, objEnd-objStart-1);
            CalendarEvent event;
            size_t titlePos=eventObj.find("\"title\"");
            if (titlePos!=std::string::npos){
                size_t colonPos=eventObj.find(':', titlePos);
                size_t quote1=eventObj.find('"', colonPos);
                if (quote1!=std::string::npos){
                    size_t quote2=eventObj.find('"', quote1+1);
                    if (quote2!=std::string::npos){
                        std::string title=eventObj.substr(quote1+1, quote2-quote1-1);
                        strncpy_s(event.title, sizeof(event.title), title.c_str(), _TRUNCATE);
                    }
                }
            }
            size_t timePos=eventObj.find("\"startTime\"");
            if (timePos!=std::string::npos){
                size_t colonPos=eventObj.find(':', timePos);
                size_t valueStart=eventObj.find_first_of("0123456789", colonPos);
                if (valueStart!=std::string::npos){
                    size_t valueEnd=eventObj.find_first_not_of("0123456789", valueStart);
                    std::string timeStr=eventObj.substr(valueStart, valueEnd-valueStart);
                    event.startTime=std::stoll(timeStr);
                }
            }
            events.push_back(event);
            eventCount++;
            pos=objEnd+1;
        }
        std::cout<<"Loaded "<<eventCount<<" events"<<std::endl;
        return eventCount>0;
    }
    DWORD WINAPI EventManager::fileWatcherProc(LPVOID param){
        EventManager* manager=static_cast<EventManager*>(param);
        if (!manager) return 0;
        std::string pathToWatch=manager->dataFilePath;
        while (true){
            Sleep(5000);
            struct _stat fileStat;
            if (_stat(pathToWatch.c_str(), &fileStat)==0){
                auto modTime=std::chrono::system_clock::from_time_t(fileStat.st_mtime);
                if (modTime>manager->lastFileModification){
                    manager->loadEventsFromFile(pathToWatch);
                    manager->lastFileModification=modTime;
                }
            }
            if (WaitForSingleObject(GetCurrentThread(), 0)==WAIT_OBJECT_0)
                break;
        }
        return 0;
    }
    bool EventManager::setupSharedMemory(){
        sharedMemory=CreateFileMappingA(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, 65536, "Local\\CalendarOverlayShared");
        if (!sharedMemory){
            std::cerr<<"Failed to create shared memory: "<<GetLastError()<<std::endl;
            return false;
        }
        sharedMemoryPtr=MapViewOfFile(sharedMemory, FILE_MAP_ALL_ACCESS, 0, 0, 0);
        if (!sharedMemoryPtr){
            CloseHandle(sharedMemory);
            sharedMemory=NULL;
            std::cerr<<"Failed to map shared memory: "<<GetLastError()<<std::endl;
            return false;
        }
        sharedMemorySize=65536;
        return true;
    }
    void EventManager::checkFileUpdates(){
        struct _stat fileStat;
        if (_stat(dataFilePath.c_str(), &fileStat)==0){
            auto modTime=std::chrono::system_clock::from_time_t(fileStat.st_mtime);
            if (modTime>lastFileModification){
                loadEventsFromFile(dataFilePath);
                lastFileModification=modTime;
            }
        }
    }
    bool EventManager::hasNewData() const{
        struct _stat fileStat;
        if (_stat(dataFilePath.c_str(), &fileStat)!=0){
            return false;
        }
        auto modTime=std::chrono::system_clock::from_time_t(fileStat.st_mtime);
        return modTime>lastFileModification;
    }
    std::vector<CalendarEvent> EventManager::getTodayEvents() const{
        std::vector<CalendarEvent> todayEvents;
        auto now=std::chrono::system_clock::now();
        auto today=std::chrono::system_clock::to_time_t(now);
        std::tm todayTm;
        localtime_s(&todayTm, &today);
        std::lock_guard<std::mutex> lock(eventsMutex);
        for (const auto& event : events){
            auto eventTime=std::chrono::system_clock::from_time_t(event.startTime/1000);
            auto eventTm=std::chrono::system_clock::to_time_t(eventTime);
            std::tm eventTimeTm;
            localtime_s(&eventTimeTm, &eventTm);
            if (eventTimeTm.tm_year==todayTm.tm_year&&eventTimeTm.tm_mon==todayTm.tm_mon&&eventTimeTm.tm_mday==todayTm.tm_mday){
                todayEvents.push_back(event);
            }
        }
        return todayEvents;
    }
    std::vector<CalendarEvent> EventManager::getUpcomingEvents(int hours) const{
        std::vector<CalendarEvent> upcoming;
        auto now=std::chrono::system_clock::now();
        auto nowMs=std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
        auto cutoff=nowMs+(hours*3600*1000);
        std::lock_guard<std::mutex> lock(eventsMutex);
        for (const auto& event : events){
            if (event.startTime>=nowMs&&event.startTime<=cutoff){
                upcoming.push_back(event);
            }
        }
        std::sort(upcoming.begin(), upcoming.end(), [](const CalendarEvent& a, const CalendarEvent& b){
            return a.startTime<b.startTime;
        });
        return upcoming;
    }
}