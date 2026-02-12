#include "calendar_render.h"
#include <wincodec.h>
#include <sstream>
#include <iomanip>
#include <chrono>
#include <algorithm>    // for std::min, std::max
#include <filesystem>
#pragma comment(lib, "windowscodecs.lib")

namespace fs = std::filesystem;

namespace CalendarOverlay {

CalendarRenderer::CalendarRenderer()
    : d2dFactory(nullptr), renderTarget(nullptr),
      textBrush(nullptr), backgroundBrush(nullptr), eventBrush(nullptr),
      writeFactory(nullptr), textFormat(nullptr), titleFormat(nullptr),
      timeFormat(nullptr), hwnd(NULL),
      scrollOffset(0.0f), maxScrollOffset(0.0f), isScrolling(false),
      needsScrollbar(false), totalEventsHeight(0.0f), visibleHeight(0.0f),
      currentAudioTrackIndex(-1), audioControlsVisible(true),
      audioProgress(0.0f), isDraggingAudioProgress(false) {
    InitializeCriticalSection(&cs);
    lastMousePos.x = 0;
    lastMousePos.y = 0;
    audioPlayer = std::make_unique<Audio::AudioPlayerEngine>();
    audioFileManager = std::make_unique<Audio::AudioFileManager>();
    scanAudioFiles();
}

CalendarRenderer::~CalendarRenderer() {
    cleanup();
    DeleteCriticalSection(&cs);
}

// ---------------------------------------------------------------------
// Viewport layout – all sizes relative to window dimensions
// ---------------------------------------------------------------------
void CalendarRenderer::updateViewportLayout() {
    float w = renderSize.width;
    float h = renderSize.height;
    float minDim = std::min(w, h);
    if (w <= 0 || h <= 0) return;

    vpPadding          = minDim * 0.02f;          // 2% of smallest side
    vpEventHeight      = h * 0.06f;               // 6% of height
    vpTimeWidth        = w * 0.15f;               // 15% of width
    vpScrollbarWidth   = w * 0.015f;              // 1.5% of width
    vpAudioControlsHeight = h * 0.10f;            // 10% of height
    vpButtonSize       = h * 0.04f;               // 4% of height
    vpVolumeWidth      = w * 0.15f;               // 15% of width
    vpCornerRadius     = minDim * 0.01f;          // 1% of smallest side
    vpFontSize         = h * 0.025f;              // 2.5% of height
    vpLineThickness    = minDim * 0.001f;         // 0.1% of smallest side

    // Clamp font size to reasonable range
    if (vpFontSize < 9.0f) vpFontSize = 9.0f;
    if (vpFontSize > 24.0f) vpFontSize = 24.0f;

    // Recreate fonts if they exist
    if (writeFactory) {
        if (textFormat) { textFormat->Release(); textFormat = nullptr; }
        if (titleFormat) { titleFormat->Release(); titleFormat = nullptr; }
        if (timeFormat) { timeFormat->Release(); timeFormat = nullptr; }

        writeFactory->CreateTextFormat(
            L"Segoe UI", NULL,
            DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_NORMAL,
            DWRITE_FONT_STRETCH_NORMAL,
            vpFontSize,
            L"en-us", &textFormat);
        writeFactory->CreateTextFormat(
            L"Segoe UI", NULL,
            DWRITE_FONT_WEIGHT_BOLD, DWRITE_FONT_STYLE_NORMAL,
            DWRITE_FONT_STRETCH_NORMAL,
            vpFontSize + 2.0f,
            L"en-us", &titleFormat);
        writeFactory->CreateTextFormat(
            L"Segoe UI", NULL,
            DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_ITALIC,
            DWRITE_FONT_STRETCH_NORMAL,
            vpFontSize - 2.0f,
            L"en-us", &timeFormat);

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
}

// ---------------------------------------------------------------------
// Device resources
// ---------------------------------------------------------------------
bool CalendarRenderer::initialize(HWND window) {
    hwnd = window;

    HRESULT hr = D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, &d2dFactory);
    if (FAILED(hr)) return false;

    hr = DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory),
                             reinterpret_cast<IUnknown**>(&writeFactory));
    if (FAILED(hr)) return false;

    return createDeviceResources();
}

