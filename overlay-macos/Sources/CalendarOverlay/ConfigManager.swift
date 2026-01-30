/*
ConfigManager.swift - Configuration management for macOS Calendar Overlay

This file implements configuration management using UserDefaults or property lists.
It's the macOS equivalent of the Windows Config class.

IMPLEMENTATION NOTES:
1. Use UserDefaults for simple preferences
2. Use property list files for more complex configuration
3. Handle macOS-specific file paths
4. Provide default configuration values
5. Thread-safe configuration access

WINDOWS EQUIVALENT: Config class in config.h/cpp

KEY macOS APIS:
- UserDefaults: Simple key-value storage for preferences
- PropertyListSerialization: For reading/writing .plist files
- FileManager: File system operations for config files
- NSKeyedArchiver/NSKeyedUnarchiver: For object serialization (if needed)

CONFIGURATION PATHS:
Windows: Registry or config file in %APPDATA%
macOS: ~/Library/Preferences/com.yourcompany.CalendarOverlay.plist or UserDefaults

ADD HERE:
1. Import Foundation
2. Define ConfigManager as singleton
3. Implement UserDefaults-based configuration
4. Provide default configuration values
5. Add methods for saving/loading configuration
6. Handle migration from older config formats if needed
*/

// TODO: Add imports for Foundation

