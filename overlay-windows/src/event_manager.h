#pragma once
#include <vector>
#include <string>
#include <memory>
#include <chrono>
#include <mutex>
#include <windows.h>
#include "shared/calendar_shared.h"
namespace CalendarOverlay{
    class EventManager{
    public:
        EventManager();
        ~EventManager();
        bool initialize();
        void update();
        std::vector<CalendarEvent> getTodayEvents() const;
        std::vector<CalendarEvent> getUpcomingEvents(int hours = 24) const;
        bool hasNewData() const;
        int getEventCount() const;
        bool loadEventsFromFile(const std::string& filepath);
    private:
        bool parseEventsJson(const std::string& json);
        void checkFileUpdates();
        std::vector<CalendarEvent> events;
        mutable std::mutex eventsMutex;
        std::string dataFilePath;
        std::chrono::system_clock::time_point lastUpdate;
        std::chrono::system_clock::time_point lastFileModification;
        bool initialized;
        HANDLE fileWatcherThread;
        static DWORD WINAPI fileWatcherProc(LPVOID param);
        void watchForChanges();
        HANDLE sharedMemory;
        void* sharedMemoryPtr;
        size_t sharedMemorySize;
        bool setupSharedMemory();
    };
}