bool CalendarRenderer::createDeviceResources() {
    if (!d2dFactory || !hwnd) return false;

    RECT rc;
    GetClientRect(hwnd, &rc);
    D2D1_SIZE_U size = D2D1::SizeU(rc.right - rc.left, rc.bottom - rc.top);

    HRESULT hr = d2dFactory->CreateHwndRenderTarget(
        D2D1::RenderTargetProperties(),
        D2D1::HwndRenderTargetProperties(hwnd, size),
        &renderTarget);
    if (FAILED(hr)) return false;

    if (renderTarget) {
        renderTarget->CreateSolidColorBrush(toColorF(config.textColor), &textBrush);
        renderTarget->CreateSolidColorBrush(toColorF(config.backgroundColor), &backgroundBrush);
        renderTarget->CreateSolidColorBrush(D2D1::ColorF(D2D1::ColorF::LightBlue), &eventBrush);
        renderSize = renderTarget->GetSize();
        updateViewportLayout();
    }
    return true;
}

void CalendarRenderer::releaseDeviceResources() {
    if (textBrush) { textBrush->Release(); textBrush = nullptr; }
    if (backgroundBrush) { backgroundBrush->Release(); backgroundBrush = nullptr; }
    if (eventBrush) { eventBrush->Release(); eventBrush = nullptr; }
    if (renderTarget) { renderTarget->Release(); renderTarget = nullptr; }
}

void CalendarRenderer::resize(int width, int height) {
    EnterCriticalSection(&cs);
    if (renderTarget) {
        renderTarget->Resize(D2D1::SizeU(width, height));
        renderSize = renderTarget->GetSize();
        updateViewportLayout();   // recalc all sizes based on new window size
    }
    LeaveCriticalSection(&cs);
}

// ---------------------------------------------------------------------
// Rendering – all positions use vp* values
// ---------------------------------------------------------------------
void CalendarRenderer::render() {
    EnterCriticalSection(&cs);
    if (!renderTarget) {
        LeaveCriticalSection(&cs);
        return;
    }

    renderTarget->BeginDraw();
    renderTarget->SetTransform(D2D1::Matrix3x2F::Identity());
    renderTarget->Clear(D2D1::ColorF(0, 0, 0, 0));

    if (config.wallpaperMode) {
        drawWallpaperContent();
    } else {
        drawBackground();
        drawDateHeader();
        drawEvents();
        drawCurrentTime();
        drawAudioControls();
    }

    HRESULT hr = renderTarget->EndDraw();
    if (hr == D2DERR_RECREATE_TARGET) {
        releaseDeviceResources();
        createDeviceResources();
    }

    framesRendered++;
    lastRenderTime = GetTickCount64();
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::drawBackground() {
    if (!backgroundBrush || !renderTarget) return;

    D2D1_ROUNDED_RECT roundedRect = D2D1::RoundedRect(
        D2D1::RectF(0, 0, renderSize.width, renderSize.height),
        vpCornerRadius, vpCornerRadius);
    renderTarget->FillRoundedRectangle(roundedRect, backgroundBrush);

    ID2D1SolidColorBrush* borderBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(1.0f, 1.0f, 1.0f, 0.2f), &borderBrush);
    renderTarget->DrawRoundedRectangle(roundedRect, borderBrush, vpLineThickness);
    borderBrush->Release();
}

void CalendarRenderer::drawDateHeader() {
    if (!textBrush || !titleFormat || !renderTarget) return;

    auto now = std::chrono::system_clock::now();
    auto nowTime = std::chrono::system_clock::to_time_t(now);
    std::tm localTime;
    localtime_s(&localTime, &nowTime);
    std::wstringstream wss;
    wss << std::put_time(&localTime, L"%A, %B %d, %Y");

    D2D1_RECT_F textRect = D2D1::RectF(
        vpPadding,
        vpPadding,
        renderSize.width - vpPadding,
        vpPadding + 30.0f);
    renderTarget->DrawTextW(wss.str().c_str(),
                            static_cast<UINT32>(wss.str().length()),
                            titleFormat, textRect, textBrush);

    ID2D1SolidColorBrush* lineBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(1.0f, 1.0f, 1.0f, 0.3f), &lineBrush);
    float lineY = vpPadding + 35.0f;
    renderTarget->DrawLine(D2D1::Point2F(vpPadding, lineY),
                           D2D1::Point2F(renderSize.width - vpPadding, lineY),
                           lineBrush, vpLineThickness);
    lineBrush->Release();
}

