/*
 * Color utility extensions for macOS Calendar Overlay.
 *
 * Responsibilities:
 * - Provide NSColor extensions for ARGB color conversions
 * - Convert between Windows-style ARGB colors and NSColor
 * - Create colors from hex strings and UInt32 values
 * - Generate random colors for event display
 *
 * Swift data types used:
 * - NSColor for macOS color representation
 * - UInt32 for ARGB color values
 * - String for hex color codes
 * - CGFloat for color components
 *
 * Swift technologies involved:
 * - NSColor extensions for utility methods
 * - Type extensions for enhanced functionality
 * - Computed properties for color conversions
 *
 * Design intent:
 * These extensions bridge the gap between Windows ARGB colors
 * and macOS NSColor, ensuring visual consistency across platforms.
 */

import AppKit

extension NSColor {
    
    // MARK: - ARGB Color Creation
    
    /// Creates an NSColor from a Windows-style ARGB UInt32 value (0xAARRGGBB)
    convenience init(argb: UInt32) {
        let alpha = CGFloat((argb >> 24) & 0xFF) / 255.0
        let red = CGFloat((argb >> 16) & 0xFF) / 255.0
        let green = CGFloat((argb >> 8) & 0xFF) / 255.0
        let blue = CGFloat(argb & 0xFF) / 255.0
        
        self.init(calibratedRed: red, green: green, blue: blue, alpha: alpha)
    }
    
    /// Creates an NSColor from RGB components with optional alpha
    convenience init(r: UInt8, g: UInt8, b: UInt8, a: UInt8 = 255) {
        self.init(
            calibratedRed: CGFloat(r) / 255.0,
            green: CGFloat(g) / 255.0,
            blue: CGFloat(b) / 255.0,
            alpha: CGFloat(a) / 255.0
        )
    }
    
    // MARK: - ARGB Conversion
    
    /// Returns the Windows-style ARGB UInt32 value (0xAARRGGBB)
    var argbValue: UInt32 {
        guard let rgbColor = usingColorSpace(.deviceRGB) else {
            return 0x00000000 // Transparent black as fallback
        }
        
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        
        rgbColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        
        let a = UInt32(alpha * 255) << 24
        let r = UInt32(red * 255) << 16
        let g = UInt32(green * 255) << 8
        let b = UInt32(blue * 255)
        
        return a | r | g | b
    }
    
    /// Returns the RGB components as a tuple (r, g, b, a)
    var argbComponents: (r: UInt8, g: UInt8, b: UInt8, a: UInt8) {
        guard let rgbColor = usingColorSpace(.deviceRGB) else {
            return (0, 0, 0, 0)
        }
        
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        
        rgbColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        
        return (
            r: UInt8(red * 255),
            g: UInt8(green * 255),
            b: UInt8(blue * 255),
            a: UInt8(alpha * 255)
        )
    }
    
    // MARK: - Hex String Support
    
    /// Creates an NSColor from a hex string (e.g., "#FF0000" or "FF0000")
    convenience init?(hex: String) {
        var hexString = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        
        // Remove # prefix if present
        if hexString.hasPrefix("#") {
            hexString.removeFirst()
        }
        
        // Validate length
        guard hexString.count == 6 || hexString.count == 8 else {
            return nil
        }
        
        var rgbValue: UInt64 = 0
        Scanner(string: hexString).scanHexInt64(&rgbValue)
        
        if hexString.count == 6 {
            // RRGGBB format
            self.init(
                r: UInt8((rgbValue >> 16) & 0xFF),
                g: UInt8((rgbValue >> 8) & 0xFF),
                b: UInt8(rgbValue & 0xFF)
            )
        } else {
            // AARRGGBB format
            self.init(
                r: UInt8((rgbValue >> 16) & 0xFF),
                g: UInt8((rgbValue >> 8) & 0xFF),
                b: UInt8(rgbValue & 0xFF),
                a: UInt8((rgbValue >> 24) & 0xFF)
            )
        }
    }
    
    /// Returns the hex string representation (e.g., "#FF0000FF")
    var hexString: String {
        let components = argbComponents
        return String(format: "#%02X%02X%02X%02X",
                     components.a, components.r, components.g, components.b)
    }
    
    /// Returns the hex string without alpha (e.g., "#FF0000")
    var hexStringRGB: String {
        let components = argbComponents
        return String(format: "#%02X%02X%02X",
                     components.r, components.g, components.b)
    }
    
    // MARK: - Color Manipulation
    
    /// Returns a new color with modified alpha component
    func withAlpha(_ alpha: CGFloat) -> NSColor {
        return self.withAlphaComponent(alpha)
    }
    
    /// Returns a lighter version of the color
    func lighter(by percentage: CGFloat = 0.3) -> NSColor {
        return adjustBrightness(by: abs(percentage))
    }
    
    /// Returns a darker version of the color
    func darker(by percentage: CGFloat = 0.3) -> NSColor {
        return adjustBrightness(by: -abs(percentage))
    }
    
    private func adjustBrightness(by percentage: CGFloat) -> NSColor {
        guard let rgbColor = usingColorSpace(.deviceRGB) else {
            return self
        }
        
        var hue: CGFloat = 0
        var saturation: CGFloat = 0
        var brightness: CGFloat = 0
        var alpha: CGFloat = 0
        
        rgbColor.getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: &alpha)
        
        brightness = max(0, min(1, brightness + percentage))
        
        return NSColor(hue: hue, saturation: saturation, brightness: brightness, alpha: alpha)
    }
    
    // MARK: - Utility Colors
    
    /// Returns a random color with full opacity
    static var random: NSColor {
        return NSColor(
            red: CGFloat.random(in: 0...1),
            green: CGFloat.random(in: 0...1),
            blue: CGFloat.random(in: 0...1),
            alpha: 1.0
        )
    }
    
    /// Returns a random pastel color
    static var randomPastel: NSColor {
        return NSColor(
            red: CGFloat.random(in: 0.6...0.9),
            green: CGFloat.random(in: 0.6...0.9),
            blue: CGFloat.random(in: 0.6...0.9),
            alpha: 1.0
        )
    }
    
    /// Returns a color that contrasts well with this color (for text)
    var contrastingColor: NSColor {
        // Calculate relative luminance
        let components = argbComponents
        let r = CGFloat(components.r) / 255.0
        let g = CGFloat(components.g) / 255.0
        let b = CGFloat(components.b) / 255.0
        
        // Relative luminance formula
        let luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        
        // Return black for light backgrounds, white for dark backgrounds
        return luminance > 0.5 ? .black : .white
    }
    
    // MARK: - Calendar-Specific Colors
    
    /// Default event colors for calendar display
    static var defaultEventColors: [NSColor] {
        return [
            NSColor.systemBlue,
            NSColor.systemGreen,
            NSColor.systemOrange,
            NSColor.systemPurple,
            NSColor.systemRed,
            NSColor.systemYellow,
            NSColor.systemPink,
            NSColor.systemTeal
        ]
    }
    
    /// Returns a color from the default event colors palette based on index
    static func eventColor(at index: Int) -> NSColor {
        let colors = defaultEventColors
        let colorIndex = index % colors.count
        return colors[colorIndex].withAlphaComponent(0.7)
    }
}