// ==================== desktop_window.cpp ====================
// Implementation of the DesktopWindow class.
// Handles window creation, message processing, rendering, and interaction.
// The overlay is always on top, can be dragged with Ctrl+click,
// and automatically hides when a full‑screen application is detected.

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
    // ------------------------------------------------------------------------
    // Constructor: sets up DPI awareness, initial size, and creates sub‑objects.
    // ------------------------------------------------------------------------
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

        // Load saved configuration
        Config &cfg = Config::getInstance();
        cfg.load();
        config = cfg.getConfig();

        // Enable per‑monitor DPI awareness (Windows 10 version 1703+)
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
                SetProcessDPIAware(); // Fallback for older Windows
            }
            FreeLibrary(user32);
        }
        else
        {
            SetProcessDPIAware();
        }

        // Parse command line for wallpaper mode flags
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

        // Determine a reasonable default window size based on screen size
        RECT workArea;
        SystemParametersInfo(SPI_GETWORKAREA, 0, &workArea, 0);
        int screenWidth = workArea.right - workArea.left;
        int screenHeight = workArea.bottom - workArea.top;

        int baseWidth = screenWidth * 22 / 100;
        int baseHeight = screenHeight * 30 / 100;

        const int MIN_WIDTH = 300;
        const int MIN_HEIGHT = 400;
        const int MAX_WIDTH = screenWidth / 2;
        const int MAX_HEIGHT = screenHeight / 2;

        baseWidth = std::max(MIN_WIDTH, std::min(baseWidth, MAX_WIDTH));
        baseHeight = std::max(MIN_HEIGHT, std::min(baseHeight, MAX_HEIGHT));

        windowWidth = baseWidth;
        windowHeight = baseHeight;

        // Default position: top‑right corner
        windowX = workArea.right - windowWidth - 10;
        windowY = workArea.top + 10;

        // Override with saved config if available
        if (config.positionX != 100 || config.positionY != 100)
        {
            windowX = config.positionX;
            windowY = config.positionY;
        }
        if (config.width != 400 || config.height != 600)
        {
            windowWidth = config.width;
            windowHeight = config.height;
            windowWidth = std::max(MIN_WIDTH, std::min(windowWidth, MAX_WIDTH));
            windowHeight = std::max(MIN_HEIGHT, std::min(windowHeight, MAX_HEIGHT));
        }

        alpha = static_cast<BYTE>(config.opacity * 255);
        clickThrough = config.clickThrough;

        // Create the renderer and event manager
        renderer = std::make_unique<CalendarRenderer>();
        eventManager = std::make_unique<EventManager>();
    }

    // ------------------------------------------------------------------------
    // Destructor: clean up all resources.
    // ------------------------------------------------------------------------
    DesktopWindow::~DesktopWindow()
    {
        close();
        cleanupDoubleBuffer();
    }

    // ------------------------------------------------------------------------
    // Registers the window class with the OS.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Creates the actual window with layered, tool‑window, top‑most styles.
    // ------------------------------------------------------------------------
    bool DesktopWindow::createWindowInstance()
    {
        DWORD exStyle = WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE;
        exStyle |= WS_EX_TOPMOST;
        if (clickThrough)
            exStyle |= WS_EX_TRANSPARENT;

        DWORD style = WS_POPUP | WS_THICKFRAME;   // Thick frame allows resizing

        hwnd = CreateWindowExW(
            exStyle,
            className.c_str(),
            L"Calendar Overlay",
            style,
            windowX, windowY, windowWidth, windowHeight,
            NULL, NULL, hInstance, this);

        if (!hwnd)
            return false;

        // Set initial opacity
        SetLayeredWindowAttributes(hwnd, 0, alpha, LWA_ALPHA);

        // Apply dark mode and rounded corners via DWM (Windows 10 1809+)
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

    // ------------------------------------------------------------------------
    // Public create method: registers class, creates window, initialises
    // double buffer, renderer, event manager, and timers.
    // ------------------------------------------------------------------------
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

        // Set up timers: render every 100 ms, update events periodically, check desktop every 500 ms.
        renderTimer = SetTimer(hwnd, 1, 100, NULL);
        updateTimer = SetTimer(hwnd, 2, config.refreshInterval * 1000, NULL);
        desktopCheckTimer = SetTimer(hwnd, 3, 500, NULL);

        createTrayIcon();
        return true;
    }

    // ------------------------------------------------------------------------
    // Shows the window (if hidden) and ensures the render timer is running.
    // ------------------------------------------------------------------------
    void DesktopWindow::show()
    {
        if (hwnd && !visible)
        {
            ShowWindow(hwnd, SW_SHOWNOACTIVATE);   // Show without stealing focus
            visible = true;
            InvalidateRect(hwnd, NULL, TRUE);
        }
        if (!renderTimer)
            renderTimer = SetTimer(hwnd, 1, 100, NULL);
    }

    // ------------------------------------------------------------------------
    // Hides the window.
    // ------------------------------------------------------------------------
    void DesktopWindow::hide()
    {
        if (hwnd && visible)
        {
            ShowWindow(hwnd, SW_HIDE);
            visible = false;
        }
    }

    // ------------------------------------------------------------------------
    // Closes the window, kills timers, removes tray icon, and unregisters class.
    // ------------------------------------------------------------------------
    void DesktopWindow::close()
    {
        if (renderTimer)
            KillTimer(hwnd, renderTimer);
            renderTimer=0;
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

    // ------------------------------------------------------------------------
    // Forces an update of events from the manager and passes them to the renderer.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Renders the window content using double‑buffering (if available).
    // ------------------------------------------------------------------------
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

            renderer->render();   // Renderer draws into its own surface (which is tied to the double buffer)

            // Alpha blend the double buffer onto the screen
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
            renderer->render();   // Fallback: render directly
        }

        EndPaint(hwnd, &ps);
    }

    // ------------------------------------------------------------------------
    // Static window procedure – dispatches messages to the appropriate instance.
    // ------------------------------------------------------------------------
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
            case WM_APP + 2:   // Custom message: track ended – play next
                if (window->renderer)
                    window->renderer->playNextTrack();
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
            case WM_APP + 1:   // Tray icon notification
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
                UINT dpiX = LOWORD(wParam);
                UINT dpiY = HIWORD(wParam);
                if (window->renderer)
                {
                    window->renderer->updateDPI(dpiX, dpiY);
                }
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

    // ------------------------------------------------------------------------
    // Paint handler – calls render().
    // ------------------------------------------------------------------------
    void DesktopWindow::onPaint()
    {
        render();
    }

    // ------------------------------------------------------------------------
    // Timer handler: invalidates for redraw, checks desktop status, processes audio.
    // ------------------------------------------------------------------------
    void DesktopWindow::onTimer()
    {
        static int updateCounter = 0;
        if (visible && hwnd)
        {
            InvalidateRect(hwnd, NULL, FALSE);   // Trigger WM_PAINT
        }
        updateWindowVisibilityBasedOnDesktop();
        if (renderer)
            renderer->handleAudioTimer();        // Update audio progress

        // Refresh events every ~30 timer ticks (3 seconds if timer is 100 ms)
        if (++updateCounter >= 30)
        {
            update();
            updateCounter = 0;
        }
    }

    // ------------------------------------------------------------------------
    // Mouse move handler: handles window dragging and forwards to renderer.
    // ------------------------------------------------------------------------
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

            // Save new position to config
            Config &cfg = Config::getInstance();
            cfg.setPosition(windowX, windowY);
            cfg.save();
        }
        else if (renderer)
        {
            renderer->handleMouseMove(x, y);   // Let renderer handle hover effects, progress drag, etc.
        }
    }

    // ------------------------------------------------------------------------
    // Mouse down handler: starts drag if Ctrl is held, otherwise passes to renderer.
    // If click is not handled, launches Java GUI.
    // ------------------------------------------------------------------------
    void DesktopWindow::onMouseDown(int x, int y)
    {
        bool ctrlDown = (GetKeyState(VK_CONTROL) & 0x8000) != 0;
        bool clickHandled = false;

        if (renderer)
            clickHandled = renderer->handleMouseDown(x, y);

        if (ctrlDown)
        {
            // Begin dragging the window
            dragging = true;
            POINT cursorPos;
            GetCursorPos(&cursorPos);
            dragStartX = cursorPos.x;
            dragStartY = cursorPos.y;
            return;
        }

        if (renderer && renderer->isScrollingActive())
            return;   // Don't launch GUI while scrolling

        if (!clickHandled)
            launchJavaGUI();   // Default action: open configuration app
    }

    // ------------------------------------------------------------------------
    // Mouse up handler: ends dragging, forwards to renderer, restores click‑through if needed.
    // ------------------------------------------------------------------------
    void DesktopWindow::onMouseUp(int x, int y)
    {
        dragging = false;
        if (renderer)
        {
            renderer->handleMouseUp(x, y);
        }
        if (clickThrough && hwnd)
        {
            // Re‑enable WS_EX_TRANSPARENT (it may have been temporarily cleared during drag)
            LONG exStyle = GetWindowLong(hwnd, GWL_EXSTYLE);
            exStyle |= WS_EX_TRANSPARENT;
            SetWindowLong(hwnd, GWL_EXSTYLE, exStyle);
        }
    }

    // ------------------------------------------------------------------------
    // Keyboard shortcuts:
    //   ESC     – hide window
    //   F5      – refresh events
    //   Space   – play/pause audio
    //   Right   – next track
    //   Left    – previous track
    // (Volume keys have been removed as per requirements)
    // ------------------------------------------------------------------------
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
        // Volume up/down and mute keys have been removed – volume slider is gone.
    }

    // ------------------------------------------------------------------------
    // Handles commands from the tray context menu.
    // ------------------------------------------------------------------------
    void DesktopWindow::onCommand(WPARAM wParam)
    {
        switch (LOWORD(wParam))
        {
        case 1001:   // Show/Hide
            if (visible)
                hide();
            else
                show();
            break;
        case 1002:   // Exit
            close();
            PostQuitMessage(0);
            break;
        }
    }

    // ------------------------------------------------------------------------
    // Creates the system tray icon.
    // ------------------------------------------------------------------------
    void DesktopWindow::createTrayIcon()
    {
        memset(&trayIconData, 0, sizeof(trayIconData));
        trayIconData.cbSize = sizeof(NOTIFYICONDATA);
        trayIconData.hWnd = hwnd;
        trayIconData.uID = 100;
        trayIconData.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
        trayIconData.uCallbackMessage = WM_APP + 1;   // Custom message for tray events
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

    // ------------------------------------------------------------------------
    // Removes the system tray icon.
    // ------------------------------------------------------------------------
    void DesktopWindow::removeTrayIcon()
    {
        if (trayIconVisible)
        {
            Shell_NotifyIcon(NIM_DELETE, &trayIconData);
            trayIconVisible = false;
        }
    }

    // ------------------------------------------------------------------------
    // Shows the right‑click context menu for the tray icon.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Launches the Java configuration GUI.
    // Searches for CalendarApp.jar in the executable directory or in ..\dist.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Detects whether the desktop is currently visible (i.e., no maximised
    // application is covering it). Checks the class name of the foreground window.
    // ------------------------------------------------------------------------
    bool DesktopWindow::checkIfOnDesktop()
    {
        HWND fg = GetForegroundWindow();
        if (!fg)
            return true;
        char className[256];
        GetClassNameA(fg, className, sizeof(className));
        // These are typical desktop / shell windows
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

    // ------------------------------------------------------------------------
    // Shows or hides the overlay based on whether the desktop is active.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Creates a compatible DC and bitmap for double‑buffering.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Cleans up double‑buffer resources.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Resizes the double buffer if the window size changed.
    // ------------------------------------------------------------------------
    void DesktopWindow::resizeDoubleBuffer(int width, int height)
    {
        if (width != bufferWidth || height != bufferHeight)
        {
            createDoubleBuffer(width, height);
        }
    }

    // ------------------------------------------------------------------------
    // Public setter for window position – moves window and saves config.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Public setter for window size – resizes window and updates renderer.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Public setter for opacity – updates layered window attribute.
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Public setter for click‑through – updates window extended style.
    // ------------------------------------------------------------------------
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