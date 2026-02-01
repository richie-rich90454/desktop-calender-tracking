/*
 * NSApplicationDelegate implementation for macOS Calendar Overlay.
 *
 * Responsibilities:
 * - Manage application lifecycle events
 * - Create and manage OverlayWindow instance
 * - Set up menu bar status item (NSStatusItem)
 * - Handle application preferences and configuration
 * - Coordinate window visibility and interaction
 *
 * Swift data types used:
 * - NSApplicationDelegate for application lifecycle
 * - OverlayWindow for window management
 * - NSStatusItem for menu bar icon
 * - NSMenu for context menu
 * - OverlayConfig for configuration data
 *
 * Swift technologies involved:
 * - Application lifecycle management
 * - Menu bar status item setup
 * - Window management and positioning
 * - Preference storage with UserDefaults
 *
 * Design intent:
 * This class serves as the central coordinator for the macOS application,
 * managing the lifecycle, UI components, and user interactions.
 */

import Foundation
import AppKit

class AppDelegate: NSObject, NSApplicationDelegate {
    
    // MARK: - Properties
    
    var overlayWindow: OverlayWindow?
    var statusItem: NSStatusItem?
    var config: OverlayConfig
    var commandLineArgs: CommandLineArgs
    
    // Menu items
    private var showHideMenuItem: NSMenuItem?
    private var preferencesMenuItem: NSMenuItem?
    private var quitMenuItem: NSMenuItem?
    
    // Application state
    private var isWindowVisible = true
    private var isAlreadyRunningChecked = false
    
    // MARK: - Initialization
    
    init(args: CommandLineArgs) {
        self.commandLineArgs = args
        self.config = ConfigManager.shared.loadConfig()
        super.init()
        
        // Apply command line arguments to config
        applyCommandLineArgsToConfig()
    }
    
    // MARK: - NSApplicationDelegate Methods
    
    func applicationDidFinishLaunching(_ notification: Notification) {
        // Check for single instance
        if !checkSingleInstance() {
            // Another instance is running, terminate this one
            NSApplication.shared.terminate(self)
            return
        }
        
        // Set up menu bar icon
        setupStatusItem()
        
        // Create overlay window
        createOverlayWindow()
        
        // Apply command line arguments to window
        applyCommandLineArgsToWindow()
        
        // Show window if not in silent mode
        if !commandLineArgs.silent {
            overlayWindow?.show()
            isWindowVisible = true
            updateShowHideMenuItem()
        } else {
            isWindowVisible = false
            updateShowHideMenuItem()
        }
        
        print("Calendar Overlay application launched successfully")
    }
    
