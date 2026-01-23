#!/bin/bash

# Java Build Script for Calendar Application
# ==========================================

set -e  # Exit on error

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$PROJECT_ROOT/control-app/src"
BUILD_DIR="$PROJECT_ROOT/build"
DIST_DIR="$PROJECT_ROOT/dist"
JAR_NAME="CalendarApp.jar"

clean() {
    echo "Cleaning build directories..."
    rm -rf "$BUILD_DIR" "$DIST_DIR"
    echo "Clean complete"
}

compile() {
    echo "Compiling Java source files..."
    
    mkdir -p "$BUILD_DIR"
    
    # Find all Java files
    JAVA_FILES=$(find "$SRC_DIR" -name "*.java")
    
    if [ -z "$JAVA_FILES" ]; then
        echo "Error: No Java source files found!"
        exit 1
    fi
    
    echo "Found $(echo "$JAVA_FILES" | wc -l) Java source files"
    
    # Compile
    javac -d "$BUILD_DIR" -cp "$SRC_DIR" -Xlint:unchecked $JAVA_FILES
    
    if [ $? -eq 0 ]; then
        echo "Compilation successful"
    else
        echo "Compilation failed"
        exit 1
    fi
}

copy_resources() {
    echo "Copying resource files..."
    
    # Find and copy non-Java files
    find "$SRC_DIR" -type f ! -name "*.java" | while read -r file; do
        rel_path="${file#$SRC_DIR/}"
        dst_path="$BUILD_DIR/$rel_path"
        mkdir -p "$(dirname "$dst_path")"
        cp "$file" "$dst_path"
    done
    
    count=$(find "$SRC_DIR" -type f ! -name "*.java" | wc -l)
    if [ $count -gt 0 ]; then
        echo "Copied $count resource files"
    else
        echo "No resource files found"
    fi
}

create_jar() {
    echo "Creating JAR file: $JAR_NAME..."
    
    mkdir -p "$DIST_DIR"
    
    # Create manifest
    cat > "$BUILD_DIR/MANIFEST.MF" << 'EOF'
Manifest-Version: 1.0
Main-Class: app.Main
Class-Path: .
EOF
    
    # Create JAR
    cd "$BUILD_DIR"
    jar cfm "$DIST_DIR/$JAR_NAME" MANIFEST.MF .
    cd "$PROJECT_ROOT"
    
    if [ -f "$DIST_DIR/$JAR_NAME" ]; then
        size=$(stat -f%z "$DIST_DIR/$JAR_NAME" 2>/dev/null || stat -c%s "$DIST_DIR/$JAR_NAME")
        size_kb=$((size / 1024))
        echo "JAR created successfully: $DIST_DIR/$JAR_NAME"
        echo "JAR size: ${size_kb} KB"
    else
        echo "Error: JAR creation failed"
        exit 1
    fi
}

build_all() {
    clean
    compile
    copy_resources
    create_jar
    
    echo ""
    echo "=================================================="
    echo "BUILD SUCCESSFUL!"
    echo "Output: $DIST_DIR/$JAR_NAME"
    echo "=================================================="
}

# Parse arguments
case "$1" in
    clean)
        clean
        ;;
    compile)
        clean
        compile
        ;;
    jar)
        create_jar
        ;;
    *)
        build_all
        ;;
esac