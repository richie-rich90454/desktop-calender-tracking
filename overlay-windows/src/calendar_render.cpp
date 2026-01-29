#include "calendar_render.h"
#include <wincodec.h>
#include <sstream>
#include <iomanip>
#include <chrono>
#include <algorithm>
#include <vector>
#pragma comment(lib, "windowscodecs.lib")

namespace CalendarOverlay{
    CalendarRenderer::CalendarRenderer() : d2dFactory(nullptr), renderTarget(nullptr), 
        textBrush(nullptr), backgroundBrush(nullptr), eventBrush(nullptr), 
        writeFactory(nullptr), textFormat(nullptr), titleFormat(nullptr), 
        timeFormat(nullptr), hwnd(NULL), padding(10.0f), eventHeight(40.0f), 
        timeWidth(80.0f), lastRenderTime(0), framesRendered(0),
        currentDPI(96), dpiScale(1.0f) {
        InitializeCriticalSection(&cs);
    }
    CalendarRenderer::~CalendarRenderer(){
        cleanup();
        DeleteCriticalSection(&cs);
    }
    bool CalendarRenderer::initialize(HWND window, UINT dpi){
        currentDPI=dpi;
        dpiScale=dpi/96.0f;
        return initialize(window);
    }
    void CalendarRenderer::onDPIChanged(UINT newDPI) {
        EnterCriticalSection(&cs);
        currentDPI = newDPI;
        dpiScale = newDPI / 96.0f;
        
        // Update fonts for new DPI
        updateFontsForDPI();
        
        // Force recreation of device resources
        if (renderTarget) {
            releaseDeviceResources();
            createDeviceResources();
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::updateFontsForDPI() {
        if (writeFactory) {
            if (textFormat) textFormat->Release();
            if (titleFormat) titleFormat->Release();
            if (timeFormat) timeFormat->Release();
            
            float baseFontSize = 12.0f;
            writeFactory->CreateTextFormat(
                L"Segoe UI", NULL, 
                DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_NORMAL, 
                DWRITE_FONT_STRETCH_NORMAL, 
                baseFontSize * dpiScale, 
                L"en-us", &textFormat);
                
            writeFactory->CreateTextFormat(
                L"Segoe UI", NULL, 
                DWRITE_FONT_WEIGHT_BOLD, DWRITE_FONT_STYLE_NORMAL, 
                DWRITE_FONT_STRETCH_NORMAL, 
                (baseFontSize + 4) * dpiScale, 
                L"en-us", &titleFormat);
                
            writeFactory->CreateTextFormat(
                L"Segoe UI", NULL, 
                DWRITE_FONT_WEIGHT_LIGHT, DWRITE_FONT_STYLE_ITALIC, 
                DWRITE_FONT_STRETCH_NORMAL, 
                (baseFontSize - 2) * dpiScale, 
                L"en-us", &timeFormat);
        }
    }
    bool CalendarRenderer::initialize(HWND window){
        hwnd=window;
        HRESULT hr=D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, &d2dFactory);
        if (FAILED(hr)){
            return false;
        }
        hr=DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory),
            reinterpret_cast<IUnknown**>(&writeFactory));
        if (FAILED(hr)){
            return false;
        }
        if (writeFactory){
            // Use DPI-scaled font sizes
            float scaledFontSize = config.fontSize * dpiScale;
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_SEMI_LIGHT, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize, L"en-us", &textFormat);
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_SEMI_BOLD, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize + 3 * dpiScale, L"en-us", &titleFormat); 
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_LIGHT, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize - 1 * dpiScale, L"en-us", &timeFormat);
            
            // Enable better text rendering
            if (textFormat) {
                textFormat->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_LEADING);
                textFormat->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
                textFormat->SetWordWrapping(DWRITE_WORD_WRAPPING_NO_WRAP);
            }
            if (titleFormat) {
                titleFormat->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_LEADING);
                titleFormat->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
            }
            if (timeFormat) {
                timeFormat->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_LEADING);
                timeFormat->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
            }
        }
        return createDeviceResources();
    }
    bool CalendarRenderer::createDeviceResources(){
        if (!d2dFactory||!hwnd){
            return false;
        }
        RECT rc;
        GetClientRect(hwnd, &rc);
        D2D1_SIZE_U size=D2D1::SizeU(rc.right-rc.left, rc.bottom-rc.top);
        HRESULT hr=d2dFactory->CreateHwndRenderTarget(D2D1::RenderTargetProperties(), D2D1::HwndRenderTargetProperties(hwnd, size), &renderTarget);
        if (FAILED(hr)){
            return false;
        }
        if (renderTarget){
            renderTarget->CreateSolidColorBrush(toColorF(config.textColor), &textBrush);
            renderTarget->CreateSolidColorBrush(toColorF(config.backgroundColor), &backgroundBrush);
            renderTarget->CreateSolidColorBrush(D2D1::ColorF(D2D1::ColorF::LightBlue), &eventBrush);
        }
        renderSize=renderTarget->GetSize();
        return true;
    }
    void CalendarRenderer::releaseDeviceResources(){
        if (textBrush) textBrush->Release();
        if (backgroundBrush) backgroundBrush->Release();
        if (eventBrush) eventBrush->Release();
        if (renderTarget) renderTarget->Release();
        if (textFormat) textFormat->Release();
        if (titleFormat) titleFormat->Release();
        if (timeFormat) timeFormat->Release();
        textBrush=nullptr;
        backgroundBrush=nullptr;
        eventBrush=nullptr;
        renderTarget=nullptr;
        textFormat=nullptr;
        titleFormat=nullptr;
        timeFormat=nullptr;
    }
    void CalendarRenderer::resize(int width, int height){
        EnterCriticalSection(&cs);
        if (renderTarget){
            renderTarget->Resize(D2D1::SizeU(width, height));
            renderSize=renderTarget->GetSize();
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::render(){
        EnterCriticalSection(&cs);
        if (!renderTarget){
            LeaveCriticalSection(&cs);
            return;
        }
        renderTarget->BeginDraw();
        renderTarget->SetTransform(D2D1::Matrix3x2F::Identity());
        renderTarget->Clear(D2D1::ColorF(0, 0, 0, 0));
        if (config.wallpaperMode){
            drawWallpaperContent();
        }
        else{
            drawBackground();
            drawDateHeader();
            drawEvents();
            drawCurrentTime();
        }
        HRESULT hr=renderTarget->EndDraw();
        if (hr==D2DERR_RECREATE_TARGET){
            releaseDeviceResources();
            createDeviceResources();
        }
        framesRendered++;
        lastRenderTime=GetTickCount64();
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::drawBackground(){
        if (!backgroundBrush||!renderTarget){
            return;
        }
        D2D1_ROUNDED_RECT roundedRect=D2D1::RoundedRect(D2D1::RectF(0, 0, renderSize.width, renderSize.height), 8.0f, 8.0f);
        renderTarget->FillRoundedRectangle(roundedRect, backgroundBrush);
        ID2D1SolidColorBrush* borderBrush;
        renderTarget->CreateSolidColorBrush(D2D1::ColorF(1.0f, 1.0f, 1.0f, 0.2f), &borderBrush);
        renderTarget->DrawRoundedRectangle(roundedRect, borderBrush, 1.0f);
        borderBrush->Release();
    }
    void CalendarRenderer::drawDateHeader(){
        if (!textBrush||!titleFormat||!renderTarget){
            return;
        }
        auto now=std::chrono::system_clock::now();
        auto nowTime=std::chrono::system_clock::to_time_t(now);
        std::tm localTime;
        localtime_s(&localTime, &nowTime);
        std::wstringstream wss;
        wss<<std::put_time(&localTime, L"%A, %B %d, %Y");
        D2D1_RECT_F textRect=D2D1::RectF(padding, padding, renderSize.width-padding, padding+30.0f);
        renderTarget->DrawTextW(wss.str().c_str(), static_cast<UINT32>(wss.str().length()), titleFormat, textRect, textBrush);
        ID2D1SolidColorBrush* lineBrush;
        renderTarget->CreateSolidColorBrush(D2D1::ColorF(1.0f, 1.0f, 1.0f, 0.3f), &lineBrush);
        float lineY=padding+35.0f;
        renderTarget->DrawLine(D2D1::Point2F(padding, lineY), D2D1::Point2F(renderSize.width-padding, lineY), lineBrush, 1.0f);
        lineBrush->Release();
    }
    void CalendarRenderer::drawEvents(){
        if (!textBrush||!textFormat||!renderTarget){
            return;
        }
        float startY=padding+50.0f;
        float currentY=startY;
        auto upcomingEvents=getUpcomingEvents(24);
        for (const auto& event : upcomingEvents){
            if (currentY+eventHeight>renderSize.height-padding)
                break;
            drawEvent(event, currentY);
            currentY+=eventHeight+5.0f;
        }
    }
    
    void CalendarRenderer::drawEvent(const CalendarEvent& event, float yPos){
        if (!eventBrush||!textBrush||!textFormat||!timeFormat||!renderTarget){
            return;
        }
        D2D1_ROUNDED_RECT eventRect=D2D1::RoundedRect(D2D1::RectF(padding, yPos, renderSize.width-padding, yPos+eventHeight), 4.0f, 4.0f);
        D2D1_COLOR_F eventColor=toColorF(event.colorR, event.colorG, event.colorB, 0.7f);
        eventBrush->SetColor(eventColor);
        renderTarget->FillRoundedRectangle(eventRect, eventBrush);
        auto eventTime=std::chrono::system_clock::from_time_t(event.startTime/1000);
        auto eventTimeT=std::chrono::system_clock::to_time_t(eventTime);
        std::tm eventTm;
        localtime_s(&eventTm, &eventTimeT);
        std::wstringstream timeStream;
        timeStream<<std::put_time(&eventTm, L"%I:%M %p");
        D2D1_RECT_F timeRect=D2D1::RectF(padding+5.0f, yPos+5.0f, padding+timeWidth, yPos+eventHeight-5.0f);
        renderTarget->DrawTextW(timeStream.str().c_str(), static_cast<UINT32>(timeStream.str().length()), timeFormat, timeRect, textBrush);
        std::wstring title;
        for (int i=0;i<256&&event.title[i]!='\0';i++){
            title+=static_cast<wchar_t>(event.title[i]);
        }
        D2D1_RECT_F titleRect=D2D1::RectF(padding+timeWidth+5.0f, yPos+5.0f, renderSize.width-padding-5.0f, yPos+eventHeight-5.0f);
        renderTarget->DrawTextW(title.c_str(), static_cast<UINT32>(title.length()), textFormat, titleRect, textBrush);
    }
    void CalendarRenderer::drawCurrentTime(){
        if (!textBrush||!textFormat||!renderTarget){
            return;
        }
        auto now=std::chrono::system_clock::now();
        auto nowTime=std::chrono::system_clock::to_time_t(now);
        std::tm localTime;
        localtime_s(&localTime, &nowTime);
        std::wstringstream wss;
        wss<<std::put_time(&localTime, L"%I:%M:%S %p");
        D2D1_RECT_F textRect=D2D1::RectF(padding, renderSize.height-25.0f, renderSize.width-padding, renderSize.height-5.0f);
        renderTarget->DrawTextW(wss.str().c_str(), static_cast<UINT32>(wss.str().length()), timeFormat, textRect, textBrush);
    }
    void CalendarRenderer::drawWallpaperContent(){
        if (!textBrush||!titleFormat||!textFormat||!timeFormat||!renderTarget){
            return;
        }
        // The window is now already 1/4 of screen size in top-right corner
        // Use the entire window area for content
        float cornerPadding=10.0f;
        D2D1_RECT_F contentRect=D2D1::RectF(
            cornerPadding,
            cornerPadding,
            renderSize.width - cornerPadding,
            renderSize.height - cornerPadding
        );
        ID2D1SolidColorBrush* bgBrush;
        renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.1f, 0.1f, 0.1f, 0.7f), &bgBrush);
        D2D1_ROUNDED_RECT bgRect=D2D1::RoundedRect(contentRect, 8.0f, 8.0f);
        renderTarget->FillRoundedRectangle(bgRect, bgBrush);
        bgBrush->Release();
        auto now=std::chrono::system_clock::now();
        auto nowTime=std::chrono::system_clock::to_time_t(now);
        std::tm localTime;
        localtime_s(&localTime, &nowTime);
        std::wstringstream dateStream;
        dateStream<<std::put_time(&localTime, L"%A, %B %d");
        D2D1_RECT_F dateRect=D2D1::RectF(contentRect.left+10.0f, contentRect.top+10.0f, contentRect.right-10.0f, contentRect.top+35.0f);
        renderTarget->DrawTextW(dateStream.str().c_str(), static_cast<UINT32>(dateStream.str().length()), titleFormat, dateRect, textBrush);
        std::wstringstream timeStream;
        timeStream<<std::put_time(&localTime, L"%I:%M %p");
        D2D1_RECT_F timeRect=D2D1::RectF(contentRect.left+10.0f, contentRect.top+40.0f, contentRect.right-10.0f, contentRect.top+65.0f);
        renderTarget->DrawTextW(timeStream.str().c_str(), static_cast<UINT32>(timeStream.str().length()), titleFormat, timeRect, textBrush);
        float eventStartY=contentRect.top+80.0f;
        float eventSpacing=25.0f;
        auto upcomingEvents=getUpcomingEvents(12);
        for (size_t i=0;i<std::min(upcomingEvents.size(), size_t(3));i++){
            const auto& event=upcomingEvents[i];
            auto eventTime=std::chrono::system_clock::from_time_t(event.startTime/1000);
            auto eventTimeT=std::chrono::system_clock::to_time_t(eventTime);
            std::tm eventTm;
            localtime_s(&eventTm, &eventTimeT);
            std::wstringstream eventTimeStream;
            eventTimeStream<<std::put_time(&eventTm, L"%I:%M");
            D2D1_RECT_F eventTimeRect=D2D1::RectF(contentRect.left+10.0f, eventStartY, contentRect.left+60.0f, eventStartY+20.0f);
            renderTarget->DrawTextW(eventTimeStream.str().c_str(), static_cast<UINT32>(eventTimeStream.str().length()), timeFormat, eventTimeRect, textBrush);
            std::wstring title;
            for (int j=0;j<256&&event.title[j]!='\0';j++){
                title+=static_cast<wchar_t>(event.title[j]);
            }
            if (title.length()>20){
                title=title.substr(0, 17)+L"...";
            }
            D2D1_RECT_F eventTitleRect=D2D1::RectF(contentRect.left+65.0f, eventStartY, contentRect.right-10.0f, eventStartY+20.0f);
            renderTarget->DrawTextW(title.c_str(), static_cast<UINT32>(title.length()), textFormat, eventTitleRect, textBrush);
            eventStartY+=eventSpacing;
        }
        if (upcomingEvents.empty()){
            std::wstring noEvents=L"No upcoming events";
            D2D1_RECT_F noEventsRect=D2D1::RectF(contentRect.left+10.0f, eventStartY, contentRect.right-10.0f, eventStartY+20.0f);
            renderTarget->DrawTextW(noEvents.c_str(), static_cast<UINT32>(noEvents.length()), textFormat, noEventsRect, textBrush);
        }
    }
    void CalendarRenderer::setEvents(const std::vector<CalendarEvent>& newEvents){
        EnterCriticalSection(&cs);
        events=newEvents;
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::setConfig(const OverlayConfig& newConfig){
        EnterCriticalSection(&cs);
        config=newConfig;
        
        // Update colors based on wallpaper if auto mode is enabled
        if (config.autoColorMode) {
            detectWallpaperColors();
        }
        
        if (renderTarget&&textBrush&&backgroundBrush){
            textBrush->SetColor(toColorF(config.textColor));
            backgroundBrush->SetColor(toColorF(config.backgroundColor));
        }
        if (writeFactory){
            if (textFormat){
                textFormat->Release();
            }
            if (titleFormat){
                titleFormat->Release();
            }
            if (timeFormat){
                timeFormat->Release();
            }
            float scaledFontSize = config.fontSize * dpiScale;
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize, L"en-us", &textFormat);
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_BOLD, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize + 2 * dpiScale, L"en-us", &titleFormat);
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_ITALIC, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize - 2 * dpiScale, L"en-us", &timeFormat);
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::cleanup(){
        releaseDeviceResources();
        if (writeFactory){
            writeFactory->Release();
        }
        if (d2dFactory){
            d2dFactory->Release();
        }
        writeFactory=nullptr;
        d2dFactory=nullptr;
    }
    D2D1_COLOR_F CalendarRenderer::toColorF(uint32_t color) const{
        float a=((color>>24)&0xFF)/255.0f;
        float r=((color>>16)&0xFF)/255.0f;
        float g=((color>>8)&0xFF)/255.0f;
        float b=(color&0xFF)/255.0f;
        return D2D1::ColorF(r, g, b, a);
    }
    D2D1::ColorF CalendarRenderer::toColorF(uint8_t r, uint8_t g, uint8_t b, float a) const{
        return D2D1::ColorF(r/255.0f, g/255.0f, b/255.0f, a);
    }
    std::vector<CalendarEvent> CalendarRenderer::getUpcomingEvents(int hours) const{
        std::vector<CalendarEvent> upcoming;
        auto now=std::chrono::system_clock::now();
        auto nowMs=std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
        auto pastCutoff=nowMs-(12*3600*1000); // Show events from past 12 hours
        auto futureCutoff=nowMs+(hours*3600*1000);
        EnterCriticalSection(&cs);
        for (const auto& event : events){
            // Show events from past 12 hours and next specified hours
            if (event.startTime>=pastCutoff&&event.startTime<=futureCutoff){
                upcoming.push_back(event);
            }
        }
        std::sort(upcoming.begin(), upcoming.end(), [](const CalendarEvent& a, const CalendarEvent& b){
            return a.startTime<b.startTime;
        });
        LeaveCriticalSection(&cs);
        return upcoming;
    }

    // Dark/Light mode optimization implementation
    float CalendarRenderer::calculateBrightness(uint8_t r, uint8_t g, uint8_t b) const {
        // Calculate relative luminance using ITU-R BT.709 formula
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255.0f;
    }

    float CalendarRenderer::calculateContrast(uint32_t color1, uint32_t color2) const {
        // Extract color components
        uint8_t r1 = (color1 >> 16) & 0xFF;
        uint8_t g1 = (color1 >> 8) & 0xFF;
        uint8_t b1 = color1 & 0xFF;
        uint8_t r2 = (color2 >> 16) & 0xFF;
        uint8_t g2 = (color2 >> 8) & 0xFF;
        uint8_t b2 = color2 & 0xFF;
        
        // Calculate relative luminance for both colors
        float l1 = calculateBrightness(r1, g1, b1);
        float l2 = calculateBrightness(r2, g2, b2);
        
        // Calculate contrast ratio (WCAG 2.0 formula)
        float lighter = (l1 > l2) ? l1 : l2;
        float darker = (l1 > l2) ? l2 : l1;
        return (lighter + 0.05f) / (darker + 0.05f);
    }

    uint32_t CalendarRenderer::getOptimalTextColor(uint32_t backgroundColor) const {
        // Extract background color components
        uint8_t bgR = (backgroundColor >> 16) & 0xFF;
        uint8_t bgG = (backgroundColor >> 8) & 0xFF;
        uint8_t bgB = backgroundColor & 0xFF;
        
        // Calculate background brightness
        float brightness = calculateBrightness(bgR, bgG, bgB);
        
        // Choose text color based on background brightness
        if (brightness > 0.5f) {
            // Light background - use dark text
            return 0xFF000000; // Black
        } else {
            // Dark background - use light text
            return 0xFFFFFFFF; // White
        }
    }

    bool CalendarRenderer::sampleWallpaperColors(std::vector<uint32_t>& samples) const {
        if (!hwnd) return false;
        
        // Get window position and size
        RECT windowRect;
        GetWindowRect(hwnd, &windowRect);
        
        // Create a temporary DC for the desktop
        HDC desktopDC = GetDC(NULL);
        if (!desktopDC) return false;
        
        // Sample colors from the area behind the window
        const int samplePoints = 9; // 3x3 grid
        int width = windowRect.right - windowRect.left;
        int height = windowRect.bottom - windowRect.top;
        
        samples.clear();
        samples.reserve(samplePoints);
        
        // Sample points in a grid pattern
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int sampleX = windowRect.left + (width * x) / 2;
                int sampleY = windowRect.top + (height * y) / 2;
                
                COLORREF color = GetPixel(desktopDC, sampleX, sampleY);
                if (color != CLR_INVALID) {
                    // Convert COLORREF (BGR) to RGB
                    uint8_t r = GetRValue(color);
                    uint8_t g = GetGValue(color);
                    uint8_t b = GetBValue(color);
                    uint32_t rgbColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
                    samples.push_back(rgbColor);
                }
            }
        }
        
        ReleaseDC(NULL, desktopDC);
        return !samples.empty();
    }

    uint32_t CalendarRenderer::calculateAverageColor(const std::vector<uint32_t>& samples) const {
        if (samples.empty()) return 0x808080; // Default gray
        
        uint64_t totalR = 0, totalG = 0, totalB = 0;
        
        for (uint32_t color : samples) {
            totalR += (color >> 16) & 0xFF;
            totalG += (color >> 8) & 0xFF;
            totalB += color & 0xFF;
        }
        
        uint8_t avgR = static_cast<uint8_t>(totalR / samples.size());
        uint8_t avgG = static_cast<uint8_t>(totalG / samples.size());
        uint8_t avgB = static_cast<uint8_t>(totalB / samples.size());
        
        return (0xFF << 24) | (avgR << 16) | (avgG << 8) | avgB;
    }

    void CalendarRenderer::detectWallpaperColors() {
        if (!config.autoColorMode) {
            // Manual mode - use configured colors
            if (config.darkMode) {
                config.backgroundColor = config.darkBackgroundColor;
                config.textColor = config.darkTextColor;
            } else {
                config.backgroundColor = config.lightBackgroundColor;
                config.textColor = config.lightTextColor;
            }
            return;
        }
        
        // Automatic mode - detect wallpaper colors
        std::vector<uint32_t> samples;
        if (sampleWallpaperColors(samples)) {
            uint32_t averageColor = calculateAverageColor(samples);
            
            // Extract color components
            uint8_t r = (averageColor >> 16) & 0xFF;
            uint8_t g = (averageColor >> 8) & 0xFF;
            uint8_t b = averageColor & 0xFF;
            
            // Calculate brightness
            float brightness = calculateBrightness(r, g, b);
            
            // Determine if background is dark or light
            bool isDarkBackground = brightness < 0.5f;
            
            // Choose appropriate colors
            if (isDarkBackground) {
                // Dark wallpaper - use dark theme with good contrast
                config.backgroundColor = config.darkBackgroundColor;
                config.textColor = getOptimalTextColor(config.darkBackgroundColor);
            } else {
                // Light wallpaper - use light theme with good contrast
                config.backgroundColor = config.lightBackgroundColor;
                config.textColor = getOptimalTextColor(config.lightBackgroundColor);
            }
            
            // Ensure good contrast
            float contrast = calculateContrast(config.backgroundColor, config.textColor);
            if (contrast < 4.5f) { // WCAG AA minimum contrast
                // Adjust text color for better contrast
                config.textColor = getOptimalTextColor(config.backgroundColor);
            }
        }
    }

    void CalendarRenderer::updateColorsBasedOnWallpaper() {
        EnterCriticalSection(&cs);
        
        // Detect wallpaper colors and update config
        detectWallpaperColors();
        
        // Update brushes if they exist
        if (renderTarget && textBrush && backgroundBrush) {
            textBrush->SetColor(toColorF(config.textColor));
            backgroundBrush->SetColor(toColorF(config.backgroundColor));
        }
        
        LeaveCriticalSection(&cs);
    }
}