    func applicationWillTerminate(_ notification: Notification) {
        // Clean up resources
        overlayWindow?.close()
        removeStatusItem()
        
        // Save configuration
        ConfigManager.shared.save(config)
        
        print("Calendar Overlay application terminating")
    }
    
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        // Don't terminate when window is closed - run in background with menu bar icon
        return false
    }
    
    // MARK: - Window Management
    
    private func createOverlayWindow() {
        // Create window with current configuration
        overlayWindow = OverlayWindow(config: config)
        
        // Set up window callbacks if needed
        // overlayWindow?.delegate = self
        
        print("Created overlay window with configuration: \(config)")
    }
    
    private func applyCommandLineArgsToConfig() {
        // Apply command line arguments to configuration
        if commandLineArgs.x != -1 {
            config.positionX = commandLineArgs.x
        }
        
        if commandLineArgs.y != -1 {
            config.positionY = commandLineArgs.y
        }
        
        if commandLineArgs.width != -1 {
            config.width = commandLineArgs.width
        }
        
        if commandLineArgs.height != -1 {
            config.height = commandLineArgs.height
        }
        
        if commandLineArgs.opacity != -1.0 {
            config.opacity = commandLineArgs.opacity
        }
    }
    
    private func applyCommandLineArgsToWindow() {
        // Apply command line arguments directly to window
        overlayWindow?.applyCommandLineArgs(
            x: commandLineArgs.x != -1 ? commandLineArgs.x : nil,
            y: commandLineArgs.y != -1 ? commandLineArgs.y : nil,
            width: commandLineArgs.width != -1 ? commandLineArgs.width : nil,
            height: commandLineArgs.height != -1 ? commandLineArgs.height : nil,
            opacity: commandLineArgs.opacity != -1.0 ? commandLineArgs.opacity : nil
        )
    }
    
    // MARK: - Menu Bar (Status Item) Management
    
    private func setupStatusItem() {
        // Create status item in system status bar
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        
        guard let statusItem = statusItem else {
            print("Failed to create status item")
            return
        }
        
        // Set icon
        if let icon = NSImage(named: NSImage.Name("CalendarIcon")) {
            icon.isTemplate = true // Allows proper dark mode support
            statusItem.button?.image = icon
        } else {
            // Fallback: Use system calendar icon or text
            statusItem.button?.title = "📅"
        }
        
        // Set tooltip
        statusItem.button?.toolTip = "Calendar Overlay - Click to show menu"
        
        // Create menu
        let menu = NSMenu()
        menu.autoenablesItems = false
        
        // Show/Hide menu item
        showHideMenuItem = NSMenuItem(
            title: "Hide Overlay",
            action: #selector(toggleWindowVisibility(_:)),
            keyEquivalent: "h"
        )
        showHideMenuItem?.target = self
        menu.addItem(showHideMenuItem!)
        
        // Separator
        menu.addItem(NSMenuItem.separator())
        
        // Preferences menu item
        preferencesMenuItem = NSMenuItem(
            title: "Preferences...",
            action: #selector(showPreferences(_:)),
            keyEquivalent: ","
        )
        preferencesMenuItem?.target = self
        menu.addItem(preferencesMenuItem!)
        
        // Separator
        menu.addItem(NSMenuItem.separator())
        
        // Quit menu item
        quitMenuItem = NSMenuItem(
            title: "Quit Calendar Overlay",
            action: #selector(terminateApplication(_:)),
            keyEquivalent: "q"
        )
        quitMenuItem?.target = self
        menu.addItem(quitMenuItem!)
        
        // Set the menu
        statusItem.menu = menu
        
        print("Set up menu bar status item")
    }
    
    private func removeStatusItem() {
        if let statusItem = statusItem {
            NSStatusBar.system.removeStatusItem(statusItem)
            self.statusItem = nil
            print("Removed status item from menu bar")
        }
    }
    
    private func updateShowHideMenuItem() {
        if isWindowVisible {
            showHideMenuItem?.title = "Hide Overlay"
        } else {
            showHideMenuItem?.title = "Show Overlay"
        }
    }
    
    // MARK: - Menu Actions
    
    @objc func toggleWindowVisibility(_ sender: Any?) {
        guard let window = overlayWindow else { return }
        
        if isWindowVisible {
            window.hide()
            isWindowVisible = false
        } else {
            window.show()
            isWindowVisible = true
        }
        
        updateShowHideMenuItem()
        print("Toggled window visibility: \(isWindowVisible ? "visible" : "hidden")")
    }
    
    @objc func showPreferences(_ sender: Any?) {
        // Launch Java GUI for preferences
        print("Launching Java GUI for preferences...")
        JavaLauncher.launchJavaGUI()
    }
    
    @objc func terminateApplication(_ sender: Any?) {
        print("Terminating application from menu...")
        NSApplication.shared.terminate(self)
    }
    
    // MARK: - Single Instance Check
    
    private func checkSingleInstance() -> Bool {
        // Check if another instance of this application is already running
        let runningApps = NSWorkspace.shared.runningApplications
        let currentBundleId = Bundle.main.bundleIdentifier ?? "CalendarOverlay"
        var instanceCount = 0
        
        for app in runningApps {
            if app.bundleIdentifier == currentBundleId {
                instanceCount += 1
            }
        }
        
        // If more than one instance (this one + another), show alert and return false
        if instanceCount > 1 {
            showAlreadyRunningAlert()
            return false
        }
        
        return true
    }
    
    private func showAlreadyRunningAlert() {
        let alert = NSAlert()
        alert.messageText = "Calendar Overlay is Already Running"
        alert.informativeText = "Another instance of Calendar Overlay is already running. Only one instance can run at a time."
        alert.alertStyle = .warning
        alert.addButton(withTitle: "OK")
        
        // Run modal alert
        alert.runModal()
    }
    
    // MARK: - Preference Management
    
    private func loadPreferences() -> OverlayConfig {
        return ConfigManager.shared.loadConfig()
    }
    
    private func savePreferences(_ config: OverlayConfig) {
        ConfigManager.shared.save(config)
    }
    
    // MARK: - Public Methods
    
    func reloadEvents() {
        overlayWindow?.reloadEvents()
    }
    
    func updateConfig(_ newConfig: OverlayConfig) {
        config = newConfig
        overlayWindow?.setOpacity(newConfig.opacity)
        overlayWindow?.setClickThrough(newConfig.clickThrough)
        overlayWindow?.setWallpaperMode(newConfig.wallpaperMode)
        
        // Save updated configuration
        savePreferences(newConfig)
        
        print("Updated configuration: \(newConfig)")
    }
    
    // MARK: - Helper Methods
    
    func getCurrentConfig() -> OverlayConfig {
        return config
    }
    
    func isOverlayVisible() -> Bool {
        return isWindowVisible
    }
}