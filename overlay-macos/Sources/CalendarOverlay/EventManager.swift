/*
 * Event loading and management from JSON files for macOS Calendar Overlay.
 *
 * Responsibilities:
 * - Load calendar events from ~/.calendarapp/calendar_events.json
 * - Monitor file changes for automatic updates
 * - Filter events for today and upcoming dates
 * - Provide thread-safe access to event data
 *
 * Swift data types used:
 * - [CalendarEvent] for event collections
 * - FileManager for file system operations
 * - DispatchQueue for asynchronous operations
 * - DispatchSource for file monitoring
 * - Date/Calendar for date calculations
 *
 * Swift technologies involved:
 * - JSONSerialization/Codable for JSON parsing
 * - Grand Central Dispatch for concurrency
 * - Error handling with try/catch
 * - Property wrappers for thread safety
 *
 * Design intent:
 * This class provides a robust event management system that automatically
 * reloads events when the source file changes, similar to Windows version.
 */

import Foundation

class EventManager {
    
    // MARK: - Properties
    
    private var events: [CalendarEvent] = []
    private let fileManager = FileManager.default
    private let fileMonitorQueue = DispatchQueue(label: "com.calendaroverlay.filemonitor")
    private var fileMonitorSource: DispatchSourceFileSystemObject?
    private var fileDescriptor: Int32 = -1
    
    // File path
    private var dataFilePath: String
    
    // Synchronization
    private let eventsLock = NSLock()
    
    // State
    private var lastUpdate: Date = Date()
    private var lastFileModification: Date?
    private var isInitialized = false
    
    // MARK: - Initialization
    
    init() {
        // Get ~/.calendarapp/calendar_events.json path
        let homeDir = fileManager.homeDirectoryForCurrentUser
        let calendarDir = homeDir.appendingPathComponent(".calendarapp")
        let jsonFile = calendarDir.appendingPathComponent("calendar_events.json")
        
        self.dataFilePath = jsonFile.path
        
        // Create directory if it doesn't exist
        createDirectoryIfNeeded(at: calendarDir)
    }
    
    deinit {
        stopFileMonitoring()
    }
    
    // MARK: - Public API
    
    func initialize() -> Bool {
        // Load initial events
        let loaded = loadEventsFromFile()
        
        // Start file monitoring
        startFileMonitoring()
        
        isInitialized = true
        return loaded
    }
    
    func update() {
        // Check for file changes and reload if needed
        checkFileUpdates()
    }
    
    func getTodayEvents() -> [CalendarEvent] {
        eventsLock.lock()
        defer { eventsLock.unlock() }
        
        return filterEventsForToday(events)
    }
    
    func getUpcomingEvents(hours: Int = 24) -> [CalendarEvent] {
        eventsLock.lock()
        defer { eventsLock.unlock() }
        
        return filterEventsUpcoming(events, hours: hours)
    }
    
    func hasNewData() -> Bool {
        // Check if file has been modified since last load
        guard let lastMod = lastFileModification else { return false }
        
        if let currentMod = getFileModificationDate() {
            return currentMod > lastMod
        }
        
        return false
    }
    
    func getEventCount() -> Int {
        eventsLock.lock()
        defer { eventsLock.unlock() }
        
        return events.count
    }
    
    func getAllEvents() -> [CalendarEvent] {
        eventsLock.lock()
        defer { eventsLock.unlock() }
        
        return events
    }
    
    // MARK: - File Operations
    
    @discardableResult
    func loadEventsFromFile() -> Bool {
        eventsLock.lock()
        defer { eventsLock.unlock() }
        
        do {
            // Check if file exists
            guard fileManager.fileExists(atPath: dataFilePath) else {
                print("Event file does not exist at \(dataFilePath)")
                events = []
                lastUpdate = Date()
                lastFileModification = nil
                return false
            }
            
            // Read file data
            let data = try Data(contentsOf: URL(fileURLWithPath: dataFilePath))
            
            // Parse JSON
            let parsedEvents = try parseEventsJSON(data)
            
            // Update events
            events = parsedEvents
            lastUpdate = Date()
            lastFileModification = getFileModificationDate()
            
            print("Loaded \(events.count) events from \(dataFilePath)")
            return true
            
        } catch {
            print("Failed to load events from \(dataFilePath): \(error)")
            return false
        }
    }
    
