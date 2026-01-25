#pragma once

#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0500
#endif

#ifndef WINVER
#define WINVER 0x0500
#endif

#ifndef _WIN32_IE
#define _WIN32_IE 0x0500
#endif

#include <windows.h>
#include <shellapi.h>
#include <string>
#include <memory>
#include <shared/calendar_shared.h>

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
        void render();
        void onPaint();
        void onTimer();
        void onMouseMove(int x, int y);
        void onMouseDown(int x, int y);
        void onMouseUp(int x, int y);
        void onKeyDown(WPARAM key);
        void createTrayIcon();
        void removeTrayIcon();
        void showContextMenu(int x, int y);
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
    };
}