/*
 * Java GUI launcher for macOS Calendar Overlay.
 *
 * Responsibilities:
 * - Launch the Java control application from macOS
 * - Handle process creation and management
 * - Provide error handling for Java execution
 * - Check for Java runtime availability
 * - Manage application paths and arguments
 *
 * Swift data types used:
 * - Process for subprocess management
 * - Pipe for inter-process communication
 * - FileManager for path operations
 * - URL for file system navigation
 *
 * Swift technologies involved:
 * - Process execution and monitoring
 * - Error handling with try/catch
 * - File system operations
 * - Inter-process communication
 *
 * Design intent:
 * This class bridges the macOS overlay with the Java control app,
 * allowing users to edit calendar events through the Java GUI.
 */

import Foundation

class JavaLauncher {
    
    // MARK: - Properties
    
    private static let javaControlAppName = "CalendarControlApp"
    private static let javaControlAppExtension = "jar"
    
    // MARK: - Public API
    
    static func launchJavaGUI() -> Bool {
        print("Attempting to launch Java GUI...")
        
        // Check if Java is available
        guard isJavaAvailable() else {
            print("Java is not available on this system")
            showJavaNotAvailableAlert()
            return false
        }
        
        // Find the Java control app
        guard let javaAppPath = findJavaControlApp() else {
            print("Java control app not found")
            showJavaAppNotFoundAlert()
            return false
        }
        
        // Launch the Java application
        return launchJavaApplication(at: javaAppPath)
    }
    
    static func isJavaAvailable() -> Bool {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/which")
        process.arguments = ["java"]
        
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe
        
        do {
            try process.run()
            process.waitUntilExit()
            
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            let output = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
            
            return output?.isEmpty == false && process.terminationStatus == 0
        } catch {
            print("Error checking Java availability: \(error)")
            return false
        }
    }
    
    static func getJavaVersion() -> String? {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/java")
        process.arguments = ["-version"]
        
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe
        
        do {
            try process.run()
            process.waitUntilExit()
            
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            return String(data: data, encoding: .utf8)
        } catch {
            print("Error getting Java version: \(error)")
            return nil
        }
    }
    
    // MARK: - Private Methods
    
    private static func findJavaControlApp() -> URL? {
        let fileManager = FileManager.default
        
        // Check common locations for the Java control app
        let searchPaths = [
            // Current application bundle
            Bundle.main.resourceURL?.appendingPathComponent("\(javaControlAppName).\(javaControlAppExtension)"),
            
            // Application Support directory
            fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?
                .appendingPathComponent("CalendarOverlay")
                .appendingPathComponent("\(javaControlAppName).\(javaControlAppExtension)"),
            
            // User's home directory
            fileManager.homeDirectoryForCurrentUser
                .appendingPathComponent(".calendarapp")
                .appendingPathComponent("\(javaControlAppName).\(javaControlAppExtension)"),
            
            // Same directory as macOS app
            Bundle.main.bundleURL.deletingLastPathComponent()
                .appendingPathComponent("\(javaControlAppName).\(javaControlAppExtension)")
        ]
        
        for path in searchPaths.compactMap({ $0 }) {
            if fileManager.fileExists(atPath: path.path) {
                print("Found Java control app at: \(path.path)")
                return path
            }
        }
        
        // Also check for the Java app in the project structure
        let projectJavaPath = findJavaAppInProject()
        if let projectPath = projectJavaPath {
            print("Found Java control app in project at: \(projectPath.path)")
            return projectPath
        }
        
        return nil
    }
    
    private static func findJavaAppInProject() -> URL? {
        let fileManager = FileManager.default
        
        // Try to find the Java app relative to the macOS app location
        let macAppURL = Bundle.main.bundleURL
        
        // Go up several directories to find the project root
        var currentURL = macAppURL
        for _ in 0..<5 {
            currentURL = currentURL.deletingLastPathComponent()
            
            // Check for control-app directory
            let controlAppURL = currentURL.appendingPathComponent("control-app")
            let jarFileURL = controlAppURL.appendingPathComponent("\(javaControlAppName).\(javaControlAppExtension)")
            
            if fileManager.fileExists(atPath: jarFileURL.path) {
                return jarFileURL
            }
            
            // Check for build directory
            let buildURL = currentURL.appendingPathComponent("build")
            let buildJarURL = buildURL.appendingPathComponent("libs")
                .appendingPathComponent("\(javaControlAppName).\(javaControlAppExtension)")
            
            if fileManager.fileExists(atPath: buildJarURL.path) {
                return buildJarURL
            }
        }
        
        return nil
    }
    
