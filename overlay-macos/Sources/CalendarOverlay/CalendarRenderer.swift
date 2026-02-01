/*
 * Core Graphics rendering engine for calendar events.
 *
 * Responsibilities:
 * - Render calendar events using Core Graphics
 * - Manage rendering resources (colors, fonts, etc.)
 * - Calculate event layout and positioning
 * - Handle Retina display scaling
 * - Coordinate with OverlayView for actual drawing
 *
 * Swift data types used:
 * - CGContext for drawing context
 * - NSFont for font management
 * - NSColor for color management
 * - NSAttributedString for styled text
 * - CGRect for layout rectangles
 *
 * Swift technologies involved:
 * - Core Graphics for 2D drawing
 * - Core Text for advanced text rendering
 * - Auto Layout calculations
 * - Retina display support
 *
 * Design intent:
 * This class provides a macOS-native rendering engine equivalent to
 * Windows Direct2D, ensuring visual consistency across platforms.
 */

import AppKit

class CalendarRenderer {
    
    // MARK: - Properties
    
    var events: [CalendarEvent] = []
    var config: OverlayConfig
    
    // Rendering resources
    var textColor: NSColor = .white
    var backgroundColor: NSColor = NSColor(argb: 0x20000000)
    var eventColors: [NSColor] = []
    
    // Font resources
    var titleFont: NSFont = NSFont.systemFont(ofSize: 14, weight: .medium)
    var dateFont: NSFont = NSFont.systemFont(ofSize: 18, weight: .bold)
    var timeFont: NSFont = NSFont.systemFont(ofSize: 12, weight: .regular)
    
    // Layout calculations
    var padding: CGFloat = 10.0
    var eventHeight: CGFloat = 60.0
    var dateHeaderHeight: CGFloat = 40.0
    
    // Retina display support
    var scaleFactor: CGFloat = 1.0
    var isRetinaDisplay: Bool = false
    
    // MARK: - Initialization
    
    init(config: OverlayConfig) {
        self.config = config
        updateScaleFactor()
        createRenderingResources()
    }
    
    // MARK: - Resource Management
    
    func createRenderingResources() {
        // Create colors from config
        textColor = NSColor(argb: config.textColor)
        backgroundColor = NSColor(argb: config.backgroundColor)
        
        // Create default event colors
        eventColors = NSColor.defaultEventColors
        
        // Scale fonts for current display
        updateFontsForScale()
    }
    
    func updateScaleFactor() {
        // Get scale factor from main screen
        if let screen = NSScreen.main {
            scaleFactor = screen.backingScaleFactor
            isRetinaDisplay = scaleFactor >= 2.0
        }
    }
    
    func updateFontsForScale() {
        // Scale fonts based on config and display scale
        let baseFontSize = CGFloat(config.fontSize)
        let scaledFontSize = baseFontSize * scaleFactor
        
        titleFont = NSFont.systemFont(ofSize: scaledFontSize, weight: .medium)
        dateFont = NSFont.systemFont(ofSize: scaledFontSize + 4, weight: .bold)
        timeFont = NSFont.systemFont(ofSize: scaledFontSize - 2, weight: .regular)
    }
    
    // MARK: - Color Utilities
    
    func colorForEvent(_ event: CalendarEvent, index: Int) -> NSColor {
        // Use event's custom color if available
        if event.colorR != 0 || event.colorG != 0 || event.colorB != 0 {
            return NSColor(
                r: event.colorR,
                g: event.colorG,
                b: event.colorB,
                a: 180 // 70% opacity
            )
        }
        
        // Use default color based on event index
        let colorIndex = index % eventColors.count
        return eventColors[colorIndex].withAlphaComponent(0.7)
    }
    
    // MARK: - Event Management
    
    func setEvents(_ newEvents: [CalendarEvent]) {
        events = newEvents
    }
    
    func getEvents() -> [CalendarEvent] {
        return events
    }
    
