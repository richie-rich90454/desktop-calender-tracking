#pragma once

// Windows headers must be included first with proper defines
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

// Set Windows version before including Windows headers
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00  // Windows 10
#endif

#ifndef WINVER
#define WINVER 0x0A00  // Windows 10
#endif

#include <windows.h>
#undef __in
#undef __out
#include <shellapi.h>
#include <dwmapi.h>
#include <string>
#include <memory>
#include "shared/calendar_shared.h"

// These constants should be defined in dwmapi.h for Windows 10+
// but we'll keep fallback definitions just in case
#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20
#endif

#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif

#ifndef DWMWCP_ROUND
#define DWMWCP_ROUND 2
#endif

#ifndef WM_DPICHANGED
#define WM_DPICHANGED 0x02E0
#endif

namespace CalendarOverlay{
    class CalendarRenderer;
    class EventManager;
    class DesktopWindow{
    public:
        DesktopWindow();
        ~DesktopWindow();
        bool create();
        void show();
        void hide();
        void close();
        void update();
        void render();
        void setPosition(int x, int y);
        void setSize(int width, int height);
        void setOpacity(float opacity);
        void setClickThrough(bool clickThrough);
        static LRESULT CALLBACK windowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
    private:
        bool registerWindowClass();
        bool createWindowInstance();
        void adjustWindowStyle();
        void updateWindowPosition();
        void onPaint();
        void onTimer();
        void onMouseMove(int x, int y);
        void onMouseDown(int x, int y);
        void onMouseUp(int x, int y);
        void onKeyDown(WPARAM key);
        void onCommand(WPARAM wParam);
        void onDPIChanged(WPARAM wParam, LPARAM lParam);
        void createTrayIcon();
        void removeTrayIcon();
        void showContextMenu(int x, int y);
        void launchJavaGUI();
        bool checkIfOnDesktop();
        void updateWindowVisibilityBasedOnDesktop();
        void initializeDPIAwareness();
        UINT getSystemDPI();
        UINT getWindowDPI();
        int scaleForDPI(int value, UINT dpi=0);
        int unscaleForDPI(int value, UINT dpi=0);
        void createDoubleBuffer(int width, int height);
        void cleanupDoubleBuffer();
        void resizeDoubleBuffer(int width, int height);
        HWND hwnd;
        HINSTANCE hInstance;
        std::wstring className;
        std::unique_ptr<CalendarRenderer> renderer;
        std::unique_ptr<EventManager> eventManager;
        bool visible;
        bool dragging;
        int dragStartX, dragStartY;
        int windowX, windowY;
        int windowWidth, windowHeight;
        OverlayConfig config;
        UINT_PTR renderTimer, updateTimer;
        NOTIFYICONDATA trayIconData;
        bool trayIconVisible;
        BYTE alpha;
        bool clickThrough;
        bool wallpaperMode;
        bool fullScreenWallpaper;
        UINT_PTR desktopCheckTimer;
        HWND lastActiveWindow;
        bool isOnDesktop;
        UINT currentDPI;
        bool hasPerMonitorDPIAwareness;
        HDC doubleBufferDC;
        HBITMAP doubleBufferBitmap;
        int bufferWidth, bufferHeight;
    };
}