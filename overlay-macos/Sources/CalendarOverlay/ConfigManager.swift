/*
 * Configuration management for macOS Calendar Overlay.
 *
 * Responsibilities:
 * - Manage application configuration using UserDefaults
 * - Load and save OverlayConfig instances
 * - Provide default configuration values
 * - Validate configuration data
 * - Handle configuration migration if needed
 *
 * Swift data types used:
 * - UserDefaults for persistent storage
 * - OverlayConfig for configuration data
 * - NSLock for thread safety
 * - Dictionary for default values
 *
 * Swift technologies involved:
 * - Singleton pattern for shared access
 * - Property wrappers for type-safe defaults
 * - Error handling with try/catch
 * - Key-value observing (optional)
 *
 * Design intent:
 * This class provides a centralized configuration management system.
 * It abstracts UserDefaults complexity and ensures thread-safe access.
 */

import Foundation

class ConfigManager {
    
    // MARK: - Singleton Instance
    
    static let shared = ConfigManager()
    
    // MARK: - UserDefaults Key Constants
    
    private enum UserDefaultsKeys {
        static let enabled = "enabled"
        static let positionX = "positionX"
        static let positionY = "positionY"
        static let width = "width"
        static let height = "height"
        static let opacity = "opacity"
        static let showPastEvents = "showPastEvents"
        static let showAllDay = "showAllDay"
        static let refreshInterval = "refreshInterval"
        static let fontSize = "fontSize"
        static let backgroundColor = "backgroundColor"
        static let textColor = "textColor"
        static let clickThrough = "clickThrough"
        static let position = "position"
        static let wallpaperMode = "wallpaperMode"
    }
    
    // MARK: - Properties
    
    private let userDefaults: UserDefaults
    private let configLock = NSLock()
    
    // MARK: - Initialization
    
    private init() {
        // Use standard UserDefaults
        userDefaults = UserDefaults.standard
        
        // Register default values
        registerDefaults()
    }
    
    // MARK: - Public API
    
    func loadConfig() -> OverlayConfig {
        configLock.lock()
        defer { configLock.unlock() }
        
        var config = OverlayConfig()
        
        // Load values from UserDefaults
        config.enabled = userDefaults.bool(forKey: UserDefaultsKeys.enabled)
        config.positionX = userDefaults.integer(forKey: UserDefaultsKeys.positionX)
        config.positionY = userDefaults.integer(forKey: UserDefaultsKeys.positionY)
        config.width = userDefaults.integer(forKey: UserDefaultsKeys.width)
        config.height = userDefaults.integer(forKey: UserDefaultsKeys.height)
        config.opacity = userDefaults.float(forKey: UserDefaultsKeys.opacity)
        config.showPastEvents = userDefaults.bool(forKey: UserDefaultsKeys.showPastEvents)
        config.showAllDay = userDefaults.bool(forKey: UserDefaultsKeys.showAllDay)
        config.refreshInterval = userDefaults.integer(forKey: UserDefaultsKeys.refreshInterval)
        config.fontSize = userDefaults.integer(forKey: UserDefaultsKeys.fontSize)
        config.backgroundColor = UInt32(userDefaults.integer(forKey: UserDefaultsKeys.backgroundColor))
        config.textColor = UInt32(userDefaults.integer(forKey: UserDefaultsKeys.textColor))
        config.clickThrough = userDefaults.bool(forKey: UserDefaultsKeys.clickThrough)
        config.position = userDefaults.string(forKey: UserDefaultsKeys.position) ?? "top-right"
        config.wallpaperMode = userDefaults.bool(forKey: UserDefaultsKeys.wallpaperMode)
        
        // Validate loaded values
        config.validate()
        
        return config
    }
    
    func save(_ config: OverlayConfig) {
        configLock.lock()
        defer { configLock.unlock() }
        
        // Save values to UserDefaults
        userDefaults.set(config.enabled, forKey: UserDefaultsKeys.enabled)
        userDefaults.set(config.positionX, forKey: UserDefaultsKeys.positionX)
        userDefaults.set(config.positionY, forKey: UserDefaultsKeys.positionY)
        userDefaults.set(config.width, forKey: UserDefaultsKeys.width)
        userDefaults.set(config.height, forKey: UserDefaultsKeys.height)
        userDefaults.set(config.opacity, forKey: UserDefaultsKeys.opacity)
        userDefaults.set(config.showPastEvents, forKey: UserDefaultsKeys.showPastEvents)
        userDefaults.set(config.showAllDay, forKey: UserDefaultsKeys.showAllDay)
        userDefaults.set(config.refreshInterval, forKey: UserDefaultsKeys.refreshInterval)
        userDefaults.set(config.fontSize, forKey: UserDefaultsKeys.fontSize)
        userDefaults.set(Int(config.backgroundColor), forKey: UserDefaultsKeys.backgroundColor)
        userDefaults.set(Int(config.textColor), forKey: UserDefaultsKeys.textColor)
        userDefaults.set(config.clickThrough, forKey: UserDefaultsKeys.clickThrough)
        userDefaults.set(config.position, forKey: UserDefaultsKeys.position)
        userDefaults.set(config.wallpaperMode, forKey: UserDefaultsKeys.wallpaperMode)
        
        // Synchronize to disk
        userDefaults.synchronize()
    }
    