void CalendarRenderer::drawEvents() {
    if (!textBrush || !textFormat || !renderTarget) return;

    float startY = vpPadding + 50.0f;
    float currentY = startY;
    auto upcomingEvents = getUpcomingEvents(24);
    totalEventsHeight = static_cast<float>(upcomingEvents.size()) * (vpEventHeight + 5.0f);
    visibleHeight = renderSize.height - startY - vpPadding - 25.0f;

    needsScrollbar = (totalEventsHeight > visibleHeight);
    if (needsScrollbar) {
        maxScrollOffset = totalEventsHeight - visibleHeight;
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;
        currentY -= scrollOffset;
        drawScrollbar();
    } else {
        scrollOffset = 0;
        maxScrollOffset = 0;
    }

    float visibleTop = startY;
    float visibleBottom = renderSize.height - vpPadding - 25.0f;

    for (const auto& event : upcomingEvents) {
        float eventTop = currentY;
        float eventBottom = currentY + vpEventHeight;
        if (eventBottom > visibleTop && eventTop < visibleBottom) {
            drawEvent(event, currentY);
        }
        currentY += vpEventHeight + 5.0f;
        if (currentY > visibleBottom) break;
    }
}

void CalendarRenderer::drawEvent(const CalendarEvent& event, float yPos) {
    if (!eventBrush || !textBrush || !textFormat || !timeFormat || !renderTarget) return;

    float eventRight = renderSize.width - vpPadding;
    if (needsScrollbar) {
        eventRight -= (vpScrollbarWidth + vpPadding * 0.5f);
    }

    auto now = std::chrono::system_clock::now();
    auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
    long long timeUntilStart = event.startTime - nowMs;
    long long timeSinceEnd = nowMs - event.endTime;

    D2D1_COLOR_F eventColor;
    if (timeSinceEnd > 0) {
        eventColor = D2D1::ColorF(0.7f, 0.7f, 0.7f, 0.7f);
    } else if (nowMs >= event.startTime && nowMs <= event.endTime) {
        eventColor = D2D1::ColorF(1.0f, 0.0f, 0.0f, 0.7f);
    } else if (timeUntilStart > 0 && timeUntilStart <= 3600000) {
        eventColor = D2D1::ColorF(1.0f, 0.5f, 0.0f, 0.7f);
    } else {
        eventColor = toColorF(event.colorR, event.colorG, event.colorB, 0.7f);
    }

    D2D1_ROUNDED_RECT eventRect = D2D1::RoundedRect(
        D2D1::RectF(vpPadding, yPos, eventRight, yPos + vpEventHeight),
        vpCornerRadius * 0.5f, vpCornerRadius * 0.5f);
    eventBrush->SetColor(eventColor);
    renderTarget->FillRoundedRectangle(eventRect, eventBrush);

    // Time
    auto eventTime = std::chrono::system_clock::from_time_t(event.startTime / 1000);
    auto eventTimeT = std::chrono::system_clock::to_time_t(eventTime);
    std::tm eventTm;
    localtime_s(&eventTm, &eventTimeT);
    std::wstringstream timeStream;
    timeStream << std::put_time(&eventTm, L"%I:%M %p");
    D2D1_RECT_F timeRect = D2D1::RectF(
        vpPadding + vpPadding * 0.5f,
        yPos + vpPadding * 0.5f,
        vpPadding + vpTimeWidth,
        yPos + vpEventHeight - vpPadding * 0.5f);
    renderTarget->DrawTextW(timeStream.str().c_str(),
                            static_cast<UINT32>(timeStream.str().length()),
                            timeFormat, timeRect, textBrush);

    // Title
    std::wstring title;
    for (int i = 0; i < 256 && event.title[i] != '\0'; i++)
        title += static_cast<wchar_t>(event.title[i]);
    D2D1_RECT_F titleRect = D2D1::RectF(
        vpPadding + vpTimeWidth + vpPadding * 0.5f,
        yPos + vpPadding * 0.5f,
        eventRight - vpPadding * 0.5f,
        yPos + vpEventHeight - vpPadding * 0.5f);
    renderTarget->DrawTextW(title.c_str(),
                            static_cast<UINT32>(title.length()),
                            textFormat, titleRect, textBrush);
}