    func eventAt(point: CGPoint, in rect: CGRect) -> CalendarEvent? {
        // Calculate which event is at the given point
        // This should match the layout logic in OverlayView
        
        var yPosition = rect.height - padding - dateHeaderHeight
        
        for (index, event) in events.enumerated() {
            let eventRect = CGRect(
                x: padding,
                y: yPosition - eventHeight,
                width: rect.width - padding * 2,
                height: eventHeight
            )
            
            if eventRect.contains(point) {
                return event
            }
            
            yPosition -= eventHeight + padding
            if yPosition < padding {
                break
            }
        }
        
        return nil
    }
    
    // MARK: - Drawing Methods
    
    func drawBackground(in rect: CGRect, context: CGContext) {
        // Draw semi-transparent rounded background
        context.saveGState()
        
        backgroundColor.setFill()
        
        let backgroundRect = rect.insetBy(dx: padding, dy: padding)
        let path = NSBezierPath(roundedRect: backgroundRect, xRadius: 12, yRadius: 12)
        path.fill()
        
        context.restoreGState()
    }
    
    func drawDateHeader(in rect: CGRect, context: CGContext) {
        // Format current date
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "EEEE, MMMM d"
        let dateString = dateFormatter.string(from: Date())
        
        // Create attributed string
        let attributes: [NSAttributedString.Key: Any] = [
            .font: dateFont,
            .foregroundColor: textColor
        ]
        let attributedString = NSAttributedString(string: dateString, attributes: attributes)
        
        // Draw at top of rect
        let drawPoint = NSPoint(x: padding * 2, y: rect.height - dateHeaderHeight)
        attributedString.draw(at: drawPoint)
    }
    
    func drawEvent(_ event: CalendarEvent, at index: Int, in rect: CGRect, context: CGContext) -> CGRect {
        // Calculate event position
        let yPosition = calculateEventYPosition(at: index, in: rect)
        let eventRect = CGRect(
            x: padding * 2,
            y: yPosition,
            width: rect.width - padding * 4,
            height: eventHeight
        )
        
        // Draw event background
        context.saveGState()
        
        let eventColor = colorForEvent(event, index: index)
        eventColor.setFill()
        
        let path = NSBezierPath(roundedRect: eventRect, xRadius: 8, yRadius: 8)
        path.fill()
        
        context.restoreGState()
        
        // Draw event text
        drawEventText(event, in: eventRect)
        
        return eventRect
    }
    
    func calculateEventYPosition(at index: Int, in rect: CGRect) -> CGFloat {
        // Calculate Y position based on index
        let startY = rect.height - dateHeaderHeight - padding * 2
        let position = startY - (CGFloat(index) * (eventHeight + padding))
        return position
    }
    
    func drawEventText(_ event: CalendarEvent, in rect: CGRect) {
        // Format event time
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "h:mm a"
        
        let startDate = Date(timeIntervalSince1970: TimeInterval(event.startTime))
        let endDate = Date(timeIntervalSince1970: TimeInterval(event.endTime))
        
        let timeString = "\(timeFormatter.string(from: startDate)) - \(timeFormatter.string(from: endDate))"
        
        // Draw title
        let titleAttributes: [NSAttributedString.Key: Any] = [
            .font: titleFont,
            .foregroundColor: textColor
        ]
        let title = NSAttributedString(string: event.title, attributes: titleAttributes)
        title.draw(in: CGRect(
            x: rect.origin.x + 10,
            y: rect.origin.y + 30,
            width: rect.width - 20,
            height: 20
        ))
        
        // Draw time
        let timeAttributes: [NSAttributedString.Key: Any] = [
            .font: timeFont,
            .foregroundColor: textColor.withAlphaComponent(0.8)
        ]
        let time = NSAttributedString(string: timeString, attributes: timeAttributes)
        time.draw(in: CGRect(
            x: rect.origin.x + 10,
            y: rect.origin.y + 10,
            width: rect.width - 20,
            height: 15
        ))
        
        // Draw description if available
        if !event.description.isEmpty {
            let descAttributes: [NSAttributedString.Key: Any] = [
                .font: timeFont.withSize(timeFont.pointSize - 1),
                .foregroundColor: textColor.withAlphaComponent(0.7)
            ]
            let description = NSAttributedString(string: event.description, attributes: descAttributes)
            let descRect = CGRect(
                x: rect.origin.x + 10,
                y: rect.origin.y + 50,
                width: rect.width - 20,
                height: 15
            )
            description.draw(in: descRect)
        }
    }
    