    // MARK: - Individual Setters (Optional)
    
    func setClickThrough(_ enabled: Bool) {
        userDefaults.set(enabled, forKey: UserDefaultsKeys.clickThrough)
        userDefaults.synchronize()
    }
    
    func setPosition(x: Int, y: Int) {
        userDefaults.set(x, forKey: UserDefaultsKeys.positionX)
        userDefaults.set(y, forKey: UserDefaultsKeys.positionY)
        userDefaults.synchronize()
    }
    
    func setSize(width: Int, height: Int) {
        userDefaults.set(width, forKey: UserDefaultsKeys.width)
        userDefaults.set(height, forKey: UserDefaultsKeys.height)
        userDefaults.synchronize()
    }
    
    func setOpacity(_ opacity: Float) {
        userDefaults.set(opacity, forKey: UserDefaultsKeys.opacity)
        userDefaults.synchronize()
    }
    
    // MARK: - Default Configuration
    
    private func registerDefaults() {
        let defaultConfig = OverlayConfig()
        
        let defaults: [String: Any] = [
            UserDefaultsKeys.enabled: defaultConfig.enabled,
            UserDefaultsKeys.positionX: defaultConfig.positionX,
            UserDefaultsKeys.positionY: defaultConfig.positionY,
            UserDefaultsKeys.width: defaultConfig.width,
            UserDefaultsKeys.height: defaultConfig.height,
            UserDefaultsKeys.opacity: defaultConfig.opacity,
            UserDefaultsKeys.showPastEvents: defaultConfig.showPastEvents,
            UserDefaultsKeys.showAllDay: defaultConfig.showAllDay,
            UserDefaultsKeys.refreshInterval: defaultConfig.refreshInterval,
            UserDefaultsKeys.fontSize: defaultConfig.fontSize,
            UserDefaultsKeys.backgroundColor: Int(defaultConfig.backgroundColor),
            UserDefaultsKeys.textColor: Int(defaultConfig.textColor),
            UserDefaultsKeys.clickThrough: defaultConfig.clickThrough,
            UserDefaultsKeys.position: defaultConfig.position,
            UserDefaultsKeys.wallpaperMode: defaultConfig.wallpaperMode
        ]
        
        userDefaults.register(defaults: defaults)
    }
    
    // MARK: - Migration (Optional)
    
    private func migrateFromOldFormat() {
        // Check if migration is needed
        // Migrate from older config format if necessary
        // This is useful if you change config structure in future versions
        
        // Example migration logic:
        // if userDefaults.object(forKey: "oldKey") != nil {
        //     let oldValue = userDefaults.value(forKey: "oldKey")
        //     userDefaults.set(oldValue, forKey: UserDefaultsKeys.newKey)
        //     userDefaults.removeObject(forKey: "oldKey")
        // }
    }
    
    // MARK: - File-based Configuration (Alternative to UserDefaults)
    
    private func getConfigFilePath() -> String {
        // Alternative: Use property list file in Application Support
        let fileManager = FileManager.default
        let appSupportURL = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let appFolderURL = appSupportURL.appendingPathComponent("CalendarOverlay")
        let configFileURL = appFolderURL.appendingPathComponent("config.plist")
        
        // Create directory if it doesn't exist
        if !fileManager.fileExists(atPath: appFolderURL.path) {
            try? fileManager.createDirectory(at: appFolderURL, withIntermediateDirectories: true)
        }
        
        return configFileURL.path
    }
    
    func exportConfigToFile(_ config: OverlayConfig) -> Bool {
        let filePath = getConfigFilePath()
        
        do {
            let encoder = PropertyListEncoder()
            encoder.outputFormat = .xml
            let data = try encoder.encode(config)
            try data.write(to: URL(fileURLWithPath: filePath))
            return true
        } catch {
            print("Failed to export config to file: \(error)")
            return false
        }
    }
    
    func importConfigFromFile() -> OverlayConfig? {
        let filePath = getConfigFilePath()
        
        do {
            let data = try Data(contentsOf: URL(fileURLWithPath: filePath))
            let decoder = PropertyListDecoder()
            let config = try decoder.decode(OverlayConfig.self, from: data)
            return config
        } catch {
            print("Failed to import config from file: \(error)")
            return nil
        }
    }
}