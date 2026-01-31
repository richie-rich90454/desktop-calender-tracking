#include "desktop_window.h"
#include "calendar_render.h"
#include "event_manager.h"
#include "config.h"
#include <windowsx.h>
#include <commctrl.h>
#include <shellapi.h>
#include <dwmapi.h>
#include <versionhelpers.h>
#include <iostream>
#include <memory>
#include <vector>
#include <string>
#include <sstream>
#include <cmath>

#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "dwmapi.lib")

namespace CalendarOverlay {
    
    DesktopWindow::DesktopWindow() : 
        hwnd(NULL), 
        hInstance(GetModuleHandle(NULL)), 
        visible(false), 
        dragging(false), 
        dragStartX(0), 
        dragStartY(0),
        windowX(100), 
        windowY(100), 
        windowWidth(400), 
        windowHeight(600),
        renderTimer(0), 
        updateTimer(0), 
        trayIconVisible(false), 
        alpha(255), 
        clickThrough(false), 
        wallpaperMode(false), 
        fullScreenWallpaper(false), 
        desktopCheckTimer(0), 
        lastActiveWindow(NULL), 
        isOnDesktop(true),
        currentDPI(96),
        hasPerMonitorDPIAwareness(false),
        doubleBufferDC(NULL),
        doubleBufferBitmap(NULL),
        bufferWidth(0),
        bufferHeight(0){
        
        className=L"CalendarOverlayWindow";
        
        // Set DPI awareness for better scaling
        initializeDPIAwareness();
        
        // Get system DPI
        currentDPI=getSystemDPI();
        
        Config& cfg=Config::getInstance();
        cfg.load();
        this->config=cfg.getConfig();
        
        #ifdef WALLPAPER_MODE
        wallpaperMode=true;
        #else
        wallpaperMode=false;
        #endif
        
        // Parse command line arguments
        int argc;
        LPWSTR* argv=CommandLineToArgvW(GetCommandLineW(), &argc);
        if (argv){
            for (int i=1; i < argc; i++){
                std::wstring arg=argv[i];
                if (arg == L"--wallpaper" || arg == L"-w"){
                    wallpaperMode=true;
                }
                else if (arg == L"--no-wallpaper" || arg == L"-nw"){
                    wallpaperMode=false;
                }
                else if (arg == L"--position" && i + 1 < argc){
                    std::wstring pos=argv[++i];
                    if (pos == L"top-right" || pos == L"tr"){
                        config.position="top-right";
                    }
                    else if (pos == L"top-left" || pos == L"tl"){
                        config.position="top-left";
                    }
                    else if (pos == L"bottom-right" || pos == L"br"){
                        config.position="bottom-right";
                    }
                    else if (pos == L"bottom-left" || pos == L"bl"){
                        config.position="bottom-left";
                    }
                }
                else if (arg == L"--fullscreen" || arg == L"-f"){
                    fullScreenWallpaper=true;
                }
            }
            LocalFree(argv);
        }
        
        // Initialize window dimensions with DPI scaling
        if (wallpaperMode){
            RECT desktopRect;
            GetWindowRect(GetDesktopWindow(), &desktopRect);
            
            // Base dimensions at 96 DPI
            int baseWidth=400;
            int baseHeight=600;
            
            // Scale for current DPI
            windowWidth=scaleForDPI(baseWidth);
            windowHeight=scaleForDPI(baseHeight);
            
            // Ensure window doesn't exceed 1/4 of screen
            int screenWidth=desktopRect.right - desktopRect.left;
            int screenHeight=desktopRect.bottom - desktopRect.top;
            
            if (windowWidth > screenWidth / 4){
                windowWidth=screenWidth / 4;
            }
            if (windowHeight > screenHeight / 4){
                windowHeight=screenHeight / 4;
            }
            
            // Position with scaled padding
            int padding=scaleForDPI(10);
            windowX=desktopRect.right - windowWidth - padding;
            windowY=padding;
            
            if (config.position.empty()){
                config.position="top-right";
            }
        }
        else {
            // Scale saved positions from config (stored at 96 DPI)
            windowX=scaleForDPI(config.positionX);
            windowY=scaleForDPI(config.positionY);
            windowWidth=scaleForDPI(config.width);
            windowHeight=scaleForDPI(config.height);
        }
        
        alpha=static_cast<BYTE>(config.opacity * 255);
        clickThrough=config.clickThrough;
        
        renderer=std::make_unique<CalendarRenderer>();
        eventManager=std::make_unique<EventManager>();
    }
    
