/*
 * Custom NSView for rendering calendar events and handling clicks.
 *
 * Responsibilities:
 * - Draw calendar events using Core Graphics
 * - Track event positions for click detection
 * - Handle mouse events for interaction
 * - Coordinate with CalendarRenderer for actual drawing
 * - Manage tracking areas for mouse movement
 *
 * Swift data types used:
 * - NSView for base view functionality
 * - CGRect for event rectangles
 * - NSTrackingArea for mouse tracking
 * - CalendarRenderer for rendering logic
 * - OverlayWindow for window coordination
 *
 * Swift technologies involved:
 * - Core Graphics for custom drawing
 * - Mouse event handling
 * - Tracking areas for hover effects
 * - View lifecycle management
 *
 * Design intent:
 * This view separates rendering logic from window management,
 * providing a clean architecture for the overlay display.
 */

import AppKit

class OverlayView: NSView {
    
    // MARK: - Properties
    
    weak var window: OverlayWindow?
    var calendarRenderer: CalendarRenderer?
    
    // Track event positions for click detection
    private var eventRects: [CGRect] = []
    private var eventIDs: [Int] = []
    
    // Drawing state
    private var needsRedraw = true
    private var lastDrawTime: Date = Date()
    
    // Tracking area
    private var trackingArea: NSTrackingArea?
    
    // MARK: - Initialization
    
    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        commonInit()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        commonInit()
    }
    
    private func commonInit() {
        // Set up view properties
        self.wantsLayer = true
        self.layer?.isOpaque = false
        self.layer?.backgroundColor = NSColor.clear.cgColor
        
        // Enable mouse events
        self.acceptsTouchEvents = false
    }
    
    // MARK: - Drawing
    
    override func draw(_ dirtyRect: NSRect) {
        super.draw(dirtyRect)
        
        // Get current graphics context
        guard let context = NSGraphicsContext.current?.cgContext else { return }
        
        // Clear to transparent
        context.clear(bounds)
        
        // Save graphics state
        context.saveGState()
        
        // Draw calendar events if renderer is available
        if let renderer = calendarRenderer {
            eventRects = renderer.drawAllEvents(in: bounds, context: context)
            eventIDs = Array(0..<min(eventRects.count, renderer.getEvents().count))
        } else {
            // Draw placeholder if no renderer
            drawPlaceholder(in: context)
        }
        
        // Restore graphics state
        context.restoreGState()
        
        lastDrawTime = Date()
        needsRedraw = false
    }
    
    private func drawPlaceholder(in context: CGContext) {
        // Draw a simple placeholder when no renderer is available
        let placeholderText = "No Calendar Events"
        let attributes: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 14),
            .foregroundColor: NSColor.white.withAlphaComponent(0.5)
        ]
        
        let attributedString = NSAttributedString(string: placeholderText, attributes: attributes)
        let stringSize = attributedString.size()
        
        let drawPoint = NSPoint(
            x: (bounds.width - stringSize.width) / 2,
            y: (bounds.height - stringSize.height) / 2
        )
        
        attributedString.draw(at: drawPoint)
    }
    
    // MARK: - Mouse Event Handling
    
    override func updateTrackingAreas() {
        super.updateTrackingAreas()
        
        // Remove old tracking area
        if let oldTrackingArea = trackingArea {
            removeTrackingArea(oldTrackingArea)
        }
        
        // Create new tracking area
        trackingArea = NSTrackingArea(
            rect: bounds,
            options: [.mouseEnteredAndExited, .mouseMoved, .activeAlways, .inVisibleRect],
            owner: self,
            userInfo: nil
        )
        
        if let newTrackingArea = trackingArea {
            addTrackingArea(newTrackingArea)
        }
    }
    
    override func mouseDown(with event: NSEvent) {
        let point = convert(event.locationInWindow, from: nil)
        
        // Check if an event was clicked
        if let clickedEvent = eventAt(point: point) {
            // Event clicked - launch Java GUI
            print("Event clicked: \(clickedEvent.title)")
            JavaLauncher.launchJavaGUI()
            return
        }
        
        // If no event was clicked, pass to window for dragging
        window?.mouseDown(with: event)
    }
    
    override func mouseEntered(with event: NSEvent) {
        // Handle mouse entered if needed
        // Could change cursor or show hover effects
        if window?.clickThroughEnabled == true {
            NSCursor.arrow.set()
        }
    }
    
    override func mouseExited(with event: NSEvent) {
        // Handle mouse exited if needed
    }
    
    override func mouseMoved(with event: NSEvent) {
        let point = convert(event.locationInWindow, from: nil)
        
        // Update cursor based on hover state
        if eventAt(point: point) != nil {
            // Hovering over an event
            NSCursor.pointingHand.set()
        } else {
            // Not hovering over an event
            NSCursor.arrow.set()
        }
    }
    
    // MARK: - Event Detection
    
    func eventAt(point: NSPoint) -> CalendarEvent? {
        for (index, eventRect) in eventRects.enumerated() {
            if eventRect.contains(point) {
                // Return the corresponding event
                return calendarRenderer?.getEvents()[safe: eventIDs[index]]
            }
        }
        return nil
    }
    
    func refresh() {
        // Mark view as needing display
        needsRedraw = true
        setNeedsDisplay(bounds)
    }
    
    func updateEvents(_ events: [CalendarEvent]) {
        calendarRenderer?.setEvents(events)
        refresh()
    }
    
    func updateConfig(_ config: OverlayConfig) {
        calendarRenderer?.setConfig(config)
        refresh()
    }
    
    // MARK: - View Lifecycle
    
    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        
        // Update tracking areas when view moves to new window
        updateTrackingAreas()
    }
    
    override func viewDidEndLiveResize() {
        super.viewDidEndLiveResize()
        
        // Redraw after resize
        refresh()
    }
    
    override func setFrameSize(_ newSize: NSSize) {
        super.setFrameSize(newSize)
        
        // Update tracking areas when size changes
        updateTrackingAreas()
    }
    
    // MARK: - Performance Optimization
    
    override var wantsUpdateLayer: Bool {
        return false // We use draw(_:) instead of updateLayer for more control
    }
    
    override func hitTest(_ point: NSPoint) -> NSView? {
        // Only respond to hit tests if click-through is disabled
        if window?.clickThroughEnabled == true {
            return nil
        }
        return super.hitTest(point)
    }
}

// MARK: - Array Extension for Safe Indexing

extension Array {
    subscript(safe index: Index) -> Element? {
        return indices.contains(index) ? self[index] : nil
    }
}