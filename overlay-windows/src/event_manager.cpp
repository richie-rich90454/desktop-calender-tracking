// ==================== event_manager.cpp ====================
// Implementation of EventManager. Loads events from a JSON file
// (written by the Java configuration app) and monitors the file
// for changes. Also sets up shared memory for potential IPC.

#include "event_manager.h"
#include "config.h"
#include <fstream>
#include <sstream>
#include <iostream>
#include <chrono>
#include <algorithm>
#include <sys/stat.h>       // For _stat, file modification times
#include <windows.h>
#include <shlobj.h>          // For SHGetFolderPathA
#include <json/json.hpp>

using json = nlohmann::json;

namespace CalendarOverlay
{
    // ------------------------------------------------------------------------
    // Constructor: determines the events file path and sets up shared memory.
    // ------------------------------------------------------------------------
    EventManager::EventManager() : initialized(false), fileWatcherThread(NULL),
                                   sharedMemory(NULL), sharedMemoryPtr(nullptr), stopWatcher(false)
    {
        lastUpdate = std::chrono::system_clock::now();
        lastFileModification = std::chrono::system_clock::time_point::min();

        // Determine the user's home directory (profile) to locate the events file.
        char userProfile[MAX_PATH];
        if (SUCCEEDED(SHGetFolderPathA(NULL, CSIDL_PROFILE, NULL, 0, userProfile)))
        {
            dataFilePath = std::string(userProfile) + "\\.calendarapp\\calendar_events.json";
        }
        else
        {
            // Fallback: use the config data path (AppData\DesktopCalendar)
            Config &config = Config::getInstance();
            dataFilePath = config.getDataPath() + "calendar_events.json";
        }

        setupSharedMemory();   // Create shared memory (unused currently)
    }

    // ------------------------------------------------------------------------
    // Destructor: stops the watcher thread and cleans up shared memory.
    // ------------------------------------------------------------------------
    EventManager::~EventManager()
    {
        stopWatcher = true;
        if (fileWatcherThread)
        {
            WaitForSingleObject(fileWatcherThread, 5000);   // Wait up to 5 seconds
            CloseHandle(fileWatcherThread);
        }
        if (sharedMemoryPtr)
        {
            UnmapViewOfFile(sharedMemoryPtr);
        }
        if (sharedMemory)
        {
            CloseHandle(sharedMemory);
        }
    }

    // ------------------------------------------------------------------------
    // initialize: attempts to load events from file and starts the watcher thread.
    // ------------------------------------------------------------------------
    bool EventManager::initialize()
    {
        if (!loadEventsFromFile(dataFilePath))
        {
            std::cout << "Could not load events from file. Will try Java IPC." << std::endl;
        }

        // Create a background thread that periodically checks the file for changes.
        fileWatcherThread = CreateThread(NULL, 0, fileWatcherProc, this, 0, NULL);
        if (!fileWatcherThread)
        {
            std::cerr << "Failed to create file watcher thread" << std::endl;
            return false;
        }
        initialized = true;
        return true;
    }

    // ------------------------------------------------------------------------
    // update: called periodically (e.g., from main timer) to check for file changes.
    // ------------------------------------------------------------------------
    void EventManager::update()
    {
        checkFileUpdates();
        if (hasNewData())
        {
            std::lock_guard<std::mutex> lock(eventsMutex);
            lastUpdate = std::chrono::system_clock::now();
        }
    }

    // ------------------------------------------------------------------------
    // loadEventsFromFile: reads the JSON file and parses it.
    // Returns true on success, false otherwise.
    // ------------------------------------------------------------------------
    bool EventManager::loadEventsFromFile(const std::string &filepath)
    {
        std::ifstream file(filepath);
        if (!file.is_open())
        {
            return false;
        }

        try
        {
            json j;
            file >> j;
            file.close();
            return parseEventsJson(j);
        }
        catch (const std::exception &e)
        {
            std::cerr << "Failed to parse JSON: " << e.what() << std::endl;
            return false;
        }
    }

