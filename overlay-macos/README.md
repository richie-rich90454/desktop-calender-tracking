# macOS Calendar Overlay

A macOS implementation of the desktop calendar overlay, providing the same functionality as the Windows version (`overlay-windows/`) but built with Swift for optimal performance on Apple Silicon and Intel Macs.

## Overview

This is a transparent overlay application that displays calendar events on your macOS desktop. It reads events from `~/.calendarapp/calendar_events.json` and renders them in a click-through window that stays on top of other applications.

## Features

- **Transparent Overlay Window**: Always-on-top window with configurable opacity
- **Click-through Behavior**: Window ignores mouse events except when dragging or clicking events
- **Event Clicking**: Click on calendar events to launch the Java GUI control application
- **Menu Bar Integration**: Status menu for controlling the overlay (Show/Hide/Exit)
- **Retina Display Support**: High-resolution rendering for Retina displays
- **Mojave+ Compatibility**: Supports macOS 10.14 and later
- **Universal Binary**: Works on both Apple Silicon (M1/M2/M3+) and Intel Macs

## Architecture

### Language Choice: Swift
**Why Swift over Objective-C:**
1. **Apple Silicon Optimization**: Swift is designed and optimized for modern Apple hardware
2. **Performance**: Better performance characteristics and memory safety
3. **Modern Ecosystem**: SwiftUI + AppKit provide a more modern development experience
4. **Future-proof**: Apple's primary development language moving forward
5. **Intel Compatibility**: Swift binaries work seamlessly on both Apple Silicon and Intel Macs via Rosetta 2

### Key macOS vs Windows Differences

| Windows Feature | macOS Equivalent |
|----------------|------------------|
| `HWND` / Window messages | `NSWindow` / `NSResponder` events |
| `WS_EX_TRANSPARENT` | `window.ignoresMouseEvents = true` |
| `WS_EX_TOPMOST` | `window.level = .floating` |
| `SetLayeredWindowAttributes` | `window.isOpaque = false`, `window.backgroundColor = .clear` |
| Direct2D rendering | Core Graphics (`CGContext`) |
| System tray (`NOTIFYICONDATA`) | Menu bar (`NSStatusItem`) |
| `%APPDATA%\.calendarapp\` | `~/.calendarapp/` |

## Building

### Requirements
- macOS 10.14 (Mojave) or later
- Xcode 12.0 or later (for command line tools)
- Swift 5.3 or later

### Using Swift Package Manager
```bash
cd overlay-macos
swift build -c release
```

### Creating .app Bundle
```bash
swift build -c release --arch arm64 --arch x86_64
# The executable will be at: .build/release/CalendarOverlay
```

## File Structure

```
overlay-macos/
├── Package.swift                    # Swift Package Manager configuration
├── README.md                        # This file
├── Sources/
│   └── CalendarOverlay/
│       ├── main.swift               # Application entry point
│       ├── AppDelegate.swift        # NSApplicationDelegate implementation
│       ├── OverlayWindow.swift      # Transparent NSWindow subclass
│       ├── OverlayView.swift        # Custom NSView for rendering and click detection
│       ├── CalendarRenderer.swift   # Core Graphics rendering engine
│       ├── EventManager.swift       # JSON event loading from ~/.calendarapp/
│       ├── ConfigManager.swift      # User preferences management
│       ├── JavaLauncher.swift       # Java GUI launcher
│       ├── Models/
│       │   ├── CalendarEvent.swift  # Event data model
│       │   └── OverlayConfig.swift  # Configuration model
│       └── Extensions/
│           └── ColorExtensions.swift # Color utility extensions
└── Resources/
    ├── Info.plist                   # Application configuration
    └── Assets.xcassets/             # Application icons (optional)
```

## Configuration

The application reads configuration from:
- `~/.calendarapp/calendar_events.json` - Calendar events (shared with Windows version)
- `~/Library/Preferences/com.yourcompany.CalendarOverlay.plist` - macOS preferences

## Usage

1. **Build the application**: `swift build -c release`
2. **Run the overlay**: `.build/release/CalendarOverlay`
3. **Control via menu bar**: Click the status menu icon in the menu bar
   - Show/Hide: Toggle overlay visibility
   - Exit: Quit the application

### Command Line Options
```bash
CalendarOverlay [options]
Options:
  --silent     Run without showing console output
  --console    Show console window for debugging
  --help       Show help message
```

## Integration with Java Control App

When you click on a calendar event in the overlay, it launches the Java GUI control application (similar to the Windows `launchJavaGUI()` function). The application looks for:
1. `CalendarApp.jar` in the same directory as the executable
2. `../dist/CalendarApp.jar` relative to the executable

## Development Notes

### Key Implementation Details

1. **Transparency**: Uses `window.isOpaque = false` and `window.backgroundColor = .clear`
2. **Click-through**: `window.ignoresMouseEvents = true` enables click-through behavior
3. **Event Detection**: `OverlayView` tracks rendered event positions and handles mouse clicks
4. **Rendering**: `CalendarRenderer` uses Core Graphics for high-performance drawing
5. **File Watching**: `EventManager` monitors `~/.calendarapp/calendar_events.json` for changes

### Testing
```bash
# Run tests
swift test

# Run with debug output
swift run CalendarOverlay --console
```

## License

Same license as the main desktop-calendar-tracking project.

## Contributing

1. Follow Swift style guidelines
2. Add comments explaining macOS-specific implementations
3. Test on both Apple Silicon and Intel Macs
4. Ensure Mojave (10.14) compatibility