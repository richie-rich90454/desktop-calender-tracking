#pragma once

#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

#include <windows.h>
#undef __in
#undef __out
#include <d2d1.h>
#include <dwrite.h>
#include <vector>
#include <memory>
#include <shared/calendar_shared.h>
#pragma comment(lib, "d2d1.lib")
#pragma comment(lib, "dwrite.lib")

namespace CalendarOverlay{
    class CalendarRenderer{
    public:
        CalendarRenderer();
        ~CalendarRenderer();
        bool initialize(HWND hwnd);
        void resize(int width, int height);
        void render();
        void cleanup();
        void setEvents(const std::vector<CalendarEvent>& newEvents);
        void setConfig(const OverlayConfig& newConfig);
        void setOpacity(float opacity);
        void setPosition(int x, int y);
    private:
        bool createDeviceResources();
        void releaseDeviceResources();
        void drawBackground();
        void drawEvents();
        void drawCurrentTime();
        void drawDateHeader();
        void drawEvent(const CalendarEvent& event, float yPos);
        void drawWallpaperContent();
        D2D1_COLOR_F toColorF(uint32_t color) const;
        D2D1::ColorF toColorF(uint8_t r, uint8_t g, uint8_t b, float a=1.0f) const;
        std::vector<CalendarEvent> getUpcomingEvents(int hours) const;
        HWND hwnd;
        ID2D1Factory* d2dFactory;
        ID2D1HwndRenderTarget* renderTarget;
        ID2D1SolidColorBrush *textBrush, *backgroundBrush, *eventBrush;
        IDWriteFactory* writeFactory;
        IDWriteTextFormat *textFormat, *titleFormat, *timeFormat;
        std::vector<CalendarEvent> events;
        OverlayConfig config;
        D2D1_SIZE_F renderSize;
        float padding;
        float eventHeight;
        float timeWidth;
        mutable CRITICAL_SECTION cs;
        ULONGLONG lastRenderTime;
        int framesRendered;
    };
}