    // ------------------------------------------------------------------------
    // parseEventsJson: extracts events from a JSON object.
    // Expected format: { "events": [ { "title": "...", "startDateTime": "...", ... } ] }
    // The startDateTime and endDateTime are expected in ISO-like format.
    // ------------------------------------------------------------------------
    bool EventManager::parseEventsJson(const json &j)
    {
        std::lock_guard<std::mutex> lock(eventsMutex);
        events.clear();

        if (!j.contains("events") || !j["events"].is_array())
        {
            return false;
        }

        int eventCount = 0;
        for (const auto &eventJson : j["events"])
        {
            CalendarEvent event;

            // Parse title (convert from std::string to char array)
            if (eventJson.contains("title") && eventJson["title"].is_string())
            {
                std::string title = eventJson["title"];
                strncpy_s(event.title, sizeof(event.title), title.c_str(), _TRUNCATE);
                std::cout << "Parsing event: " << title << std::endl;
            }

            // Parse start date/time. The JSON may contain either ISO format with 'T' or a space.
            if (eventJson.contains("startDateTime") && eventJson["startDateTime"].is_string())
            {
                std::string startDateTimeStr = eventJson["startDateTime"];
                std::cout << "Parsing startDateTime: " << startDateTimeStr << std::endl;
                try
                {
                    // Attempt ISO format: "2025-01-28T10:30:00"
                    std::tm tm = {};
                    std::istringstream ss(startDateTimeStr);
                    ss >> std::get_time(&tm, "%Y-%m-%dT%H:%M:%S");
                    if (!ss.fail())
                    {
                        auto timePoint = std::chrono::system_clock::from_time_t(std::mktime(&tm));
                        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(timePoint.time_since_epoch());
                        event.startTime = ms.count();
                        std::cout << "Parsed start time: " << event.startTime << " ms" << std::endl;
                    }
                    else
                    {
                        std::cout << "Failed to parse startDateTime with ISO format" << std::endl;
                    }
                }
                catch (const std::exception &e)
                {
                    std::cout << "Exception parsing startDateTime: " << e.what() << std::endl;
                    // Try alternative format with space instead of 'T'
                    try
                    {
                        std::tm tm = {};
                        std::istringstream ss(startDateTimeStr);
                        ss >> std::get_time(&tm, "%Y-%m-%d %H:%M:%S");
                        if (!ss.fail())
                        {
                            auto timePoint = std::chrono::system_clock::from_time_t(std::mktime(&tm));
                            auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(timePoint.time_since_epoch());
                            event.startTime = ms.count();
                            std::cout << "Parsed start time with alt format: " << event.startTime << " ms" << std::endl;
                        }
                        else
                        {
                            std::cout << "Failed to parse startDateTime with alt format" << std::endl;
                            event.startTime = 0;
                        }
                    }
                    catch (const std::exception &e2)
                    {
                        std::cout << "Exception parsing startDateTime alt format: " << e2.what() << std::endl;
                        event.startTime = 0;
                    }
                }
            }

            // Parse end date/time similarly.
            if (eventJson.contains("endDateTime") && eventJson["endDateTime"].is_string())
            {
                std::string endDateTimeStr = eventJson["endDateTime"];
                std::cout << "Parsing endDateTime: " << endDateTimeStr << std::endl;
                try
                {
                    std::tm tm = {};
                    std::istringstream ss(endDateTimeStr);
                    ss >> std::get_time(&tm, "%Y-%m-%dT%H:%M:%S");
                    if (!ss.fail())
                    {
                        auto timePoint = std::chrono::system_clock::from_time_t(std::mktime(&tm));
                        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(timePoint.time_since_epoch());
                        event.endTime = ms.count();
                        std::cout << "Parsed end time: " << event.endTime << " ms" << std::endl;
                    }
                    else
                    {
                        std::cout << "Failed to parse endDateTime with ISO format" << std::endl;
                    }
                }
                catch (const std::exception &e)
                {
                    try
                    {
                        std::tm tm = {};
                        std::istringstream ss(endDateTimeStr);
                        ss >> std::get_time(&tm, "%Y-%m-%d %H:%M:%S");
                        if (!ss.fail())
                        {
                            auto timePoint = std::chrono::system_clock::from_time_t(std::mktime(&tm));
                            auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(timePoint.time_since_epoch());
                            event.endTime = ms.count();
                            std::cout << "Parsed end time with alt format: " << event.endTime << " ms" << std::endl;
                        }
                        else
                        {
                            std::cout << "Failed to parse endDateTime with alt format" << std::endl;
                            event.endTime = 0;
                        }
                    }
                    catch (const std::exception &e2)
                    {
                        std::cout << "Exception parsing endDateTime alt format: " << e2.what() << std::endl;
                        event.endTime = 0;
                    }
                }
            }

            // Set default color (blue). The Java side may later supply colors.
            event.colorR = 66;
            event.colorG = 133;
            event.colorB = 244;
            event.priority = 5;   // Default priority
            event.allDay = false;  // Not used currently

            events.push_back(event);
            eventCount++;
        }
        std::cout << "Loaded " << eventCount << " events from Java JSON format" << std::endl;
        return eventCount > 0;
    }

