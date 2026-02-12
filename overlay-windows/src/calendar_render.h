#pragma once

#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
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

    class CalendarRenderer
    {
    public:
        CalendarRenderer();
        ~CalendarRenderer();

        bool initialize(HWND hwnd);
        void resize(int width, int height);
        void render();
        void cleanup();

        void setEvents(const std::vector<CalendarEvent> &newEvents);
        void setConfig(const OverlayConfig &newConfig);
        void setOpacity(float opacity) {}
        void setPosition(int x, int y) {}

        void handleMouseWheel(float delta);
        void handleMouseDown(int x, int y);
        void handleMouseMove(int x, int y);
        void handleMouseUp(int x, int y);
        void resetScroll();
        bool isScrollingActive() const;

        // Audio methods
        void toggleAudioPlayback();
        void playNextTrack();
        void playPreviousTrack();
        void setAudioVolume(float volume);
        float getAudioVolume() const;
        bool isAudioPlaying() const;
        std::wstring getCurrentAudioTrack() const;
        void scanAudioFiles();
        void playAudioTrack(int index);
        void stopAudioPlayback();
        void pauseAudioPlayback();
        void resumeAudioPlayback();
        void seekAudio(long positionMillis);
        void updateDPI(UINT dpiX, UINT dpiY);

    private:
        bool createDeviceResources();
        void releaseDeviceResources();

        void drawBackground();
        void drawEvents();
        void drawCurrentTime();
        void drawDateHeader();
        void drawEvent(const CalendarEvent &event, float yPos);
        void drawWallpaperContent();
        void drawScrollbar();
        void drawAudioControls();

        D2D1_COLOR_F toColorF(uint32_t color) const;
        D2D1::ColorF toColorF(uint8_t r, uint8_t g, uint8_t b, float a = 1.0f) const;

        std::vector<CalendarEvent> getUpcomingEvents(int hours) const;
        void updateViewportLayout(); // recalc all sizes based on window size

        // █████ VIEWPORT-RELATIVE SIZES (percentages of window size) █████
        float vpPadding;             // 2% of min(width,height)
        float vpEventHeight;         // 6% of height
        float vpTimeWidth;           // 15% of width
        float vpScrollbarWidth;      // 1.5% of width
        float vpAudioControlsHeight; // 10% of height
        float vpButtonSize;          // 4% of height
        float vpVolumeWidth;         // 15% of width
        float vpCornerRadius;        // 1% of min(width,height)
        float vpFontSize;            // 2.5% of height (min 9pt, max 24pt)
        float vpLineThickness;       // 0.1% of min(width,height)

        HWND hwnd;
        ID2D1Factory *d2dFactory;
        ID2D1HwndRenderTarget *renderTarget;
        ID2D1SolidColorBrush *textBrush, *backgroundBrush, *eventBrush;
        IDWriteFactory *writeFactory;
        IDWriteTextFormat *textFormat, *titleFormat, *timeFormat;

        std::vector<CalendarEvent> events;
        OverlayConfig config;
        D2D1_SIZE_F renderSize;

        mutable CRITICAL_SECTION cs;
        ULONGLONG lastRenderTime;
        int framesRendered;

        // Scroll state
        float scrollOffset;
        float maxScrollOffset;
        bool isScrolling;
        POINT lastMousePos;
        bool needsScrollbar;
        float totalEventsHeight;
        float visibleHeight;

        // Audio members
        std::unique_ptr<Audio::AudioPlayerEngine> audioPlayer;
        std::unique_ptr<Audio::AudioFileManager> audioFileManager;
        std::vector<Audio::AudioTrack> audioTracks;
        int currentAudioTrackIndex;
        bool audioControlsVisible;
        float audioProgress;
        bool isDraggingAudioProgress;
    };

} // namespace