// This file defines how the overlay window behaves.
// Windows will call our WindowProc whenever something happens.
// We do nothing unless Windows tells us to.

//g++ desktop_window.cpp -std=c++11 -lgdi32

#include <windows.h>

// Forward declaration: promise that this function exists
LRESULT CALLBACK WindowProc(
    HWND hwnd,
    UINT uMsg,
    WPARAM wParam,
    LPARAM lParam
);

// Program entry point
int WINAPI WinMain(
    HINSTANCE hInstance,
    HINSTANCE,
    LPSTR,
    int nCmdShow
)
{
    // Registration key (UTF-16), not visible to users
    const wchar_t CLASS_NAME[] = L"DesktopCalendarTracking";

    // Describe a window type
    WNDCLASSW wc = {};
    wc.lpfnWndProc   = WindowProc;
    wc.hInstance     = hInstance;
    wc.lpszClassName = CLASS_NAME;

    // Register the window class
    RegisterClassW(&wc);

    // Create the window
    HWND hwnd = CreateWindowExW(
        0,
        CLASS_NAME,
        L"Desktop Calendar Tracking",
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT,
        800, 600,
        NULL,
        NULL,
        hInstance,
        NULL
    );

    // Make the window visible
    ShowWindow(hwnd, nCmdShow);

    // Message loop
    MSG msg = {};
    while (GetMessage(&msg, NULL, 0, 0))
    {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    return 0;
}

// Window procedure: handles messages
LRESULT CALLBACK WindowProc(
    HWND hwnd,
    UINT uMsg,
    WPARAM wParam,
    LPARAM lParam
)
{
    switch (uMsg)
    {
        case WM_PAINT:
        {
            PAINTSTRUCT ps;
            HDC hdc = BeginPaint(hwnd, &ps);

            TextOutW(hdc, 10, 10, L"Hello, WinAPI", 13);

            EndPaint(hwnd, &ps);
            return 0;
        }

        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;

        default:
            return DefWindowProcW(hwnd, uMsg, wParam, lParam);
    }
}