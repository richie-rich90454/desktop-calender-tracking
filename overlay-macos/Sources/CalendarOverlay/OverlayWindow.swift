/*
 * Transparent NSWindow subclass for macOS Calendar Overlay.
 *
 * Responsibilities:
 * - Create and manage transparent, always-on-top window
 * - Handle window positioning, sizing, and dragging
 * - Coordinate with CalendarRenderer and OverlayView
 * - Manage timers for rendering and event updates
 * - Handle mouse events for interaction
 *
 * Swift data types used:
 * - NSWindow for window management
 * - Timer for scheduled updates
 * - CGRect for window geometry
 * - CalendarRenderer for event rendering
 * - EventManager for event data
 *
 * Swift technologies involved:
 * - Window transparency and click-through
 * - Timer scheduling and management
 * - Mouse event handling for dragging
 * - Screen coordinate system conversion
 *
 * Design intent:
 * This window provides the macOS equivalent of Windows DesktopWindow,
 * creating a transparent overlay that displays calendar events.
 */

import AppKit

class OverlayWindow: NSWindow, NSWindowDelegate {
    
    // MARK: - Properties
    
    var overlayView: OverlayView?
    var calendarRenderer: CalendarRenderer?
    var eventManager: EventManager?
    var config: OverlayConfig
    
    // Dragging state
    private var isDragging = false
    private var dragStartPoint: NSPoint = .zero
    private var windowStartFrame: NSRect = .zero
    
    // Window state
    var isVisible = false
    var clickThroughEnabled = false
    var wallpaperMode = false
    
    // Timers
    private var renderTimer: Timer?
    private var updateTimer: Timer?
    
    // MARK: - Initialization
    
    init(config: OverlayConfig) {
        // Calculate initial frame
        let frame = calculateInitialFrame(config: config)
        
        // Create window style mask for borderless, transparent window
        let styleMask: NSWindow.StyleMask = [.borderless]
        
        // Initialize NSWindow
        super.init(contentRect: frame,
                   styleMask: styleMask,
                   backing: .buffered,
                   defer: false)
        
        self.config = config
        configureWindow()
        createOverlayView()
    }
    
    // MARK: - Window Configuration
    
    private func configureWindow() {
        // Set window properties for transparency
        self.isOpaque = false
        self.backgroundColor = .clear
        self.level = .floating  // Always on top (WS_EX_TOPMOST equivalent)
        self.ignoresMouseEvents = config.clickThrough  // WS_EX_TRANSPARENT equivalent
        self.collectionBehavior = [.canJoinAllSpaces, .stationary]
        
        // Set initial alpha value (opacity)
        self.alphaValue = CGFloat(config.opacity)
        
        // Disable window shadow
        self.hasShadow = false
        
        // Set delegate
        self.delegate = self
        
        // Store click-through state
        clickThroughEnabled = config.clickThrough
    }
    
    private func calculateInitialFrame(config: OverlayConfig) -> NSRect {
        // Get screen frame
        guard let screen = NSScreen.main else {
            return NSRect(x: 100, y: 100, width: 400, height: 600)
        }
        
        // Convert from Windows-style coordinates (top-left origin) to macOS (bottom-left origin)
        let screenHeight = screen.frame.height
        let yPos = screenHeight - CGFloat(config.positionY) - CGFloat(config.height)
        
        return NSRect(x: CGFloat(config.positionX),
                      y: yPos,
                      width: CGFloat(config.width),
                      height: CGFloat(config.height))
    }
    
    private func createOverlayView() {
        // Create OverlayView instance
        let view = OverlayView(frame: self.contentView?.bounds ?? .zero)
        view.window = self
        
        // Initialize CalendarRenderer
        calendarRenderer = CalendarRenderer(config: config)
        view.calendarRenderer = calendarRenderer
        
        // Set as content view
        self.contentView = view
        self.overlayView = view
        
        // Initialize EventManager
        eventManager = EventManager()
        _ = eventManager?.initialize()
        
        // Load initial events
        updateEvents()
    }
    
