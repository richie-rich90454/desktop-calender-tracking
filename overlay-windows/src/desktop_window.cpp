#include "desktop_window.h"
#include "config.h"
#include <windowsx.h>
#include <commctrl.h>
#include <dwmapi.h>
#include <algorithm>
#include <iostream>
#include <memory>

#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "dwmapi.lib")

namespace CalendarOverlay
{

    DesktopWindow::DesktopWindow()
        : hwnd(NULL), hInstance(GetModuleHandle(NULL)),
          visible(false), dragging(false), dragStartX(0), dragStartY(0),
          windowX(100), windowY(100), windowWidth(400), windowHeight(600),
          renderTimer(0), updateTimer(0), desktopCheckTimer(0),
          trayIconVisible(false), alpha(255), clickThrough(false),
          wallpaperMode(false), fullScreenWallpaper(false),
          lastActiveWindow(NULL), isOnDesktop(true),
          doubleBufferDC(NULL), doubleBufferBitmap(NULL),
          bufferWidth(0), bufferHeight(0)
    {
        className = L"CalendarOverlayWindow";

        Config &cfg = Config::getInstance();
        cfg.load();
        config = cfg.getConfig();
        HMODULE user32 = LoadLibrary(L"user32.dll");
        if (user32)
        {
            auto setProcessDpiAwarenessContext = (BOOL(WINAPI *)(DPI_AWARENESS_CONTEXT))
                GetProcAddress(user32, "SetProcessDpiAwarenessContext");
            if (setProcessDpiAwarenessContext)
            {
                setProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
            }
            else
            {
                // Fallback for older Windows 10 / Windows 8.1
                SetProcessDPIAware();
            }
            FreeLibrary(user32);
        }
        else
        {
            SetProcessDPIAware();
        }
        // Command line handling
        int argc;
        LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
        if (argv)
        {
            for (int i = 1; i < argc; i++)
            {
                std::wstring arg = argv[i];
                if (arg == L"--wallpaper" || arg == L"-w")
                {
                    wallpaperMode = true;
                }
                else if (arg == L"--no-wallpaper" || arg == L"-nw")
                {
                    wallpaperMode = false;
                }
                else if (arg == L"--fullscreen" || arg == L"-f")
                {
                    fullScreenWallpaper = true;
                }
            }
            LocalFree(argv);
        }

        // -----------------------------------------------------------------
        // WINDOW SIZE = PERCENTAGE OF SCREEN (WORK AREA)
        // -----------------------------------------------------------------
        RECT workArea;
        SystemParametersInfo(SPI_GETWORKAREA, 0, &workArea, 0);
        int screenWidth = workArea.right - workArea.left;
        int screenHeight = workArea.bottom - workArea.top;

        // Base size: 20% width, 30% height – adjust these percentages to taste
        int baseWidth = screenWidth * 20 / 100;
        int baseHeight = screenHeight * 30 / 100;

        // Clamp to reasonable min/max
        const int MIN_WIDTH = 300;
        const int MIN_HEIGHT = 400;
        const int MAX_WIDTH = screenWidth / 2;
        const int MAX_HEIGHT = screenHeight / 2;

        baseWidth = std::max(MIN_WIDTH, std::min(baseWidth, MAX_WIDTH));
        baseHeight = std::max(MIN_HEIGHT, std::min(baseHeight, MAX_HEIGHT));

        windowWidth = baseWidth;
        windowHeight = baseHeight;

        // Default position: top-right corner
        windowX = workArea.right - windowWidth - 10;
        windowY = workArea.top + 10;

        // Override with saved config if present
        if (config.positionX != 100 || config.positionY != 100)
        {
            windowX = config.positionX;
            windowY = config.positionY;
        }
        if (config.width != 400 || config.height != 600)
        {
            windowWidth = config.width;
            windowHeight = config.height;
            // Re-clamp to screen limits
            windowWidth = std::max(MIN_WIDTH, std::min(windowWidth, MAX_WIDTH));
            windowHeight = std::max(MIN_HEIGHT, std::min(windowHeight, MAX_HEIGHT));
        }

        alpha = static_cast<BYTE>(config.opacity * 255);
        clickThrough = config.clickThrough;

        renderer = std::make_unique<CalendarRenderer>();
        eventManager = std::make_unique<EventManager>();
    }

