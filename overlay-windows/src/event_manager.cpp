#include "event_manager.h"
#include "config.h"
#include <fstream>
#include <sstream>
#include <iostream>
#include <chrono>
#include <algorithm>
#include <sys/stat.h>
#include <windows.h>
#include <json/json.hpp>

using json=nlohmann::json;

namespace CalendarOverlay{
    EventManager::EventManager() : initialized(false), fileWatcherThread(NULL),
        sharedMemory(NULL), sharedMemoryPtr(nullptr), stopWatcher(false){
        lastUpdate=std::chrono::system_clock::now();
        lastFileModification=std::chrono::system_clock::time_point::min();
        Config& config=Config::getInstance();
        dataFilePath=config.getDataPath()+"calendar_events.json";
        setupSharedMemory();
    }
    EventManager::~EventManager(){
        stopWatcher=true;
        if (fileWatcherThread){
            WaitForSingleObject(fileWatcherThread, 5000);
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
        try{
            json j;
            file>>j;
            file.close();
            return parseEventsJson(j);
        }
        catch (const std::exception& e){
            std::cerr<<"Failed to parse JSON: "<<e.what()<<std::endl;
            return false;
        }
    }
    bool EventManager::parseEventsJson(const json& j){
        std::lock_guard<std::mutex> lock(eventsMutex);
        events.clear();
        if (!j.contains("events")||!j["events"].is_array()){
            return false;
        }
        int eventCount=0;
        for (const auto& eventJson : j["events"]){
            CalendarEvent event;
            if (eventJson.contains("title")&&eventJson["title"].is_string()){
                std::string title=eventJson["title"];
                strncpy_s(event.title, sizeof(event.title), title.c_str(), _TRUNCATE);
            }
            if (eventJson.contains("description")&&eventJson["description"].is_string()){
                std::string desc=eventJson["description"];
                strncpy_s(event.description, sizeof(event.description), desc.c_str(), _TRUNCATE);
            }
            if (eventJson.contains("startTime")&&eventJson["startTime"].is_number()){
                event.startTime=eventJson["startTime"];
            }
            if (eventJson.contains("endTime")&&eventJson["endTime"].is_number()){
                event.endTime=eventJson["endTime"];
            }
            if (eventJson.contains("colorR")&&eventJson["colorR"].is_number()){
                event.colorR=eventJson["colorR"];
            }
            if (eventJson.contains("colorG")&&eventJson["colorG"].is_number()){
                event.colorG=eventJson["colorG"];
            }
            if (eventJson.contains("colorB")&&eventJson["colorB"].is_number()){
                event.colorB=eventJson["colorB"];
            }
            if (eventJson.contains("priority")&&eventJson["priority"].is_number()){
                event.priority=eventJson["priority"];
            }
            if (eventJson.contains("allDay")&&eventJson["allDay"].is_boolean()){
                event.allDay=eventJson["allDay"];
            }
            events.push_back(event);
            eventCount++;
        }
        std::cout<<"Loaded "<<eventCount<<" events"<<std::endl;
        return eventCount>0;
    }
    DWORD WINAPI EventManager::fileWatcherProc(LPVOID param){
        EventManager* manager=static_cast<EventManager*>(param);
        if (!manager) return 0;
        while (!manager->stopWatcher){
            Sleep(5000);
            manager->checkFileUpdates();
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