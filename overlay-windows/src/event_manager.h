// ==================== event_manager.h ====================
// EventManager – handles loading calendar events from a JSON file
// and provides access to today's events and upcoming events.
// It also sets up shared memory (for future IPC) and a file watcher thread
// to detect changes in the events file.

#pragma once

// Ensure Windows headers don't pull in unnecessary macros.
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
#include <shared/calendar_shared.h>   // Contains CalendarEvent definition
#include <json/json.hpp>               // JSON library for parsing

namespace CalendarOverlay
{
    // Manages calendar events: loads from file, monitors for changes,
    // and provides filtered event lists for the renderer.
    class EventManager
    {
    public:
        EventManager();
        ~EventManager();

        // Initialises the file watcher thread. Returns true on success.
        bool initialize();

        // Called periodically to check for file updates.
        void update();

        // Returns all events that occur today (based on system date).
        std::vector<CalendarEvent> getTodayEvents() const;

        // Returns events starting within the next 'hours' hours.
        std::vector<CalendarEvent> getUpcomingEvents(int hours = 24) const;

        // Checks whether the events file has been modified since last load.
        bool hasNewData() const;

        // Not used, kept for potential future use.
        int getEventCount() const;

        // Loads events from the given file path. Returns true on success.
        bool loadEventsFromFile(const std::string &filepath);

    private:
        // Parses a JSON object (from nlohmann::json) into the events vector.
        bool parseEventsJson(const nlohmann::json &j);

        // Checks file modification time and reloads if changed.
        void checkFileUpdates();

        std::vector<CalendarEvent> events;               // All loaded events
        mutable std::mutex eventsMutex;                   // Protects events vector

        std::string dataFilePath;                         // Path to the JSON file
        std::chrono::system_clock::time_point lastUpdate; // Last time we updated (unused)
        std::chrono::system_clock::time_point lastFileModification; // Last mod time of file

        bool initialized;                                  // True after init succeeds

        HANDLE fileWatcherThread;                          // Background thread for file watching
        static DWORD WINAPI fileWatcherProc(LPVOID param); // Thread procedure

        // Shared memory (currently unused, but set up for future Java IPC)
        HANDLE sharedMemory;
        void *sharedMemoryPtr;
        size_t sharedMemorySize;
        bool setupSharedMemory();

        bool stopWatcher;                                   // Signal to stop the watcher thread
    };
}