    // ------------------------------------------------------------------------
    // fileWatcherProc: static thread procedure that calls checkFileUpdates() periodically.
    // ------------------------------------------------------------------------
    DWORD WINAPI EventManager::fileWatcherProc(LPVOID param)
    {
        EventManager *manager = static_cast<EventManager *>(param);
        if (!manager)
            return 0;

        while (!manager->stopWatcher)
        {
            Sleep(5000);                         // Check every 5 seconds
            manager->checkFileUpdates();
        }
        return 0;
    }

    // ------------------------------------------------------------------------
    // setupSharedMemory: creates a named shared memory block for potential IPC.
    // Currently not used but kept for future Java integration.
    // ------------------------------------------------------------------------
    bool EventManager::setupSharedMemory()
    {
        sharedMemory = CreateFileMappingA(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, 65536, "Local\\CalendarOverlayShared");
        if (!sharedMemory)
        {
            std::cerr << "Failed to create shared memory: " << GetLastError() << std::endl;
            return false;
        }

        sharedMemoryPtr = MapViewOfFile(sharedMemory, FILE_MAP_ALL_ACCESS, 0, 0, 0);
        if (!sharedMemoryPtr)
        {
            CloseHandle(sharedMemory);
            sharedMemory = NULL;
            std::cerr << "Failed to map shared memory: " << GetLastError() << std::endl;
            return false;
        }
        sharedMemorySize = 65536;
        return true;
    }

    // ------------------------------------------------------------------------
    // checkFileUpdates: compares the file's last modification time with the
    // stored value; if newer, reloads the file.
    // ------------------------------------------------------------------------
    void EventManager::checkFileUpdates()
    {
        struct _stat fileStat;
        if (_stat(dataFilePath.c_str(), &fileStat) == 0)
        {
            auto modTime = std::chrono::system_clock::from_time_t(fileStat.st_mtime);
            if (modTime > lastFileModification)
            {
                loadEventsFromFile(dataFilePath);
                lastFileModification = modTime;
            }
        }
    }

    // ------------------------------------------------------------------------
    // hasNewData: returns true if the file has been modified since last load.
    // ------------------------------------------------------------------------
    bool EventManager::hasNewData() const
    {
        struct _stat fileStat;
        if (_stat(dataFilePath.c_str(), &fileStat) != 0)
        {
            return false;
        }
        auto modTime = std::chrono::system_clock::from_time_t(fileStat.st_mtime);
        return modTime > lastFileModification;
    }

    // ------------------------------------------------------------------------
    // getTodayEvents: returns all events that occur on the current calendar day.
    // ------------------------------------------------------------------------
    std::vector<CalendarEvent> EventManager::getTodayEvents() const
    {
        std::vector<CalendarEvent> todayEvents;
        auto now = std::chrono::system_clock::now();
        auto today = std::chrono::system_clock::to_time_t(now);
        std::tm todayTm;
        localtime_s(&todayTm, &today);

        std::lock_guard<std::mutex> lock(eventsMutex);
        for (const auto &event : events)
        {
            auto eventTime = std::chrono::system_clock::from_time_t(event.startTime / 1000);
            auto eventTm = std::chrono::system_clock::to_time_t(eventTime);
            std::tm eventTimeTm;
            localtime_s(&eventTimeTm, &eventTm);

            // Compare year, month, day
            if (eventTimeTm.tm_year == todayTm.tm_year &&
                eventTimeTm.tm_mon == todayTm.tm_mon &&
                eventTimeTm.tm_mday == todayTm.tm_mday)
            {
                todayEvents.push_back(event);
            }
        }
        return todayEvents;
    }

    // ------------------------------------------------------------------------
    // getUpcomingEvents: returns events that start within the next 'hours' hours.
    // ------------------------------------------------------------------------
    std::vector<CalendarEvent> EventManager::getUpcomingEvents(int hours) const
    {
        std::vector<CalendarEvent> upcoming;
        auto now = std::chrono::system_clock::now();
        auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
        auto cutoff = nowMs + (hours * 3600 * 1000);

        std::lock_guard<std::mutex> lock(eventsMutex);
        for (const auto &event : events)
        {
            if (event.startTime >= nowMs && event.startTime <= cutoff)
            {
                upcoming.push_back(event);
            }
        }

        // Sort by start time (earliest first)
        std::sort(upcoming.begin(), upcoming.end(), [](const CalendarEvent &a, const CalendarEvent &b)
                  { return a.startTime < b.startTime; });
        return upcoming;
    }
}