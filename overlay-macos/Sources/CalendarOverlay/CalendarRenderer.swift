/*
CalendarRenderer.swift - Core Graphics rendering engine for calendar events

This file implements the rendering engine that draws calendar events using Core Graphics.
It's the macOS equivalent of the Windows CalendarRenderer class using Direct2D.

IMPLEMENTATION NOTES:
1. Manage rendering state and resources
2. Use Core Graphics for all drawing operations
3. Cache rendering resources (colors, fonts, etc.)
4. Handle Retina display scaling
5. Coordinate with OverlayView for actual drawing

WINDOWS EQUIVALENT: CalendarRenderer class in calendar_render.h/cpp

KEY macOS APIS:
- Core Graphics (CGContext): 2D drawing context
- Core Text: Advanced text rendering (optional, can use NSAttributedString)
- NSFont: Font management
- NSColor: Color management
- NSAttributedString: Styled text rendering

RENDERING EQUIVALENTS:
Windows Direct2D -> macOS Core Graphics:
- ID2D1RenderTarget -> CGContext
- ID2D1SolidColorBrush -> NSColor with setFill()/setStroke()
- IDWriteTextFormat -> NSAttributedString with font attributes
- D2D1_SIZE_F -> CGSize
- D2D1_COLOR_F -> NSColor or CGColor

ADD HERE:
1. Import Foundation and AppKit
2. Define CalendarRenderer class
3. Implement rendering resource management
4. Create drawing methods for events, background, text
5. Handle Retina display scaling
6. Manage event data and layout calculations
*/

// TODO: Add imports for Foundation and AppKit
// import CoreGraphics may also be needed

