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
        }
    };
    #pragma pack(pop)
}