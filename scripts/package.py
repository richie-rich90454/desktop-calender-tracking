#!/usr/bin/env python3

import os
import sys
import shutil
import subprocess
import platform
from pathlib import Path
import argparse

class PackageBuilder:
    def __init__(self, project_root="."):
        self.project_root = Path(project_root).absolute()
        self.control_app = self.project_root / "control-app"
        self.overlay_windows = self.project_root / "overlay-windows"
        self.shared = self.project_root / "shared"
        self.scripts = self.project_root / "scripts"
        self.dist = self.project_root / "dist"
        
    def clean_dist(self):
        if self.dist.exists():
            shutil.rmtree(self.dist)
        self.dist.mkdir(parents=True, exist_ok=True)
        
    def build_java_app(self):
        print("Building Java application...")
        
        src_dir = self.control_app / "src"
        build_dir = self.dist / "java-app"
        build_dir.mkdir(parents=True, exist_ok=True)
        
        java_files = []
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                if file.endswith(".java"):
                    java_files.append(str(Path(root) / file))
        
        if not java_files:
            print("No Java source files found!")
            return False
            
        classpath = [
            str(self.control_app / "lib" / "*") if (self.control_app / "lib").exists() else ""
        ]
        cp_str = ":".join(filter(None, classpath)) if platform.system() != "Windows" else ";".join(filter(None, classpath))
        
        compile_cmd = [
            "javac",
            "-d", str(build_dir),
            "-cp", cp_str
        ] + java_files
        
        try:
            subprocess.run(compile_cmd, check=True, capture_output=True)
            print("Java compilation successful")
            
            self._copy_resources(build_dir)
            
            self._create_jar(build_dir)
            
            return True
            
        except subprocess.CalledProcessError as e:
            print(f"Java compilation failed: {e.stderr.decode()}")
            return False
    
    def _copy_resources(self, build_dir):
        for root, dirs, files in os.walk(self.control_app / "src"):
            for file in files:
                if not file.endswith(".java"):
                    src_path = Path(root) / file
                    rel_path = src_path.relative_to(self.control_app / "src")
                    dst_path = build_dir / rel_path
                    dst_path.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(src_path, dst_path)
    
    def _create_jar(self, build_dir):
        print("Creating JAR file...")
        
        jar_path = self.dist / "CalendarApp.jar"
        
        manifest_path = build_dir / "MANIFEST.MF"
        with open(manifest_path, "w") as f:
            f.write("""Manifest-Version: 1.0
Main-Class: app.Main
Class-Path: .
""")
        
        jar_cmd = [
            "jar", "cfm",
            str(jar_path),
            str(manifest_path),
            "-C", str(build_dir), "."
        ]
        
        subprocess.run(jar_cmd, check=True)
        print(f"JAR created: {jar_path}")
    
    def build_windows_overlay(self):
        if platform.system() != "Windows":
            print("Skipping Windows overlay build (not on Windows)")
            return True
            
        print("Building Windows overlay...")
        
        overlay_build = self.dist / "overlay"
        overlay_build.mkdir(parents=True, exist_ok=True)
        
        if self._has_visual_studio():
            return self._build_with_msbuild()
        elif self._has_mingw():
            return self._build_with_mingw()
        else:
            print("No C++ compiler found. Skipping overlay build.")
            return True
    
    def _has_visual_studio(self):
        vs_paths = [
            "C:\\Program Files\\Microsoft Visual Studio\\",
            "C:\\Program Files (x86)\\Microsoft Visual Studio\\"
        ]
        return any(Path(p).exists() for p in vs_paths)
    
    def _has_mingw(self):
        try:
            subprocess.run(["g++", "--version"], check=True, capture_output=True)
            return True
        except (subprocess.CalledProcessError, FileNotFoundError):
            return False
    
    def _build_with_msbuild(self):
        print("Visual Studio detected - overlay build would go here")
        return True
    
    def _build_with_mingw(self):
        print("MinGW detected - building overlay...")
        
        cpp_files = []
        for root, dirs, files in os.walk(self.overlay_windows):
            for file in files:
                if file.endswith((".cpp", ".c")):
                    cpp_files.append(str(Path(root) / file))
        
        if not cpp_files:
            print("No C++ source files found for overlay")
            return True
        
        exe_path = self.dist / "overlay" / "CalendarOverlay.exe"
        build_cmd = [
            "g++", "-o", str(exe_path),
            "-static",
            "-mwindows"
        ] + cpp_files
        
        try:
            subprocess.run(build_cmd, check=True, capture_output=True)
            print(f"Overlay built: {exe_path}")
            return True
        except subprocess.CalledProcessError as e:
            print(f"Overlay build failed: {e.stderr.decode()}")
            return False
    
    def create_installer(self):
        print("Creating installer package...")
        
        package_dir = self.dist / "DesktopCalendar"
        package_dir.mkdir(parents=True, exist_ok=True)
        
        jar_file = self.dist / "CalendarApp.jar"
        if jar_file.exists():
            shutil.copy2(jar_file, package_dir / "CalendarApp.jar")
        
        overlay_exe = self.dist / "overlay" / "CalendarOverlay.exe"
        if overlay_exe.exists():
            shutil.copy2(overlay_exe, package_dir / "CalendarOverlay.exe")
        
        if self.shared.exists():
            shutil.copytree(self.shared, package_dir / "shared", dirs_exist_ok=True)
        
        self._create_launcher_scripts(package_dir)
        
        self._create_readme(package_dir)
        
        print(f"Package created in: {package_dir}")
        
        self._create_zip_archive(package_dir)
    
    def _create_launcher_scripts(self, package_dir):
        with open(package_dir / "launch.bat", "w") as f:
            f.write("""@echo off
echo Launching Desktop Calendar...
start javaw -jar CalendarApp.jar
echo GUI application started.
echo.
echo To start the desktop overlay, run:
echo CalendarOverlay.exe
pause
""")
        
        with open(package_dir / "launch.sh", "w") as f:
            f.write("""#!/bin/bash
echo "Launching Desktop Calendar..."
java -jar CalendarApp.jar &
echo "GUI application started."
echo ""
echo "Note: Desktop overlay is Windows-only"
""")
        os.chmod(package_dir / "launch.sh", 0o755)
    
    def _create_readme(self, package_dir):
        with open(package_dir / "README.txt", "w") as f:
            f.write("""Desktop Calendar Tracking Application
=========================================

This application consists of two parts:

1. Java GUI Application (Cross-platform)
   - Run CalendarApp.jar
   - Requires Java 8 or higher

2. Windows Desktop Overlay (Windows only)
   - Run CalendarOverlay.exe
   - Displays calendar as desktop overlay

Launch Options:
---------------
Windows: Double-click launch.bat
Mac/Linux: Run ./launch.sh from terminal

Requirements:
-------------
- Java Runtime Environment (JRE) 8+
- For Windows overlay: Windows 10/11

Configuration:
--------------
Calendar data is stored in:
- Windows: %USERPROFILE%/.calendarapp/
- Mac/Linux: ~/.calendarapp/

Troubleshooting:
----------------
If Java app doesn't start:
1. Ensure Java is installed (java -version)
2. Try: java -jar CalendarApp.jar

For the Windows overlay:
1. Run as Administrator if needed
2. Ensure proper display drivers

Contact & Support:
------------------
[placeholder]
""")
    
    def _create_zip_archive(self, package_dir):
        import zipfile
        
        zip_path = self.dist / "DesktopCalendar.zip"
        with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
            for root, dirs, files in os.walk(package_dir):
                for file in files:
                    file_path = Path(root) / file
                    arcname = file_path.relative_to(package_dir.parent)
                    zipf.write(file_path, arcname)
        
        print(f"ZIP archive created: {zip_path}")
    
    def run_all(self):
        print("=" * 60)
        print("Desktop Calendar Tracking - Packaging Tool")
        print("=" * 60)
        
        self.clean_dist()
        
        if not self.build_java_app():
            print("Java build failed!")
            return False
        
        if not self.build_windows_overlay():
            print("Windows overlay build failed!")
            return False
        
        self.create_installer()
        
        print("\n" + "=" * 60)
        print("Build completed successfully!")
        print(f"Output in: {self.dist}")
        print("=" * 60)
        
        return True

def main():
    parser = argparse.ArgumentParser(description="Package Desktop Calendar Tracking application")
    parser.add_argument("--java-only", action="store_true", help="Build only Java application")
    parser.add_argument("--overlay-only", action="store_true", help="Build only Windows overlay")
    parser.add_argument("--clean", action="store_true", help="Clean distribution directory only")
    
    args = parser.parse_args()
    
    builder = PackageBuilder()
    
    if args.clean:
        builder.clean_dist()
        print("Distribution directory cleaned")
    elif args.java_only:
        builder.clean_dist()
        builder.build_java_app()
    elif args.overlay_only:
        builder.clean_dist()
        builder.build_windows_overlay()
    else:
        builder.run_all()

if __name__ == "__main__":
    main()