    // MARK: - Window Lifecycle
    
    func show() {
        self.makeKeyAndOrderFront(nil)
        isVisible = true
        
        // Start timers
        startTimers()
        
        print("Overlay window shown at position: \(self.frame)")
    }
    
    func hide() {
        self.orderOut(nil)
        isVisible = false
        
        // Stop timers
        stopTimers()
        
        print("Overlay window hidden")
    }
    
    override func close() {
        stopTimers()
        super.close()
    }
    
    // MARK: - Timer Management
    
    private func startTimers() {
        // Render timer (~60 FPS)
        renderTimer = Timer.scheduledTimer(withTimeInterval: 1.0/60.0, repeats: true) { [weak self] _ in
            self?.overlayView?.refresh()
        }
        
        // Update timer (config.refreshInterval seconds)
        updateTimer = Timer.scheduledTimer(withTimeInterval: TimeInterval(config.refreshInterval), repeats: true) { [weak self] _ in
            self?.updateEvents()
        }
        
        print("Started timers: render=60Hz, update=\(config.refreshInterval)s")
    }
    
    private func stopTimers() {
        renderTimer?.invalidate()
        renderTimer = nil
        updateTimer?.invalidate()
        updateTimer = nil
        
        print("Stopped timers")
    }
    
    // MARK: - Event Management
    
    private func updateEvents() {
        eventManager?.update()
        if let events = eventManager?.getTodayEvents() {
            calendarRenderer?.setEvents(events)
            overlayView?.updateEvents(events)
            
            // Update window size based on events if auto-size is enabled
            if config.wallpaperMode {
                updateWindowSizeForEvents(events)
            }
        }
    }
    
    private func updateWindowSizeForEvents(_ events: [CalendarEvent]) {
        guard let renderer = calendarRenderer else { return }
        
        let requiredHeight = renderer.calculateRequiredHeight()
        let optimalWidth = renderer.calculateOptimalWidth()
        
        var newFrame = self.frame
        newFrame.size.height = requiredHeight
        newFrame.size.width = optimalWidth
        
        // Maintain position (adjust Y coordinate for height change)
        newFrame.origin.y += (self.frame.height - requiredHeight)
        
        self.setFrame(newFrame, display: true, animate: true)
    }
    
    // MARK: - Mouse Event Handling
    
    override func mouseDown(with event: NSEvent) {
        let point = self.contentView?.convert(event.locationInWindow, from: nil) ?? .zero
        
        if wallpaperMode {
            // In wallpaper mode, click launches Java GUI
            print("Wallpaper mode click - launching Java GUI")
            JavaLauncher.launchJavaGUI()
            return
        }
        
        // Check if an event was clicked
        if let clickedEvent = calendarRenderer?.eventAt(point: point, in: overlayView?.bounds ?? .zero) {
            // Event was clicked - launch Java GUI
            print("Event clicked: \(clickedEvent.title) - launching Java GUI")
            JavaLauncher.launchJavaGUI()
            return
        }
        
        // Otherwise, start dragging
        startDragging(with: event)
    }
    
    private func startDragging(with event: NSEvent) {
        isDragging = true
        dragStartPoint = event.locationInWindow
        windowStartFrame = self.frame
        
        // Temporarily disable click-through for dragging
        if clickThroughEnabled {
            self.ignoresMouseEvents = false
        }
        
        print("Started dragging window")
    }
    
    override func mouseDragged(with event: NSEvent) {
        guard isDragging else { return }
        
        let currentPoint = event.locationInWindow
        let deltaX = currentPoint.x - dragStartPoint.x
        let deltaY = currentPoint.y - dragStartPoint.y
        
        // macOS Y coordinate is inverted compared to Windows
        let newFrame = NSRect(x: windowStartFrame.origin.x + deltaX,
                              y: windowStartFrame.origin.y + deltaY,
                              width: windowStartFrame.width,
                              height: windowStartFrame.height)
        
        self.setFrame(newFrame, display: true)
    }
    
