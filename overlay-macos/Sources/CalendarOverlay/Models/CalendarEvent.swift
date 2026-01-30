/*
CalendarEvent.swift - Calendar event data model for macOS Calendar Overlay

This file defines the CalendarEvent data structure for storing calendar events.
It's the Swift equivalent of the Windows CalendarEvent struct in calendar_shared.h.

IMPLEMENTATION NOTES:
1. Define CalendarEvent as a Swift struct or class
2. Match the Windows struct layout for compatibility
3. Add Swift-specific features (Codable, Equatable, etc.)
4. Provide conversion methods if needed
5. Add validation and utility methods

WINDOWS EQUIVALENT: CalendarEvent struct in calendar_shared.h

STRUCTURE COMPARISON:
Windows C++ struct:
struct CalendarEvent {
    char title[256];
    char description[512];
    int64_t startTime;
    int64_t endTime;
    uint8_t colorR, colorG, colorB;
    uint8_t priority;
    bool allDay;
}

Swift equivalent considerations:
- Use String instead of char arrays
- Use Int64 for timestamps (Unix timestamps)
- Use UInt8 for color components
- Use Bool for allDay flag
- Consider making it Codable for JSON serialization

ADD HERE:
1. Import Foundation
2. Define CalendarEvent struct or class
3. Add properties matching Windows struct
4. Implement initializers
5. Add convenience methods
6. Consider making it Codable for JSON parsing
*/

// TODO: Add imports for Foundation

// TODO: Define CalendarEvent struct
// struct CalendarEvent {
//     
//     // TODO: Add properties matching Windows struct
//     // var title: String
//     // var description: String
//     // var startTime: Int64  // Unix timestamp
//     // var endTime: Int64    // Unix timestamp
//     // var colorR: UInt8
//     // var colorG: UInt8
//     // var colorB: UInt8
//     // var priority: UInt8   // 1-10 scale, 5 is default
//     // var allDay: Bool
//     
//     // TODO: Implement default initializer
//     // init() {
//     //     self.title = ""
//     //     self.description = ""
//     //     self.startTime = 0
//     //     self.endTime = 0
//     //     self.colorR = 66    // Default blue color
//     //     self.colorG = 133
//     //     self.colorB = 244
//     //     self.priority = 5   // Default priority
//     //     self.allDay = false
//     // }
//     
//     // TODO: Implement parameterized initializer
//     // init(title: String, description: String, startTime: Int64, endTime: Int64,
//     //      colorR: UInt8 = 66, colorG: UInt8 = 133, colorB: UInt8 = 244,
//     //      priority: UInt8 = 5, allDay: Bool = false) {
//     //     self.title = title
//     //     self.description = description
//     //     self.startTime = startTime
//     //     self.endTime = endTime
//     //     self.colorR = colorR
//     //     self.colorG = colorG
//     //     self.colorB = colorB
//     //     self.priority = priority
//     //     self.allDay = allDay
//     // }
//     
//     // MARK: - Computed Properties
//     
//     // TODO: Add computed properties for convenience
//     // var startDate: Date {
//     //     return Date(timeIntervalSince1970: TimeInterval(startTime))
//     // }
//     // 
//     // var endDate: Date {
//     //     return Date(timeIntervalSince1970: TimeInterval(endTime))
//     // }
//     // 
//     // var duration: TimeInterval {
//     //     return TimeInterval(endTime - startTime)
//     // }
//     // 
//     // var isCurrent: Bool {
//     //     let now = Date()
//     //     return now >= startDate && now <= endDate
//     // }
//     // 
//     // var isUpcoming: Bool {
//     //     return Date() < startDate
//     // }
//     // 
//     // var isPast: Bool {
//     //     return Date() > endDate
//     // }
//     
//     // MARK: - Color Utilities
//     
//     // TODO: Add color utility methods
//     // func color() -> (r: UInt8, g: UInt8, b: UInt8) {
//     //     return (colorR, colorG, colorB)
//     // }
//     // 
//     // mutating func setColor(r: UInt8, g: UInt8, b: UInt8) {
//     //     self.colorR = r
//     //     self.colorG = g
//     //     self.colorB = b
//     // }
//     // 
//     // func hexColor() -> String {
//     //     return String(format: "#%02X%02X%02X", colorR, colorG, colorB)
//     // }
//     
//     // MARK: - Validation
//     
//     // TODO: Add validation methods
//     // func isValid() -> Bool {
//     //     // Basic validation
//     //     guard !title.isEmpty else { return false }
//     //     guard startTime > 0 else { return false }
//     //     guard endTime >= startTime else { return false }
//     //     guard priority >= 1 && priority <= 10 else { return false }
//     //     
//     //     return true
//     // }
//     
//     // MARK: - JSON Support (if using Codable)
//     
//     // TODO: Make CalendarEvent Codable for JSON parsing
//     // extension CalendarEvent: Codable {
//     //     // Swift will automatically synthesize Codable conformance
//     //     // if all properties are Codable (which they are)
//     //     
//     //     // Optional: Custom coding keys if needed
//     //     // enum CodingKeys: String, CodingKey {
//     //     //     case title
//     //     //     case description
//     //     //     case startTime = "start_time"
//     //     //     case endTime = "end_time"
//     //     //     case colorR = "color_r"
//     //     //     case colorG = "color_g"
//     //     //     case colorB = "color_b"
//     //     //     case priority
//     //     //     case allDay = "all_day"
//     //     // }
//     // }
//     
//     // MARK: - Equatable & Hashable
//     
//     // TODO: Make CalendarEvent Equatable for comparison
//     // extension CalendarEvent: Equatable {
//     //     static func == (lhs: CalendarEvent, rhs: CalendarEvent) -> Bool {
//     //         return lhs.title == rhs.title &&
//     //                lhs.description == rhs.description &&
//     //                lhs.startTime == rhs.startTime &&
//     //                lhs.endTime == rhs.endTime &&
//     //                lhs.colorR == rhs.colorR &&
//     //                lhs.colorG == rhs.colorG &&
//     //                lhs.colorB == rhs.colorB &&
//     //                lhs.priority == rhs.priority &&
//     //                lhs.allDay == rhs.allDay
//     //     }
//     // }
//     // 
//     // // Optional: Make it Hashable if needed for sets/dictionaries
//     // extension CalendarEvent: Hashable {
//     //     func hash(into hasher: inout Hasher) {
//     //         hasher.combine(title)
//     //         hasher.combine(startTime)
//     //         hasher.combine(endTime)
//     //     }
//     // }
//     
//     // MARK: - Windows Compatibility (optional)
//     
//     // TODO: Add methods for Windows struct compatibility if needed
//     // func toWindowsStruct() -> WindowsCalendarEvent {
//     //     // Convert to C-compatible struct if interfacing with C/C++ code
//     //     // This would require a separate C-compatible struct definition
//     // }
//     // 
//     // static func fromWindowsStruct(_ winEvent: WindowsCalendarEvent) -> CalendarEvent {
//     //     // Convert from C-compatible struct
//     // }
// }

// TODO: Define Windows-compatible struct if needed for C interop
// This would be needed only if you're sharing data with C/C++ code
// struct WindowsCalendarEvent {
//     var title: (CChar, CChar, ... 256 times) // C char array
//     var description: (CChar, CChar, ... 512 times) // C char array
//     var startTime: Int64
//     var endTime: Int64
//     var colorR: UInt8
//     var colorG: UInt8
//     var colorB: UInt8
//     var priority: UInt8
//     var allDay: CBool
// }