// TODO: Define CalendarRenderer class
// class CalendarRenderer {
//     
//     // TODO: Add properties
//     // var events: [CalendarEvent] = []
//     // var config: OverlayConfig
//     // 
//     // // Rendering resources
//     // var textColor: NSColor = .white
//     // var backgroundColor: NSColor = NSColor(white: 0.1, alpha: 0.8)
//     // var eventColors: [NSColor] = []
//     // 
//     // // Font resources
//     // var titleFont: NSFont = NSFont.systemFont(ofSize: 14, weight: .medium)
//     // var dateFont: NSFont = NSFont.systemFont(ofSize: 18, weight: .bold)
//     // var timeFont: NSFont = NSFont.systemFont(ofSize: 12, weight: .regular)
//     // 
//     // // Layout calculations
//     // var padding: CGFloat = 10.0
//     // var eventHeight: CGFloat = 60.0
//     // var dateHeaderHeight: CGFloat = 40.0
//     // 
//     // // Retina display support
//     // var scaleFactor: CGFloat = 1.0
//     // var isRetinaDisplay: Bool = false
//     
//     // TODO: Initialize with configuration
//     // init(config: OverlayConfig) {
//     //     self.config = config
//     //     updateScaleFactor()
//     //     createRenderingResources()
//     // }
//     
//     // MARK: - Resource Management
//     
//     // TODO: Implement createRenderingResources()
//     // func createRenderingResources() {
//     //     // Create colors from config
//     //     textColor = colorFromUInt32(config.textColor)
//     //     backgroundColor = colorFromUInt32(config.backgroundColor)
//     //     
//     //     // Create default event colors
//     //     eventColors = [
//     //         NSColor.systemBlue,
//     //         NSColor.systemGreen,
//     //         NSColor.systemOrange,
//     //         NSColor.systemPurple,
//     //         NSColor.systemRed
//     //     ]
//     //     
//     //     // Scale fonts for current display
//     //     updateFontsForScale()
//     // }
//     
//     // TODO: Implement updateScaleFactor()
//     // func updateScaleFactor() {
//     //     // Get scale factor from main screen
//     //     if let screen = NSScreen.main {
//     //         scaleFactor = screen.backingScaleFactor
//     //         isRetinaDisplay = scaleFactor >= 2.0
//     //     }
//     // }
//     
//     // TODO: Implement updateFontsForScale()
//     // func updateFontsForScale() {
//     //     // Scale fonts based on config and display scale
//     //     let baseFontSize = CGFloat(config.fontSize)
//     //     let scaledFontSize = baseFontSize * scaleFactor
//     //     
//     //     titleFont = NSFont.systemFont(ofSize: scaledFontSize, weight: .medium)
//     //     dateFont = NSFont.systemFont(ofSize: scaledFontSize + 4, weight: .bold)
//     //     timeFont = NSFont.systemFont(ofSize: scaledFontSize - 2, weight: .regular)
//     // }
//     
//     // MARK: - Color Utilities
//     
//     // TODO: Implement colorFromUInt32()
//     // func colorFromUInt32(_ value: UInt32) -> NSColor {
//     //     // Convert Windows-style 0xAARRGGBB to NSColor
//     //     let alpha = CGFloat((value >> 24) & 0xFF) / 255.0
//     //     let red = CGFloat((value >> 16) & 0xFF) / 255.0
//     //     let green = CGFloat((value >> 8) & 0xFF) / 255.0
//     //     let blue = CGFloat(value & 0xFF) / 255.0
//     //     
//     //     return NSColor(calibratedRed: red, green: green, blue: blue, alpha: alpha)
//     // }
//     
//     // TODO: Implement colorForEvent()
//     // func colorForEvent(_ event: CalendarEvent, index: Int) -> NSColor {
//     //     // Use event's custom color if available, otherwise use default color based on index
//     //     if event.colorR != 0 || event.colorG != 0 || event.colorB != 0 {
//     //         return NSColor(calibratedRed: CGFloat(event.colorR)/255.0,
//     //                        green: CGFloat(event.colorG)/255.0,
//     //                        blue: CGFloat(event.colorB)/255.0,
//     //                        alpha: 0.7)
//     //     }
//     //     
//     //     // Use default color based on event index
//     //     let colorIndex = index % eventColors.count
//     //     return eventColors[colorIndex].withAlphaComponent(0.7)
//     // }
//     
//     // MARK: - Event Management
//     
//     // TODO: Implement setEvents()
//     // func setEvents(_ newEvents: [CalendarEvent]) {
//     //     events = newEvents
//     // }
//     
//     // TODO: Implement getEvents()
//     // func getEvents() -> [CalendarEvent] {
//     //     return events
//     // }
//     
//     // TODO: Implement eventAt(point:) for click detection
//     // func eventAt(point: CGPoint, in rect: CGRect) -> CalendarEvent? {
//     //     // Calculate which event is at the given point
//     //     // This should match the layout logic in OverlayView
//     //     
//     //     let padding: CGFloat = 10.0
//     //     let eventHeight: CGFloat = 60.0
//     //     var yPosition = rect.height - padding - 40.0
//     //     
//     //     for (index, event) in events.enumerated() {
//     //         let eventRect = CGRect(x: padding, 
//     //                                y: yPosition - eventHeight,
//     //                                width: rect.width - padding * 2,
//     //                                height: eventHeight)
//     //         
//     //         if eventRect.contains(point) {
//     //             return event
//     //         }
//     //         
//     //         yPosition -= eventHeight + padding
//     //         if yPosition < padding {
//     //             break
//     //         }
//     //     }
//     //     
//     //     return nil
//     // }
//     
//     // MARK: - Drawing Methods (called by OverlayView)
//     
//     // TODO: Implement drawBackground(in:context:)
//     // func drawBackground(in rect: CGRect, context: CGContext) {
//     //     // Draw semi-transparent rounded background
//     //     backgroundColor.setFill()
//     //     
//     //     let backgroundRect = rect.insetBy(dx: padding, dy: padding)
//     //     let path = NSBezierPath(roundedRect: backgroundRect, xRadius: 12, yRadius: 12)
//     //     path.fill()
//     // }
//     
//     // TODO: Implement drawDateHeader(in:context:)
//     // func drawDateHeader(in rect: CGRect, context: CGContext) {
//     //     // Format current date
//     //     let dateFormatter = DateFormatter()
//     //     dateFormatter.dateFormat = "EEEE, MMMM d"
//     //     let dateString = dateFormatter.string(from: Date())
//     //     
//     //     // Create attributed string
//     //     let attributes: [NSAttributedString.Key: Any] = [
//     //         .font: dateFont,
//     //         .foregroundColor: textColor
//     //     ]
//     //     let attributedString = NSAttributedString(string: dateString, attributes: attributes)
//     //     
//     //     // Draw at top of rect
//     //     let drawPoint = NSPoint(x: padding * 2, y: rect.height - dateHeaderHeight)
//     //     attributedString.draw(at: drawPoint)
//     // }
//     
//     // TODO: Implement drawEvent(_:at:in:context:)
//     // func drawEvent(_ event: CalendarEvent, at index: Int, in rect: CGRect, context: CGContext) -> CGRect {
//     //     // Calculate event position
//     //     let yPosition = calculateEventYPosition(at: index, in: rect)
//     //     let eventRect = CGRect(x: padding * 2,
//     //                            y: yPosition,
//     //                            width: rect.width - padding * 4,
//     //                            height: eventHeight)
//     //     
//     //     // Draw event background
//     //     let eventColor = colorForEvent(event, index: index)
//     //     eventColor.setFill()
//     //     
//     //     let path = NSBezierPath(roundedRect: eventRect, xRadius: 8, yRadius: 8)
//     //     path.fill()
//     //     
//     //     // Draw event text
//     //     drawEventText(event, in: eventRect)
//     //     
//     //     return eventRect
//     // }
//     
//     // TODO: Implement calculateEventYPosition()
//     // func calculateEventYPosition(at index: Int, in rect: CGRect) -> CGFloat {
//     //     // Calculate Y position based on index
//     //     let startY = rect.height - dateHeaderHeight - padding * 2
//     //     let position = startY - (CGFloat(index) * (eventHeight + padding))
//     //     return position
//     // }
//     
//     // TODO: Implement drawEventText()
//     // func drawEventText(_ event: CalendarEvent, in rect: CGRect) {
//     //     // Format event time
//     //     let timeFormatter = DateFormatter()
//     //     timeFormatter.dateFormat = "h:mm a"
//     //     
//     //     let startDate = Date(timeIntervalSince1970: TimeInterval(event.startTime))
//     //     let endDate = Date(timeIntervalSince1970: TimeInterval(event.endTime))
//     //     
//     //     let timeString = "\(timeFormatter.string(from: startDate)) - \(timeFormatter.string(from: endDate))"
//     //     
//     //     // Draw title
//     //     let titleAttributes: [NSAttributedString.Key: Any] = [
//     //         .font: titleFont,
//     //         .foregroundColor: textColor
//     //     ]
//     //     let title = NSAttributedString(string: event.title, attributes: titleAttributes)
//     //     title.draw(in: CGRect(x: rect.origin.x + 10, y: rect.origin.y + 30,
//     //                           width: rect.width - 20, height: 20))
//     //     
//     //     // Draw time
//     //     let timeAttributes: [NSAttributedString.Key: Any] = [
//     //         .font: timeFont,
//     //         .foregroundColor: textColor.withAlphaComponent(0.8)
//     //     ]
//     //     let time = NSAttributedString(string: timeString, attributes: timeAttributes)
//     //     time.draw(in: CGRect(x: rect.origin.x + 10, y: rect.origin.y + 10,
//     //                          width: rect.width - 20, height: 15))
//     // }
//     
//     // TODO: Implement drawCurrentTimeIndicator()
//     // func drawCurrentTimeIndicator(in rect: CGRect, context: CGContext) {
//     //     // Optional: Draw current time line similar to Windows version
//     //     // Calculate position based on current time
//     //     // Draw a line across the calendar
//     // }
//     
//     // MARK: - Configuration Updates
//     
//     // TODO: Implement setConfig()
//     // func setConfig(_ newConfig: OverlayConfig) {
//     //     config = newConfig
//     //     createRenderingResources() // Recreate resources with new config
//     // }
//     
//     // TODO: Implement setOpacity()
//     // func setOpacity(_ opacity: Float) {
//     //     config.opacity = opacity
//     //     // Update background color alpha
//     //     backgroundColor = backgroundColor.withAlphaComponent(CGFloat(opacity))
//     // }
// }

// TODO: Add Date extension for formatting if needed
// extension Date {
//     func formattedString(format: String) -> String {
//         let formatter = DateFormatter()
//         formatter.dateFormat = format
//         return formatter.string(from: self)
//     }
// }