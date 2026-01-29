#pragma once
#include <string>
#include <vector>
#include <chrono>
#include <cstring>

namespace CalendarOverlay{
    #pragma pack(push, 1)
    struct CalendarEvent{
        char title[256];
        char description[512];
        int64_t startTime;
        int64_t endTime;
        uint8_t colorR, colorG, colorB;
        uint8_t priority;
        bool allDay;
        CalendarEvent(){
            memset(title, 0, sizeof(title));
            memset(description, 0, sizeof(description));
            startTime=0;
            endTime=0;
            colorR=66;
            colorG=133;
            colorB=244;
            priority=5;
            allDay=false;
        }
    };
    struct OverlayConfig{
        bool enabled;
        int positionX, positionY;
        int width, height;
        float opacity;
        bool showPastEvents;
        bool showAllDay;
        int refreshInterval;
        int fontSize;
        uint32_t backgroundColor;
        uint32_t textColor;
        bool clickThrough;
        std::string position;
        bool wallpaperMode;
        // Dark/Light mode optimization
        bool autoColorMode;      // true = automatic, false = manual
        bool darkMode;           // true = dark mode, false = light mode (when autoColorMode is false)
        uint32_t darkBackgroundColor;
        uint32_t darkTextColor;
        uint32_t lightBackgroundColor;
        uint32_t lightTextColor;
        OverlayConfig(){
            enabled=true;
            positionX=100;
            positionY=100;
            width=400;
            height=600;
            opacity=0.85f;
            showPastEvents=false;
            showAllDay=true;
            refreshInterval=30;
            fontSize=14;
            backgroundColor=0x20000000;
            textColor=0xFFFFFFFF;
            clickThrough=false;
            position="top-right";
            wallpaperMode=false;
            // Dark/Light mode defaults
            autoColorMode = true;  // Automatic by default
            darkMode = false;      // Light mode by default when manual
            darkBackgroundColor = 0x20000000;  // Semi-transparent black
            darkTextColor = 0xFFFFFFFF;        // White text
            lightBackgroundColor = 0x80FFFFFF; // Semi-transparent white
            lightTextColor = 0xFF000000;       // Black text
        }
    };
    #pragma pack(pop)
}