#include "calendar_render.h"
#include "audio_player.h"
#include <wincodec.h>
#include <sstream>
#include <iomanip>
#include <chrono>
#include <algorithm>
#include <vector>
#include <filesystem>
#pragma comment(lib, "windowscodecs.lib")

namespace fs=std::filesystem;

namespace CalendarOverlay{
    CalendarRenderer::CalendarRenderer() : d2dFactory(nullptr), renderTarget(nullptr), 
        textBrush(nullptr), backgroundBrush(nullptr), eventBrush(nullptr), 
        writeFactory(nullptr), textFormat(nullptr), titleFormat(nullptr), 
        timeFormat(nullptr), hwnd(NULL), padding(10.0f), eventHeight(40.0f), 
        timeWidth(80.0f), lastRenderTime(0), framesRendered(0),
        currentDPI(96), dpiScale(1.0f),
        scrollOffset(0.0f), maxScrollOffset(0.0f), isScrolling(false),
        scrollbarWidth(8.0f), needsScrollbar(false),
        totalEventsHeight(0.0f), visibleHeight(0.0f),
        currentAudioTrackIndex(-1), audioControlsVisible(true), 
        audioControlsHeight(60.0f), audioProgress(0.0f), 
        isDraggingAudioProgress(false){
        InitializeCriticalSection(&cs);
        lastMousePos.x=0;
        lastMousePos.y=0;
        audioPlayer=std::make_unique<Audio::AudioPlayerEngine>();
        audioFileManager=std::make_unique<Audio::AudioFileManager>();
        scanAudioFiles();
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
    void CalendarRenderer::onDPIChanged(UINT newDPI){
        EnterCriticalSection(&cs);
        currentDPI=newDPI;
        dpiScale=newDPI/96.0f;
        updateFontsForDPI();
        if (renderTarget){
            releaseDeviceResources();
            createDeviceResources();
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::updateFontsForDPI(){
        if (writeFactory){
            if (textFormat) textFormat->Release();
            if (titleFormat) titleFormat->Release();
            if (timeFormat) timeFormat->Release();
            float baseFontSize=12.0f;
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
                (baseFontSize+4) * dpiScale, 
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
            float scaledFontSize=config.fontSize * dpiScale;
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_SEMI_LIGHT, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize, L"en-us", &textFormat);
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_SEMI_BOLD, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize+3 * dpiScale, L"en-us", &titleFormat); 
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_LIGHT, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize - 1 * dpiScale, L"en-us", &timeFormat);
            if (textFormat){
                textFormat->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_LEADING);
                textFormat->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
                textFormat->SetWordWrapping(DWRITE_WORD_WRAPPING_NO_WRAP);
            }
            if (titleFormat){
                titleFormat->SetTextAlignment(DWRITE_TEXT_ALIGNMENT_LEADING);
                titleFormat->SetParagraphAlignment(DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
            }
            if (timeFormat){
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
        totalEventsHeight=static_cast<float>(upcomingEvents.size()) * (eventHeight+5.0f);
        visibleHeight=renderSize.height - startY - padding - 25.0f; // Subtract bottom padding and time display
        needsScrollbar=(totalEventsHeight > visibleHeight);
        if (needsScrollbar){
            maxScrollOffset=totalEventsHeight - visibleHeight;
            if (scrollOffset > maxScrollOffset){
                scrollOffset=maxScrollOffset;
            }
            currentY -= scrollOffset;
            drawScrollbar();
        }
        else{
            scrollOffset=0;
            maxScrollOffset=0;
        }
        float visibleTop=padding+50.0f;
        float visibleBottom=renderSize.height - padding - 25.0f;
        for (const auto& event : upcomingEvents){
            float eventTop=currentY;
            float eventBottom=currentY+eventHeight;
            if (eventBottom > visibleTop&&eventTop<visibleBottom){
                drawEvent(event, currentY);
            }
            currentY+=eventHeight+5.0f;
            if (currentY > visibleBottom){
                break;
            }
        }
    }
    void CalendarRenderer::drawEvent(const CalendarEvent& event, float yPos){
        if (!eventBrush||!textBrush||!textFormat||!timeFormat||!renderTarget){
            return;
        }
        float eventRight=renderSize.width - padding;
        if (needsScrollbar){
            eventRight -= scrollbarWidth+5.0f;
        }
        auto now=std::chrono::system_clock::now();
        auto nowMs=std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
        long long timeUntilStart=event.startTime - nowMs;
        long long timeSinceEnd=nowMs - event.endTime;
        D2D1_COLOR_F eventColor;
        if (timeSinceEnd > 0){
            eventColor=D2D1::ColorF(0.7f, 0.7f, 0.7f, 0.7f);
        } 
        else if (nowMs>=event.startTime&&nowMs <= event.endTime){
            eventColor=D2D1::ColorF(1.0f, 0.0f, 0.0f, 0.7f);
        }
        else if (timeUntilStart > 0&&timeUntilStart <= 3600000){
            eventColor=D2D1::ColorF(1.0f, 0.5f, 0.0f, 0.7f);
        }
        else{
            eventColor=toColorF(event.colorR, event.colorG, event.colorB, 0.7f);
        }
        D2D1_ROUNDED_RECT eventRect=D2D1::RoundedRect(
            D2D1::RectF(padding, yPos, eventRight, yPos+eventHeight), 
            4.0f, 4.0f);
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
        D2D1_RECT_F titleRect=D2D1::RectF(
            padding+timeWidth+5.0f, 
            yPos+5.0f, 
            eventRight - 5.0f, 
            yPos+eventHeight-5.0f
        );
        renderTarget->DrawTextW(title.c_str(), static_cast<UINT32>(title.length()), textFormat, titleRect, textBrush);
    }
    void CalendarRenderer::drawScrollbar(){
        if (!renderTarget||!needsScrollbar){
            return;
        }
        float scrollbarX=renderSize.width - padding - scrollbarWidth;
        float scrollAreaTop=padding+50.0f;
        float scrollAreaBottom=renderSize.height - padding - 25.0f;
        float scrollAreaHeight=scrollAreaBottom - scrollAreaTop;
        ID2D1SolidColorBrush* trackBrush;
        renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.3f, 0.3f, 0.3f, 0.3f), &trackBrush);
        D2D1_RECT_F trackRect=D2D1::RectF(
            scrollbarX,
            scrollAreaTop,
            scrollbarX+scrollbarWidth,
            scrollAreaBottom
        );
        renderTarget->FillRectangle(trackRect, trackBrush);
        trackBrush->Release();
        float thumbHeight=(visibleHeight/totalEventsHeight) * scrollAreaHeight;
        if (thumbHeight<20.0f) thumbHeight=20.0f;
        float thumbTop=scrollAreaTop+(scrollOffset/totalEventsHeight) * scrollAreaHeight;
        float thumbBottom=thumbTop+thumbHeight;
        if (thumbBottom > scrollAreaBottom){
            thumbTop=scrollAreaBottom - thumbHeight;
            thumbBottom=scrollAreaBottom;
        }
        ID2D1SolidColorBrush* thumbBrush;
        renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.6f, 0.6f, 0.6f, 0.6f), &thumbBrush);
        D2D1_RECT_F thumbRect=D2D1::RectF(
            scrollbarX,
            thumbTop,
            scrollbarX+scrollbarWidth,
            thumbBottom
        );
        renderTarget->FillRectangle(thumbRect, thumbBrush);
        ID2D1SolidColorBrush* thumbBorderBrush;
        renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.8f, 0.8f, 0.8f, 0.8f), &thumbBorderBrush);
        renderTarget->DrawRectangle(thumbRect, thumbBorderBrush, 1.0f);
        thumbBorderBrush->Release();
        thumbBrush->Release();
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
        if (!textBrush||!titleFormat||!textFormat||!timeFormat||!renderTarget||!eventBrush){
            return;
        }
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
        auto nowMs=std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
        float eventStartY=contentRect.top+80.0f;
        float eventSpacing=25.0f;
        auto upcomingEvents=getUpcomingEvents(12);
        for (size_t i=0;i<std::min(upcomingEvents.size(), size_t(3));i++){
            const auto& event=upcomingEvents[i];
            long long timeUntilStart=event.startTime - nowMs;
            long long timeSinceEnd=nowMs - event.endTime;
            float dotSize=6.0f;
            D2D1_ELLIPSE statusDot=D2D1::Ellipse(
                D2D1::Point2F(contentRect.left+10.0f, eventStartY+10.0f),
                dotSize, dotSize
            );
            if (timeSinceEnd > 0){
                eventBrush->SetColor(D2D1::ColorF(0.7f, 0.7f, 0.7f, 0.7f));
            } 
            else if (nowMs>=event.startTime&&nowMs <= event.endTime){
                eventBrush->SetColor(D2D1::ColorF(1.0f, 0.0f, 0.0f, 0.7f));
            }
            else if (timeUntilStart > 0&&timeUntilStart <= 3600000){
                eventBrush->SetColor(D2D1::ColorF(1.0f, 0.5f, 0.0f, 0.7f));
            }
            else{
                eventBrush->SetColor(toColorF(event.colorR, event.colorG, event.colorB, 0.7f));
            }
            renderTarget->FillEllipse(statusDot, eventBrush);
            auto eventTime=std::chrono::system_clock::from_time_t(event.startTime/1000);
            auto eventTimeT=std::chrono::system_clock::to_time_t(eventTime);
            std::tm eventTm;
            localtime_s(&eventTm, &eventTimeT);
            std::wstringstream eventTimeStream;
            eventTimeStream<<std::put_time(&eventTm, L"%I:%M");
            D2D1_RECT_F eventTimeRect=D2D1::RectF(contentRect.left+20.0f, eventStartY, contentRect.left+70.0f, eventStartY+20.0f);
            renderTarget->DrawTextW(eventTimeStream.str().c_str(), static_cast<UINT32>(eventTimeStream.str().length()), timeFormat, eventTimeRect, textBrush);
            std::wstring title;
            for (int j=0;j<256&&event.title[j]!='\0';j++){
                title+=static_cast<wchar_t>(event.title[j]);
            }
            if (title.length()>20){
                title=title.substr(0, 17)+L"...";
            }
            D2D1_RECT_F eventTitleRect=D2D1::RectF(contentRect.left+75.0f, eventStartY, contentRect.right-10.0f, eventStartY+20.0f);
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
        scrollOffset=0;
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::setConfig(const OverlayConfig& newConfig){
        EnterCriticalSection(&cs);
        config=newConfig;
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
            float scaledFontSize=config.fontSize * dpiScale;
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize, L"en-us", &textFormat);
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_BOLD, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize+2 * dpiScale, L"en-us", &titleFormat);
            writeFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_ITALIC, DWRITE_FONT_STRETCH_NORMAL, scaledFontSize - 2 * dpiScale, L"en-us", &timeFormat);
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::setOpacity(float opacity){

    }
    void CalendarRenderer::setPosition(int x, int y){

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
    void CalendarRenderer::handleMouseWheel(float delta){
        EnterCriticalSection(&cs);
        if (needsScrollbar){
            float scrollSpeed=eventHeight * 3;
            scrollOffset+=delta * scrollSpeed;
            if (scrollOffset<0) scrollOffset=0;
            if (scrollOffset > maxScrollOffset) scrollOffset=maxScrollOffset;
            if (hwnd){
                InvalidateRect(hwnd, NULL, FALSE);
            }
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::handleMouseDown(int x, int y){
        EnterCriticalSection(&cs);
        if (needsScrollbar){
            float scrollbarX=renderSize.width - padding - scrollbarWidth;
            D2D1_RECT_F scrollbarRect=D2D1::RectF(
                scrollbarX,
                padding+50.0f,
                scrollbarX+scrollbarWidth,
                renderSize.height - padding - 25.0f
            );
            if (x>=scrollbarRect.left&&x <= scrollbarRect.right &&
                y>=scrollbarRect.top&&y <= scrollbarRect.bottom){
                isScrolling=true;
                lastMousePos.x=x;
                lastMousePos.y=y;
            }
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::handleMouseMove(int x, int y){
        EnterCriticalSection(&cs);
        if (isScrolling&&needsScrollbar){
            float deltaY=static_cast<float>(y - lastMousePos.y);
            float scrollAreaHeight=(renderSize.height - padding - 25.0f) - (padding+50.0f);
            float scrollRatio=deltaY/scrollAreaHeight;
            scrollOffset+=scrollRatio * totalEventsHeight;
            if (scrollOffset<0) scrollOffset=0;
            if (scrollOffset > maxScrollOffset) scrollOffset=maxScrollOffset;
            lastMousePos.x=x;
            lastMousePos.y=y;
            if (hwnd){
                InvalidateRect(hwnd, NULL, FALSE);
            }
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::handleMouseUp(int x, int y){
        EnterCriticalSection(&cs);
        isScrolling=false;
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::resetScroll(){
        EnterCriticalSection(&cs);
        scrollOffset=0;
        LeaveCriticalSection(&cs);
    }
    bool CalendarRenderer::isScrollingActive() const{
        EnterCriticalSection(&cs);
        bool scrolling=isScrolling;
        LeaveCriticalSection(&cs);
        return scrolling;
    }
    void CalendarRenderer::toggleAudioPlayback(){
        EnterCriticalSection(&cs);
        if (audioPlayer){
            if (audioPlayer->isPlaying()){
                audioPlayer->pause();
            }
            else if (audioPlayer->isPaused()){
                audioPlayer->resume();
            }
            else if (currentAudioTrackIndex>=0&&currentAudioTrackIndex<(int)audioTracks.size()){
                audioPlayer->play(audioTracks[currentAudioTrackIndex]);
            }
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::playNextTrack(){
        EnterCriticalSection(&cs);
        if (audioTracks.empty()){
            LeaveCriticalSection(&cs);
            return;
        }
        if (currentAudioTrackIndex<0){
            currentAudioTrackIndex=0;
        }
        else{
            currentAudioTrackIndex=(currentAudioTrackIndex+1)%audioTracks.size();
        }
        if (audioPlayer){
            audioPlayer->stop();
            audioPlayer->play(audioTracks[currentAudioTrackIndex]);
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::playPreviousTrack(){
        EnterCriticalSection(&cs);
        if (audioTracks.empty()){
            LeaveCriticalSection(&cs);
            return;
        }
        if (currentAudioTrackIndex<0){
            currentAudioTrackIndex=0;
        }
        else{
            currentAudioTrackIndex=(currentAudioTrackIndex - 1+audioTracks.size())%audioTracks.size();
        }
        if (audioPlayer){
            audioPlayer->stop();
            audioPlayer->play(audioTracks[currentAudioTrackIndex]);
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::setAudioVolume(float volume){
        EnterCriticalSection(&cs);
        if (audioPlayer){
            audioPlayer->setVolume(volume);
        }
        LeaveCriticalSection(&cs);
    }
    float CalendarRenderer::getAudioVolume() const{
        EnterCriticalSection(&cs);
        float volume=0.5f;
        if (audioPlayer){
            volume=audioPlayer->getVolume();
        }
        LeaveCriticalSection(&cs);
        return volume;
    }
    bool CalendarRenderer::isAudioPlaying() const{
        EnterCriticalSection(&cs);
        bool playing=false;
        if (audioPlayer){
            playing=audioPlayer->isPlaying();
        }
        LeaveCriticalSection(&cs);
        return playing;
    }
    std::wstring CalendarRenderer::getCurrentAudioTrack() const{
        EnterCriticalSection(&cs);
        std::wstring trackName=L"";
        if (currentAudioTrackIndex>=0&&currentAudioTrackIndex<(int)audioTracks.size()){
            trackName=audioTracks[currentAudioTrackIndex].displayName;
        }
        LeaveCriticalSection(&cs);
        return trackName;
    }
    void CalendarRenderer::scanAudioFiles(){
        EnterCriticalSection(&cs);
        if (audioFileManager){
            audioTracks=audioFileManager->scanAudioFiles();
            if (!audioTracks.empty()&&currentAudioTrackIndex<0){
                currentAudioTrackIndex=0;
            }
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::playAudioTrack(int index){
        EnterCriticalSection(&cs);
        if (index>=0&&index<(int)audioTracks.size()){
            currentAudioTrackIndex=index;
            if (audioPlayer){
                audioPlayer->stop();
                audioPlayer->play(audioTracks[index]);
            }
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::stopAudioPlayback(){
        EnterCriticalSection(&cs);
        if (audioPlayer){
            audioPlayer->stop();
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::pauseAudioPlayback(){
        EnterCriticalSection(&cs);
        if (audioPlayer){
            audioPlayer->pause();
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::resumeAudioPlayback(){
        EnterCriticalSection(&cs);
        if (audioPlayer){
            audioPlayer->resume();
        }
        LeaveCriticalSection(&cs);
    }
    void CalendarRenderer::seekAudio(long positionMillis){
        EnterCriticalSection(&cs);
        if (audioPlayer){
            audioPlayer->seek(positionMillis);
        }
        LeaveCriticalSection(&cs);
    }
}