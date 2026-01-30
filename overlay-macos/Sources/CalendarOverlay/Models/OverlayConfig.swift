/*
OverlayConfig.swift - Configuration data model for macOS Calendar Overlay

This file defines the OverlayConfig data structure for storing application configuration.
It's the Swift equivalent of the Windows OverlayConfig struct in calendar_shared.h.

IMPLEMENTATION NOTES:
1. Define OverlayConfig as a Swift struct or class
2. Match the Windows struct layout for compatibility
3. Add Swift-specific features (Codable, Equatable, etc.)
4. Provide default values matching Windows defaults
5. Add validation and utility methods

WINDOWS EQUIVALENT: OverlayConfig struct in calendar_shared.h

STRUCTURE COMPARISON:
Windows C++ struct:
struct OverlayConfig {
    bool enabled;
    int positionX, positionY;
    int width, height;
    float opacity;
    bool showPastEvents;
    bool showAllDay;
    int refreshInterval;
    int fontSize;
    uint32_t backgroundColor;
    uint32_t textColor;
    bool clickThrough;
    std::string position;
    bool wallpaperMode;
}

Swift equivalent considerations:
- Use Swift types (Bool, Int, Float, UInt32, String)
- Provide sensible default values
- Consider making it Codable for UserDefaults/JSON
- Add validation for values (opacity 0.0-1.0, etc.)

ADD HERE:
1. Import Foundation
2. Define OverlayConfig struct or class
3. Add properties matching Windows struct
4. Implement initializers with default values
5. Add validation methods
6. Consider making it Codable for persistence
*/

// TODO: Add imports for Foundation

