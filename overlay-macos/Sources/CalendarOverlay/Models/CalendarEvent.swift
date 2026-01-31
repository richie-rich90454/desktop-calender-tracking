/*
 * Calendar event data model for macOS Calendar Overlay.
 *
 * Responsibilities:
 * - Represent a single calendar event with title, description, and timing
 * - Store color information for event display
 * - Provide date utilities and validation
 * - Support JSON serialization/deserialization
 *
 * Swift data types used:
 * - String for text fields
 * - Int64 for Unix timestamps
 * - UInt8 for color components
 * - Bool for flags
 * - Date for Swift date conversions
 *
 * Swift technologies involved:
 * - Codable protocol for JSON support
 * - Equatable and Hashable protocols
 * - Computed properties for date utilities
 *
 * Design intent:
 * This struct mirrors the Windows CalendarEvent struct for cross-platform compatibility.
 * It provides Swift-friendly APIs while maintaining data structure parity.
 */

import Foundation

struct CalendarEvent: Codable, Equatable, Hashable {
    
    // MARK: - Properties
    
    var title: String
    var description: String
    var startTime: Int64  // Unix timestamp
    var endTime: Int64    // Unix timestamp
    var colorR: UInt8
    var colorG: UInt8
    var colorB: UInt8
    var priority: UInt8   // 1-10 scale, 5 is default
    var allDay: Bool
    
    // MARK: - Initializers
    
    init() {
        self.title = ""
        self.description = ""
        self.startTime = 0
        self.endTime = 0
        self.colorR = 66    // Default blue color
        self.colorG = 133
        self.colorB = 244
        self.priority = 5   // Default priority
        self.allDay = false
    }
    
    init(title: String, description: String, startTime: Int64, endTime: Int64,
         colorR: UInt8 = 66, colorG: UInt8 = 133, colorB: UInt8 = 244,
         priority: UInt8 = 5, allDay: Bool = false) {
        self.title = title
        self.description = description
        self.startTime = startTime
        self.endTime = endTime
        self.colorR = colorR
        self.colorG = colorG
        self.colorB = colorB
        self.priority = priority
        self.allDay = allDay
    }
    
    // MARK: - Computed Properties
    
    var startDate: Date {
        return Date(timeIntervalSince1970: TimeInterval(startTime))
    }
    
    var endDate: Date {
        return Date(timeIntervalSince1970: TimeInterval(endTime))
    }
    
    var duration: TimeInterval {
        return TimeInterval(endTime - startTime)
    }
    
    var isCurrent: Bool {
        let now = Date()
        return now >= startDate && now <= endDate
    }
    
    var isUpcoming: Bool {
        return Date() < startDate
    }
    
    var isPast: Bool {
        return Date() > endDate
    }
    
    // MARK: - Color Utilities
    
    func color() -> (r: UInt8, g: UInt8, b: UInt8) {
        return (colorR, colorG, colorB)
    }
    
    mutating func setColor(r: UInt8, g: UInt8, b: UInt8) {
        self.colorR = r
        self.colorG = g
        self.colorB = b
    }
    
    func hexColor() -> String {
        return String(format: "#%02X%02X%02X", colorR, colorG, colorB)
    }
    
    // MARK: - Validation
    
    func isValid() -> Bool {
        // Basic validation
        guard !title.isEmpty else { return false }
        guard startTime > 0 else { return false }
        guard endTime >= startTime else { return false }
        guard priority >= 1 && priority <= 10 else { return false }
        
        return true
    }
    
    // MARK: - Custom Coding Keys
    
    enum CodingKeys: String, CodingKey {
        case title
        case description
        case startTime = "start_time"
        case endTime = "end_time"
        case colorR = "color_r"
        case colorG = "color_g"
        case colorB = "color_b"
        case priority
        case allDay = "all_day"
    }
    
    // MARK: - Equatable Implementation
    
    static func == (lhs: CalendarEvent, rhs: CalendarEvent) -> Bool {
        return lhs.title == rhs.title &&
               lhs.description == rhs.description &&
               lhs.startTime == rhs.startTime &&
               lhs.endTime == rhs.endTime &&
               lhs.colorR == rhs.colorR &&
               lhs.colorG == rhs.colorG &&
               lhs.colorB == rhs.colorB &&
               lhs.priority == rhs.priority &&
               lhs.allDay == rhs.allDay
    }
    
    // MARK: - Hashable Implementation
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(title)
        hasher.combine(startTime)
        hasher.combine(endTime)
    }
}

// MARK: - Supporting Types

struct CalendarEventsResponse: Codable {
    let events: [CalendarEvent]
}

// MARK: - Date Extension for Convenience

extension Date {
    func formattedString(format: String) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = format
        return formatter.string(from: self)
    }
}