    func drawCurrentTimeIndicator(in rect: CGRect, context: CGContext) {
        // Draw current time line if enabled in config
        guard config.showPastEvents else { return }
        
        let calendar = Calendar.current
        let now = Date()
        let components = calendar.dateComponents([.hour, .minute], from: now)
        
        guard let hour = components.hour, let minute = components.minute else { return }
        
        // Calculate position based on time of day
        let totalMinutes = CGFloat(hour * 60 + minute)
        let dayProgress = totalMinutes / (24 * 60)
        
        let indicatorY = rect.height - dateHeaderHeight - padding - (rect.height - dateHeaderHeight - padding * 2) * dayProgress
        
        context.saveGState()
        
        // Draw indicator line
        NSColor.systemRed.withAlphaComponent(0.7).setStroke()
        context.setLineWidth(2.0)
        
        let startPoint = CGPoint(x: padding * 2, y: indicatorY)
        let endPoint = CGPoint(x: rect.width - padding * 2, y: indicatorY)
        
        context.move(to: startPoint)
        context.addLine(to: endPoint)
        context.strokePath()
        
        // Draw indicator circle
        let circleRect = CGRect(x: padding, y: indicatorY - 4, width: 8, height: 8)
        let circlePath = NSBezierPath(ovalIn: circleRect)
        NSColor.systemRed.setFill()
        circlePath.fill()
        
        context.restoreGState()
    }
    
    func drawAllEvents(in rect: CGRect, context: CGContext) -> [CGRect] {
        var eventRects: [CGRect] = []
        
        // Draw background
        drawBackground(in: rect, context: context)
        
        // Draw date header
        drawDateHeader(in: rect, context: context)
        
        // Draw current time indicator
        drawCurrentTimeIndicator(in: rect, context: context)
        
        // Draw events
        for (index, event) in events.enumerated() {
            let eventRect = drawEvent(event, at: index, in: rect, context: context)
            eventRects.append(eventRect)
            
            // Stop if we run out of space
            if calculateEventYPosition(at: index + 1, in: rect) < padding {
                break
            }
        }
        
        return eventRects
    }
    
    // MARK: - Configuration Updates
    
    func setConfig(_ newConfig: OverlayConfig) {
        config = newConfig
        createRenderingResources() // Recreate resources with new config
    }
    
    func setOpacity(_ opacity: Float) {
        config.opacity = opacity
        // Update background color alpha
        backgroundColor = backgroundColor.withAlphaComponent(CGFloat(opacity))
    }
    
    // MARK: - Layout Calculations
    
    func calculateRequiredHeight() -> CGFloat {
        let maxEventsToShow = 5 // Maximum events to show before scrolling
        let eventsToShow = min(events.count, maxEventsToShow)
        
        let totalHeight = dateHeaderHeight + // Date header
                         padding * 2 +       // Top and bottom padding
                         (eventHeight + padding) * CGFloat(eventsToShow) // Events
        
        return totalHeight
    }
    
    func calculateOptimalWidth() -> CGFloat {
        // Calculate width based on longest event title
        let maxTitleWidth = events.map { event in
            let size = (event.title as NSString).size(withAttributes: [.font: titleFont])
            return size.width
        }.max() ?? 200
        
        return min(maxTitleWidth + padding * 4, 500) // Cap at 500px
    }
}