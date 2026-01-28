# Desktop Calendar Wallpaper Overlay - Implementation Plan

## Phase 1: Master Build Script ✓ COMPLETED
- [x] Create master_build.py that builds both Java and C++ components
- [x] Add dependency checking for Java, Python, CMake, Visual Studio
- [x] Create Python launcher script with configuration
- [x] Create distribution package with README

## Phase 2: C++ Wallpaper Overlay Modifications
- [ ] Update CMakeLists.txt to support WALLPAPER_MODE option
- [ ] Modify desktop_window.cpp for full-screen wallpaper mode
- [ ] Change window creation to sit behind windows but above wallpaper
- [ ] Implement top-right corner positioning
- [ ] Add click handler to launch Java GUI
- [ ] Update calendar_render.cpp for feature-rich display
- [ ] Add command-line arguments for wallpaper mode

## Phase 3: Java Integration
- [ ] Ensure Java app writes events to JSON file correctly
- [ ] Test Java ↔ C++ data sharing
- [ ] Verify click-to-launch functionality works

## Phase 4: Testing & Polish
- [ ] Test full build process with master_build.py
- [ ] Test wallpaper overlay functionality
- [ ] Test click interaction and Java launch
- [ ] Performance testing
- [ ] Documentation updates

## Current Status: Starting Phase 2