// ==================== desktop_window.h ====================
// Main window class for the Calendar Overlay.
// Creates a layered, always‑on‑top window that can be dragged (with Ctrl key)
// and displays the calendar events via the CalendarRenderer.
// Also handles system tray icon, context menu, and background updates.

#pragma once

// Windows version and header tuning
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00   // Target Windows 10
#endif
#ifndef WINVER
#define WINVER 0x0A00
#endif

#include <windows.h>
#undef __in
#undef __out
#include <shellapi.h>
#include <dwmapi.h>
#include <string>
#include <memory>
#include <vector>
#include "shared/calendar_shared.h"
#include "calendar_render.h"
#include "event_manager.h"

#pragma comment(lib, "dwmapi.lib")

// DWM constants that might be missing in older SDKs
#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20
#endif
#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif
#ifndef DWMWCP_ROUND
#define DWMWCP_ROUND 2
#endif

namespace CalendarOverlay
{
    class DesktopWindow
    {
    public:
        DesktopWindow();
        ~DesktopWindow();

        // Window lifecycle
        bool create();      // Registers class and creates the window
        void show();        // Makes the window visible
        void hide();        // Hides it
        void close();       // Destroys window and cleans up
        void update();      // Refreshes events from the manager
        void render();      // Redraws the window content

        // Configuration setters – update window and save to config
        void setPosition(int x, int y);
        void setSize(int width, int height);
        void setOpacity(float opacity);
        void setClickThrough(bool enabled);

        // Accessors
        HWND getHandle() const { return hwnd; }
        bool isVisible() const { return visible; }
        int getWidth() const { return windowWidth; }
        int getHeight() const { return windowHeight; }

        // Static window procedure (required for Windows message loop)
        static LRESULT CALLBACK windowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);

    private:
        // Initialisation helpers
        bool registerWindowClass();
        bool createWindowInstance();
        void adjustWindowStyle();   // Not implemented but kept for future use
        void updateWindowPosition(); // Not implemented but kept for future use

        // Message handlers
        void onPaint();
        void onTimer();
        void onMouseMove(int x, int y);
        void onMouseDown(int x, int y);
        void onMouseUp(int x, int y);
        void onKeyDown(WPARAM key);
        void onCommand(WPARAM wParam);

        // System tray
        void createTrayIcon();
        void removeTrayIcon();
        void showContextMenu(int x, int y);
        void launchJavaGUI();   // Opens the Java configuration app

        // Desktop detection (hide overlay when other apps are full‑screen)
        bool checkIfOnDesktop();
        void updateWindowVisibilityBasedOnDesktop();

        // Double‑buffering for smooth rendering (avoid flicker)
        void createDoubleBuffer(int width, int height);
        void cleanupDoubleBuffer();
        void resizeDoubleBuffer(int width, int height);

        // Window handles and state
        HWND hwnd;
        HINSTANCE hInstance;
        std::wstring className;

        // Core components
        std::unique_ptr<CalendarRenderer> renderer;
        std::unique_ptr<EventManager> eventManager;

        // Visibility and dragging
        bool visible;
        bool dragging;          // Whether user is dragging the window (Ctrl+click)
        int dragStartX, dragStartY;

        // Window geometry
        int windowX, windowY;
        int windowWidth, windowHeight;
        OverlayConfig config;   // Current settings

        // Timers
        UINT_PTR renderTimer;       // Timer for periodic redraw (100 ms)
        UINT_PTR updateTimer;       // Timer for event refresh (refreshInterval seconds)
        UINT_PTR desktopCheckTimer; // Timer for desktop detection (500 ms)

        // System tray
        NOTIFYICONDATA trayIconData;
        bool trayIconVisible;

        // Layered window attributes
        BYTE alpha;          // Opacity (0‑255)
        bool clickThrough;   // Whether clicks pass through the window
        bool wallpaperMode;  // Simplified display mode (from command line)
        bool fullScreenWallpaper; // Full‑screen wallpaper mode (from command line)

        // Desktop detection
        HWND lastActiveWindow;
        bool isOnDesktop;    // Cached result of checkIfOnDesktop()

        // Double‑buffer
        HDC doubleBufferDC;
        HBITMAP doubleBufferBitmap;
        int bufferWidth, bufferHeight;
    };

} // namespace