    private func parseEventsJSON(_ data: Data) throws -> [CalendarEvent] {
        // Parse JSON using Codable
        let decoder = JSONDecoder()
        
        do {
            // Try to parse as CalendarEventsResponse first
            let response = try decoder.decode(CalendarEventsResponse.self, from: data)
            return response.events
        } catch {
            // If that fails, try to parse as a simple array
            let events = try decoder.decode([CalendarEvent].self, from: data)
            return events
        }
    }
    
    private func getFileModificationDate() -> Date? {
        do {
            let attributes = try fileManager.attributesOfItem(atPath: dataFilePath)
            return attributes[.modificationDate] as? Date
        } catch {
            return nil
        }
    }
    
    private func checkFileUpdates() {
        if hasNewData() {
            _ = loadEventsFromFile()
        }
    }
    
    // MARK: - File Monitoring
    
    private func startFileMonitoring() {
        // macOS equivalent of Windows file watcher thread
        // Use DispatchSource to monitor file changes
        
        let fileURL = URL(fileURLWithPath: dataFilePath)
        
        // Check if file exists
        guard fileManager.fileExists(atPath: dataFilePath) else {
            print("Cannot monitor non-existent file: \(dataFilePath)")
            return
        }
        
        // Get file descriptor
        fileDescriptor = open(fileURL.path, O_EVTONLY)
        guard fileDescriptor >= 0 else {
            print("Failed to open file for monitoring: \(dataFilePath)")
            return
        }
        
        // Create dispatch source for file system events
        fileMonitorSource = DispatchSource.makeFileSystemObjectSource(
            fileDescriptor: fileDescriptor,
            eventMask: .write,
            queue: fileMonitorQueue
        )
        
        fileMonitorSource?.setEventHandler { [weak self] in
            // File was modified
            print("Event file modified, reloading...")
            DispatchQueue.main.async {
                self?.loadEventsFromFile()
            }
        }
        
        fileMonitorSource?.setCancelHandler { [weak self] in
            guard let self = self else { return }
            close(self.fileDescriptor)
            self.fileDescriptor = -1
        }
        
        fileMonitorSource?.resume()
        print("Started monitoring file: \(dataFilePath)")
    }
    
    private func stopFileMonitoring() {
        fileMonitorSource?.cancel()
        fileMonitorSource = nil
    }
    
    // MARK: - Directory Management
    
    private func createDirectoryIfNeeded(at url: URL) {
        if !fileManager.fileExists(atPath: url.path) {
            do {
                try fileManager.createDirectory(at: url, withIntermediateDirectories: true)
                print("Created directory: \(url.path)")
            } catch {
                print("Failed to create directory \(url.path): \(error)")
            }
        }
    }
    
    // MARK: - Event Filtering
    
    private func filterEventsForToday(_ events: [CalendarEvent]) -> [CalendarEvent] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: today)!
        
        return events.filter { event in
            let eventDate = Date(timeIntervalSince1970: TimeInterval(event.startTime))
            return eventDate >= today && eventDate < tomorrow
        }
    }
    
    private func filterEventsUpcoming(_ events: [CalendarEvent], hours: Int) -> [CalendarEvent] {
        let now = Date()
        let cutoff = Calendar.current.date(byAdding: .hour, value: hours, to: now)!
        
        return events.filter { event in
            let eventDate = Date(timeIntervalSince1970: TimeInterval(event.startTime))
            return eventDate >= now && eventDate <= cutoff
        }
    }
    
    // MARK: - Event Search
    
    func searchEventsByTitle(_ searchText: String) -> [CalendarEvent] {
        eventsLock.lock()
        defer { eventsLock.unlock() }
        
        guard !searchText.isEmpty else { return events }
        
        return events.filter { event in
            event.title.localizedCaseInsensitiveContains(searchText)
        }
    }
    
    func getEventsForDate(_ date: Date) -> [CalendarEvent] {
        eventsLock.lock()
        defer { eventsLock.unlock() }
        
        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: date)
        let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay)!
        
        return events.filter { event in
            let eventDate = Date(timeIntervalSince1970: TimeInterval(event.startTime))
            return eventDate >= startOfDay && eventDate < endOfDay
        }
    }
}