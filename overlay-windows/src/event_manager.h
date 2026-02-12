#pragma once

#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

#include <vector>
#include <string>
#include <memory>
#include <chrono>
#include <mutex>
#include <windows.h>
#undef __in
#undef __out
#include <shared/calendar_shared.h>
#include <json/json.hpp>
namespace CalendarOverlay
{
    class EventManager
    {
    public:
        EventManager();
        ~EventManager();
        bool initialize();
        void update();
        std::vector<CalendarEvent> getTodayEvents() const;
        std::vector<CalendarEvent> getUpcomingEvents(int hours = 24) const;
        bool hasNewData() const;
        int getEventCount() const;
        bool loadEventsFromFile(const std::string &filepath);

    private:
        bool parseEventsJson(const nlohmann::json &j);
        void checkFileUpdates();
        std::vector<CalendarEvent> events;
        mutable std::mutex eventsMutex;
        std::string dataFilePath;
        std::chrono::system_clock::time_point lastUpdate;
        std::chrono::system_clock::time_point lastFileModification;
        bool initialized;
        HANDLE fileWatcherThread;
        static DWORD WINAPI fileWatcherProc(LPVOID param);
        HANDLE sharedMemory;
        void *sharedMemoryPtr;
        size_t sharedMemorySize;
        bool setupSharedMemory();
        bool stopWatcher;
    };
}