void CalendarRenderer::drawScrollbar() {
    if (!renderTarget || !needsScrollbar) return;

    float scrollbarX = renderSize.width - vpPadding - vpScrollbarWidth;
    float scrollAreaTop = vpPadding + 50.0f;
    float scrollAreaBottom = renderSize.height - vpPadding - 25.0f;
    float scrollAreaHeight = scrollAreaBottom - scrollAreaTop;

    ID2D1SolidColorBrush* trackBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.3f, 0.3f, 0.3f, 0.3f), &trackBrush);
    D2D1_RECT_F trackRect = D2D1::RectF(
        scrollbarX, scrollAreaTop,
        scrollbarX + vpScrollbarWidth, scrollAreaBottom);
    renderTarget->FillRectangle(trackRect, trackBrush);
    trackBrush->Release();

    float thumbHeight = (visibleHeight / totalEventsHeight) * scrollAreaHeight;
    if (thumbHeight < 20.0f) thumbHeight = 20.0f;
    float thumbTop = scrollAreaTop + (scrollOffset / totalEventsHeight) * scrollAreaHeight;
    float thumbBottom = thumbTop + thumbHeight;
    if (thumbBottom > scrollAreaBottom) {
        thumbTop = scrollAreaBottom - thumbHeight;
        thumbBottom = scrollAreaBottom;
    }

    ID2D1SolidColorBrush* thumbBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.6f, 0.6f, 0.6f, 0.6f), &thumbBrush);
    D2D1_RECT_F thumbRect = D2D1::RectF(
        scrollbarX, thumbTop,
        scrollbarX + vpScrollbarWidth, thumbBottom);
    renderTarget->FillRectangle(thumbRect, thumbBrush);
    thumbBrush->Release();

    ID2D1SolidColorBrush* thumbBorderBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.8f, 0.8f, 0.8f, 0.8f), &thumbBorderBrush);
    renderTarget->DrawRectangle(thumbRect, thumbBorderBrush, vpLineThickness);
    thumbBorderBrush->Release();
}

void CalendarRenderer::drawCurrentTime() {
    if (!textBrush || !textFormat || !renderTarget) return;

    auto now = std::chrono::system_clock::now();
    auto nowTime = std::chrono::system_clock::to_time_t(now);
    std::tm localTime;
    localtime_s(&localTime, &nowTime);
    std::wstringstream wss;
    wss << std::put_time(&localTime, L"%I:%M:%S %p");

    D2D1_RECT_F textRect = D2D1::RectF(
        vpPadding,
        renderSize.height - 25.0f,
        renderSize.width - vpPadding,
        renderSize.height - 5.0f);
    renderTarget->DrawTextW(wss.str().c_str(),
                            static_cast<UINT32>(wss.str().length()),
                            timeFormat, textRect, textBrush);
}

