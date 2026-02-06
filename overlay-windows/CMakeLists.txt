cmake_minimum_required(VERSION 3.15)
project(CalendarOverlay LANGUAGES CXX RC)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --------------------
# Sources
# --------------------
set(SOURCES
    src/main.cpp
    src/desktop_window.cpp
    src/calendar_render.cpp
    src/event_manager.cpp
    src/config.cpp
    src/audio_player.cpp
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

# --------------------
# Executable (GUI app)
# --------------------
add_executable(CalendarOverlay
    ${SOURCES}
    ${RESOURCES}
)

# Remove WIN32 if you don't need subsystem:Windows (use CONSOLE for console app)
# WIN32 creates a Windows GUI application (no console window)
set_target_properties(CalendarOverlay PROPERTIES
    WIN32_EXECUTABLE TRUE  # Creates GUI app (subsystem:Windows)
    # Or use this for console app:
    # WIN32_EXECUTABLE FALSE  # Creates console app (subsystem:Console)
)

# --------------------
# Include paths
# --------------------
target_include_directories(CalendarOverlay PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/include/json
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

# --------------------
# Wallpaper mode option
# --------------------
option(WALLPAPER_MODE "Enable wallpaper mode (full-screen overlay behind windows)" ON)

# --------------------
# Windows compile definitions
# --------------------
target_compile_definitions(CalendarOverlay PRIVATE
    NOMINMAX
    WIN32_LEAN_AND_MEAN
    _WIN32_WINNT=0x0A00  # Windows 10/11 (was 0x0601 for Win7)
    UNICODE
    _UNICODE
    $<$<BOOL:${WALLPAPER_MODE}>:WALLPAPER_MODE>
)

# --------------------
# MSVC runtime (static CRT) - FIXED SYNTAX
# --------------------
if(MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    
    # Additional MSVC-specific settings
    add_compile_options(
        "$<$<CXX_COMPILER_ID:MSVC>:/permissive->"  # Strict standards compliance
        "$<$<CXX_COMPILER_ID:MSVC>:/Zc:__cplusplus>"  # Enable updated __cplusplus macro
    )
endif()

# --------------------
# Link Windows system libraries
# --------------------
target_link_libraries(CalendarOverlay PRIVATE
    user32
    gdi32
    ole32
    shell32
    comctl32
    d2d1
    dwrite
    windowscodecs
    msimg32  # For AlphaBlend function
    # Add these commonly needed libs:
    advapi32
    uuid
    winmm.lib
    dsound.lib
    strmiids.lib
    mf.lib
    mfplat.lib
    mfreadwrite.lib
    mfuuid.lib
)

# --------------------
# Resource compiler settings
# --------------------
if(MSVC)
    # Set resource compiler include directories
    set_source_files_properties(${RESOURCES} PROPERTIES
        COMPILE_FLAGS "/I${CMAKE_CURRENT_SOURCE_DIR}/src"
    )
endif()

# --------------------
# Debug/Release configurations
# --------------------
# Set different settings for Debug vs Release
target_compile_definitions(CalendarOverlay PRIVATE
    $<$<CONFIG:Debug>:_DEBUG>
    $<$<CONFIG:Release>:NDEBUG>
)

# --------------------
# Output configuration
# --------------------
set_target_properties(CalendarOverlay PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
    RUNTIME_OUTPUT_DIRECTORY_DEBUG ${CMAKE_BINARY_DIR}/bin/Debug
    RUNTIME_OUTPUT_DIRECTORY_RELEASE ${CMAKE_BINARY_DIR}/bin/Release
    ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)

# --------------------
# Install
# --------------------
install(TARGETS CalendarOverlay
    RUNTIME DESTINATION bin
    CONFIGURATIONS Release  # Only install Release builds
)

# --------------------
# Optional: Post-build copy for dependencies
# --------------------
# If you need to copy DLLs or other files after build
add_custom_command(TARGET CalendarOverlay POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_BINARY_DIR}/bin/$<CONFIG>
    COMMENT "Creating output directory"
)