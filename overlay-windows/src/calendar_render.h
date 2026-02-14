// ==================== calendar_render.h ====================
// Header for the calendar overlay renderer. Handles all Direct2D drawing,
// event display, scrolling, and audio controls integration.
// The renderer is designed to be driven by a Windows timer and processes
// user input (mouse wheel, clicks) to provide a smooth overlay experience.

#pragma once

// Windows header tuning: avoid min/max macros and unnecessary includes.
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00  // Target Windows 10
#endif

#include <windows.h>
#undef __in
#undef __out
#include <d2d1.h>
#include <dwrite.h>
#include <vector>
#include <memory>
#include <mutex>
#include "shared/calendar_shared.h"
#include "audio_player.h"

#pragma comment(lib, "d2d1.lib")
#pragma comment(lib, "dwrite.lib")

namespace CalendarOverlay
{
    // Main rendering class. All coordinates are in DIPs (device-independent pixels)
    // and scaled automatically for DPI. Layout sizes are calculated as percentages
    // of the window size (see vp* members) so that the overlay adapts to any window size.
    class CalendarRenderer
    {
    public:
        CalendarRenderer();
        ~CalendarRenderer();

        // Initializes Direct2D, DirectWrite, and creates device-dependent resources.
        bool initialize(HWND hwnd);

        // Called when the window is resized; updates render target and recalculates layout.
        void resize(int width, int height);

        // Main drawing function – must be called on the UI thread.
        void render();

        // Releases all device-dependent resources (called on destruction or D2DERR_RECREATE_TARGET).
        void cleanup();

        // DPI scaling factors (actual DPI / 96) – used for hit testing.
        float dpiScaleX;
        float dpiScaleY;

        // Sets the list of calendar events (from the Java side).
        void setEvents(const std::vector<CalendarEvent> &newEvents);

        // Applies configuration changes (colors, wallpaper mode).
        void setConfig(const OverlayConfig &newConfig);

        // Unused but kept for interface compatibility.
        void setOpacity(float opacity) {}
        void setPosition(int x, int y) {}

        // Mouse input handling – returns true if the event was handled.
        void handleMouseWheel(float delta);
        bool handleMouseDown(int x, int y);
        void handleMouseMove(int x, int y);
        void handleMouseUp(int x, int y);

        // Scroll management.
        void resetScroll();                // Scrolls back to top.
        bool isScrollingActive() const;    // Whether user is currently dragging scrollbar.

        // Audio control methods (volume removed per project requirements).
        void toggleAudioPlayback();         // Play/pause, auto-selects a track if none chosen.
        void playNextTrack();
        void playPreviousTrack();
        bool isAudioPlaying() const;
        std::wstring getCurrentAudioTrack() const;
        void scanAudioFiles();               // Refresh list from disk.
        void playAudioTrack(int index);
        void stopAudioPlayback();
        void pauseAudioPlayback();
        void resumeAudioPlayback();
        void seekAudio(long positionMillis);

        // Updates DPI when the window moves to a different monitor.
        void updateDPI(UINT dpiX, UINT dpiY);

        // Called periodically (e.g., from a timer) to process audio events and update UI.
        void handleAudioTimer();

        // Timer ID used for audio progress updates.
        static const UINT AUDIO_TIMER_ID;

    private:
        // Device resource management.
        bool createDeviceResources();       // Creates render target, brushes, text formats.
        void releaseDeviceResources();

        // Drawing subroutines.
        void drawBackground();
        void drawEvents();
        void drawCurrentTime();
        void drawDateHeader();
        void drawEvent(const CalendarEvent &event, float yPos);
        void drawWallpaperContent();        // Simplified view for wallpaper mode.
        void drawScrollbar();
        void drawAudioControls();           // Draws progress bar, buttons, track name (no volume).

        // Color conversion helpers.
        D2D1_COLOR_F toColorF(uint32_t color) const;
        D2D1::ColorF toColorF(uint8_t r, uint8_t g, uint8_t b, float a = 1.0f) const;

        // Returns events that are within a window of "hours" from now.
        std::vector<CalendarEvent> getUpcomingEvents(int hours) const;

        // Recalculates all viewport-relative sizes based on current window size.
        void updateViewportLayout();

        // █████ VIEWPORT-RELATIVE SIZES (percentages of window size) █████
        // These are recalculated on every resize/DPI change.
        float vpPadding;             // 2% of min(width,height)
        float vpEventHeight;         // 6% of height
        float vpTimeWidth;           // 15% of width
        float vpScrollbarWidth;      // 1.5% of width
        float vpAudioControlsHeight; // 10% of height
        float vpButtonSize;          // 4% of height
        float vpCornerRadius;        // 1% of min(width,height)
        float vpFontSize;            // 2.5% of height (min 9pt, max 24pt)
        float vpLineThickness;       // 0.1% of min(width,height)

        // Window and DirectX resources.
        HWND hwnd;
        ID2D1Factory *d2dFactory;
        ID2D1HwndRenderTarget *renderTarget;
        ID2D1SolidColorBrush *textBrush, *backgroundBrush, *eventBrush;
        IDWriteFactory *writeFactory;
        IDWriteTextFormat *textFormat, *titleFormat, *timeFormat;

        // Data.
        std::vector<CalendarEvent> events;   // All events (raw, from Java).
        OverlayConfig config;                  // Current color/wallpaper settings.
        D2D1_SIZE_F renderSize;                // Current render target size in DIPs.

        // Thread safety – most methods are called from the UI thread, but we lock anyway.
        mutable CRITICAL_SECTION cs;
        ULONGLONG lastRenderTime;               // For performance monitoring.
        int framesRendered;

        // Scroll state.
        float scrollOffset;                      // Current scroll offset in DIPs.
        float maxScrollOffset;                   // Maximum allowed offset.
        bool isScrolling;                         // Whether the user is dragging the scrollbar thumb.
        POINT lastMousePos;                       // Last mouse position during drag.
        bool needsScrollbar;                       // Whether we need to show a scrollbar.
        float totalEventsHeight;                   // Total height of all event boxes.
        float visibleHeight;                        // Height of the scrollable area.

        // Audio members.
        std::unique_ptr<Audio::AudioPlayerEngine> audioPlayer;   // Playback engine.
        std::unique_ptr<Audio::AudioFileManager> audioFileManager; // File manager.
        std::vector<Audio::AudioTrack> audioTracks;               // List of available tracks.
        int currentAudioTrackIndex;                                 // Index of selected track (-1 if none).
        bool audioControlsVisible;                                 // Whether to show the audio panel.
        float audioProgress;                                        // Current progress (0..1) for display.
        bool isDraggingAudioProgress;                               // True when user drags the progress bar.

        // Audio timer.
        UINT_PTR m_audioTimerID;
    };
}