    DesktopWindow::~DesktopWindow(){
        close();
        cleanupDoubleBuffer();
    }
    
    void DesktopWindow::initializeDPIAwareness(){
        // Try per-monitor DPI awareness for Windows 10+
        HMODULE shcore=LoadLibrary(L"Shcore.dll");
        if (shcore){
            // Check if SetProcessDpiAwareness exists (Windows 8.1+)
            typedef HRESULT(WINAPI* SetProcessDpiAwarenessProc)(int);
            auto SetProcessDpiAwareness=(SetProcessDpiAwarenessProc)GetProcAddress(shcore, "SetProcessDpiAwareness");
            
            if (SetProcessDpiAwareness){
                // Try per-monitor awareness first (value 2)
                if (SUCCEEDED(SetProcessDpiAwareness(2))){
                    hasPerMonitorDPIAwareness=true;
                }
            }
            FreeLibrary(shcore);
        }
        
        // Fallback to system DPI awareness for older systems
        if (!hasPerMonitorDPIAwareness){
            SetProcessDPIAware();  // This works on Windows Vista+
        }
    }
    
    bool DesktopWindow::create(){
        if (!registerWindowClass()){
            std::cerr << "Failed to register window class" << std::endl;
            return false;
        }
        
        if (!createWindowInstance()){
            std::cerr << "Failed to create window instance" << std::endl;
            return false;
        }
        
        // Initialize double buffer
        createDoubleBuffer(windowWidth, windowHeight);
        
        // Initialize renderer
        if (!renderer->initialize(hwnd, currentDPI)){
            std::cerr << "Failed to initialize renderer" << std::endl;
            return false;
        }
        
        renderer->setConfig(config);
        
        if (!eventManager->initialize()){
            std::cerr << "Failed to initialize event manager" << std::endl;
        }
        
        renderer->setEvents(eventManager->getTodayEvents());
        
        // Set timers
        renderTimer=SetTimer(hwnd, 1, 16, NULL); // ~60 FPS
        updateTimer=SetTimer(hwnd, 2, config.refreshInterval * 1000, NULL);
        desktopCheckTimer=SetTimer(hwnd, 3, 500, NULL);
        
        createTrayIcon();
        return true;
    }
    
    bool DesktopWindow::registerWindowClass(){
        WNDCLASSEXW wc={};
        wc.cbSize=sizeof(WNDCLASSEXW);
        wc.style=CS_HREDRAW | CS_VREDRAW;
        wc.lpfnWndProc=windowProc;
        wc.hInstance=hInstance;
        wc.hCursor=LoadCursor(NULL, IDC_ARROW);
        wc.hbrBackground=(HBRUSH)GetStockObject(BLACK_BRUSH);
        wc.lpszClassName=className.c_str();
        wc.hIcon=LoadIcon(NULL, IDI_APPLICATION);
        wc.hIconSm=LoadIcon(NULL, IDI_APPLICATION);
        
        return RegisterClassExW(&wc) != 0;
    }
    
