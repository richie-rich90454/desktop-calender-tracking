/*
 * Application entry point for macOS Calendar Overlay.
 *
 * Responsibilities:
 * - Parse command line arguments
 * - Initialize NSApplication instance
 * - Set up AppDelegate with parsed arguments
 * - Handle help flag and display usage information
 * - Start the application event loop
 *
 * Swift data types used:
 * - CommandLineArgs struct for argument parsing
 * - NSApplication for application lifecycle
 * - ProcessInfo for accessing command line arguments
 *
 * Swift technologies involved:
 * - Command line argument parsing
 * - NSApplication configuration
 * - Application lifecycle management
 *
 * Design intent:
 * This file contains the main entry point that bootstraps the application.
 * It parses command line arguments and wires the application components together.
 */

import Foundation
import AppKit

// MARK: - Command Line Argument Structure

struct CommandLineArgs {
    var silent = false
    var console = false
    var help = false
    var x = -1
    var y = -1
    var width = -1
    var height = -1
    var opacity = -1.0
    
    init() {}
}

// MARK: - Command Line Parsing

func parseCommandLine() -> CommandLineArgs {
    var args = CommandLineArgs()
    let arguments = CommandLine.arguments
    
    var i = 1 // Skip program name
    while i < arguments.count {
        let arg = arguments[i]
        
        switch arg {
        case "--silent", "-s":
            args.silent = true
        case "--console", "-c":
            args.console = true
        case "--help", "-h":
            args.help = true
        case "--x":
            i += 1
            if i < arguments.count, let value = Int(arguments[i]) {
                args.x = value
            }
        case "--y":
            i += 1
            if i < arguments.count, let value = Int(arguments[i]) {
                args.y = value
            }
        case "--width":
            i += 1
            if i < arguments.count, let value = Int(arguments[i]) {
                args.width = value
            }
        case "--height":
            i += 1
            if i < arguments.count, let value = Int(arguments[i]) {
                args.height = value
            }
        case "--opacity":
            i += 1
            if i < arguments.count, let value = Float(arguments[i]) {
                args.opacity = value
            }
        default:
            // Unknown argument, ignore
            break
        }
        
        i += 1
    }
    
    return args
}

// MARK: - Help Display

func printHelp() {
    let helpText = """
    macOS Calendar Overlay - Display calendar events as a desktop overlay
    
    Usage: CalendarOverlay [options]
    
    Options:
      --silent, -s        Run silently (no console, auto-start)
      --console, -c       Show console window for debugging
      --help, -h          Show this help message
      --x POS             Window X position (default: 100)
      --y POS             Window Y position (default: 100)
      --width SIZE        Window width (default: 400)
      --height SIZE       Window height (default: 600)
      --opacity VALUE     Window opacity 0.0-1.0 (default: 0.8)
    
    Examples:
      CalendarOverlay --silent --x 50 --y 50 --opacity 0.9
      CalendarOverlay --console --width 500 --height 700
    
    The application runs in the background with a menu bar icon.
    Click the icon to show/hide the overlay window.
    """
    
    print(helpText)
}

// MARK: - Application Setup

func setupApplication(args: CommandLineArgs) {
    // Configure console visibility based on arguments
    if !args.console {
        // Redirect stdout/stderr to /dev/null to hide console
        freopen("/dev/null", "w", stdout)
        freopen("/dev/null", "w", stderr)
    }
    
    // Create and configure the application
    let app = NSApplication.shared
    app.setActivationPolicy(.accessory) // Run as background app with menu bar icon
    
    // Create app delegate with parsed arguments
    let delegate = AppDelegate(args: args)
    app.delegate = delegate
    
    // Run the application
    app.run()
}

// MARK: - Main Entry Point

func main() {
    let args = parseCommandLine()
    
    if args.help {
        printHelp()
        exit(0)
    }
    
    // Validate opacity value if provided
    if args.opacity != -1.0 && (args.opacity < 0.0 || args.opacity > 1.0) {
        print("Error: Opacity must be between 0.0 and 1.0")
        exit(1)
    }
    
    // Validate position and size values if provided
    if (args.x != -1 && args.x < 0) || (args.y != -1 && args.y < 0) {
        print("Error: Position values must be non-negative")
        exit(1)
    }
    
    if (args.width != -1 && args.width <= 0) || (args.height != -1 && args.height <= 0) {
        print("Error: Size values must be positive")
        exit(1)
    }
    
    setupApplication(args: args)
}

// Start the application
main()