void CalendarRenderer::drawAudioControls() {
    if (!textBrush || !textFormat || !renderTarget || !audioPlayer || !audioControlsVisible)
        return;

    float controlsTop = renderSize.height - vpAudioControlsHeight - 5.0f;
    float controlsWidth = renderSize.width - 2 * vpPadding;
    float controlsLeft = vpPadding;

    // Background
    ID2D1SolidColorBrush* controlsBgBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.1f, 0.1f, 0.1f, 0.8f), &controlsBgBrush);
    D2D1_ROUNDED_RECT bgRect = D2D1::RoundedRect(
        D2D1::RectF(controlsLeft, controlsTop,
                    controlsLeft + controlsWidth, controlsTop + vpAudioControlsHeight),
        vpCornerRadius * 0.5f, vpCornerRadius * 0.5f);
    renderTarget->FillRoundedRectangle(bgRect, controlsBgBrush);
    controlsBgBrush->Release();

    ID2D1SolidColorBrush* borderBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.3f, 0.3f, 0.3f, 0.8f), &borderBrush);
    renderTarget->DrawRoundedRectangle(bgRect, borderBrush, vpLineThickness);
    borderBrush->Release();

    // Buttons
    float buttonSize = vpButtonSize;
    float buttonSpacing = vpPadding * 0.5f;
    float currentX = controlsLeft + vpPadding;
    float buttonY = controlsTop + vpPadding;

    ID2D1SolidColorBrush* buttonBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.8f, 0.8f, 0.8f, 1.0f), &buttonBrush);

    // Prev
    D2D1_RECT_F prevRect = D2D1::RectF(currentX, buttonY, currentX + buttonSize, buttonY + buttonSize);
    renderTarget->FillRectangle(prevRect, buttonBrush);
    std::wstring prevText = L"<";
    renderTarget->DrawTextW(prevText.c_str(), (UINT32)prevText.length(), textFormat, prevRect, textBrush);
    currentX += buttonSize + buttonSpacing;

    // Play/Pause
    D2D1_RECT_F playPauseRect = D2D1::RectF(currentX, buttonY, currentX + buttonSize, buttonY + buttonSize);
    renderTarget->FillRectangle(playPauseRect, buttonBrush);
    std::wstring playPauseText = audioPlayer->isPlaying() ? L"||" : L">";
    renderTarget->DrawTextW(playPauseText.c_str(), (UINT32)playPauseText.length(), textFormat, playPauseRect, textBrush);
    currentX += buttonSize + buttonSpacing;

    // Next
    D2D1_RECT_F nextRect = D2D1::RectF(currentX, buttonY, currentX + buttonSize, buttonY + buttonSize);
    renderTarget->FillRectangle(nextRect, buttonBrush);
    std::wstring nextText = L">";
    renderTarget->DrawTextW(nextText.c_str(), (UINT32)nextText.length(), textFormat, nextRect, textBrush);
    currentX += buttonSize + buttonSpacing;

    buttonBrush->Release();

    // Volume bar
    float volumeHeight = 10.0f;
    float volumeY = buttonY + (buttonSize - volumeHeight) / 2;
    D2D1_RECT_F volumeTrackRect = D2D1::RectF(
        currentX, volumeY,
        currentX + vpVolumeWidth, volumeY + volumeHeight);
    ID2D1SolidColorBrush* volumeTrackBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.3f, 0.3f, 0.3f, 1.0f), &volumeTrackBrush);
    renderTarget->FillRectangle(volumeTrackRect, volumeTrackBrush);
    volumeTrackBrush->Release();

    float volumeLevel = audioPlayer->getVolume();
    float volumeFillWidth = vpVolumeWidth * volumeLevel;
    D2D1_RECT_F volumeFillRect = D2D1::RectF(
        currentX, volumeY,
        currentX + volumeFillWidth, volumeY + volumeHeight);
    ID2D1SolidColorBrush* volumeFillBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.0f, 0.7f, 0.0f, 1.0f), &volumeFillBrush);
    renderTarget->FillRectangle(volumeFillRect, volumeFillBrush);
    volumeFillBrush->Release();

    currentX += vpVolumeWidth + vpPadding;

    // Track name
    std::wstring trackName = getCurrentAudioTrack();
    if (trackName.empty()) trackName = L"No track";
    D2D1_RECT_F trackNameRect = D2D1::RectF(
        currentX, buttonY,
        controlsLeft + controlsWidth - vpPadding, buttonY + buttonSize);
    renderTarget->DrawTextW(trackName.c_str(), (UINT32)trackName.length(), textFormat, trackNameRect, textBrush);

    // Progress bar
    float progressBarY = controlsTop + vpAudioControlsHeight - 15.0f;
    float progressBarWidth = controlsWidth - 2 * vpPadding;
    float progressBarHeight = 3.0f;
    D2D1_RECT_F progressTrackRect = D2D1::RectF(
        controlsLeft + vpPadding, progressBarY,
        controlsLeft + vpPadding + progressBarWidth, progressBarY + progressBarHeight);
    ID2D1SolidColorBrush* progressTrackBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.3f, 0.3f, 0.3f, 1.0f), &progressTrackBrush);
    renderTarget->FillRectangle(progressTrackRect, progressTrackBrush);
    progressTrackBrush->Release();

    float progress = 0.0f;
    if (audioPlayer->isPlaying() || audioPlayer->isPaused()) {
        long currentPos = audioPlayer->getCurrentPosition();
        long duration = audioPlayer->getDuration();
        if (duration > 0) progress = static_cast<float>(currentPos) / static_cast<float>(duration);
    }
    float progressFillWidth = progressBarWidth * progress;
    D2D1_RECT_F progressFillRect = D2D1::RectF(
        controlsLeft + vpPadding, progressBarY,
        controlsLeft + vpPadding + progressFillWidth, progressBarY + progressBarHeight);
    ID2D1SolidColorBrush* progressFillBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.0f, 0.5f, 1.0f, 1.0f), &progressFillBrush);
    renderTarget->FillRectangle(progressFillRect, progressFillBrush);
    progressFillBrush->Release();
}

