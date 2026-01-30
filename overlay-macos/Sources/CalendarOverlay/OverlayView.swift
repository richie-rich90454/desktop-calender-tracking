/*
OverlayView.swift - Custom NSView for rendering calendar events and handling clicks

This file implements the custom view that draws calendar events and handles mouse interactions.
It's the macOS equivalent of the rendering and click detection logic in the Windows version.

IMPLEMENTATION NOTES:
1. Subclass NSView for custom drawing
2. Use Core Graphics for rendering (equivalent to Windows Direct2D)
3. Track event positions for click detection
4. Handle mouse events for interacting with calendar events
5. Coordinate with CalendarRenderer for actual drawing

WINDOWS EQUIVALENT: Combined functionality from DesktopWindow and CalendarRenderer classes

KEY macOS APIS:
- NSView: Base view class for drawing and event handling
- draw(_ dirtyRect:): Main drawing method using Core Graphics
- mouseDown(with:), mouseUp(with:): Mouse event handling
- NSTrackingArea: For mouse movement tracking
- Core Graphics: For 2D drawing (CGContext, NSBezierPath, etc.)

RENDERING EQUIVALENTS:
Windows: Direct2D (ID2D1RenderTarget, ID2D1SolidColorBrush, IDWriteTextFormat)
macOS: Core Graphics (CGContext, NSBezierPath, NSAttributedString, NSFont)

ADD HERE:
1. Import Foundation and AppKit
2. Define OverlayView class inheriting from NSView
3. Implement drawing with Core Graphics
4. Track event positions for click detection
5. Handle mouse events
6. Coordinate with parent window and CalendarRenderer
*/

// TODO: Add imports for Foundation and AppKit

