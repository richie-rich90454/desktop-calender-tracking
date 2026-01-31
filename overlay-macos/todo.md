# macOS Overlay Implementation Order

This document outlines the recommended order for implementing the macOS Calendar Overlay application files. The order is based on dependencies and logical progression from foundational data models to UI components.

## Phase 1: Foundation Models and Data Structures

### 1. Models/OverlayConfig.swift
**Priority: HIGH** - Foundation for configuration management
- Define `OverlayConfig` struct with properties matching Windows equivalent
- Implement initializers with default values
- Add validation methods for values (opacity, dimensions, etc.)
- Make it `Codable` for persistence with `NSUserDefaults`
- Add color utility methods and computed properties
- Implement `Equatable` conformance for comparison

### 2. Models/CalendarEvent.swift
**Priority: HIGH** - Core data model for calendar events
- Define `CalendarEvent` struct with event properties
- Add date/time handling with `Date` and `Calendar` types
- Implement parsing from JSON/Java bridge data
- Add utility methods for event formatting and display

## Phase 2: Configuration and Event Management

### 3. ConfigManager.swift
**Priority: HIGH** - Configuration persistence layer
- Implement singleton pattern for shared configuration
- Add methods to load/save configuration from `NSUserDefaults`
- Handle migration of configuration versions
- Provide default configuration fallback

### 4. EventManager.swift
**Priority: HIGH** - Event data management
- Implement event fetching from Java bridge
- Add event caching and refresh logic
- Handle event filtering (past events, all-day events)
- Implement event sorting by date/time

### 5. JavaLauncher.swift
**Priority: MEDIUM** - Bridge to Java control application
- Implement process management for Java application
- Add IPC communication methods
- Handle Java process lifecycle
- Implement error handling for Java bridge failures

## Phase 3: Core Application Infrastructure

### 6. main.swift
**Priority: HIGH** - Application entry point
- Implement command line argument parsing (`CommandLineArgs` struct)
- Add `parseCommandLine()` function
- Implement `printHelp()` function
- Set up `NSApplication` instance with delegate
- Handle single instance checking

### 7. AppDelegate.swift
**Priority: HIGH** - Application lifecycle management
- Implement `NSApplicationDelegate` protocol
- Create and manage `OverlayWindow` instance
- Set up menu bar status item (`NSStatusItem`)
- Implement application lifecycle methods
- Add preference management with `NSUserDefaults`

## Phase 4: Window and View Management

### 8. OverlayWindow.swift
**Priority: HIGH** - Main window implementation
- Create `NSWindow` subclass with custom behavior
- Implement click-through window support
- Add window positioning and sizing logic
- Handle window level management (always on top)
- Implement transparency and visual effects

### 9. OverlayView.swift
**Priority: HIGH** - Custom view for calendar display
- Create `NSView` subclass for rendering calendar
- Implement event layout and drawing
- Add mouse interaction handling
- Implement custom drawing with `NSBezierPath` and `NSAttributedString`

### 10. CalendarRenderer.swift
**Priority: MEDIUM** - Calendar rendering logic
- Implement event to visual representation conversion
- Add date formatting and layout algorithms
- Handle different display modes (day/week/month views)
- Implement text rendering with custom fonts and colors

## Phase 5: Extensions and Utilities

### 11. Extensions/ColorExtensions.swift
**Priority: LOW** - Utility extensions
- Add `NSColor` extensions for ARGB color conversion
- Implement color blending and manipulation utilities
- Add convenience methods for common color operations

## Phase 6: Resources and Build Configuration

### 12. Resources/Info.plist
**Priority: MEDIUM** - Application metadata
- Configure application bundle properties
- Set up permissions and entitlements
- Configure application appearance and behavior

### 13. Package.swift
**Priority: LOW** - Build configuration (already exists)
- Review and update dependencies if needed
- Configure build settings for release/debug
- Set up resource bundling

## Implementation Notes

### Dependencies Graph:
```
main.swift → AppDelegate.swift → OverlayWindow.swift → OverlayView.swift
    ↓              ↓                    ↓
ConfigManager.swift   CalendarRenderer.swift
    ↓              ↓
OverlayConfig.swift   EventManager.swift
    ↓              ↓
CalendarEvent.swift   JavaLauncher.swift
```

### Key macOS APIs to Use:
- **Foundation**: `UserDefaults`, `Date`, `Calendar`, `JSONSerialization`
- **AppKit**: `NSApplication`, `NSWindow`, `NSView`, `NSStatusItem`, `NSMenu`
- **Core Graphics**: Custom drawing and rendering
- **Process**: For Java bridge process management

### Testing Strategy:
1. Start with unit tests for models (`OverlayConfig`, `CalendarEvent`)
2. Test configuration persistence (`ConfigManager`)
3. Test window and view rendering in isolation
4. End-to-end testing with Java bridge

### Build Commands:
```bash
# Build debug version
swift build

# Build release version  
swift build -c release

# Build universal binary (Apple Silicon + Intel)
swift build --arch arm64 --arch x86_64
```

## Windows Compatibility Notes

When implementing, ensure compatibility with the Windows version where possible:
- Match configuration structure with `calendar_shared.h`
- Use similar command line arguments
- Maintain similar event data format
- Keep consistent default values

## Next Steps After Implementation

1. Create `.app` bundle packaging
2. Add code signing for distribution
3. Implement auto-update mechanism
4. Add advanced features (themes, custom layouts)
5. Create installer package