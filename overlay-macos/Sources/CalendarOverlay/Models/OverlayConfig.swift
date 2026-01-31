/*
 * Configuration data model for macOS Calendar Overlay.
 *
 * Responsibilities:
 * - Store all application configuration settings
 * - Provide default values matching Windows version
 * - Validate configuration values
 * - Support persistence via UserDefaults or JSON
 *
 * Swift data types used:
 * - Bool for toggle settings
 * - Int for dimensions and intervals
 * - Float for opacity values
 * - UInt32 for ARGB colors
 * - String for text settings
 *
 * Swift technologies involved:
 * - Codable protocol for serialization
 * - Equatable protocol for comparison
 * - Mutating methods for validation
 * - Computed properties for color manipulation
 *
 * Design intent:
 * This struct mirrors the Windows OverlayConfig struct for cross-platform compatibility.
 * It provides type-safe configuration with validation and sensible defaults.
 */

import Foundation

struct OverlayConfig: Codable, Equatable {
    
    // MARK: - Properties
    
    var enabled: Bool
    var positionX: Int
    var positionY: Int
    var width: Int
    var height: Int
    var opacity: Float
    var showPastEvents: Bool
    var showAllDay: Bool
    var refreshInterval: Int
    var fontSize: Int
    var backgroundColor: UInt32
    var textColor: UInt32
    var clickThrough: Bool
    var position: String
    var wallpaperMode: Bool
    
    // MARK: - Initializers
    
    init() {
        self.enabled = true
        self.positionX = 100
        self.positionY = 100
        self.width = 400
        self.height = 600
        self.opacity = 0.85
        self.showPastEvents = false
        self.showAllDay = true
        self.refreshInterval = 30
        self.fontSize = 14
        self.backgroundColor = 0x20000000  // Semi-transparent black
        self.textColor = 0xFFFFFFFF        // White
        self.clickThrough = false
        self.position = "top-right"
        self.wallpaperMode = false
        
        // Validate initial values
        validate()
    }
    
    init(enabled: Bool = true,
         positionX: Int = 100, positionY: Int = 100,
         width: Int = 400, height: Int = 600,
         opacity: Float = 0.85,
         showPastEvents: Bool = false,
         showAllDay: Bool = true,
         refreshInterval: Int = 30,
         fontSize: Int = 14,
         backgroundColor: UInt32 = 0x20000000,
         textColor: UInt32 = 0xFFFFFFFF,
         clickThrough: Bool = false,
         position: String = "top-right",
         wallpaperMode: Bool = false) {
        
        self.enabled = enabled
        self.positionX = positionX
        self.positionY = positionY
        self.width = width
        self.height = height
        self.opacity = opacity
        self.showPastEvents = showPastEvents
        self.showAllDay = showAllDay
        self.refreshInterval = refreshInterval
        self.fontSize = fontSize
        self.backgroundColor = backgroundColor
        self.textColor = textColor
        self.clickThrough = clickThrough
        self.position = position
        self.wallpaperMode = wallpaperMode
        
        // Validate values
        validate()
    }
    
    // MARK: - Validation
    
    mutating func validate() {
        // Ensure opacity is between 0.0 and 1.0
        opacity = max(0.0, min(1.0, opacity))
        
        // Ensure reasonable window dimensions
        width = max(100, min(5000, width))
        height = max(100, min(5000, height))
        
        // Ensure reasonable position
        positionX = max(0, positionX)
        positionY = max(0, positionY)
        
        // Ensure reasonable font size
        fontSize = max(8, min(72, fontSize))
        
        // Ensure reasonable refresh interval
        refreshInterval = max(5, min(3600, refreshInterval)) // 5 seconds to 1 hour
        
        // Ensure position is valid
        let validPositions = ["top-right", "top-left", "bottom-right", "bottom-left", "custom"]
        if !validPositions.contains(position) {
            position = "top-right"
        }
    }
    
    func isValid() -> Bool {
        // Check if configuration is valid
        guard opacity >= 0.0 && opacity <= 1.0 else { return false }
        guard width > 0 && height > 0 else { return false }
        guard fontSize >= 8 && fontSize <= 72 else { return false }
        guard refreshInterval >= 5 && refreshInterval <= 3600 else { return false }
        
        let validPositions = ["top-right", "top-left", "bottom-right", "bottom-left", "custom"]
        guard validPositions.contains(position) else { return false }
        
        return true
    }
    
    // MARK: - Computed Properties
    
    var backgroundColorAlpha: Float {
        // Extract alpha component from ARGB color
        return Float((backgroundColor >> 24) & 0xFF) / 255.0
    }
    
    var backgroundColorRGB: UInt32 {
        // Extract RGB components (without alpha)
        return backgroundColor & 0x00FFFFFF
    }
    
