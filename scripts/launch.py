#!/usr/bin/env python3

import os
import sys
import subprocess
import platform
import argparse
from pathlib import Path

class ApplicationLauncher:
    def __init__(self, app_dir=None):
        if app_dir is None:
            self.app_dir = Path(".").absolute()
        else:
            self.app_dir = Path(app_dir).absolute()
        
        self.jar_path = self.app_dir / "CalendarApp.jar"
        self.overlay_path = self.app_dir / "CalendarOverlay.exe"
        self.is_windows = platform.system() == "Windows"
    
    def check_dependencies(self):
        print("Checking dependencies...")
        
        if not self.jar_path.exists():
            print(f"Error: CalendarApp.jar not found at {self.jar_path}")
            return False
        
        if self.is_windows:
            try:
                subprocess.run(["java", "-version"], check=True, capture_output=True)
            except (subprocess.CalledProcessError, FileNotFoundError):
                print("Error: Java is not installed or not in PATH")
                print("Please install Java 8 or higher from: https://java.com")
                return False
        
        return True
    
    def launch_java_app(self, headless=False):
        print("Launching Java Calendar Application...")
        
        java_cmd = ["java"]
        if headless:
            java_cmd.append("-Djava.awt.headless=true")
        
        java_cmd.extend(["-jar", str(self.jar_path)])
        
        try:
            if self.is_windows:
                subprocess.Popen(["start", "javaw", "-jar", str(self.jar_path)], shell=True)
            else:
                subprocess.Popen(java_cmd, start_new_session=True)
            print("Java application launched successfully")
            return True
        except Exception as e:
            print(f"Failed to launch Java application: {e}")
            return False
    
    def launch_overlay(self):
        if not self.is_windows:
            print("Overlay is Windows-only feature")
            return False
        
        if not self.overlay_path.exists():
            print(f"Overlay executable not found: {self.overlay_path}")
            return False
        
        print("Launching Windows Desktop Overlay...")
        
        try:
            if os.name == 'nt':
                CREATE_NO_WINDOW = 0x08000000
                subprocess.Popen([str(self.overlay_path)], creationflags=CREATE_NO_WINDOW)
            else:
                subprocess.Popen([str(self.overlay_path)])
            
            print("Windows overlay launched successfully")
            return True
        except Exception as e:
            print(f"Failed to launch overlay: {e}")
            return False
    
    def launch_both(self):
        print("Launching both Java application and Windows overlay...")
        
        java_success = self.launch_java_app()
        
        overlay_success = False
        if self.is_windows:
            overlay_success = self.launch_overlay()
        else:
            print("Skipping Windows overlay (not on Windows)")
            overlay_success = True
        
        return java_success and overlay_success
    
    def launch_gui_only(self):
        return self.launch_java_app()
    
    def launch_overlay_only(self):
        return self.launch_overlay()
    
    def launch_headless(self):
        return self.launch_java_app(headless=True)
    
    def check_status(self):
        print("Application Status:")
        print(f"  Java JAR exists: {'Yes' if self.jar_path.exists() else 'No'}")
        print(f"  Overlay exists: {'Yes' if self.overlay_path.exists() else 'No'}")
        print(f"  Platform: {platform.system()}")
        print(f"  Architecture: {platform.machine()}")
        
        try:
            java_version = subprocess.run(["java", "-version"], capture_output=True, text=True)
            if java_version.returncode == 0:
                lines = java_version.stderr.split('\n')
                for line in lines:
                    if 'version' in line.lower():
                        print(f"  Java Version: {line.strip()}")
                        break
        except:
            print("  Java Version: Not found")
    
    def kill_processes(self):
        if self.is_windows:
            os.system("taskkill /f /im javaw.exe 2>nul")
            os.system("taskkill /f /im CalendarOverlay.exe 2>nul")
            print("Processes terminated")
        else:
            os.system("pkill -f CalendarApp.jar 2>/dev/null")
            print("Java process terminated")

def main():
    parser = argparse.ArgumentParser(description="Launch Desktop Calendar Tracking Application")
    parser.add_argument("--both", action="store_true", help="Launch both Java app and Windows overlay")
    parser.add_argument("--gui", action="store_true", help="Launch only Java GUI application")
    parser.add_argument("--overlay", action="store_true", help="Launch only Windows overlay")
    parser.add_argument("--headless", action="store_true", help="Launch Java app in headless mode")
    parser.add_argument("--dir", type=str, help="Application directory (default: current)")
    parser.add_argument("--status", action="store_true", help="Check application status")
    parser.add_argument("--kill", action="store_true", help="Kill running application processes")
    
    args = parser.parse_args()
    
    launcher = ApplicationLauncher(args.dir)
    
    if not launcher.check_dependencies():
        return 1
    
    if args.status:
        launcher.check_status()
        return 0
    
    if args.kill:
        launcher.kill_processes()
        return 0
    
    success = False
    
    if args.both:
        success = launcher.launch_both()
    elif args.gui:
        success = launcher.launch_gui_only()
    elif args.overlay:
        success = launcher.launch_overlay_only()
    elif args.headless:
        success = launcher.launch_headless()
    else:
        success = launcher.launch_both()
    
    if success:
        print("\nLaunch successful!")
        if launcher.is_windows:
            print("Applications running in background.")
            print("Use 'python launch.py --kill' to terminate.")
        return 0
    else:
        print("\nLaunch failed!")
        return 1

if __name__ == "__main__":
    sys.exit(main())