    private static func launchJavaApplication(at path: URL) -> Bool {
        let process = Process()
        
        // Set up the Java process
        process.executableURL = URL(fileURLWithPath: "/usr/bin/java")
        process.arguments = ["-jar", path.path]
        
        // Set up environment
        var environment = ProcessInfo.processInfo.environment
        environment["JAVA_HOME"] = getJavaHome()
        process.environment = environment
        
        // Set up pipes for output
        let outputPipe = Pipe()
        let errorPipe = Pipe()
        process.standardOutput = outputPipe
        process.standardError = errorPipe
        
        // Set up termination handler
        process.terminationHandler = { process in
            print("Java process terminated with status: \(process.terminationStatus)")
            
            // Read any remaining output
            let outputData = outputPipe.fileHandleForReading.readDataToEndOfFile()
            let errorData = errorPipe.fileHandleForReading.readDataToEndOfFile()
            
            if let output = String(data: outputData, encoding: .utf8), !output.isEmpty {
                print("Java output: \(output)")
            }
            
            if let error = String(data: errorData, encoding: .utf8), !error.isEmpty {
                print("Java error: \(error)")
            }
        }
        
        do {
            print("Launching Java application: \(path.path)")
            try process.run()
            
            // Don't wait for the process to complete (it's a GUI app)
            DispatchQueue.global().async {
                process.waitUntilExit()
            }
            
            print("Java application launched successfully")
            return true
            
        } catch {
            print("Failed to launch Java application: \(error)")
            
            // Try to read error output
            let errorData = errorPipe.fileHandleForReading.readDataToEndOfFile()
            if let errorOutput = String(data: errorData, encoding: .utf8) {
                print("Java launch error output: \(errorOutput)")
            }
            
            return false
        }
    }
    
    private static func getJavaHome() -> String? {
        // Try to get JAVA_HOME from environment
        if let javaHome = ProcessInfo.processInfo.environment["JAVA_HOME"] {
            return javaHome
        }
        
        // Try to find Java Home using /usr/libexec/java_home
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/libexec/java_home")
        
        let pipe = Pipe()
        process.standardOutput = pipe
        
        do {
            try process.run()
            process.waitUntilExit()
            
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            let javaHome = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
            
            return javaHome
        } catch {
            print("Error getting JAVA_HOME: \(error)")
            return nil
        }
    }
    
    // MARK: - Alert Dialogs
    
    private static func showJavaNotAvailableAlert() {
        DispatchQueue.main.async {
            let alert = NSAlert()
            alert.messageText = "Java Not Available"
            alert.informativeText = "Java is required to run the calendar control application. Please install Java from https://www.java.com"
            alert.alertStyle = .warning
            alert.addButton(withTitle: "OK")
            alert.addButton(withTitle: "Download Java")
            
            let response = alert.runModal()
            if response == .alertSecondButtonReturn {
                // Open Java download page
                if let url = URL(string: "https://www.java.com/download/") {
                    NSWorkspace.shared.open(url)
                }
            }
        }
    }
    
    private static func showJavaAppNotFoundAlert() {
        DispatchQueue.main.async {
            let alert = NSAlert()
            alert.messageText = "Calendar Control App Not Found"
            alert.informativeText = "The Java calendar control application could not be found. Please ensure it is installed in the correct location."
            alert.alertStyle = .warning
            alert.addButton(withTitle: "OK")
            
            alert.runModal()
        }
    }
    
    // MARK: - Utility Methods
    
    static func checkJavaRequirements() -> (isAvailable: Bool, version: String?, javaHome: String?) {
        let isAvailable = isJavaAvailable()
        let version = getJavaVersion()
        let javaHome = getJavaHome()
        
        print("Java check - Available: \(isAvailable), Version: \(version ?? "Unknown"), JAVA_HOME: \(javaHome ?? "Not set")")
        
        return (isAvailable, version, javaHome)
    }
    
    static func launchWithArguments(_ arguments: [String]) -> Bool {
        guard let javaAppPath = findJavaControlApp() else {
            return false
        }
        
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/java")
        
        var allArguments = ["-jar", javaAppPath.path]
        allArguments.append(contentsOf: arguments)
        process.arguments = allArguments
        
        do {
            try process.run()
            
            // Don't wait for GUI process
            DispatchQueue.global().async {
                process.waitUntilExit()
            }
            
            print("Launched Java app with arguments: \(arguments)")
            return true
        } catch {
            print("Failed to launch Java app with arguments: \(error)")
            return false
        }
    }
}