    DesktopWindow::~DesktopWindow()
    {
        close();
        cleanupDoubleBuffer();
    }

    bool DesktopWindow::registerWindowClass()
    {
        WNDCLASSEXW wc = {};
        wc.cbSize = sizeof(WNDCLASSEXW);
        wc.style = CS_HREDRAW | CS_VREDRAW;
        wc.lpfnWndProc = windowProc;
        wc.hInstance = hInstance;
        wc.hCursor = LoadCursor(NULL, IDC_ARROW);
        wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
        wc.lpszClassName = className.c_str();
        wc.hIcon = LoadIcon(NULL, IDI_APPLICATION);
        wc.hIconSm = LoadIcon(NULL, IDI_APPLICATION);
        return RegisterClassExW(&wc) != 0;
    }

    bool DesktopWindow::createWindowInstance()
    {
        DWORD exStyle = WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE;
        exStyle |= WS_EX_TOPMOST;
        if (clickThrough)
            exStyle |= WS_EX_TRANSPARENT;

        DWORD style = WS_POPUP | WS_THICKFRAME; // resizable

        hwnd = CreateWindowExW(
            exStyle,
            className.c_str(),
            L"Calendar Overlay",
            style,
            windowX, windowY, windowWidth, windowHeight,
            NULL, NULL, hInstance, this);

        if (!hwnd)
            return false;

        SetLayeredWindowAttributes(hwnd, 0, alpha, LWA_ALPHA);

        // Dark mode (Windows 10/11)
        HMODULE dwmapi = LoadLibrary(L"dwmapi.dll");
        if (dwmapi)
        {
            BOOL darkMode = TRUE;
            DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &darkMode, sizeof(darkMode));
            DWORD cornerPref = DWMWCP_ROUND;
            DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, &cornerPref, sizeof(cornerPref));
            FreeLibrary(dwmapi);
        }

        return true;
    }

    bool DesktopWindow::create()
    {
        if (!registerWindowClass())
        {
            std::cerr << "Failed to register window class" << std::endl;
            return false;
        }
        if (!createWindowInstance())
        {
            std::cerr << "Failed to create window instance" << std::endl;
            return false;
        }

        createDoubleBuffer(windowWidth, windowHeight);

        if (!renderer->initialize(hwnd))
        {
            std::cerr << "Failed to initialize renderer" << std::endl;
            return false;
        }
        renderer->setConfig(config);

        if (!eventManager->initialize())
        {
            std::cerr << "Failed to initialize event manager" << std::endl;
        }
        renderer->setEvents(eventManager->getTodayEvents());

        renderTimer = SetTimer(hwnd, 1, 16, NULL);
        updateTimer = SetTimer(hwnd, 2, config.refreshInterval * 1000, NULL);
        desktopCheckTimer = SetTimer(hwnd, 3, 500, NULL);

        createTrayIcon();
        return true;
    }

    void DesktopWindow::show()
    {
        if (hwnd && !visible)
        {
            ShowWindow(hwnd, SW_SHOWNOACTIVATE);
            visible = true;
            InvalidateRect(hwnd, NULL, TRUE);
        }
    }

    void DesktopWindow::hide()
    {
        if (hwnd && visible)
        {
            ShowWindow(hwnd, SW_HIDE);
            visible = false;
        }
    }

    void DesktopWindow::close()
    {
        if (renderTimer)
            KillTimer(hwnd, renderTimer);
        if (updateTimer)
            KillTimer(hwnd, updateTimer);
        if (desktopCheckTimer)
            KillTimer(hwnd, desktopCheckTimer);
        removeTrayIcon();
        if (hwnd)
        {
            DestroyWindow(hwnd);
            hwnd = NULL;
        }
        UnregisterClassW(className.c_str(), hInstance);
    }

    void DesktopWindow::update()
    {
        if (eventManager)
        {
            eventManager->update();
            if (renderer)
            {
                renderer->setEvents(eventManager->getTodayEvents());
            }
        }
    }

    void DesktopWindow::render()
    {
        if (!visible || !renderer || !hwnd)
            return;

        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hwnd, &ps);

        if (doubleBufferDC && doubleBufferBitmap)
        {
            RECT clientRect;
            GetClientRect(hwnd, &clientRect);
            HBRUSH bgBrush = CreateSolidBrush(RGB(0, 0, 0));
            FillRect(doubleBufferDC, &clientRect, bgBrush);
            DeleteObject(bgBrush);

            renderer->render();

            BLENDFUNCTION blend = {0};
            blend.BlendOp = AC_SRC_OVER;
            blend.SourceConstantAlpha = alpha;
            blend.AlphaFormat = AC_SRC_ALPHA;

            HDC screenDC = GetDC(hwnd);
            AlphaBlend(screenDC, 0, 0, clientRect.right, clientRect.bottom,
                       doubleBufferDC, 0, 0, clientRect.right, clientRect.bottom, blend);
            ReleaseDC(hwnd, screenDC);
        }
        else
        {
            renderer->render();
        }

        EndPaint(hwnd, &ps);
    }

    LRESULT CALLBACK DesktopWindow::windowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam)
    {
        DesktopWindow *window = nullptr;
        if (msg == WM_NCCREATE)
        {
            CREATESTRUCT *create = reinterpret_cast<CREATESTRUCT *>(lParam);
            window = static_cast<DesktopWindow *>(create->lpCreateParams);
            SetWindowLongPtr(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(window));
            return DefWindowProc(hwnd, msg, wParam, lParam);
        }
        window = reinterpret_cast<DesktopWindow *>(GetWindowLongPtr(hwnd, GWLP_USERDATA));
        if (window)
        {
            switch (msg)
            {
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
            case WM_MOUSEWHEEL:
                if (window->renderer)
                {
                    int delta = GET_WHEEL_DELTA_WPARAM(wParam);
                    window->renderer->handleMouseWheel(-delta / 120.0f);
                }
                return 0;
            case WM_KEYDOWN:
                window->onKeyDown(wParam);
                return 0;
            case WM_COMMAND:
                window->onCommand(wParam);
                return 0;
            case WM_APP + 1:
                if (lParam == WM_RBUTTONUP || lParam == WM_CONTEXTMENU)
                {
                    POINT pt;
                    GetCursorPos(&pt);
                    window->showContextMenu(pt.x, pt.y);
                }
                return 0;
            case WM_SIZE:
                if (window->hwnd)
                {
                    RECT rc;
                    GetClientRect(window->hwnd, &rc);
                    window->resizeDoubleBuffer(rc.right, rc.bottom);
                    if (window->renderer)
                        window->renderer->resize(rc.right, rc.bottom);
                }
                return 0;
            case WM_DPICHANGED:
            {
                // New DPI is in wParam (HIWORD = Y, LOWORD = X)
                UINT dpiX = LOWORD(wParam);
                UINT dpiY = HIWORD(wParam);
                if (window->renderer)
                {
                    window->renderer->updateDPI(dpiX, dpiY); // we'll add this method
                }
                // Suggested rectangle to preserve logical size
                RECT *suggestedRect = (RECT *)lParam;
                SetWindowPos(hwnd, NULL,
                             suggestedRect->left,
                             suggestedRect->top,
                             suggestedRect->right - suggestedRect->left,
                             suggestedRect->bottom - suggestedRect->top,
                             SWP_NOZORDER | SWP_NOACTIVATE);
                return 0;
            }
            case WM_GETMINMAXINFO:
            {
                MINMAXINFO *mmi = (MINMAXINFO *)lParam;
                mmi->ptMinTrackSize.x = 300;
                mmi->ptMinTrackSize.y = 400;
                return 0;
            }
            case WM_DESTROY:
                PostQuitMessage(0);
                return 0;
            default:
                break;
            }
        }
        return DefWindowProc(hwnd, msg, wParam, lParam);
    }

    void DesktopWindow::onPaint()
    {
        render();
    }

    void DesktopWindow::onTimer()
    {
        static int updateCounter = 0;
        if (visible && hwnd)
        {
            InvalidateRect(hwnd, NULL, FALSE);
        }
        updateWindowVisibilityBasedOnDesktop();
        if (++updateCounter >= 30)
        {
            update();
            updateCounter = 0;
        }
    }

    void DesktopWindow::onMouseMove(int x, int y)
    {
        if (dragging)
        {
            POINT cursorPos;
            GetCursorPos(&cursorPos);
            int deltaX = cursorPos.x - dragStartX;
            int deltaY = cursorPos.y - dragStartY;
            windowX += deltaX;
            windowY += deltaY;
            SetWindowPos(hwnd, NULL, windowX, windowY, 0, 0,
                         SWP_NOZORDER | SWP_NOSIZE | SWP_NOACTIVATE);
            dragStartX = cursorPos.x;
            dragStartY = cursorPos.y;

            Config &cfg = Config::getInstance();
            cfg.setPosition(windowX, windowY);
            cfg.save();
        }
        else if (renderer)
        {
            renderer->handleMouseMove(x, y);
        }
    }

    void DesktopWindow::onMouseDown(int x, int y)
    {
        bool ctrlDown = (GetKeyState(VK_CONTROL) & 0x8000) != 0;
        if (renderer)
        {
            renderer->handleMouseDown(x, y);
        }
        if (ctrlDown)
        {
            dragging = true;
            POINT cursorPos;
            GetCursorPos(&cursorPos);
            dragStartX = cursorPos.x;
            dragStartY = cursorPos.y;
            return;
        }
        if (renderer && renderer->isScrollingActive())
        {
            return;
        }
        launchJavaGUI();
    }

    void DesktopWindow::onMouseUp(int x, int y)
    {
        dragging = false;
        if (renderer)
        {
            renderer->handleMouseUp(x, y);
        }
        if (clickThrough && hwnd)
        {
            LONG exStyle = GetWindowLong(hwnd, GWL_EXSTYLE);
            exStyle |= WS_EX_TRANSPARENT;
            SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
        }
    }

    void DesktopWindow::onKeyDown(WPARAM key)
    {
        if (key == VK_ESCAPE)
            hide();
        else if (key == VK_F5)
            update();
        else if (key == VK_SPACE && renderer)
            renderer->toggleAudioPlayback();
        else if (key == VK_RIGHT && renderer)
            renderer->playNextTrack();
        else if (key == VK_LEFT && renderer)
            renderer->playPreviousTrack();
        else if (key == VK_UP && renderer)
        {
            float vol = renderer->getAudioVolume();
            renderer->setAudioVolume(std::min(1.0f, vol + 0.1f));
        }
        else if (key == VK_DOWN && renderer)
        {
            float vol = renderer->getAudioVolume();
            renderer->setAudioVolume(std::max(0.0f, vol - 0.1f));
        }
        else if (key == 'M' || key == 'm')
        {
            if (renderer)
            {
                float vol = renderer->getAudioVolume();
                renderer->setAudioVolume(vol > 0.0f ? 0.0f : 0.5f);
            }
        }
    }

    void DesktopWindow::onCommand(WPARAM wParam)
    {
        switch (LOWORD(wParam))
        {
        case 1001:
            if (visible)
                hide();
            else
                show();
            break;
        case 1002:
            close();
            PostQuitMessage(0);
            break;
        }
    }

    void DesktopWindow::createTrayIcon()
    {
        memset(&trayIconData, 0, sizeof(trayIconData));
        trayIconData.cbSize = sizeof(NOTIFYICONDATA);
        trayIconData.hWnd = hwnd;
        trayIconData.uID = 100;
        trayIconData.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
        trayIconData.uCallbackMessage = WM_APP + 1;
        trayIconData.hIcon = (HICON)LoadImage(hInstance,
                                              MAKEINTRESOURCE(1),
                                              IMAGE_ICON,
                                              GetSystemMetrics(SM_CXSMICON),
                                              GetSystemMetrics(SM_CYSMICON),
                                              0);
        if (!trayIconData.hIcon)
            trayIconData.hIcon = LoadIcon(NULL, IDI_APPLICATION);
        wcscpy_s(trayIconData.szTip, L"Calendar Overlay");
        Shell_NotifyIcon(NIM_ADD, &trayIconData);
        trayIconVisible = true;
    }

    void DesktopWindow::removeTrayIcon()
    {
        if (trayIconVisible)
        {
            Shell_NotifyIcon(NIM_DELETE, &trayIconData);
            trayIconVisible = false;
        }
    }

    void DesktopWindow::showContextMenu(int x, int y)
    {
        HMENU hMenu = CreatePopupMenu();
        if (hMenu)
        {
            InsertMenuW(hMenu, 0, MF_BYPOSITION | MF_STRING, 1001, L"Show/Hide");
            InsertMenuW(hMenu, 1, MF_BYPOSITION | MF_SEPARATOR, 0, NULL);
            InsertMenuW(hMenu, 2, MF_BYPOSITION | MF_STRING, 1002, L"Exit");
            SetForegroundWindow(hwnd);
            TrackPopupMenu(hMenu, TPM_RIGHTBUTTON | TPM_NOANIMATION, x, y, 0, hwnd, NULL);
            DestroyMenu(hMenu);
        }
    }

    void DesktopWindow::launchJavaGUI()
    {
        std::wstring javaPath = L"java";
        std::wstring jarPath = L"CalendarApp.jar";

        wchar_t exePath[MAX_PATH];
        GetModuleFileNameW(NULL, exePath, MAX_PATH);
        std::wstring exeDir = exePath;
        size_t lastSlash = exeDir.find_last_of(L"\\/");
        if (lastSlash != std::wstring::npos)
        {
            exeDir = exeDir.substr(0, lastSlash + 1);
            jarPath = exeDir + L"CalendarApp.jar";
        }

        DWORD fileAttrib = GetFileAttributesW(jarPath.c_str());
        if (fileAttrib == INVALID_FILE_ATTRIBUTES || (fileAttrib & FILE_ATTRIBUTE_DIRECTORY))
        {
            jarPath = L"..\\dist\\CalendarApp.jar";
            fileAttrib = GetFileAttributesW(jarPath.c_str());
            if (fileAttrib == INVALID_FILE_ATTRIBUTES || (fileAttrib & FILE_ATTRIBUTE_DIRECTORY))
            {
                std::cerr << "Java JAR not found." << std::endl;
                return;
            }
        }

        std::wstring command = L"\"" + javaPath + L"\" -jar \"" + jarPath + L"\"";
        STARTUPINFOW si = {sizeof(si)};
        PROCESS_INFORMATION pi = {0};
        if (CreateProcessW(NULL, const_cast<LPWSTR>(command.c_str()), NULL, NULL,
                           FALSE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi))
        {
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);
        }
        else
        {
            std::cerr << "Failed to launch Java: " << GetLastError() << std::endl;
        }
    }

    bool DesktopWindow::checkIfOnDesktop()
    {
        HWND fg = GetForegroundWindow();
        if (!fg)
            return true;
        char className[256];
        GetClassNameA(fg, className, sizeof(className));
        if (strcmp(className, "Progman") == 0 ||
            strcmp(className, "WorkerW") == 0 ||
            strcmp(className, "Shell_TrayWnd") == 0 ||
            strcmp(className, "Button") == 0)
        {
            return true;
        }
        if (IsIconic(fg) || !IsWindowVisible(fg))
            return true;
        if (fg == hwnd)
            return true;
        return false;
    }

    void DesktopWindow::updateWindowVisibilityBasedOnDesktop()
    {
        bool onDesktop = checkIfOnDesktop();
        if (onDesktop != isOnDesktop)
        {
            isOnDesktop = onDesktop;
            if (isOnDesktop)
                show();
            else
                hide();
        }
    }

    void DesktopWindow::createDoubleBuffer(int width, int height)
    {
        cleanupDoubleBuffer();
        if (hwnd && width > 0 && height > 0)
        {
            HDC hdc = GetDC(hwnd);
            if (hdc)
            {
                doubleBufferDC = CreateCompatibleDC(hdc);
                doubleBufferBitmap = CreateCompatibleBitmap(hdc, width, height);
                SelectObject(doubleBufferDC, doubleBufferBitmap);
                ReleaseDC(hwnd, hdc);
                bufferWidth = width;
                bufferHeight = height;
            }
        }
    }

    void DesktopWindow::cleanupDoubleBuffer()
    {
        if (doubleBufferBitmap)
        {
            DeleteObject(doubleBufferBitmap);
            doubleBufferBitmap = NULL;
        }
        if (doubleBufferDC)
        {
            DeleteDC(doubleBufferDC);
            doubleBufferDC = NULL;
        }
        bufferWidth = 0;
        bufferHeight = 0;
    }

    void DesktopWindow::resizeDoubleBuffer(int width, int height)
    {
        if (width != bufferWidth || height != bufferHeight)
        {
            createDoubleBuffer(width, height);
        }
    }

    void DesktopWindow::setPosition(int x, int y)
    {
        windowX = x;
        windowY = y;
        if (hwnd)
        {
            SetWindowPos(hwnd, NULL, windowX, windowY, 0, 0,
                         SWP_NOZORDER | SWP_NOSIZE | SWP_NOACTIVATE);
        }
        Config &cfg = Config::getInstance();
        cfg.setPosition(x, y);
        cfg.save();
    }

    void DesktopWindow::setSize(int width, int height)
    {
        RECT workArea;
        SystemParametersInfo(SPI_GETWORKAREA, 0, &workArea, 0);
        int screenWidth = workArea.right - workArea.left;
        int screenHeight = workArea.bottom - workArea.top;

        width = std::max(300, std::min(width, screenWidth / 2));
        height = std::max(400, std::min(height, screenHeight / 2));

        windowWidth = width;
        windowHeight = height;
        if (hwnd)
        {
            SetWindowPos(hwnd, NULL, 0, 0, windowWidth, windowHeight,
                         SWP_NOZORDER | SWP_NOMOVE | SWP_NOACTIVATE);
            resizeDoubleBuffer(windowWidth, windowHeight);
            if (renderer)
                renderer->resize(windowWidth, windowHeight);
        }
        Config &cfg = Config::getInstance();
        cfg.setSize(width, height);
        cfg.save();
    }

    void DesktopWindow::setOpacity(float opacity)
    {
        alpha = static_cast<BYTE>(std::max(0.0f, std::min(1.0f, opacity)) * 255);
        config.opacity = opacity;
        if (hwnd)
        {
            SetLayeredWindowAttributes(hwnd, 0, alpha, LWA_ALPHA);
        }
        Config &cfg = Config::getInstance();
        cfg.setOpacity(opacity);
        cfg.save();
    }

    void DesktopWindow::setClickThrough(bool enabled)
    {
        clickThrough = enabled;
        config.clickThrough = enabled;
        if (hwnd)
        {
            LONG exStyle = GetWindowLong(hwnd, GWL_EXSTYLE);
            if (enabled)
                exStyle |= WS_EX_TRANSPARENT;
            else
                exStyle &= ~WS_EX_TRANSPARENT;
            SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
        }
        Config &cfg = Config::getInstance();
        cfg.setClickThrough(enabled);
        cfg.save();
    }

} // namespace