// TODO: Define ConfigManager as singleton
// class ConfigManager {
//     
//     // TODO: Singleton instance
//     // static let shared = ConfigManager()
//     // 
//     // // UserDefaults key constants
//     // private enum UserDefaultsKeys {
//     //     static let enabled = "enabled"
//     //     static let positionX = "positionX"
//     //     static let positionY = "positionY"
//     //     static let width = "width"
//     //     static let height = "height"
//     //     static let opacity = "opacity"
//     //     static let showPastEvents = "showPastEvents"
//     //     static let showAllDay = "showAllDay"
//     //     static let refreshInterval = "refreshInterval"
//     //     static let fontSize = "fontSize"
//     //     static let backgroundColor = "backgroundColor"
//     //     static let textColor = "textColor"
//     //     static let clickThrough = "clickThrough"
//     //     static let position = "position"
//     //     static let wallpaperMode = "wallpaperMode"
//     // }
//     // 
//     // // UserDefaults instance
//     // private let userDefaults: UserDefaults
//     // 
//     // // Synchronization
//     // private let configLock = NSLock()
//     
//     // TODO: Private initializer for singleton
//     // private init() {
//     //     // Use standard UserDefaults
//     //     userDefaults = UserDefaults.standard
//     //     
//     //     // Register default values
//     //     registerDefaults()
//     // }
//     
//     // MARK: - Public API
//     
//     // TODO: Implement loadConfig() method
//     // func loadConfig() -> OverlayConfig {
//     //     configLock.lock()
//     //     defer { configLock.unlock() }
//     //     
//     //     var config = OverlayConfig()
//     //     
//     //     // Load values from UserDefaults
//     //     config.enabled = userDefaults.bool(forKey: UserDefaultsKeys.enabled)
//     //     config.positionX = userDefaults.integer(forKey: UserDefaultsKeys.positionX)
//     //     config.positionY = userDefaults.integer(forKey: UserDefaultsKeys.positionY)
//     //     config.width = userDefaults.integer(forKey: UserDefaultsKeys.width)
//     //     config.height = userDefaults.integer(forKey: UserDefaultsKeys.height)
//     //     config.opacity = userDefaults.float(forKey: UserDefaultsKeys.opacity)
//     //     config.showPastEvents = userDefaults.bool(forKey: UserDefaultsKeys.showPastEvents)
//     //     config.showAllDay = userDefaults.bool(forKey: UserDefaultsKeys.showAllDay)
//     //     config.refreshInterval = userDefaults.integer(forKey: UserDefaultsKeys.refreshInterval)
//     //     config.fontSize = userDefaults.integer(forKey: UserDefaultsKeys.fontSize)
//     //     config.backgroundColor = UInt32(userDefaults.integer(forKey: UserDefaultsKeys.backgroundColor))
//     //     config.textColor = UInt32(userDefaults.integer(forKey: UserDefaultsKeys.textColor))
//     //     config.clickThrough = userDefaults.bool(forKey: UserDefaultsKeys.clickThrough)
//     //     config.position = userDefaults.string(forKey: UserDefaultsKeys.position) ?? "top-right"
//     //     config.wallpaperMode = userDefaults.bool(forKey: UserDefaultsKeys.wallpaperMode)
//     //     
//     //     // Validate loaded values
//     //     validateConfig(&config)
//     //     
//     //     return config
//     // }
//     
//     // TODO: Implement save(config:) method
//     // func save(_ config: OverlayConfig) {
//     //     configLock.lock()
//     //     defer { configLock.unlock() }
//     //     
//     //     // Save values to UserDefaults
//     //     userDefaults.set(config.enabled, forKey: UserDefaultsKeys.enabled)
//     //     userDefaults.set(config.positionX, forKey: UserDefaultsKeys.positionX)
//     //     userDefaults.set(config.positionY, forKey: UserDefaultsKeys.positionY)
//     //     userDefaults.set(config.width, forKey: UserDefaultsKeys.width)
//     //     userDefaults.set(config.height, forKey: UserDefaultsKeys.height)
//     //     userDefaults.set(config.opacity, forKey: UserDefaultsKeys.opacity)
//     //     userDefaults.set(config.showPastEvents, forKey: UserDefaultsKeys.showPastEvents)
//     //     userDefaults.set(config.showAllDay, forKey: UserDefaultsKeys.showAllDay)
//     //     userDefaults.set(config.refreshInterval, forKey: UserDefaultsKeys.refreshInterval)
//     //     userDefaults.set(config.fontSize, forKey: UserDefaultsKeys.fontSize)
//     //     userDefaults.set(Int(config.backgroundColor), forKey: UserDefaultsKeys.backgroundColor)
//     //     userDefaults.set(Int(config.textColor), forKey: UserDefaultsKeys.textColor)
//     //     userDefaults.set(config.clickThrough, forKey: UserDefaultsKeys.clickThrough)
//     //     userDefaults.set(config.position, forKey: UserDefaultsKeys.position)
//     //     userDefaults.set(config.wallpaperMode, forKey: UserDefaultsKeys.wallpaperMode)
//     //     
//     //     // Synchronize to disk
//     //     userDefaults.synchronize()
//     // }
//     
//     // TODO: Implement individual setter methods (optional)
//     // func setClickThrough(_ enabled: Bool) {
//     //     userDefaults.set(enabled, forKey: UserDefaultsKeys.clickThrough)
//     //     userDefaults.synchronize()
//     // }
//     // 
//     // func setPosition(x: Int, y: Int) {
//     //     userDefaults.set(x, forKey: UserDefaultsKeys.positionX)
//     //     userDefaults.set(y, forKey: UserDefaultsKeys.positionY)
//     //     userDefaults.synchronize()
//     // }
//     // 
//     // func setSize(width: Int, height: Int) {
//     //     userDefaults.set(width, forKey: UserDefaultsKeys.width)
//     //     userDefaults.set(height, forKey: UserDefaultsKeys.height)
//     //     userDefaults.synchronize()
//     // }
//     // 
//     // func setOpacity(_ opacity: Float) {
//     //     userDefaults.set(opacity, forKey: UserDefaultsKeys.opacity)
//     //     userDefaults.synchronize()
//     // }
//     
//     // MARK: - Default Configuration
//     
//     // TODO: Implement registerDefaults() method
//     // private func registerDefaults() {
//     //     let defaultConfig = OverlayConfig()
//     //     
//     //     let defaults: [String: Any] = [
//     //         UserDefaultsKeys.enabled: defaultConfig.enabled,
//     //         UserDefaultsKeys.positionX: defaultConfig.positionX,
//     //         UserDefaultsKeys.positionY: defaultConfig.positionY,
//     //         UserDefaultsKeys.width: defaultConfig.width,
//     //         UserDefaultsKeys.height: defaultConfig.height,
//     //         UserDefaultsKeys.opacity: defaultConfig.opacity,
//     //         UserDefaultsKeys.showPastEvents: defaultConfig.showPastEvents,
//     //         UserDefaultsKeys.showAllDay: defaultConfig.showAllDay,
//     //         UserDefaultsKeys.refreshInterval: defaultConfig.refreshInterval,
//     //         UserDefaultsKeys.fontSize: defaultConfig.fontSize,
//     //         UserDefaultsKeys.backgroundColor: Int(defaultConfig.backgroundColor),
//     //         UserDefaultsKeys.textColor: Int(defaultConfig.textColor),
//     //         UserDefaultsKeys.clickThrough: defaultConfig.clickThrough,
//     //         UserDefaultsKeys.position: defaultConfig.position,
//     //         UserDefaultsKeys.wallpaperMode: defaultConfig.wallpaperMode
//     //     ]
//     //     
//     //     userDefaults.register(defaults: defaults)
//     // }
//     
//     // TODO: Implement validateConfig() method
//     // private func validateConfig(_ config: inout OverlayConfig) {
//     //     // Ensure values are within valid ranges
//     //     
//     //     // Opacity should be between 0.0 and 1.0
//     //     config.opacity = max(0.0, min(1.0, config.opacity))
//     //     
//     //     // Font size should be reasonable
//     //     config.fontSize = max(8, min(72, config.fontSize))
//     //     
//     //     // Refresh interval should be reasonable
//     //     config.refreshInterval = max(5, min(3600, config.refreshInterval))
//     //     
//     //     // Window dimensions should be reasonable
//     //     config.width = max(100, min(2000, config.width))
//     //     config.height = max(100, min(2000, config.height))
//     //     
//     //     // Position should be valid
//     //     let validPositions = ["top-right", "top-left", "bottom-right", "bottom-left", "custom"]
//     //     if !validPositions.contains(config.position) {
//     //         config.position = "top-right"
//     //     }
//     // }
//     
//     // MARK: - Migration (optional)
//     
//     // TODO: Implement migrateFromOldFormat() if needed
//     // private func migrateFromOldFormat() {
//     //     // Check if migration is needed
//     //     // Migrate from older config format if necessary
//     //     // This is useful if you change config structure in future versions
//     // }
//     
//     // MARK: - File-based Configuration (alternative to UserDefaults)
//     
//     // TODO: Implement getConfigFilePath() method
//     // private func getConfigFilePath() -> String {
//     //     // Alternative: Use property list file in Application Support
//     //     let fileManager = FileManager.default
//     //     let appSupportURL = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
//     //     let appFolderURL = appSupportURL.appendingPathComponent("CalendarOverlay")
//     //     let configFileURL = appFolderURL.appendingPathComponent("config.plist")
//     //     
//     //     // Create directory if it doesn't exist
//     //     if !fileManager.fileExists(atPath: appFolderURL.path) {
//     //         try? fileManager.createDirectory(at: appFolderURL, withIntermediateDirectories: true)
//     //     }
//     //     
//     //     return configFileURL.path
//     // }
//     
//     // TODO: Implement loadConfigFromFile() method (alternative)
//     // private func loadConfigFromFile() -> OverlayConfig? {
//     //     let filePath = getConfigFilePath()
//     //     
//     //     guard let data = try? Data(contentsOf: URL(fileURLWithPath: filePath)),
//     //           let plist = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any] else {
//     //         return nil
//     //     }
//     //     
//     //     // Parse plist dictionary into OverlayConfig
//     //     // This would require custom parsing logic
//     //     
//     //     return nil // Placeholder
//     // }
//     
//     // TODO: Implement saveConfigToFile() method (alternative)
//     // private func saveConfigToFile(_ config: OverlayConfig) -> Bool {
//     //     let filePath = getConfigFilePath()
//     //     
//     //     // Convert config to dictionary
//     //     let configDict = config.toDictionary()
//     //     
//     //     do {
//     //         let data = try PropertyListSerialization.data(fromPropertyList: configDict, format: .xml, options: 0)
//     //         try data.write(to: URL(fileURLWithPath: filePath))
//     //         return true
//     //     } catch {
//     //         print("Failed to save config to file: \(error)")
//     //         return false
//     //     }
//     // }
// }

// TODO: Add OverlayConfig extension for dictionary conversion if using file-based config
// extension OverlayConfig {
//     // func toDictionary() -> [String: Any] {
//     //     return [
//     //         "enabled": enabled,
//     //         "positionX": positionX,
//     //         "positionY": positionY,
//     //         "width": width,
//     //         "height": height,
//     //         "opacity": opacity,
//     //         "showPastEvents": showPastEvents,
//     //         "showAllDay": showAllDay,
//     //         "refreshInterval": refreshInterval,
//     //         "fontSize": fontSize,
//     //         "backgroundColor": backgroundColor,
//     //         "textColor": textColor,
//     //         "clickThrough": clickThrough,
//     //         "position": position,
//     //         "wallpaperMode": wallpaperMode
//     //     ]
//     // }
//     // 
//     // static func fromDictionary(_ dict: [String: Any]) -> OverlayConfig? {
//     //     var config = OverlayConfig()
//     //     
//     //     // Parse dictionary values
//     //     // Handle type conversions and validation
//     //     
//     //     return config
//     // }
// }