// TODO: Define OverlayConfig struct
// struct OverlayConfig {
//     
//     // TODO: Add properties matching Windows struct
//     // var enabled: Bool
//     // var positionX: Int
//     // var positionY: Int
//     // var width: Int
//     // var height: Int
//     // var opacity: Float
//     // var showPastEvents: Bool
//     // var showAllDay: Bool
//     // var refreshInterval: Int
//     // var fontSize: Int
//     // var backgroundColor: UInt32
//     // var textColor: UInt32
//     // var clickThrough: Bool
//     // var position: String
//     // var wallpaperMode: Bool
//     
//     // TODO: Implement default initializer with Windows-compatible defaults
//     // init() {
//     //     self.enabled = true
//     //     self.positionX = 100
//     //     self.positionY = 100
//     //     self.width = 400
//     //     self.height = 600
//     //     self.opacity = 0.85
//     //     self.showPastEvents = false
//     //     self.showAllDay = true
//     //     self.refreshInterval = 30
//     //     self.fontSize = 14
//     //     self.backgroundColor = 0x20000000  // Semi-transparent black
//     //     self.textColor = 0xFFFFFFFF        // White
//     //     self.clickThrough = false
//     //     self.position = "top-right"
//     //     self.wallpaperMode = false
//     // }
//     
//     // TODO: Implement parameterized initializer
//     // init(enabled: Bool = true,
//     //      positionX: Int = 100, positionY: Int = 100,
//     //      width: Int = 400, height: Int = 600,
//     //      opacity: Float = 0.85,
//     //      showPastEvents: Bool = false,
//     //      showAllDay: Bool = true,
//     //      refreshInterval: Int = 30,
//     //      fontSize: Int = 14,
//     //      backgroundColor: UInt32 = 0x20000000,
//     //      textColor: UInt32 = 0xFFFFFFFF,
//     //      clickThrough: Bool = false,
//     //      position: String = "top-right",
//     //      wallpaperMode: Bool = false) {
//     //     
//     //     self.enabled = enabled
//     //     self.positionX = positionX
//     //     self.positionY = positionY
//     //     self.width = width
//     //     self.height = height
//     //     self.opacity = opacity
//     //     self.showPastEvents = showPastEvents
//     //     self.showAllDay = showAllDay
//     //     self.refreshInterval = refreshInterval
//     //     self.fontSize = fontSize
//     //     self.backgroundColor = backgroundColor
//     //     self.textColor = textColor
//     //     self.clickThrough = clickThrough
//     //     self.position = position
//     //     self.wallpaperMode = wallpaperMode
//     //     
//     //     // Validate values
//     //     validate()
//     // }
//     
//     // MARK: - Validation
//     
//     // TODO: Implement validate() method
//     // mutating func validate() {
//     //     // Ensure opacity is between 0.0 and 1.0
//     //     opacity = max(0.0, min(1.0, opacity))
//     //     
//     //     // Ensure reasonable window dimensions
//     //     width = max(100, min(5000, width))
//     //     height = max(100, min(5000, height))
//     //     
//     //     // Ensure reasonable position
//     //     positionX = max(0, positionX)
//     //     positionY = max(0, positionY)
//     //     
//     //     // Ensure reasonable font size
//     //     fontSize = max(8, min(72, fontSize))
//     //     
//     //     // Ensure reasonable refresh interval
//     //     refreshInterval = max(5, min(3600, refreshInterval)) // 5 seconds to 1 hour
//     //     
//     //     // Ensure position is valid
//     //     let validPositions = ["top-right", "top-left", "bottom-right", "bottom-left", "custom"]
//     //     if !validPositions.contains(position) {
//     //         position = "top-right"
//     //     }
//     // }
//     
//     // TODO: Implement isValid() method
//     // func isValid() -> Bool {
//     //     // Check if configuration is valid
//     //     guard opacity >= 0.0 && opacity <= 1.0 else { return false }
//     //     guard width > 0 && height > 0 else { return false }
//     //     guard fontSize >= 8 && fontSize <= 72 else { return false }
//     //     guard refreshInterval >= 5 && refreshInterval <= 3600 else { return false }
//     //     
//     //     let validPositions = ["top-right", "top-left", "bottom-right", "bottom-left", "custom"]
//     //     guard validPositions.contains(position) else { return false }
//     //     
//     //     return true
//     // }
//     
//     // MARK: - Computed Properties
//     
//     // TODO: Add computed properties for convenience
//     // var backgroundColorAlpha: Float {
//     //     // Extract alpha component from ARGB color
//     //     return Float((backgroundColor >> 24) & 0xFF) / 255.0
//     // }
//     // 
//     // var backgroundColorRGB: UInt32 {
//     //     // Extract RGB components (without alpha)
//     //     return backgroundColor & 0x00FFFFFF
//     // }
//     // 
//     // var textColorAlpha: Float {
//     //     // Extract alpha component from ARGB color
//     //     return Float((textColor >> 24) & 0xFF) / 255.0
//     // }
//     // 
//     // var textColorRGB: UInt32 {
//     //     // Extract RGB components (without alpha)
//     //     return textColor & 0x00FFFFFF
//     // }
//     
//     // MARK: - Color Utilities
//     
//     // TODO: Add color utility methods
//     // mutating func setBackgroundColor(r: UInt8, g: UInt8, b: UInt8, a: UInt8 = 0x20) {
//     //     // Set background color with ARGB format
//     //     backgroundColor = (UInt32(a) << 24) | (UInt32(r) << 16) | (UInt32(g) << 8) | UInt32(b)
//     // }
//     // 
//     // mutating func setTextColor(r: UInt8, g: UInt8, b: UInt8, a: UInt8 = 0xFF) {
//     //     // Set text color with ARGB format
//     //     textColor = (UInt32(a) << 24) | (UInt32(r) << 16) | (UInt32(g) << 8) | UInt32(b)
//     // }
//     // 
//     // func backgroundColorComponents() -> (r: UInt8, g: UInt8, b: UInt8, a: UInt8) {
//     //     // Extract color components
//     //     let a = UInt8((backgroundColor >> 24) & 0xFF)
//     //     let r = UInt8((backgroundColor >> 16) & 0xFF)
//     //     let g = UInt8((backgroundColor >> 8) & 0xFF)
//     //     let b = UInt8(backgroundColor & 0xFF)
//     //     return (r, g, b, a)
//     // }
//     // 
//     // func textColorComponents() -> (r: UInt8, g: UInt8, b: UInt8, a: UInt8) {
//     //     // Extract color components
//     //     let a = UInt8((textColor >> 24) & 0xFF)
//     //     let r = UInt8((textColor >> 16) & 0xFF)
//     //     let g = UInt8((textColor >> 8) & 0xFF)
//     //     let b = UInt8(textColor & 0xFF)
//     //     return (r, g, b, a)
//     // }
//     
//     // MARK: - Position Utilities
//     
//     // TODO: Add position utility methods
//     // mutating func setPosition(_ newPosition: String) {
//     //     let validPositions = ["top-right", "top-left", "bottom-right", "bottom-left", "custom"]
//     //     if validPositions.contains(newPosition) {
//     //         position = newPosition
//     //     } else {
//     //         position = "top-right"
//     //     }
//     // }
//     // 
//     // func isPositionValid() -> Bool {
//     //     let validPositions = ["top-right", "top-left", "bottom-right", "bottom-left", "custom"]
//     //     return validPositions.contains(position)
//     // }
//     
//     // MARK: - JSON Support (if using Codable)
//     
//     // TODO: Make OverlayConfig Codable for persistence
//     // extension OverlayConfig: Codable {
//     //     // Swift will automatically synthesize Codable conformance
//     //     // if all properties are Codable (which they are)
//     //     
//     //     // Optional: Custom coding keys if needed
//     //     // enum CodingKeys: String, CodingKey {
//     //     //     case enabled
//     //     //     case positionX = "position_x"
//     //     //     case positionY = "position_y"
//     //     //     case width
//     //     //     case height
//     //     //     case opacity
//     //     //     case showPastEvents = "show_past_events"
//     //     //     case showAllDay = "show_all_day"
//     //     //     case refreshInterval = "refresh_interval"
//     //     //     case fontSize = "font_size"
//     //     //     case backgroundColor = "background_color"
//     //     //     case textColor = "text_color"
//     //     //     case clickThrough = "click_through"
//     //     //     case position
//     //     //     case wallpaperMode = "wallpaper_mode"
//     //     // }
//     // }
//     
//     // MARK: - Equatable
//     
//     // TODO: Make OverlayConfig Equatable for comparison
//     // extension OverlayConfig: Equatable {
//     //     static func == (lhs: OverlayConfig, rhs: OverlayConfig) -> Bool {
//     //         return lhs.enabled == rhs.enabled &&
//     //                lhs.positionX == rhs.positionX &&
//     //                lhs.positionY == rhs.positionY &&
//     //                lhs.width == rhs.width &&
//     //                lhs.height == rhs.height &&
//     //                lhs.opacity == rhs.opacity &&
//     //                lhs.showPastEvents == rhs.showPastEvents &&
//     //                lhs.showAllDay == rhs.showAllDay &&
//     //                lhs.refreshInterval == rhs.refreshInterval &&
//     //                lhs.fontSize == rhs.fontSize &&
//     //                lhs.backgroundColor == rhs.backgroundColor &&
//     //                lhs.textColor == rhs.textColor &&
//     //                lhs.clickThrough == rhs.clickThrough &&
//     //                lhs.position == rhs.position &&
//     //                lhs.wallpaperMode == rhs.wallpaperMode
//     //     }
//     // }
//     
//     // MARK: - Windows Compatibility (optional)
//     
//     // TODO: Add methods for Windows struct compatibility if needed
//     // This would be needed only if sharing config with C/C++ Windows code
//     // func toWindowsStruct() -> WindowsOverlayConfig {
//     //     // Convert to C-compatible struct
//     // }
//     // 
//     // static func fromWindowsStruct(_ winConfig: WindowsOverlayConfig) -> OverlayConfig {
//     //     // Convert from C-compatible struct
//     // }
// }

// TODO: Define color constants for easy reference
// struct OverlayColors {
//     static let defaultBackground: UInt32 = 0x20000000  // Semi-transparent black
//     static let defaultText: UInt32 = 0xFFFFFFFF        // White
//     static let transparent: UInt32 = 0x00000000        // Fully transparent
//     static let black: UInt32 = 0xFF000000              // Opaque black
//     static let white: UInt32 = 0xFFFFFFFF              // Opaque white
//     static let blue: UInt32 = 0xFF4285F4               // Google blue
//     static let green: UInt32 = 0xFF34A853              // Google green
//     static let red: UInt32 = 0xFFEA4335                // Google red
//     static let yellow: UInt32 = 0xFFFBBC05             // Google yellow
// }

// TODO: Define position constants
// struct OverlayPositions {
//     static let topRight = "top-right"
//     static let topLeft = "top-left"
//     static let bottomRight = "bottom-right"
//     static let bottomLeft = "bottom-left"
//     static let custom = "custom"
// }