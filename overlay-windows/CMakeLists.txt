cmake_minimum_required(VERSION 3.15)
project(CalendarOverlay)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(WIN32)
    set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS ON)
    set(CMAKE_RC_COMPILER_INIT windres)
    enable_language(RC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} /SUBSYSTEM:WINDOWS")
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