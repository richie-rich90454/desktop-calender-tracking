#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0500
#endif

#ifndef WINVER
#define WINVER 0x0500
#endif

#ifndef _WIN32_IE
#define _WIN32_IE 0x0500
#endif

#include "desktop_window.h"
#include "calendar_render.h"
#include "event_manager.h"
#include "config.h"
#include <windowsx.h>
#include <commctrl.h>
#include <shellapi.h>
#include <iostream>
#include <memory>
#pragma comment(lib, "comctl32.lib")
namespace CalendarOverlay{
    DesktopWindow::DesktopWindow() : hwnd(NULL), hInstance(GetModuleHandle(NULL)), visible(false), dragging(false), dragStartX(0), dragStartY(0), windowX(100), windowY(100), windowWidth(400), windowHeight(600), renderTimer(0), updateTimer(0), trayIconVisible(false), alpha(255), clickThrough(false){
        className=L"CalendarOverlayWindow";
        Config& cfg=Config::getInstance();
        cfg.load();
        this->config=cfg.getConfig();
        windowX=config.positionX;
        windowY=config.positionY;
        windowWidth=config.width;
        windowHeight=config.height;
        alpha=static_cast<BYTE>(config.opacity*255);
        clickThrough=config.clickThrough;
        renderer=std::make_unique<CalendarRenderer>();
        eventManager=std::make_unique<EventManager>();
    }
    DesktopWindow::~DesktopWindow(){
        close();
    }
    bool DesktopWindow::create(){
        if (!registerWindowClass()){
            std::cerr<<"Failed to register window class"<<std::endl;
            return false;
        }
        if (!createWindowInstance()){
            std::cerr<<"Failed to create window instance"<<std::endl;
            return false;
        }
        if (!eventManager->initialize()){
            std::cerr<<"Failed to initialize event manager"<<std::endl;
        }
        if (!renderer->initialize(hwnd)){
            std::cerr<<"Failed to initialize renderer"<<std::endl;
        }
        renderer->setConfig(config);
        renderer->setEvents(eventManager->getTodayEvents());
        renderTimer=SetTimer(hwnd, 1, 16, NULL);
        updateTimer=SetTimer(hwnd, 2, config.refreshInterval*1000, NULL);
        createTrayIcon();
        return true;
    }
    bool DesktopWindow::registerWindowClass(){
        WNDCLASSEXW wc={};
        wc.cbSize=sizeof(WNDCLASSEXW);
        wc.style=CS_HREDRAW|CS_VREDRAW;
        wc.lpfnWndProc=windowProc;
        wc.hInstance=hInstance;
        wc.hCursor=LoadCursor(NULL, IDC_ARROW);
        wc.hbrBackground=(HBRUSH)GetStockObject(BLACK_BRUSH);
        wc.lpszClassName=className.c_str();
        wc.hIcon=LoadIcon(NULL, IDI_APPLICATION);
        return RegisterClassExW(&wc)!=0;
    }
    bool DesktopWindow::createWindowInstance(){
        hwnd=CreateWindowExW(
            WS_EX_TOPMOST|WS_EX_TRANSPARENT|WS_EX_LAYERED|WS_EX_TOOLWINDOW|WS_EX_NOACTIVATE,
            className.c_str(),
            L"Calendar Overlay",
            WS_POPUP,
            windowX, windowY, windowWidth, windowHeight,
            NULL, NULL, hInstance, this);
        if (!hwnd){
            return false;
        }
        SetLayeredWindowAttributes(hwnd, RGB(0, 0, 0), alpha, LWA_ALPHA|LWA_COLORKEY);
        if (clickThrough){
            SetWindowLong(hwnd, GWL_EXSTYLE, GetWindowLong(hwnd, GWL_EXSTYLE)|WS_EX_TRANSPARENT);
        }
        return true;
    }
    void DesktopWindow::show(){
        if (hwnd&&!visible){
            ShowWindow(hwnd, SW_SHOWNOACTIVATE);
            visible=true;
        }
    }
    void DesktopWindow::hide(){
        if (hwnd&&visible){
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
            renderer->setEvents(eventManager->getTodayEvents());
        }
    }
    void DesktopWindow::render(){
        if (renderer&&visible){
            renderer->render();
        }
    }
    LRESULT CALLBACK DesktopWindow::windowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam){
        DesktopWindow* window=nullptr;
        if (msg==WM_NCCREATE){
            CREATESTRUCT* create=reinterpret_cast<CREATESTRUCT*>(lParam);
            window=static_cast<DesktopWindow*>(create->lpCreateParams);
            SetWindowLongPtr(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(window));
        }
        else{
            window=reinterpret_cast<DesktopWindow*>(GetWindowLongPtr(hwnd, GWLP_USERDATA));
        }
        if (window){
            switch (msg){
                case WM_PAINT:
                    window->onPaint();
                    break;
                case WM_TIMER:
                    window->onTimer();
                    break;
                case WM_MOUSEMOVE:
                    window->onMouseMove(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
                    break;
                case WM_LBUTTONDOWN:
                    window->onMouseDown(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
                    break;
                case WM_LBUTTONUP:
                    window->onMouseUp(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam));
                    break;
                case WM_KEYDOWN:
                    window->onKeyDown(wParam);
                    break;
                case WM_DESTROY:
                    PostQuitMessage(0);
                    break;
                default:
                    return DefWindowProc(hwnd, msg, wParam, lParam);
            }
            return 0;
        }
        return DefWindowProc(hwnd, msg, wParam, lParam);
    }
    void DesktopWindow::onPaint(){
        PAINTSTRUCT ps;
        HDC hdc=BeginPaint(hwnd, &ps);
        render();
        EndPaint(hwnd, &ps);
    }
    void DesktopWindow::onTimer(){
        static int updateCounter=0;
        render();
        if (++updateCounter>=30){
            update();
            updateCounter=0;
        }
    }
    void DesktopWindow::onMouseMove(int x, int y){
        if (dragging){
            POINT cursorPos;
            GetCursorPos(&cursorPos);
            int deltaX=cursorPos.x-dragStartX;
            int deltaY=cursorPos.y-dragStartY;
            windowX+=deltaX;
            windowY+=deltaY;
            SetWindowPos(hwnd, NULL, windowX, windowY, 0, 0, SWP_NOZORDER|SWP_NOSIZE);
            dragStartX=cursorPos.x;
            dragStartY=cursorPos.y;
            Config& cfg=Config::getInstance();
            cfg.setPosition(windowX, windowY);
            cfg.save();
        }
    }
    void DesktopWindow::onMouseDown(int x, int y){
        dragging=true;
        POINT cursorPos;
        GetCursorPos(&cursorPos);
        dragStartX=cursorPos.x;
        dragStartY=cursorPos.y;
        windowX=cursorPos.x-x;
        windowY=cursorPos.y-y;
        SetWindowLong(hwnd, GWL_EXSTYLE, GetWindowLong(hwnd, GWL_EXSTYLE) & ~WS_EX_TRANSPARENT);
    }
    void DesktopWindow::onMouseUp(int x, int y){
        dragging=false;
        if (clickThrough){
            SetWindowLong(hwnd, GWL_EXSTYLE, 
                GetWindowLong(hwnd, GWL_EXSTYLE)|WS_EX_TRANSPARENT);
        }
    }
    void DesktopWindow::onKeyDown(WPARAM key){
        if (key==VK_ESCAPE){
            hide();
        }
        else if (key==VK_F5){
            update();
        }
    }
    void DesktopWindow::createTrayIcon(){
        memset(&trayIconData, 0, sizeof(trayIconData));
        trayIconData.cbSize=sizeof(trayIconData);
        trayIconData.hWnd=hwnd;
        trayIconData.uID=100;
        trayIconData.uFlags=NIF_ICON|NIF_MESSAGE|NIF_TIP;
        trayIconData.uCallbackMessage=WM_APP+1;
        trayIconData.hIcon=LoadIcon(NULL, IDI_APPLICATION);
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
    void DesktopWindow::setPosition(int x, int y){
        windowX=x;
        windowY=y;
        if (hwnd){
            SetWindowPos(hwnd, NULL, windowX, windowY, 0, 0, SWP_NOZORDER|SWP_NOSIZE|SWP_NOACTIVATE);
        }
        Config& cfg=Config::getInstance();
        cfg.setPosition(x, y);
        cfg.save();
    }
    void DesktopWindow::setSize(int width, int height){
        windowWidth=width;
        windowHeight=height;
        if (hwnd){
            SetWindowPos(hwnd, NULL, 0, 0, windowWidth, windowHeight, SWP_NOZORDER|SWP_NOMOVE|SWP_NOACTIVATE);
        }
        Config& cfg=Config::getInstance();
        cfg.setSize(width, height);
        cfg.save();
    }
    
    void DesktopWindow::setOpacity(float opacity){
        if (opacity<0.0f){
            opacity=0.0f;
        }
        if (opacity>1.0f){
            opacity=1.0f;
        }
        alpha=static_cast<BYTE>(opacity*255);
        config.opacity=opacity;
        if (hwnd){
            SetLayeredWindowAttributes(hwnd, RGB(0, 0, 0), alpha, LWA_ALPHA|LWA_COLORKEY);
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
                exStyle|=WS_EX_TRANSPARENT;
            }
            else{
                exStyle&=~WS_EX_TRANSPARENT;
            }
            SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
        }
        Config& cfg=Config::getInstance();
        cfg.setClickThrough(enabled);
        cfg.save();
    }
    void DesktopWindow::adjustWindowStyle(){
        if (hwnd){
            LONG exStyle=GetWindowLong(hwnd, GWL_EXSTYLE);
            exStyle|=WS_EX_TOPMOST|WS_EX_LAYERED|WS_EX_TOOLWINDOW|WS_EX_NOACTIVATE;
            if (clickThrough){
                exStyle|=WS_EX_TRANSPARENT;
            }
            else{
                exStyle&=~WS_EX_TRANSPARENT;
            }
            SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
            SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE|SWP_NOSIZE|SWP_NOACTIVATE|SWP_FRAMECHANGED);
        }
    }
    void DesktopWindow::updateWindowPosition(){
        if (hwnd){
            SetWindowPos(hwnd, NULL, windowX, windowY, 0, 0, 
                SWP_NOZORDER|SWP_NOSIZE|SWP_NOACTIVATE);
        }
    }
    void DesktopWindow::showContextMenu(int x, int y){
        HMENU hMenu=CreatePopupMenu();
        if (hMenu){
            AppendMenuW(hMenu, MF_STRING, 1001, L"Show/Hide");
            AppendMenuW(hMenu, MF_SEPARATOR, 0, NULL);
            AppendMenuW(hMenu, MF_STRING, 1002, L"Exit");
            SetForegroundWindow(hwnd);
            TrackPopupMenu(hMenu, TPM_RIGHTBUTTON, x, y, 0, hwnd, NULL);
            DestroyMenu(hMenu);
        }
    }
}