void CalendarRenderer::drawWallpaperContent() {
    if (!textBrush || !titleFormat || !textFormat || !timeFormat || !renderTarget || !eventBrush)
        return;

    float cornerPadding = vpPadding;
    D2D1_RECT_F contentRect = D2D1::RectF(
        cornerPadding, cornerPadding,
        renderSize.width - cornerPadding, renderSize.height - cornerPadding);

    ID2D1SolidColorBrush* bgBrush = nullptr;
    renderTarget->CreateSolidColorBrush(D2D1::ColorF(0.1f, 0.1f, 0.1f, 0.7f), &bgBrush);
    D2D1_ROUNDED_RECT bgRect = D2D1::RoundedRect(contentRect, vpCornerRadius, vpCornerRadius);
    renderTarget->FillRoundedRectangle(bgRect, bgBrush);
    bgBrush->Release();

    auto now = std::chrono::system_clock::now();
    auto nowTime = std::chrono::system_clock::to_time_t(now);
    std::tm localTime;
    localtime_s(&localTime, &nowTime);
    std::wstringstream dateStream;
    dateStream << std::put_time(&localTime, L"%A, %B %d");
    D2D1_RECT_F dateRect = D2D1::RectF(
        contentRect.left + vpPadding, contentRect.top + vpPadding,
        contentRect.right - vpPadding, contentRect.top + 35.0f);
    renderTarget->DrawTextW(dateStream.str().c_str(),
                            static_cast<UINT32>(dateStream.str().length()),
                            titleFormat, dateRect, textBrush);

    std::wstringstream timeStream;
    timeStream << std::put_time(&localTime, L"%I:%M %p");
    D2D1_RECT_F timeRect = D2D1::RectF(
        contentRect.left + vpPadding, contentRect.top + 40.0f,
        contentRect.right - vpPadding, contentRect.top + 65.0f);
    renderTarget->DrawTextW(timeStream.str().c_str(),
                            static_cast<UINT32>(timeStream.str().length()),
                            titleFormat, timeRect, textBrush);

    auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
    float eventStartY = contentRect.top + 80.0f;
    float eventSpacing = 25.0f;
    auto upcomingEvents = getUpcomingEvents(12);
    for (size_t i = 0; i < std::min(upcomingEvents.size(), size_t(3)); i++) {
        const auto& event = upcomingEvents[i];
        long long timeUntilStart = event.startTime - nowMs;
        long long timeSinceEnd = nowMs - event.endTime;

        float dotSize = 6.0f;
        D2D1_ELLIPSE statusDot = D2D1::Ellipse(
            D2D1::Point2F(contentRect.left + vpPadding, eventStartY + vpPadding * 0.5f),
            dotSize, dotSize);

        if (timeSinceEnd > 0) {
            eventBrush->SetColor(D2D1::ColorF(0.7f, 0.7f, 0.7f, 0.7f));
        } else if (nowMs >= event.startTime && nowMs <= event.endTime) {
            eventBrush->SetColor(D2D1::ColorF(1.0f, 0.0f, 0.0f, 0.7f));
        } else if (timeUntilStart > 0 && timeUntilStart <= 3600000) {
            eventBrush->SetColor(D2D1::ColorF(1.0f, 0.5f, 0.0f, 0.7f));
        } else {
            eventBrush->SetColor(toColorF(event.colorR, event.colorG, event.colorB, 0.7f));
        }
        renderTarget->FillEllipse(statusDot, eventBrush);

        auto eventTime = std::chrono::system_clock::from_time_t(event.startTime / 1000);
        auto eventTimeT = std::chrono::system_clock::to_time_t(eventTime);
        std::tm eventTm;
        localtime_s(&eventTm, &eventTimeT);
        std::wstringstream eventTimeStream;
        eventTimeStream << std::put_time(&eventTm, L"%I:%M");
        D2D1_RECT_F eventTimeRect = D2D1::RectF(
            contentRect.left + vpPadding * 2, eventStartY,
            contentRect.left + vpTimeWidth, eventStartY + 20.0f);
        renderTarget->DrawTextW(eventTimeStream.str().c_str(),
                                static_cast<UINT32>(eventTimeStream.str().length()),
                                timeFormat, eventTimeRect, textBrush);

        std::wstring title;
        for (int j = 0; j < 256 && event.title[j] != '\0'; j++)
            title += static_cast<wchar_t>(event.title[j]);
        if (title.length() > 20)
            title = title.substr(0, 17) + L"...";
        D2D1_RECT_F eventTitleRect = D2D1::RectF(
            contentRect.left + vpTimeWidth + vpPadding, eventStartY,
            contentRect.right - vpPadding, eventStartY + 20.0f);
        renderTarget->DrawTextW(title.c_str(),
                                static_cast<UINT32>(title.length()),
                                textFormat, eventTitleRect, textBrush);

        eventStartY += eventSpacing;
    }

    if (upcomingEvents.empty()) {
        std::wstring noEvents = L"No upcoming events";
        D2D1_RECT_F noEventsRect = D2D1::RectF(
            contentRect.left + vpPadding, eventStartY,
            contentRect.right - vpPadding, eventStartY + 20.0f);
        renderTarget->DrawTextW(noEvents.c_str(),
                                static_cast<UINT32>(noEvents.length()),
                                textFormat, noEventsRect, textBrush);
    }
}