    bool DesktopWindow::createWindowInstance(){
        DWORD exStyle=WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE;
        
        if (wallpaperMode){
            exStyle |= WS_EX_TOPMOST;
        }
        else {
            exStyle |= WS_EX_TOPMOST;
        }
        
        if (clickThrough){
            exStyle |= WS_EX_TRANSPARENT;
        }
        
        hwnd=CreateWindowExW(
            exStyle,
            className.c_str(),
            L"Calendar Overlay",
            WS_POPUP,
            windowX, windowY, windowWidth, windowHeight,
            NULL, NULL, hInstance, this);
            
        if (!hwnd){
            return false;
        }
        
        // Set transparency
        SetLayeredWindowAttributes(hwnd, 0, 100, LWA_ALPHA);
        
        // Enable modern window effects if available
        HMODULE dwmapi=LoadLibrary(L"dwmapi.dll");
        if (dwmapi){
            // Check if we're on Windows 10 or later
            OSVERSIONINFOEX osvi={ sizeof(osvi), 10, 0 };
            DWORDLONG dwlConditionMask=0;
            VER_SET_CONDITION(dwlConditionMask, VER_MAJORVERSION, VER_GREATER_EQUAL);
            
            if (VerifyVersionInfo(&osvi, VER_MAJORVERSION, dwlConditionMask)){
                // Enable dark mode if available
                BOOL darkMode=TRUE;
                DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &darkMode, sizeof(darkMode));
                
                // Enable rounded corners on Windows 11
                OSVERSIONINFOEX osvi11={ sizeof(osvi11), 10, 0, 22000 };
                DWORDLONG dwlConditionMask11=0;
                VER_SET_CONDITION(dwlConditionMask11, VER_BUILDNUMBER, VER_GREATER_EQUAL);
                
                if (VerifyVersionInfo(&osvi11, VER_BUILDNUMBER, dwlConditionMask11)){
                    DWORD cornerPref=DWMWCP_ROUND;
                    DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, &cornerPref, sizeof(cornerPref));
                }
            }
            FreeLibrary(dwmapi);
        }
        
        if (wallpaperMode){
            SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, 
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_FRAMECHANGED);
        }
        
        return true;
    }
    
    void DesktopWindow::show(){
        if (hwnd && !visible){
            ShowWindow(hwnd, SW_SHOWNOACTIVATE);
            visible=true;
            InvalidateRect(hwnd, NULL, TRUE);
        }
    }
    
    void DesktopWindow::hide(){
        if (hwnd && visible){
            ShowWindow(hwnd, SW_HIDE);
            visible=false;
        }
    }
    
    void DesktopWindow::close(){
        if (renderTimer){
            KillTimer(hwnd, renderTimer);
        }
        if (updateTimer){
            KillTimer(hwnd, updateTimer);
        }
        if (desktopCheckTimer){
            KillTimer(hwnd, desktopCheckTimer);
        }
        
        removeTrayIcon();
        
        if (hwnd){
            DestroyWindow(hwnd);
            hwnd=NULL;
        }
        
        UnregisterClassW(className.c_str(), hInstance);
    }
    
    void DesktopWindow::update(){
        if (eventManager){
            eventManager->update();
            if (renderer){
                renderer->setEvents(eventManager->getTodayEvents());
            }
        }
    }
    
    void DesktopWindow::render(){
        if (!visible || !renderer || !hwnd){
            return;
        }
        
        PAINTSTRUCT ps;
        HDC hdc=BeginPaint(hwnd, &ps);
        
        // Use double buffering for smooth rendering
        if (doubleBufferDC && doubleBufferBitmap){
            // Clear the buffer with transparency
            RECT clientRect;
            GetClientRect(hwnd, &clientRect);
            
            HBRUSH bgBrush=CreateSolidBrush(RGB(0, 0, 0));
            FillRect(doubleBufferDC, &clientRect, bgBrush);
            DeleteObject(bgBrush);
            
            // Get renderer to draw to buffer
            renderer->render();
            
            // Apply transparency when copying
            BLENDFUNCTION blend={ 0 };
            blend.BlendOp=AC_SRC_OVER;
            blend.SourceConstantAlpha=alpha;
            blend.AlphaFormat=AC_SRC_ALPHA;
            
            // Copy buffer to screen with alpha blending
            HDC screenDC=GetDC(hwnd);
            AlphaBlend(screenDC, 0, 0, clientRect.right, clientRect.bottom,
                      doubleBufferDC, 0, 0, clientRect.right, clientRect.bottom,
                      blend);
            ReleaseDC(hwnd, screenDC);
        }
        else {
            // Fallback: render directly
            renderer->render();
        }
        
        EndPaint(hwnd, &ps);
    }
    
    LRESULT CALLBACK DesktopWindow::windowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam){
        DesktopWindow* window=nullptr;
        
        if (msg == WM_NCCREATE){
            CREATESTRUCT* create=reinterpret_cast<CREATESTRUCT*>(lParam);
            window=static_cast<DesktopWindow*>(create->lpCreateParams);
            SetWindowLongPtr(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(window));
            return DefWindowProc(hwnd, msg, wParam, lParam);
        }
        
        window=reinterpret_cast<DesktopWindow*>(GetWindowLongPtr(hwnd, GWLP_USERDATA));
        
        if (window){
            switch (msg){
                case WM_PAINT:
                    window->onPaint();
                    return 0;
                    
                case WM_TIMER:
                    window->onTimer();
                    return 0;
                    
                case WM_MOUSEMOVE:
                    window->onMouseMove(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
                    return 0;
                    
                case WM_LBUTTONDOWN:
                    window->onMouseDown(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
                    return 0;
                    
                case WM_LBUTTONUP:
                    window->onMouseUp(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
                    return 0;
                    
                case WM_MOUSEWHEEL: {
                    if (window->renderer){
                        int delta=GET_WHEEL_DELTA_WPARAM(wParam);
                        window->renderer->handleMouseWheel(-delta / 120.0f); // Standard wheel delta is 120 per notch
                    }
                    return 0;
                }
                    
                case WM_KEYDOWN:
                    window->onKeyDown(wParam);
                    return 0;
                    
                case WM_COMMAND:
                    window->onCommand(wParam);
                    return 0;
                    
                case WM_APP + 1:
                    if (lParam == WM_RBUTTONUP || lParam == WM_CONTEXTMENU){
                        POINT pt;
                        GetCursorPos(&pt);
                        window->showContextMenu(pt.x, pt.y);
                    }
                    return 0;
                    
                case WM_DPICHANGED:
                    window->onDPIChanged(wParam, lParam);
                    return 0;
                    
                case WM_SIZE:
                    if (window->hwnd){
                        RECT clientRect;
                        GetClientRect(window->hwnd, &clientRect);
                        window->resizeDoubleBuffer(clientRect.right, clientRect.bottom);
                        if (window->renderer){
                            window->renderer->resize(clientRect.right, clientRect.bottom);
                        }
                    }
                    return 0;
                    
                case WM_DESTROY:
                    PostQuitMessage(0);
                    return 0;
                    
                default:
                    break;
            }
        }
        
        return DefWindowProc(hwnd, msg, wParam, lParam);
    }
    
    void DesktopWindow::onPaint(){
        render();
    }
    
    void DesktopWindow::onTimer(){
        static int updateCounter=0;
        
        // Force repaint
        if (visible && hwnd){
            InvalidateRect(hwnd, NULL, FALSE);
        }
        
        // Check desktop visibility
        updateWindowVisibilityBasedOnDesktop();
        
        // Update data periodically
        if (++updateCounter >= 30){
            update();
            updateCounter=0;
        }
    }
    
    void DesktopWindow::onMouseMove(int x, int y){
        if (dragging){
            POINT cursorPos;
            GetCursorPos(&cursorPos);
            
            int deltaX=cursorPos.x - dragStartX;
            int deltaY=cursorPos.y - dragStartY;
            
            windowX += deltaX;
            windowY += deltaY;
            
            SetWindowPos(hwnd, NULL, windowX, windowY, 0, 0, 
                SWP_NOZORDER | SWP_NOSIZE | SWP_NOACTIVATE);
            
            dragStartX=cursorPos.x;
            dragStartY=cursorPos.y;
            
            // Save unscaled position
            Config& cfg=Config::getInstance();
            int unscaledX=unscaleForDPI(windowX);
            int unscaledY=unscaleForDPI(windowY);
            cfg.setPosition(unscaledX, unscaledY);
            cfg.save();
        }
    }
    
    void DesktopWindow::onMouseDown(int x, int y){
        bool ctrlDown=(GetKeyState(VK_CONTROL) & 0x8000) != 0;
        
        // First, forward to renderer for scrollbar handling
        if (renderer){
            renderer->handleMouseDown(x, y);
        }
        
        if (ctrlDown){
            dragging=true;
            POINT cursorPos;
            GetCursorPos(&cursorPos);
            dragStartX=cursorPos.x;
            dragStartY=cursorPos.y;
            return;
        }
        
        // Check if we clicked on scrollbar
        // If not on scrollbar, launch Java GUI
        if (renderer){
            // We'll let the renderer handle scrollbar clicks
            // If it's not a scrollbar click, launch the GUI
            // The renderer will set isScrolling if it's a scrollbar click
        }
        launchJavaGUI();
    }
    
    void DesktopWindow::onMouseUp(int x, int y){
        dragging=false;
        
        // Forward to renderer
        if (renderer){
            renderer->handleMouseUp(x, y);
        }
        
        if (clickThrough && hwnd){
            LONG exStyle=GetWindowLong(hwnd, GWL_EXSTYLE);
            exStyle |= WS_EX_TRANSPARENT;
            SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
        }
    }
    
    void DesktopWindow::onKeyDown(WPARAM key){
        if (key == VK_ESCAPE){
            hide();
        }
        else if (key == VK_F5){
            update();
        }
    }
    
    void DesktopWindow::onCommand(WPARAM wParam){
        switch (LOWORD(wParam)){
            case 1001: // Show/Hide
                if (visible){
                    hide();
                } else {
                    show();
                }
                break;
            case 1002: // Exit
                close();
                PostQuitMessage(0);
                break;
        }
    }
    
    void DesktopWindow::onDPIChanged(WPARAM wParam, LPARAM lParam){
        if (hasPerMonitorDPIAwareness && hwnd){
            UINT newDPI=HIWORD(wParam);
            
            // Update DPI in renderer
            if (renderer){
                renderer->onDPIChanged(newDPI);
            }
            
            // Calculate scale factor
            float scaleFactor=static_cast<float>(newDPI) / static_cast<float>(currentDPI);
            
            // Update window size and position
            RECT* suggestedRect=(RECT*)lParam;
            if (suggestedRect){
                windowX=suggestedRect->left;
                windowY=suggestedRect->top;
                windowWidth=suggestedRect->right - suggestedRect->left;
                windowHeight=suggestedRect->bottom - suggestedRect->top;
                
                SetWindowPos(hwnd, NULL,
                    windowX, windowY,
                    windowWidth, windowHeight,
                    SWP_NOZORDER | SWP_NOACTIVATE);
                
                // Resize double buffer
                resizeDoubleBuffer(windowWidth, windowHeight);
                
                // Resize renderer
                if (renderer){
                    renderer->resize(windowWidth, windowHeight);
                }
            }
            
            // Update DPI
            currentDPI=newDPI;
            
            // Force repaint
            InvalidateRect(hwnd, NULL, TRUE);
        }
    }
    
    void DesktopWindow::createTrayIcon(){
        memset(&trayIconData, 0, sizeof(trayIconData));
        trayIconData.cbSize=sizeof(NOTIFYICONDATA);
        trayIconData.hWnd=hwnd;
        trayIconData.uID=100;
        trayIconData.uFlags=NIF_ICON | NIF_MESSAGE | NIF_TIP;
        trayIconData.uCallbackMessage=WM_APP + 1;
        
        // Try to load application icon
        trayIconData.hIcon=(HICON)LoadImage(hInstance, 
            MAKEINTRESOURCE(1), 
            IMAGE_ICON, 
            GetSystemMetrics(SM_CXSMICON), 
            GetSystemMetrics(SM_CYSMICON), 
            0);
            
        if (!trayIconData.hIcon){
            trayIconData.hIcon=LoadIcon(NULL, IDI_APPLICATION);
        }
        
        wcscpy_s(trayIconData.szTip, L"Calendar Overlay");
        Shell_NotifyIcon(NIM_ADD, &trayIconData);
        trayIconVisible=true;
    }
    
    void DesktopWindow::removeTrayIcon(){
        if (trayIconVisible){
            Shell_NotifyIcon(NIM_DELETE, &trayIconData);
            trayIconVisible=false;
        }
    }
    
    void DesktopWindow::showContextMenu(int x, int y){
        HMENU hMenu=CreatePopupMenu();
        if (hMenu){
            // Add menu items with proper Unicode support
            InsertMenuW(hMenu, 0, MF_BYPOSITION | MF_STRING, 1001, L"Show/Hide");
            InsertMenuW(hMenu, 1, MF_BYPOSITION | MF_SEPARATOR, 0, NULL);
            InsertMenuW(hMenu, 2, MF_BYPOSITION | MF_STRING, 1002, L"Exit");
            
            SetForegroundWindow(hwnd);
            TrackPopupMenu(hMenu, TPM_RIGHTBUTTON | TPM_NOANIMATION, 
                          x, y, 0, hwnd, NULL);
            DestroyMenu(hMenu);
        }
    }
    
    void DesktopWindow::launchJavaGUI(){
        std::wstring javaPath=L"java";
        std::wstring jarPath=L"CalendarApp.jar";
        wchar_t exePath[MAX_PATH];
        GetModuleFileNameW(NULL, exePath, MAX_PATH);
        std::wstring exeDir=exePath;
        size_t lastSlash=exeDir.find_last_of(L"\\/");
        if (lastSlash != std::wstring::npos){
            exeDir=exeDir.substr(0, lastSlash + 1);
            jarPath=exeDir + L"CalendarApp.jar";
        }
        
        DWORD fileAttrib=GetFileAttributesW(jarPath.c_str());
        if (fileAttrib == INVALID_FILE_ATTRIBUTES || (fileAttrib & FILE_ATTRIBUTE_DIRECTORY)){
            jarPath=L"..\\dist\\CalendarApp.jar";
            fileAttrib=GetFileAttributesW(jarPath.c_str());
            if (fileAttrib == INVALID_FILE_ATTRIBUTES || (fileAttrib & FILE_ATTRIBUTE_DIRECTORY)){
                std::cerr << "Java JAR not found. Please ensure CalendarApp.jar is in the same directory." << std::endl;
                return;
            }
        }
        
        std::wstring command=L"\"" + javaPath + L"\" -jar \"" + jarPath + L"\"";
        STARTUPINFOW si={ sizeof(si) };
        PROCESS_INFORMATION pi={ 0 };
        if (CreateProcessW(NULL, const_cast<LPWSTR>(command.c_str()), NULL, NULL, FALSE, 
                           CREATE_NO_WINDOW, NULL, NULL, &si, &pi)){
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);
        } else {
            std::cerr << "Failed to launch Java application. Error: " << GetLastError() << std::endl;
        }
    }
    
    bool DesktopWindow::checkIfOnDesktop(){
        HWND foregroundWindow=GetForegroundWindow();
        if (!foregroundWindow){
            return true;
        }
        
        char className[256];
        GetClassNameA(foregroundWindow, className, sizeof(className));
        
        if (strcmp(className, "Progman") == 0 || 
            strcmp(className, "WorkerW") == 0 ||
            strcmp(className, "Shell_TrayWnd") == 0 ||
            strcmp(className, "Button") == 0){
            return true;
        }
        
        if (IsIconic(foregroundWindow) || !IsWindowVisible(foregroundWindow)){
            return true;
        }
        
        if (foregroundWindow == hwnd){
            return true;
        }
        
        return false;
    }
    
    void DesktopWindow::updateWindowVisibilityBasedOnDesktop(){
        bool currentlyOnDesktop=checkIfOnDesktop();
        
        if (currentlyOnDesktop != isOnDesktop){
            isOnDesktop=currentlyOnDesktop;
            if (isOnDesktop){
                show();
            } else {
                hide();
            }
        }
    }
    
    UINT DesktopWindow::getSystemDPI(){
        HDC hdc=GetDC(NULL);
        if (!hdc) return 96;
        
        UINT dpi=GetDeviceCaps(hdc, LOGPIXELSX);
        ReleaseDC(NULL, hdc);
        return dpi;
    }
    
    UINT DesktopWindow::getWindowDPI(){
        if (hasPerMonitorDPIAwareness){
            HMODULE shcore=LoadLibrary(L"Shcore.dll");
            if (shcore){
                typedef UINT(WINAPI* GetDpiForWindowProc)(HWND);
                auto GetDpiForWindow=(GetDpiForWindowProc)GetProcAddress(shcore, "GetDpiForWindow");
                if (GetDpiForWindow && hwnd){
                    UINT dpi=GetDpiForWindow(hwnd);
                    FreeLibrary(shcore);
                    return dpi;
                }
                FreeLibrary(shcore);
            }
        }
        return currentDPI;
    }
    
    int DesktopWindow::scaleForDPI(int value, UINT dpi){
        if (dpi == 0) dpi=currentDPI;
        return MulDiv(value, dpi, 96);
    }
    
    int DesktopWindow::unscaleForDPI(int value, UINT dpi){
        if (dpi == 0) dpi=currentDPI;
        return MulDiv(value, 96, dpi);
    }
    
    void DesktopWindow::createDoubleBuffer(int width, int height){
        cleanupDoubleBuffer();
        
        if (hwnd && width > 0 && height > 0){
            HDC hdc=GetDC(hwnd);
            if (hdc){
                doubleBufferDC=CreateCompatibleDC(hdc);
                doubleBufferBitmap=CreateCompatibleBitmap(hdc, width, height);
                SelectObject(doubleBufferDC, doubleBufferBitmap);
                ReleaseDC(hwnd, hdc);
                
                bufferWidth=width;
                bufferHeight=height;
            }
        }
    }
    
    void DesktopWindow::cleanupDoubleBuffer(){
        if (doubleBufferBitmap){
            DeleteObject(doubleBufferBitmap);
            doubleBufferBitmap=NULL;
        }
        if (doubleBufferDC){
            DeleteDC(doubleBufferDC);
            doubleBufferDC=NULL;
        }
        bufferWidth=0;
        bufferHeight=0;
    }
    
    void DesktopWindow::resizeDoubleBuffer(int width, int height){
        if (width != bufferWidth || height != bufferHeight){
            createDoubleBuffer(width, height);
        }
    }
    
    void DesktopWindow::setPosition(int x, int y){
        // Scale from 96 DPI to current DPI
        windowX=scaleForDPI(x);
        windowY=scaleForDPI(y);
        
        if (hwnd){
            SetWindowPos(hwnd, NULL, windowX, windowY, 0, 0, 
                SWP_NOZORDER | SWP_NOSIZE | SWP_NOACTIVATE);
        }
        
        Config& cfg=Config::getInstance();
        cfg.setPosition(x, y);
        cfg.save();
    }
    
    void DesktopWindow::setSize(int width, int height){
        // Scale from 96 DPI to current DPI
        windowWidth=scaleForDPI(width);
        windowHeight=scaleForDPI(height);
        
        if (hwnd){
            SetWindowPos(hwnd, NULL, 0, 0, windowWidth, windowHeight, 
                SWP_NOZORDER | SWP_NOMOVE | SWP_NOACTIVATE);
            
            // Resize double buffer
            resizeDoubleBuffer(windowWidth, windowHeight);
            
            // Resize renderer
            if (renderer){
                renderer->resize(windowWidth, windowHeight);
            }
        }
        
        Config& cfg=Config::getInstance();
        cfg.setSize(width, height);
        cfg.save();
    }
    
    void DesktopWindow::setOpacity(float opacity){
        if (opacity < 0.0f) opacity=0.0f;
        if (opacity > 1.0f) opacity=1.0f;
        
        alpha=static_cast<BYTE>(opacity * 255);
        config.opacity=opacity;
        
        if (hwnd){
            SetLayeredWindowAttributes(hwnd, 0, 100, LWA_ALPHA);
        }
        
        Config& cfg=Config::getInstance();
        cfg.setOpacity(opacity);
        cfg.save();
    }
    
    void DesktopWindow::setClickThrough(bool enabled){
        clickThrough=enabled;
        config.clickThrough=enabled;
        
        if (hwnd){
            LONG exStyle=GetWindowLong(hwnd, GWL_EXSTYLE);
            if (enabled){
                exStyle |= WS_EX_TRANSPARENT;
            }
            else {
                exStyle &= ~WS_EX_TRANSPARENT;
            }
            SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
        }
        
        Config& cfg=Config::getInstance();
        cfg.setClickThrough(enabled);
        cfg.save();
    }
    
    void DesktopWindow::adjustWindowStyle(){
        if (!hwnd) return;
        LONG exStyle=GetWindowLong(hwnd, GWL_EXSTYLE);
        exStyle |= WS_EX_TOPMOST | WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE;
        if (clickThrough){
            exStyle |= WS_EX_TRANSPARENT;
        }
        else {
            exStyle &= ~WS_EX_TRANSPARENT;
        }
        
        SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
        SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, 
            SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_FRAMECHANGED);
    }
    void DesktopWindow::updateWindowPosition(){
        if (hwnd){
            SetWindowPos(hwnd, NULL, windowX, windowY, 0, 0, 
                SWP_NOZORDER | SWP_NOSIZE | SWP_NOACTIVATE);
        }
    }
}