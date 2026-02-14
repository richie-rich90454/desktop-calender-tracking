// ==================== calendar_shared.h ====================
// Shared data structures used by both the overlay renderer and the
// configuration/event sources. These structs are designed to be
// memory‑layout compatible across processes (used with shared memory).
// The #pragma pack(1) ensures no padding, which is important for binary
// compatibility when reading/writing from different compilers or languages
// (e.g., Java via JNI or file mapping).

#pragma once

#include <string>
#include <vector>
#include <chrono>
#include <cstring>

namespace CalendarOverlay
{
    // Pack the structures tightly – no padding bytes.
    // This is critical if these structs are ever used for shared memory
    // or file I/O between different processes/compilers.
#pragma pack(push, 1)

    // ------------------------------------------------------------------------
    // CalendarEvent – represents a single calendar event.
    // Fields are sized for compatibility with the Java side (which writes
    // events to a JSON file; these fields are then populated from that JSON).
    // ------------------------------------------------------------------------
    struct CalendarEvent
    {
        char title[256];           // Event title (null‑terminated)
        char description[512];      // Event description (null‑terminated)
        int64_t startTime;          // Start time in milliseconds since epoch (UTC)
        int64_t endTime;            // End time in milliseconds since epoch (UTC)
        uint8_t colorR, colorG, colorB; // RGB color components (0‑255) for event display
        uint8_t priority;           // Priority level (1‑10, higher = more important)
        bool allDay;                // True if event spans the entire day (not fully used)

        // Constructor sets default values (blue color, medium priority, not all‑day).
        CalendarEvent()
        {
            memset(title, 0, sizeof(title));
            memset(description, 0, sizeof(description));
            startTime = 0;
            endTime = 0;
            colorR = 66;
            colorG = 133;
            colorB = 244;
            priority = 5;
            allDay = false;
        }
    };

    // ------------------------------------------------------------------------
    // OverlayConfig – persistent settings for the overlay window.
    // Loaded from and saved to a JSON file (overlay_config.json).
    // ------------------------------------------------------------------------
    struct OverlayConfig
    {
        bool enabled;               // Whether the overlay should be shown at all
        int positionX, positionY;    // Window position (in screen coordinates)
        int width, height;           // Window size (in pixels)
        float opacity;               // Opacity factor (0.0 = transparent, 1.0 = opaque)
        bool showPastEvents;          // Whether to display events that have already ended
        bool showAllDay;              // Whether to show all‑day events
        int refreshInterval;          // How often to refresh events from the file (seconds)
        int fontSize;                 // Base font size for event text (in points)
        uint32_t backgroundColor;     // ARGB color for the background panel
        uint32_t textColor;           // ARGB color for text
        bool clickThrough;            // If true, mouse clicks pass through the window
        std::string position;         // Named position (e.g., "top‑right") – may override X/Y
        bool wallpaperMode;           // If true, draw a simplified panel suitable for wallpaper

        // Constructor sets reasonable defaults.
        OverlayConfig()
        {
            enabled = true;
            positionX = 100;
            positionY = 100;
            width = 400;
            height = 600;
            opacity = 0.85f;
            showPastEvents = false;
            showAllDay = true;
            refreshInterval = 30;
            fontSize = 14;
            backgroundColor = 0x20000000;   // Semi‑transparent black (ARGB)
            textColor = 0xFFFFFFFF;           // White (fully opaque)
            clickThrough = false;
            position = "top-right";
            wallpaperMode = false;
        }
    };

#pragma pack(pop)
}