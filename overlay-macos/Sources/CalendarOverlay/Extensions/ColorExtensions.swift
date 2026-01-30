/*
ColorExtensions.swift - Color utility extensions for macOS Calendar Overlay

This file provides color utility extensions for working with colors in the macOS overlay.
It includes extensions for NSColor, CGColor, and color conversions between different formats.

IMPLEMENTATION NOTES:
1. Provide extensions for NSColor for common color operations
2. Add utilities for converting between ARGB UInt32 and NSColor
3. Add color blending and manipulation utilities
4. Provide macOS-specific color utilities for the overlay

WINDOWS EQUIVALENT: Color utility functions in various Windows files

COLOR FORMATS:
- ARGB UInt32: 0xAARRGGBB format used in Windows config
- NSColor: macOS native color class
- CGColor: Core Graphics color reference
- RGB components: Separate R, G, B, A values

ADD HERE:
1. Import Foundation and AppKit (or CoreGraphics)
2. Define NSColor extensions for ARGB conversion
3. Add color manipulation utilities
4. Add color blending utilities
5. Add macOS-specific color utilities
*/

// TODO: Add imports for Foundation and AppKit
// import AppKit

// TODO: Define NSColor extension for ARGB conversion
// extension NSColor {
//     
//     // MARK: - ARGB UInt32 Conversion
//     
//     // TODO: Implement convenience initializer from ARGB UInt32
//     // convenience init(argb: UInt32) {
//     //     let alpha = CGFloat((argb >> 24) & 0xFF) / 255.0
//     //     let red = CGFloat((argb >> 16) & 0xFF) / 255.0
//     //     let green = CGFloat((argb >> 8) & 0xFF) / 255.0
//     //     let blue = CGFloat(argb & 0xFF) / 255.0
//     //     
//     //     self.init(calibratedRed: red, green: green, blue: blue, alpha: alpha)
//     // }
//     
//     // TODO: Implement computed property to get ARGB UInt32
//     // var argb: UInt32 {
//     //     guard let rgbColor = self.usingColorSpace(.deviceRGB) else {
//     //         return 0x00000000 // Return transparent black if conversion fails
//     //     }
//     //     
//     //     let alpha = UInt32(rgbColor.alphaComponent * 255.0) << 24
//     //     let red = UInt32(rgbColor.redComponent * 255.0) << 16
//     //     let green = UInt32(rgbColor.greenComponent * 255.0) << 8
//     //     let blue = UInt32(rgbColor.blueComponent * 255.0)
//     //     
//     //     return alpha | red | green | blue
//     // }
//     
//     // TODO: Implement convenience initializer from RGB components
//     // convenience init(r: UInt8, g: UInt8, b: UInt8, a: UInt8 = 255) {
//     //     self.init(calibratedRed: CGFloat(r) / 255.0,
//     //               green: CGFloat(g) / 255.0,
//     //               blue: CGFloat(b) / 255.0,
//     //               alpha: CGFloat(a) / 255.0)
//     // }
//     
//     // MARK: - Color Manipulation
//     
//     // TODO: Implement withAlpha() method
//     // func withAlpha(_ alpha: CGFloat) -> NSColor {
//     //     return self.withAlphaComponent(alpha)
//     // }
//     
//     // TODO: Implement lighter() method
//     // func lighter(by percentage: CGFloat = 0.3) -> NSColor {
//     //     return adjustBrightness(by: abs(percentage))
//     // }
//     
//     // TODO: Implement darker() method
//     // func darker(by percentage: CGFloat = 0.3) -> NSColor {
//     //     return adjustBrightness(by: -abs(percentage))
//     // }
//     
//     // TODO: Implement adjustBrightness() method
//     // private func adjustBrightness(by percentage: CGFloat) -> NSColor {
//     //     guard let rgbColor = self.usingColorSpace(.deviceRGB) else {
//     //         return self
//     //     }
//     //     
//     //     var red = rgbColor.redComponent
//     //     var green = rgbColor.greenComponent
//     //     var blue = rgbColor.blueComponent
//     //     let alpha = rgbColor.alphaComponent
//     //     
//     //     // Adjust brightness
//     //     let adjustment = 1.0 + percentage
//     //     red = min(1.0, max(0.0, red * adjustment))
//     //     green = min(1.0, max(0.0, green * adjustment))
//     //     blue = min(1.0, max(0.0, blue * adjustment))
//     //     
//     //     return NSColor(calibratedRed: red, green: green, blue: blue, alpha: alpha)
//     // }
//     
//     // MARK: - Color Blending
//     
//     // TODO: Implement blended() method
//     // func blended(with other: NSColor, factor: CGFloat = 0.5) -> NSColor {
//     //     guard let rgbColor1 = self.usingColorSpace(.deviceRGB),
//     //           let rgbColor2 = other.usingColorSpace(.deviceRGB) else {
//     //         return self
//     //     }
//     //     
//     //     let red = rgbColor1.redComponent * (1 - factor) + rgbColor2.redComponent * factor
//     //     let green = rgbColor1.greenComponent * (1 - factor) + rgbColor2.greenComponent * factor
//     //     let blue = rgbColor1.blueComponent * (1 - factor) + rgbColor2.blueComponent * factor
//     //     let alpha = rgbColor1.alphaComponent * (1 - factor) + rgbColor2.alphaComponent * factor
//     //     
//     //     return NSColor(calibratedRed: red, green: green, blue: blue, alpha: alpha)
//     // }
//     
//     // MARK: - Color Components
//     
//     // TODO: Implement rgbComponents property
//     // var rgbComponents: (red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat)? {
//     //     guard let rgbColor = self.usingColorSpace(.deviceRGB) else {
//     //         return nil
//     //     }
//     //     
//     //     return (rgbColor.redComponent, rgbColor.greenComponent, rgbColor.blueComponent, rgbColor.alphaComponent)
//     // }
//     
//     // TODO: Implement uInt8Components property
//     // var uInt8Components: (red: UInt8, green: UInt8, blue: UInt8, alpha: UInt8)? {
//     //     guard let components = rgbComponents else {
//     //         return nil
//     //     }
//     //     
//     //     return (UInt8(components.red * 255.0),
//     //             UInt8(components.green * 255.0),
//     //             UInt8(components.blue * 255.0),
//     //             UInt8(components.alpha * 255.0))
//     // }
//     
//     // MARK: - Utility Colors
//     
//     // TODO: Add static utility colors
//     // static var semiTransparentBlack: NSColor {
//     //     return NSColor(white: 0.0, alpha: 0.7)
//     // }
//     // 
//     // static var semiTransparentWhite: NSColor {
//     //     return NSColor(white: 1.0, alpha: 0.7)
//     // }
//     // 
//     // static var overlayBackground: NSColor {
//     //     return NSColor(argb: 0x20000000) // Semi-transparent black
//     // }
//     // 
//     // static var overlayText: NSColor {
//     //     return NSColor(argb: 0xFFFFFFFF) // White
//     // }
// }