// TODO: Define OverlayView class
// class OverlayView: NSView {
//     
//     // TODO: Add properties
//     // weak var window: OverlayWindow?
//     // var calendarRenderer: CalendarRenderer?
//     // 
//     // // Track event positions for click detection
//     // var eventRects: [CGRect] = []
//     // var eventIDs: [Int] = []
//     // 
//     // // Drawing state
//     // var needsRedraw = true
//     // var lastDrawTime: Date = Date()
//     
//     // TODO: Initialize view
//     // override init(frame frameRect: NSRect) {
//     //     super.init(frame: frameRect)
//     //     commonInit()
//     // }
//     // 
//     // required init?(coder: NSCoder) {
//     //     super.init(coder: coder)
//     //     commonInit()
//     // }
//     // 
//     // func commonInit() {
//     //     // Set up view properties
//     //     self.wantsLayer = true
//     //     self.layer?.isOpaque = false
//     //     self.layer?.backgroundColor = NSColor.clear.cgColor
//     //     
//     //     // Set up tracking area for mouse events
//     //     setupTrackingArea()
//     // }
//     
//     // MARK: - Drawing
//     
//     // TODO: Override draw(_ dirtyRect:)
//     // override func draw(_ dirtyRect: NSRect) {
//     //     super.draw(dirtyRect)
//     //     
//     //     // Get current graphics context
//     //     guard let context = NSGraphicsContext.current?.cgContext else { return }
//     //     
//     //     // Clear to transparent
//     //     context.clear(bounds)
//     //     
//     //     // Save graphics state
//     //     context.saveGState()
//     //     
//     //     // Draw background with transparency
//     //     drawBackground(in: context)
//     //     
//     //     // Draw calendar events
//     //     drawEvents(in: context)
//     //     
//     //     // Draw date header
//     //     drawDateHeader(in: context)
//     //     
//     //     // Draw current time indicator
//     //     drawCurrentTime(in: context)
//     //     
//     //     // Restore graphics state
//     //     context.restoreGState()
//     //     
//     //     lastDrawTime = Date()
//     // }
//     
//     // TODO: Implement drawBackground(in:)
//     // func drawBackground(in context: CGContext) {
//     //     // Draw semi-transparent background
//     //     // Use config.backgroundColor with alpha
//     //     // Equivalent to Windows: renderTarget->FillRectangle()
//     //     
//     //     // Example:
//     //     // let backgroundColor = NSColor(calibratedRed: 0.1, green: 0.1, blue: 0.1, alpha: 0.8)
//     //     // backgroundColor.setFill()
//     //     // bounds.fill()
//     // }
//     
//     // TODO: Implement drawEvents(in:)
//     // func drawEvents(in context: CGContext) {
//     //     // Clear previous event positions
//     //     eventRects.removeAll()
//     //     eventIDs.removeAll()
//     //     
//     //     // Get events from calendarRenderer
//     //     guard let events = calendarRenderer?.getEvents() else { return }
//     //     
//     //     // Calculate layout
//     //     let padding: CGFloat = 10.0
//     //     let eventHeight: CGFloat = 60.0
//     //     var yPosition = bounds.height - padding - 40.0 // Start below date header
//     //     
//     //     for (index, event) in events.enumerated() {
//     //         // Calculate event rect
//     //         let eventRect = CGRect(x: padding, 
//     //                                y: yPosition - eventHeight,
//     //                                width: bounds.width - padding * 2,
//     //                                height: eventHeight)
//     //         
//     //         // Store for click detection
//     //         eventRects.append(eventRect)
//     //         eventIDs.append(index)
//     //         
//     //         // Draw event background
//     //         drawEventBackground(event: event, rect: eventRect, in: context)
//     //         
//     //         // Draw event text
//     //         drawEventText(event: event, rect: eventRect, in: context)
//     //         
//     //         // Update y position for next event
//     //         yPosition -= eventHeight + padding
//     //         
//     //         // Stop if we run out of space
//     //         if yPosition < padding {
//     //             break
//     //         }
//     //     }
//     // }
//     
//     // TODO: Implement drawEventBackground()
//     // func drawEventBackground(event: CalendarEvent, rect: CGRect, in context: CGContext) {
//     //     // Draw rounded rect background for event
//     //     // Use event color from CalendarEvent
//     //     // Equivalent to Windows: renderTarget->FillRoundedRectangle()
//     //     
//     //     // Example:
//     //     // let eventColor = NSColor(calibratedRed: CGFloat(event.colorR)/255.0,
//     //     //                          green: CGFloat(event.colorG)/255.0,
//     //     //                          blue: CGFloat(event.colorB)/255.0,
//     //     //                          alpha: 0.7)
//     //     // eventColor.setFill()
//     //     // let path = NSBezierPath(roundedRect: rect, xRadius: 8, yRadius: 8)
//     //     // path.fill()
//     // }
//     
//     // TODO: Implement drawEventText()
//     // func drawEventText(event: CalendarEvent, rect: CGRect, in context: CGContext) {
//     //     // Draw event title and time
//     //     // Use NSAttributedString for styled text
//     //     // Equivalent to Windows: renderTarget->DrawTextLayout()
//     //     
//     //     // Example:
//     //     // let titleAttributes: [NSAttributedString.Key: Any] = [
//     //     //     .font: NSFont.systemFont(ofSize: 14, weight: .medium),
//     //     //     .foregroundColor: NSColor.white
//     //     // ]
//     //     // let title = NSAttributedString(string: event.title, attributes: titleAttributes)
//     //     // title.draw(in: CGRect(x: rect.origin.x + 10, y: rect.origin.y + 30, 
//     //     //                       width: rect.width - 20, height: 20))
//     // }
//     
//     // TODO: Implement drawDateHeader()
//     // func drawDateHeader(in context: CGContext) {
//     //     // Draw current date at top of view
//     //     // Use NSDateFormatter for localized date formatting
//     //     
//     //     // Example:
//     //     // let dateFormatter = DateFormatter()
//     //     // dateFormatter.dateFormat = "EEEE, MMMM d"
//     //     // let dateString = dateFormatter.string(from: Date())
//     //     // 
//     //     // let attributes: [NSAttributedString.Key: Any] = [
//     //     //     .font: NSFont.systemFont(ofSize: 18, weight: .bold),
//     //     //     .foregroundColor: NSColor.white
//     //     // ]
//     //     // let attributedString = NSAttributedString(string: dateString, attributes: attributes)
//     //     // attributedString.draw(at: NSPoint(x: 10, y: bounds.height - 30))
//     // }
//     
//     // TODO: Implement drawCurrentTime()
//     // func drawCurrentTime(in context: CGContext) {
//     //     // Draw current time indicator if needed
//     //     // Optional feature similar to Windows version
//     // }
//     
//     // MARK: - Mouse Event Handling
//     
//     // TODO: Implement setupTrackingArea()
//     // func setupTrackingArea() {
//     //     let trackingArea = NSTrackingArea(
//     //         rect: bounds,
//     //         options: [.mouseMoved, .activeAlways, .inVisibleRect],
//     //         owner: self,
//     //         userInfo: nil
//     //     )
//     //     addTrackingArea(trackingArea)
//     // }
//     
//     // TODO: Override updateTrackingAreas()
//     // override func updateTrackingAreas() {
//     //     super.updateTrackingAreas()
//     //     
//     //     // Remove old tracking areas
//     //     for trackingArea in trackingAreas {
//     //         removeTrackingArea(trackingArea)
//     //     }
//     //     
//     //     // Add new tracking area
//     //     setupTrackingArea()
//     // }
//     
//     // TODO: Override mouseDown(with:)
//     // override func mouseDown(with event: NSEvent) {
//     //     let point = convert(event.locationInWindow, from: nil)
//     //     
//     //     // Check if an event was clicked
//     //     for (index, eventRect) in eventRects.enumerated() {
//     //         if eventRect.contains(point) {
//     //             // Event clicked - launch Java GUI
//     //             JavaLauncher.launchJavaGUI()
//     //             return
//     //         }
//     //     }
//     //     
//     //     // If no event was clicked, pass to window for dragging
//     //     window?.mouseDown(with: event)
//     // }
//     
//     // TODO: Implement eventAt(point:) for click detection
//     // func eventAt(point: NSPoint) -> CalendarEvent? {
//     //     for (index, eventRect) in eventRects.enumerated() {
//     //         if eventRect.contains(point) {
//     //             // Return the corresponding event
//     //             return calendarRenderer?.getEvents()[safe: index]
//     //         }
//     //     }
//     //     return nil
//     // }
//     
//     // MARK: - View Updates
//     
//     // TODO: Override viewDidMoveToWindow()
//     // override func viewDidMoveToWindow() {
//     //     super.viewDidMoveToWindow()
//     //     
//     //     // Update tracking areas when view moves to new window
//     //     updateTrackingAreas()
//     // }
//     
//     // TODO: Override viewDidEndLiveResize()
//     // override func viewDidEndLiveResize() {
//     //     super.viewDidEndLiveResize()
//     //     
//     //     // Redraw after resize
//     //     setNeedsDisplay(bounds)
//     // }
// }

// TODO: Add Array extension for safe indexing
// extension Array {
//     subscript(safe index: Index) -> Element? {
//         return indices.contains(index) ? self[index] : nil
//     }
// }