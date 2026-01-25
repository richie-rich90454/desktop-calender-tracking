cmake_minimum_required(VERSION 3.15)
project(CalendarOverlay)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(WIN32)
    set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS ON)
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} /SUBSYSTEM:WINDOWS")
    # Add Windows definitions to prevent macro conflicts
    add_definitions(-D NOMINMAX -D WIN32_LEAN_AND_MEAN -D _WIN32_WINNT=0x0601 -D WINVER=0x0601 -D NTDDI_VERSION=0x06010000 -D _WIN32_IE=0x0600 -D _NO_SAL)
    if(MINGW)
        set(CMAKE_RC_COMPILER_INIT windres)
        add_definitions(-D _M_IX86 -D _NO_ASM -D __NO_ASM -D _NO_INLINE_ASM)
    endif()
    if(MSVC)
        set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    endif()
    enable_language(RC)
    # Find Windows SDK - use version 10.0.19041.0 for compatibility
    set(WINDOWS_SDK_DIR "C:/Program Files (x86)/Windows Kits/10/Include/10.0.19041.0")
    if(NOT EXISTS ${WINDOWS_SDK_DIR})
        # Fallback to latest
        file(GLOB SDK_VERSIONS "C:/Program Files (x86)/Windows Kits/10/Include/*")
        list(SORT SDK_VERSIONS)
        list(REVERSE SDK_VERSIONS)
        if(SDK_VERSIONS)
            list(GET SDK_VERSIONS 0 WINDOWS_SDK_DIR)
        endif()
    endif()
    message(STATUS "Windows SDK directory: ${WINDOWS_SDK_DIR}")
    include_directories(${WINDOWS_SDK_DIR}/um ${WINDOWS_SDK_DIR}/shared ${WINDOWS_SDK_DIR}/winrt)
endif()

set(SOURCES
    src/main.cpp
    src/desktop_window.cpp
    src/calendar_render.cpp
    src/event_manager.cpp
    src/config.cpp
)
set(HEADERS
    include/shared/calendar_shared.h
    src/desktop_window.h
    src/calendar_render.h
    src/event_manager.h
    src/config.h
    src/resource.h
)
set(RESOURCES
    src/resources/calendar.rc
)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include ${CMAKE_CURRENT_SOURCE_DIR}/src)
add_executable(CalendarOverlay ${SOURCES} ${HEADERS} ${RESOURCES})
target_include_directories(CalendarOverlay PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include/json)
target_link_libraries(CalendarOverlay
    d2d1
    dwrite
    windowscodecs
    comctl32
    shell32
    ole32
    user32
    gdi32
)
install(TARGETS CalendarOverlay
    RUNTIME DESTINATION bin
    BUNDLE DESTINATION bin
)