    override func mouseUp(with event: NSEvent) {
        if isDragging {
            isDragging = false
            
            // Restore click-through setting
            if clickThroughEnabled {
                self.ignoresMouseEvents = true
            }
            
            // Save new window position
            saveWindowPosition()
            
            print("Stopped dragging window")
        }
    }
    
    // MARK: - Configuration Updates
    
    func setOpacity(_ opacity: Float) {
        config.opacity = opacity
        self.alphaValue = CGFloat(opacity)
        ConfigManager.shared.save(config)
        
        print("Window opacity set to \(opacity)")
    }
    
    func setClickThrough(_ enabled: Bool) {
        config.clickThrough = enabled
        clickThroughEnabled = enabled
        self.ignoresMouseEvents = enabled
        ConfigManager.shared.save(config)
        
        print("Click-through \(enabled ? "enabled" : "disabled")")
    }
    
    func setWallpaperMode(_ enabled: Bool) {
        config.wallpaperMode = enabled
        wallpaperMode = enabled
        ConfigManager.shared.save(config)
        
        if enabled {
            // In wallpaper mode, make window non-interactive
            self.ignoresMouseEvents = true
            self.level = .desktopIcon
        } else {
            // Restore normal behavior
            self.ignoresMouseEvents = config.clickThrough
            self.level = .floating
        }
        
        print("Wallpaper mode \(enabled ? "enabled" : "disabled")")
    }
    
    private func saveWindowPosition() {
        // Convert macOS coordinates (bottom-left origin) to Windows-style (top-left origin)
        guard let screen = NSScreen.main else { return }
        
        let screenHeight = screen.frame.height
        let windowsStyleY = screenHeight - self.frame.origin.y - self.frame.height
        
        config.positionX = Int(self.frame.origin.x)
        config.positionY = Int(windowsStyleY)
        config.width = Int(self.frame.width)
        config.height = Int(self.frame.height)
        
        ConfigManager.shared.save(config)
        
        print("Saved window position: x=\(config.positionX), y=\(config.positionY), size=\(config.width)x\(config.height)")
    }
    
    // MARK: - NSWindowDelegate
    
    func windowDidMove(_ notification: Notification) {
        // Handle window movement
        saveWindowPosition()
    }
    
    func windowDidResize(_ notification: Notification) {
        // Handle window resize
        overlayView?.setNeedsDisplay(overlayView?.bounds ?? .zero)
        saveWindowPosition()
    }
    
    func windowWillClose(_ notification: Notification) {
        // Clean up resources
        stopTimers()
        print("Overlay window closing")
    }
    
    // MARK: - Utility Methods
    
    func toggleVisibility() {
        if isVisible {
            hide()
        } else {
            show()
        }
    }
    
    func reloadEvents() {
        updateEvents()
        print("Events reloaded")
    }
    
    func applyCommandLineArgs(x: Int? = nil, y: Int? = nil, width: Int? = nil, height: Int? = nil, opacity: Float? = nil) {
        var needsUpdate = false
        
        if let x = x, let y = y {
            // Convert Windows-style coordinates to macOS
            guard let screen = NSScreen.main else { return }
            let screenHeight = screen.frame.height
            let macY = screenHeight - CGFloat(y) - self.frame.height
            
            let newFrame = NSRect(x: CGFloat(x), y: macY, width: self.frame.width, height: self.frame.height)
            self.setFrame(newFrame, display: true)
            needsUpdate = true
        }
        
        if let width = width, let height = height {
            var newFrame = self.frame
            newFrame.size.width = CGFloat(width)
            newFrame.size.height = CGFloat(height)
            self.setFrame(newFrame, display: true)
            needsUpdate = true
        }
        
        if let opacity = opacity {
            setOpacity(opacity)
        }
        
        if needsUpdate {
            saveWindowPosition()
        }
    }