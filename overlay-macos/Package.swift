// swift-tools-version:5.7
// The swift-tools-version declares the minimum version of Swift required to build this package.

/*
Package.swift - Swift Package Manager configuration for macOS Calendar Overlay

This file configures the Swift package for building the macOS overlay application.
It defines the executable target, dependencies, and platform requirements.

Key Configuration:
- macOS 10.14 minimum deployment target (Mojave)
- Executable target for the application
- No external dependencies (uses Foundation and AppKit frameworks)

Build Commands:
- `swift build` - Build debug version
- `swift build -c release` - Build release version
- `swift build --arch arm64 --arch x86_64` - Build universal binary for Apple Silicon + Intel

Note: This creates a command-line executable. To create a proper .app bundle,
additional steps are needed (see README.md for details).
*/

import PackageDescription

let package = Package(
    name: "CalendarOverlay",
    platforms: [
        .macOS(.v10_14) // Mojave (10.14) minimum to match requirements
    ],
    products: [
        .executable(
            name: "CalendarOverlay",
            targets: ["CalendarOverlay"]
        ),
    ],
    dependencies: [
        // No external dependencies - using Foundation and AppKit frameworks
        // If you need JSON parsing, consider adding: .package(url: "https://github.com/apple/swift-foundation", from: "1.0.0")
    ],
    targets: [
        .executableTarget(
            name: "CalendarOverlay",
            dependencies: [],
            path: "Sources",
            resources: [
                .copy("Resources/Info.plist"),
                .copy("Resources/Assets.xcassets")
            ],
            swiftSettings: [
                // Enable strict concurrency checking for safety
                .enableUpcomingFeature("StrictConcurrency"),
                
                // Optimization settings
                .unsafeFlags(["-O"], .when(configuration: .release)),
                
                // Debug settings
                .unsafeFlags(["-g"], .when(configuration: .debug)),
            ]
        ),
        
        // Optional: Add test target later
        // .testTarget(
        //     name: "CalendarOverlayTests",
        //     dependencies: ["CalendarOverlay"]
        // ),
    ]
)