    var textColorAlpha: Float {
        // Extract alpha component from ARGB color
        return Float((textColor >> 24) & 0xFF) / 255.0
    }
    
    var textColorRGB: UInt32 {
        // Extract RGB components (without alpha)
        return textColor & 0x00FFFFFF
    }
    
    // MARK: - Color Utilities
    
    mutating func setBackgroundColor(r: UInt8, g: UInt8, b: UInt8, a: UInt8 = 0x20) {
        // Set background color with ARGB format
        backgroundColor = (UInt32(a) << 24) | (UInt32(r) << 16) | (UInt32(g) << 8) | UInt32(b)
    }
    
    mutating func setTextColor(r: UInt8, g: UInt8, b: UInt8, a: UInt8 = 0xFF) {
        // Set text color with ARGB format
        textColor = (UInt32(a) << 24) | (UInt32(r) << 16) | (UInt32(g) << 8) | UInt32(b)
    }
    
    func backgroundColorComponents() -> (r: UInt8, g: UInt8, b: UInt8, a: UInt8) {
        // Extract color components
        let a = UInt8((backgroundColor >> 24) & 0xFF)
        let r = UInt8((backgroundColor >> 16) & 0xFF)
        let g = UInt8((backgroundColor >> 8) & 0xFF)
        let b = UInt8(backgroundColor & 0xFF)
        return (r, g, b, a)
    }
    
    func textColorComponents() -> (r: UInt8, g: UInt8, b: UInt8, a: UInt8) {
        // Extract color components
        let a = UInt8((textColor >> 24) & 0xFF)
        let r = UInt8((textColor >> 16) & 0xFF)
        let g = UInt8((textColor >> 8) & 0xFF)
        let b = UInt8(textColor & 0xFF)
        return (r, g, b, a)
    }
    
    // MARK: - Position Utilities
    
    mutating func setPosition(_ newPosition: String) {
        let validPositions = ["top-right", "top-left", "bottom-right", "bottom-left", "custom"]
        if validPositions.contains(newPosition) {
            position = newPosition
        } else {
            position = "top-right"
        }
    }
    
    func isPositionValid() -> Bool {
        let validPositions = ["top-right", "top-left", "bottom-right", "bottom-left", "custom"]
        return validPositions.contains(position)
    }
    
    // MARK: - Custom Coding Keys
    
    enum CodingKeys: String, CodingKey {
        case enabled
        case positionX = "position_x"
        case positionY = "position_y"
        case width
        case height
        case opacity
        case showPastEvents = "show_past_events"
        case showAllDay = "show_all_day"
        case refreshInterval = "refresh_interval"
        case fontSize = "font_size"
        case backgroundColor = "background_color"
        case textColor = "text_color"
        case clickThrough = "click_through"
        case position
        case wallpaperMode = "wallpaper_mode"
    }
    
    // MARK: - Equatable Implementation
    
    static func == (lhs: OverlayConfig, rhs: OverlayConfig) -> Bool {
        return lhs.enabled == rhs.enabled &&
               lhs.positionX == rhs.positionX &&
               lhs.positionY == rhs.positionY &&
               lhs.width == rhs.width &&
               lhs.height == rhs.height &&
               lhs.opacity == rhs.opacity &&
               lhs.showPastEvents == rhs.showPastEvents &&
               lhs.showAllDay == rhs.showAllDay &&
               lhs.refreshInterval == rhs.refreshInterval &&
               lhs.fontSize == rhs.fontSize &&
               lhs.backgroundColor == rhs.backgroundColor &&
               lhs.textColor == rhs.textColor &&
               lhs.clickThrough == rhs.clickThrough &&
               lhs.position == rhs.position &&
               lhs.wallpaperMode == rhs.wallpaperMode
    }
}

// MARK: - Color Constants

struct OverlayColors {
    static let defaultBackground: UInt32 = 0x20000000  // Semi-transparent black
    static let defaultText: UInt32 = 0xFFFFFFFF        // White
    static let transparent: UInt32 = 0x00000000        // Fully transparent
    static let black: UInt32 = 0xFF000000              // Opaque black
    static let white: UInt32 = 0xFFFFFFFF              // Opaque white
    static let blue: UInt32 = 0xFF4285F4               // Google blue
    static let green: UInt32 = 0xFF34A853              // Google green
    static let red: UInt32 = 0xFFEA4335                // Google red
    static let yellow: UInt32 = 0xFFFBBC05             // Google yellow
}

// MARK: - Position Constants

struct OverlayPositions {
    static let topRight = "top-right"
    static let topLeft = "top-left"
    static let bottomRight = "bottom-right"
    static let bottomLeft = "bottom-left"
    static let custom = "custom"
}