// TODO: Define CGColor extension if needed
// extension CGColor {
//     // TODO: Add CGColor utilities if needed
//     // static func fromARGB(_ argb: UInt32) -> CGColor? {
//     //     let color = NSColor(argb: argb)
//     //     return color.cgColor
//     // }
// }

// TODO: Define UInt32 extension for color utilities
// extension UInt32 {
//     
//     // MARK: - Color Component Extraction
//     
//     // TODO: Implement color component properties
//     // var alphaComponent: UInt8 {
//     //     return UInt8((self >> 24) & 0xFF)
//     // }
//     // 
//     // var redComponent: UInt8 {
//     //     return UInt8((self >> 16) & 0xFF)
//     // }
//     // 
//     // var greenComponent: UInt8 {
//     //     return UInt8((self >> 8) & 0xFF)
//     // }
//     // 
//     // var blueComponent: UInt8 {
//     //     return UInt8(self & 0xFF)
//     // }
//     
//     // TODO: Implement withAlpha() method
//     // func withAlpha(_ alpha: UInt8) -> UInt32 {
//     //     return (UInt32(alpha) << 24) | (self & 0x00FFFFFF)
//     // }
//     
//     // TODO: Implement withRed() method
//     // func withRed(_ red: UInt8) -> UInt32 {
//     //     return (self & 0xFF00FFFF) | (UInt32(red) << 16)
//     // }
//     
//     // TODO: Implement withGreen() method
//     // func withGreen(_ green: UInt8) -> UInt32 {
//     //     return (self & 0xFFFF00FF) | (UInt32(green) << 8)
//     // }
//     
//     // TODO: Implement withBlue() method
//     // func withBlue(_ blue: UInt8) -> UInt32 {
//     //     return (self & 0xFFFFFF00) | UInt32(blue)
//     // }
//     
//     // MARK: - Color Creation
//     
//     // TODO: Implement static color creation methods
//     // static func argb(alpha: UInt8, red: UInt8, green: UInt8, blue: UInt8) -> UInt32 {
//     //     return (UInt32(alpha) << 24) | (UInt32(red) << 16) | (UInt32(green) << 8) | UInt32(blue)
//     // }
//     // 
//     // static func rgb(red: UInt8, green: UInt8, blue: UInt8) -> UInt32 {
//     //     return argb(alpha: 255, red: red, green: green, blue: blue)
//     // }
//     
//     // TODO: Implement hex string conversion
//     // var hexString: String {
//     //     return String(format: "#%08X", self)
//     // }
// }

// TODO: Define color constants for the overlay
// struct OverlayColorConstants {
//     // Background colors
//     static let defaultBackground: UInt32 = 0x20000000  // Semi-transparent black
//     static let lightBackground: UInt32 = 0x40FFFFFF    // Semi-transparent white
//     static let darkBackground: UInt32 = 0x60000000     // More opaque black
//     
//     // Text colors
//     static let defaultText: UInt32 = 0xFFFFFFFF        // White
//     static let darkText: UInt32 = 0xFF000000           // Black
//     static let mutedText: UInt32 = 0xFF888888          // Gray
//     
//     // Event colors (Google Calendar inspired)
//     static let eventBlue: UInt32 = 0xFF4285F4          // Google Blue
//     static let eventGreen: UInt32 = 0xFF34A853         // Google Green
//     static let eventYellow: UInt32 = 0xFFFBBC05        // Google Yellow
//     static let eventRed: UInt32 = 0xFFEA4335           // Google Red
//     static let eventPurple: UInt32 = 0xFFA142F4        // Purple
//     static let eventOrange: UInt32 = 0xFFFF9800        // Orange
//     
//     // Status colors
//     static let success: UInt32 = 0xFF4CAF50            // Green
//     static let warning: UInt32 = 0xFFFFC107           // Amber
//     static let error: UInt32 = 0xFFF44336             // Red
//     static let info: UInt32 = 0xFF2196F3              // Blue
// }