// ---------------------------------------------------------------------
// Event management
// ---------------------------------------------------------------------
void CalendarRenderer::setEvents(const std::vector<CalendarEvent>& newEvents) {
    EnterCriticalSection(&cs);
    events = newEvents;
    scrollOffset = 0;
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::setConfig(const OverlayConfig& newConfig) {
    EnterCriticalSection(&cs);
    config = newConfig;
    if (renderTarget && textBrush && backgroundBrush) {
        textBrush->SetColor(toColorF(config.textColor));
        backgroundBrush->SetColor(toColorF(config.backgroundColor));
    }
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::cleanup() {
    releaseDeviceResources();
    if (writeFactory) { writeFactory->Release(); writeFactory = nullptr; }
    if (d2dFactory) { d2dFactory->Release(); d2dFactory = nullptr; }
}

D2D1_COLOR_F CalendarRenderer::toColorF(uint32_t color) const {
    float a = ((color >> 24) & 0xFF) / 255.0f;
    float r = ((color >> 16) & 0xFF) / 255.0f;
    float g = ((color >> 8) & 0xFF) / 255.0f;
    float b = (color & 0xFF) / 255.0f;
    return D2D1::ColorF(r, g, b, a);
}

D2D1::ColorF CalendarRenderer::toColorF(uint8_t r, uint8_t g, uint8_t b, float a) const {
    return D2D1::ColorF(r / 255.0f, g / 255.0f, b / 255.0f, a);
}

std::vector<CalendarEvent> CalendarRenderer::getUpcomingEvents(int hours) const {
    std::vector<CalendarEvent> upcoming;
    auto now = std::chrono::system_clock::now();
    auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
    auto pastCutoff = nowMs - (12 * 3600 * 1000);
    auto futureCutoff = nowMs + (hours * 3600 * 1000);

    EnterCriticalSection(&cs);
    for (const auto& event : events) {
        if (event.startTime >= pastCutoff && event.startTime <= futureCutoff) {
            upcoming.push_back(event);
        }
    }
    std::sort(upcoming.begin(), upcoming.end(),
              [](const CalendarEvent& a, const CalendarEvent& b) { return a.startTime < b.startTime; });
    LeaveCriticalSection(&cs);
    return upcoming;
}

// ---------------------------------------------------------------------
// Mouse handling
// ---------------------------------------------------------------------
void CalendarRenderer::handleMouseWheel(float delta) {
    EnterCriticalSection(&cs);
    if (needsScrollbar) {
        float scrollSpeed = vpEventHeight * 3;
        scrollOffset += delta * scrollSpeed;
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;
        if (hwnd) InvalidateRect(hwnd, NULL, FALSE);
    }
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::handleMouseDown(int x, int y) {
    EnterCriticalSection(&cs);
    if (needsScrollbar) {
        float scrollbarX = renderSize.width - vpPadding - vpScrollbarWidth;
        D2D1_RECT_F scrollbarRect = D2D1::RectF(
            scrollbarX,
            vpPadding + 50.0f,
            scrollbarX + vpScrollbarWidth,
            renderSize.height - vpPadding - 25.0f);
        if (x >= scrollbarRect.left && x <= scrollbarRect.right &&
            y >= scrollbarRect.top && y <= scrollbarRect.bottom) {
            isScrolling = true;
            lastMousePos.x = x;
            lastMousePos.y = y;
        }
    }
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::handleMouseMove(int x, int y) {
    EnterCriticalSection(&cs);
    if (isScrolling && needsScrollbar) {
        float deltaY = static_cast<float>(y - lastMousePos.y);
        float scrollAreaHeight = (renderSize.height - vpPadding - 25.0f) -
                                 (vpPadding + 50.0f);
        float scrollRatio = deltaY / scrollAreaHeight;
        scrollOffset += scrollRatio * totalEventsHeight;
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;
        lastMousePos.x = x;
        lastMousePos.y = y;
        if (hwnd) InvalidateRect(hwnd, NULL, FALSE);
    }
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::handleMouseUp(int x, int y) {
    EnterCriticalSection(&cs);
    isScrolling = false;
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::resetScroll() {
    EnterCriticalSection(&cs);
    scrollOffset = 0;
    LeaveCriticalSection(&cs);
}

bool CalendarRenderer::isScrollingActive() const {
    EnterCriticalSection(&cs);
    bool scrolling = isScrolling;
    LeaveCriticalSection(&cs);
    return scrolling;
}

// ---------------------------------------------------------------------
// Audio methods
// ---------------------------------------------------------------------
void CalendarRenderer::toggleAudioPlayback() {
    EnterCriticalSection(&cs);
    if (audioPlayer) {
        if (audioPlayer->isPlaying())
            audioPlayer->pause();
        else if (audioPlayer->isPaused())
            audioPlayer->resume();
        else if (currentAudioTrackIndex >= 0 && currentAudioTrackIndex < (int)audioTracks.size())
            audioPlayer->play(audioTracks[currentAudioTrackIndex]);
    }
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::playNextTrack() {
    EnterCriticalSection(&cs);
    if (audioTracks.empty()) { LeaveCriticalSection(&cs); return; }
    if (currentAudioTrackIndex < 0) currentAudioTrackIndex = 0;
    else currentAudioTrackIndex = (currentAudioTrackIndex + 1) % audioTracks.size();
    if (audioPlayer) {
        audioPlayer->stop();
        audioPlayer->play(audioTracks[currentAudioTrackIndex]);
    }
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::playPreviousTrack() {
    EnterCriticalSection(&cs);
    if (audioTracks.empty()) { LeaveCriticalSection(&cs); return; }
    if (currentAudioTrackIndex < 0) currentAudioTrackIndex = 0;
    else currentAudioTrackIndex = (currentAudioTrackIndex - 1 + audioTracks.size()) % audioTracks.size();
    if (audioPlayer) {
        audioPlayer->stop();
        audioPlayer->play(audioTracks[currentAudioTrackIndex]);
    }
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::setAudioVolume(float volume) {
    EnterCriticalSection(&cs);
    if (audioPlayer) audioPlayer->setVolume(volume);
    LeaveCriticalSection(&cs);
}

float CalendarRenderer::getAudioVolume() const {
    EnterCriticalSection(&cs);
    float volume = 0.5f;
    if (audioPlayer) volume = audioPlayer->getVolume();
    LeaveCriticalSection(&cs);
    return volume;
}

bool CalendarRenderer::isAudioPlaying() const {
    EnterCriticalSection(&cs);
    bool playing = false;
    if (audioPlayer) playing = audioPlayer->isPlaying();
    LeaveCriticalSection(&cs);
    return playing;
}

std::wstring CalendarRenderer::getCurrentAudioTrack() const {
    EnterCriticalSection(&cs);
    std::wstring trackName;
    if (currentAudioTrackIndex >= 0 && currentAudioTrackIndex < (int)audioTracks.size())
        trackName = audioTracks[currentAudioTrackIndex].displayName;
    LeaveCriticalSection(&cs);
    return trackName;
}

void CalendarRenderer::scanAudioFiles() {
    EnterCriticalSection(&cs);
    if (audioFileManager) {
        audioTracks = audioFileManager->scanAudioFiles();
        if (!audioTracks.empty() && currentAudioTrackIndex < 0)
            currentAudioTrackIndex = 0;
    }
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::playAudioTrack(int index) {
    EnterCriticalSection(&cs);
    if (index >= 0 && index < (int)audioTracks.size()) {
        currentAudioTrackIndex = index;
        if (audioPlayer) {
            audioPlayer->stop();
            audioPlayer->play(audioTracks[index]);
        }
    }
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::stopAudioPlayback() {
    EnterCriticalSection(&cs);
    if (audioPlayer) audioPlayer->stop();
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::pauseAudioPlayback() {
    EnterCriticalSection(&cs);
    if (audioPlayer) audioPlayer->pause();
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::resumeAudioPlayback() {
    EnterCriticalSection(&cs);
    if (audioPlayer) audioPlayer->resume();
    LeaveCriticalSection(&cs);
}

void CalendarRenderer::seekAudio(long positionMillis) {
    EnterCriticalSection(&cs);
    if (audioPlayer) audioPlayer->seek(positionMillis);
    LeaveCriticalSection(&cs);
}

} // namespace