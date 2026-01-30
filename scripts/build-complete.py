#!/usr/bin/env python3
"""
Master Build Script for Desktop Calendar Tracking
Builds both Java control app and C++ wallpaper overlay
"""

import os
import sys
import shutil
import subprocess
import platform
import argparse
from pathlib import Path
import time
import json

class DesktopCalendarBuilder:
    def __init__(self):
        self.project_root = Path(__file__).parent.parent.absolute()
        self.scripts_dir = self.project_root / "scripts"
        self.control_app_dir = self.project_root / "control-app"
        self.overlay_dir = self.project_root / "overlay-windows"
        self.dist_dir = self.project_root / "dist"
        self.build_dir = self.project_root / "build"
        
        # Platform-specific settings
        self.is_windows = platform.system() == "Windows"
        self.is_linux = platform.system() == "Linux"
        self.is_mac = platform.system() == "Darwin"
        
        # Build configuration
        self.config = {
            "java_jar_name": "CalendarApp.jar",
            "cpp_exe_name": "CalendarWallpaper.exe",
            "python_launcher_name": "launcher.py",
            "config_file": "desktop_calendar_config.json"
        }
        
    def print_header(self, title):
        """Print formatted header"""
        print("\n" + "="*60)
        print(f" {title}")
        print("="*60)
        
    def check_dependencies(self):
        """Check for required dependencies"""
        self.print_header("Checking Dependencies")
        
        dependencies_ok = True
        
        # Check Java
        try:
            result = subprocess.run(["java", "-version"], capture_output=True, text=True)
            print(f"✓ Java found: {result.stderr.splitlines()[0] if result.stderr else 'Unknown version'}")
        except FileNotFoundError:
            print("✗ Java not found. Please install Java JDK 8 or later.")
            dependencies_ok = False
            
        # Check javac
        try:
            result = subprocess.run(["javac", "-version"], capture_output=True, text=True)
            print(f"✓ Java compiler found: {result.stdout}")
        except FileNotFoundError:
            print("✗ Java compiler (javac) not found. Please install Java JDK (not just JRE).")
            dependencies_ok = False
            
        # Check Python
        print(f"✓ Python {sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}")
        
        # Check CMake (for C++ build)
        try:
            result = subprocess.run(["cmake", "--version"], capture_output=True, text=True)
            cmake_version = result.stdout.splitlines()[0]
            print(f"✓ {cmake_version}")
        except FileNotFoundError:
            print("⚠ CMake not found. C++ overlay will not be built.")
            
        # Check Visual Studio on Windows
        if self.is_windows:
            vs_paths = [
                r"C:\Program Files\Microsoft Visual Studio\2022\Community",
                r"C:\Program Files\Microsoft Visual Studio\2022\Professional",
                r"C:\Program Files\Microsoft Visual Studio\2022\Enterprise",
                r"C:\Program Files (x86)\Microsoft Visual Studio\2019\Community",
                r"C:\Program Files (x86)\Microsoft Visual Studio\2019\Professional",
                r"C:\Program Files (x86)\Microsoft Visual Studio\2019\Enterprise",
            ]
            
            vs_found = False
            for vs_path in vs_paths:
                if Path(vs_path).exists():
                    print(f"✓ Visual Studio found: {vs_path}")
                    vs_found = True
                    break
                    
            if not vs_found:
                print("⚠ Visual Studio not found. C++ overlay will not be built.")
                
        return dependencies_ok
        
    def build_java(self, clean=True, incremental=False):
        """Build Java control application"""
        self.print_header("Building Java Control Application")
        
        if clean and self.build_dir.exists():
            shutil.rmtree(self.build_dir)
            
        if clean and self.dist_dir.exists():
            # Only remove Java-related files, keep C++ executable if it exists
            for file in self.dist_dir.glob("*.jar"):
                file.unlink()
                
        # Use existing build-java.py script
        build_script = self.scripts_dir / "build-java.py"
        
        if not build_script.exists():
            print(f"Error: Java build script not found at {build_script}")
            return False
            
        cmd = [sys.executable, str(build_script)]
        
        if incremental:
            cmd.append("--incremental")
        else:
            # Don't use --clean flag as it only cleans without building
            # The build-java.py script does a full build by default
            pass
            
        try:
            print(f"Running: {' '.join(cmd)}")
            result = subprocess.run(cmd, capture_output=True, text=True, cwd=self.project_root)
            
            if result.returncode != 0:
                print(f"Java build failed with error:\n{result.stderr}")
                return False
                
            print(result.stdout)
            
            # Verify JAR was created
            jar_path = self.dist_dir / self.config["java_jar_name"]
            if jar_path.exists():
                print(f"✓ Java JAR created: {jar_path} ({jar_path.stat().st_size / 1024:.1f} KB)")
                return True
            else:
                print(f"✗ Java JAR not found at expected location: {jar_path}")
                return False
                
        except subprocess.CalledProcessError as e:
            print(f"Java build failed: {e}")
            return False
            
    def build_cpp(self, clean=True, wallpaper_mode=True):
        """Build C++ wallpaper overlay by calling build-cpp.bat directly"""
        self.print_header("Building C++ Wallpaper Overlay")
        
        if not self.is_windows:
            print("C++ overlay is only supported on Windows. Skipping C++ build.")
            return True
            
        # Check if overlay directory exists
        if not self.overlay_dir.exists():
            print(f"Error: Overlay directory not found at {self.overlay_dir}")
            return False
            
        # Check if build-cpp.bat exists
        build_bat = self.scripts_dir / "build-cpp.bat"
        if not build_bat.exists():
            print(f"Error: build-cpp.bat not found at {build_bat}")
            return False
            
        try:
            # Call build-cpp.bat directly
            print(f"Running build-cpp.bat...")
            
            # Run the batch file
            result = subprocess.run(
                [str(build_bat)],
                capture_output=True,
                text=True,
                encoding='utf-8',
                errors='ignore',
                shell=True,
                cwd=self.project_root,
                stdin=subprocess.DEVNULL
            )
            
            # Print output
            if result.stdout:
                print(result.stdout)
            if result.stderr:
                print(f"Stderr: {result.stderr}")
                
            if result.returncode != 0:
                print(f"Build failed with return code: {result.returncode}")
                return False
                
            # Copy executable to dist directory
            self.dist_dir.mkdir(parents=True, exist_ok=True)
            
            # Look for executable in build_nmake directory (where build-cpp.bat puts it)
            cpp_build_dir = self.overlay_dir / "build_nmake"
            exe_src = None
            
            # Check various possible locations
            possible_paths = [
                cpp_build_dir / "bin" / "Debug" / "CalendarOverlay.exe",
                cpp_build_dir / "bin" / "Release" / "CalendarOverlay.exe",
                cpp_build_dir / "Release" / "CalendarOverlay.exe",
                cpp_build_dir / "Debug" / "CalendarOverlay.exe",
                cpp_build_dir / "CalendarOverlay.exe",
            ]
            
            for possible_path in possible_paths:
                if possible_path.exists():
                    exe_src = possible_path
                    break
            
            if exe_src:
                exe_dst = self.dist_dir / self.config["cpp_exe_name"]
                shutil.copy2(exe_src, exe_dst)
                print(f"✓ C++ executable created: {exe_dst} ({exe_dst.stat().st_size / 1024:.1f} KB)")
                print(f"  Source: {exe_src}")
                return True
            else:
                print(f"✗ Executable not found in build directory: {cpp_build_dir}")
                # Try to find it anywhere in the build directory
                for possible_path in cpp_build_dir.rglob("CalendarOverlay.exe"):
                    print(f"  Found at: {possible_path}")
                    exe_dst = self.dist_dir / self.config["cpp_exe_name"]
                    shutil.copy2(possible_path, exe_dst)
                    return True
                    
                return False
                
        except Exception as e:
            print(f"C++ build failed: {e}")
            import traceback
            traceback.print_exc()
            return False
            
    def create_python_launcher(self):
        """Create Python launcher script"""
        self.print_header("Creating Python Launcher")
        
        launcher_path = self.dist_dir / self.config["python_launcher_name"]
        config_path = self.dist_dir / self.config["config_file"]
        
        # Create launcher script
        launcher_content = '''#!/usr/bin/env python3
"""
Desktop Calendar Launcher
Launches both Java control app and C++ wallpaper overlay
"""

import os
import sys
import subprocess
import threading
import time
import json
from pathlib import Path

class DesktopCalendarLauncher:
    def __init__(self):
        self.script_dir = Path(__file__).parent.absolute()
        self.config_file = self.script_dir / "desktop_calendar_config.json"
        self.java_process = None
        self.cpp_process = None
        self.load_config()
        
    def load_config(self):
        """Load configuration from JSON file"""
        default_config = {
            "java_jar": "CalendarApp.jar",
            "cpp_exe": "CalendarWallpaper.exe",
            "cpp_args": ["--wallpaper", "--position", "top-right", "--fullscreen"],
            "auto_start_java": False,
            "auto_start_cpp": True,
            "wallpaper_mode": True
        }
        
        if self.config_file.exists():
            try:
                with open(self.config_file, 'r') as f:
                    self.config = {**default_config, **json.load(f)}
            except:
                self.config = default_config
        else:
            self.config = default_config
            self.save_config()
            
    def save_config(self):
        """Save configuration to JSON file"""
        with open(self.config_file, 'w') as f:
            json.dump(self.config, f, indent=2)
            
    def start_java_gui(self):
        """Launch Java calendar application"""
        jar_path = self.script_dir / self.config["java_jar"]
        
        if not jar_path.exists():
            print(f"Error: Java JAR not found at {jar_path}")
            return False
            
        try:
            print(f"Starting Java application: {jar_path.name}")
            self.java_process = subprocess.Popen(
                ["java", "-jar", str(jar_path)],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
            )
            
            # Start a thread to monitor Java process output
            threading.Thread(target=self.monitor_java_output, daemon=True).start()
            print("Java application started")
            return True
            
        except Exception as e:
            print(f"Failed to start Java application: {e}")
            return False
            
    def monitor_java_output(self):
        """Monitor Java process output"""
        if self.java_process:
            for line in iter(self.java_process.stdout.readline, b''):
                if line:
                    print(f"[Java] {line.decode().strip()}")
                    
    def start_cpp_overlay(self):
        """Launch C++ wallpaper overlay"""
        exe_path = self.script_dir / self.config["cpp_exe"]
        
        if not exe_path.exists():
            print(f"Error: C++ executable not found at {exe_path}")
            return False
            
        try:
            args = [str(exe_path)] + self.config["cpp_args"]
            print(f"Starting C++ overlay: {' '.join(args)}")
            
            self.cpp_process = subprocess.Popen(
                args,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
            )
            
            # Start a thread to monitor C++ process output
            threading.Thread(target=self.monitor_cpp_output, daemon=True).start()
            print("C++ overlay started")
            return True
            
        except Exception as e:
            print(f"Failed to start C++ overlay: {e}")
            return False
            
    def monitor_cpp_output(self):
        """Monitor C++ process output"""
        if self.cpp_process:
            for line in iter(self.cpp_process.stdout.readline, b''):
                if line:
                    print(f"[C++] {line.decode().strip()}")
                    
    def stop_all(self):
        """Stop both Java and C++ processes"""
        print("Stopping applications...")
        
        if self.cpp_process:
            try:
                self.cpp_process.terminate()
                self.cpp_process.wait(timeout=5)
                print("C++ overlay stopped")
            except:
                try:
                    self.cpp_process.kill()
                except:
                    pass
                    
        if self.java_process:
            try:
                self.java_process.terminate()
                self.java_process.wait(timeout=5)
                print("Java application stopped")
            except:
                try:
                    self.java_process.kill()
                except:
                    pass
                    
    def run_cli(self):
        """Run in command-line interface mode"""
        print("Desktop Calendar Launcher")
        print("=" * 40)
        
        if self.config.get("auto_start_cpp", True):
            self.start_cpp_overlay()
            
        if self.config.get("auto_start_java", False):
            self.start_java_gui()
            
        print("\\nApplications started. Press Ctrl+C to stop.")
        
        try:
            # Keep the main thread alive
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\\nStopping applications...")
            self.stop_all()
            
    def run(self):
        """Main entry point"""
        self.run_cli()

def main():
    """Main function for launcher script"""
    import argparse
    
    parser = argparse.ArgumentParser(description="Desktop Calendar Launcher")
    parser.add_argument("--no-cpp", action="store_true", help="Don't start C++ overlay")
    parser.add_argument("--no-java", action="store_true", help="Don't start Java app")
    
    args = parser.parse_args()
    
    launcher = DesktopCalendarLauncher()
    
    # Override config based on command line arguments
    if args.no_cpp:
        launcher.config["auto_start_cpp"] = False
    if args.no_java:
        launcher.config["auto_start_java"] = False
        
    launcher.run()

if __name__ == "__main__":
    main()
'''
        
        # Write launcher script
        with open(launcher_path, 'w') as f:
            f.write(launcher_content)
            
        # Create default config
        default_config = {
            "java_jar": "CalendarApp.jar",
            "cpp_exe": "CalendarWallpaper.exe",
            "cpp_args": ["--wallpaper", "--position", "top-right", "--fullscreen"],
            "auto_start_java": False,
            "auto_start_cpp": True,
            "wallpaper_mode": True
        }
        
        with open(config_path, 'w') as f:
            json.dump(default_config, f, indent=2)
            
        print(f"✓ Python launcher created: {launcher_path}")
        print(f"✓ Configuration file created: {config_path}")
        
        # Make executable on Unix-like systems
        if not self.is_windows:
            os.chmod(launcher_path, 0o755)
            
        return True
        
    def create_distribution(self):
        """Create complete distribution package"""
        self.print_header("Creating Distribution Package")
        
        self.dist_dir.mkdir(parents=True, exist_ok=True)
        
        # Copy README and LICENSE
        for file in ["README.md", "LICENSE"]:
            src = self.project_root / file
            if src.exists():
                shutil.copy2(src, self.dist_dir / file)
                print(f"✓ Copied {file} to distribution")
        
        # Create a simple README for the distribution
        dist_readme = self.dist_dir / "README_DIST.txt"
        with open(dist_readme, 'w') as f:
            f.write("""Desktop Calendar Tracking - Distribution Package
================================================

This package contains:

1. CalendarApp.jar - Java calendar application
2. CalendarWallpaper.exe - C++ desktop wallpaper overlay
3. launcher.py - Python launcher script
4. desktop_calendar_config.json - Configuration file

Quick Start:
------------
1. Make sure you have Java installed (JDK 8 or later)
2. Run: python launcher.py

Options:
--------
- python launcher.py --no-cpp    # Start only Java app
- python launcher.py --no-java   # Start only C++ overlay

Features:
---------
- Java GUI for managing calendar events
- C++ wallpaper overlay showing today's events
- Top-right corner positioning
- Click on overlay to launch Java GUI for editing
- Full-screen overlay behind windows but above wallpaper

Configuration:
--------------
Edit desktop_calendar_config.json to change:
- Auto-start settings
- Overlay position and arguments
- Wallpaper mode

For more information, see the main README.md file.
""")
        
        print(f"✓ Distribution README created: {dist_readme}")
        print(f"\n✓ Distribution package created in: {self.dist_dir}")
        total_size = sum(f.stat().st_size for f in self.dist_dir.rglob('*') if f.is_file())
        print(f"  Total size: {total_size / 1024:.1f} KB")
        
        return True
    def build_all(self, clean=True, wallpaper_mode=True, standalone=False):
        """Build everything: Java, C++, and create distribution"""
        self.print_header("Building Complete Desktop Calendar System")
        
        success = True
        
        # Check dependencies first
        if not self.check_dependencies():
            print("⚠ Some dependencies missing, but will attempt build anyway...")
        
        # Build Java
        if not self.build_java(clean=clean):
            print("⚠ Java build failed or skipped")
            success = False
        
        # Build C++
        if not self.build_cpp(clean=clean, wallpaper_mode=wallpaper_mode):
            print("⚠ C++ build failed or skipped")
            success = False
        
        # Create Python launcher
        if not self.create_python_launcher():
            print("⚠ Failed to create Python launcher")
            success = False
        
        # Create distribution package
        if not self.create_distribution():
            print("⚠ Failed to create distribution package")
            success = False
        
        # Create standalone executable if requested
        if standalone and self.is_windows:
            if not self.create_standalone_exe():
                print("⚠ Failed to create standalone executable")
                success = False
        
        if success:
            self.print_header("BUILD SUCCESSFUL")
            print(f"Distribution ready in: {self.dist_dir}")
            
            if standalone and self.is_windows:
                standalone_exe = self.dist_dir / "DesktopCalendar.exe"
                if standalone_exe.exists():
                    print(f"\nStandalone executable: {standalone_exe}")
                    print("This single EXE contains everything and doesn't require Python!")
            else:
                print("\nTo run the application:")
                print(f"  cd {self.dist_dir}")
                print("  python launcher.py")
                print("\nOr with options:")
                print("  python launcher.py --no-cpp    # Start only Java app")
                print("  python launcher.py --no-java   # Start only C++ overlay")
        else:
            self.print_header("BUILD PARTIALLY SUCCESSFUL")
            print("Some components failed to build, but distribution was created.")
            print(f"Check {self.dist_dir} for available components.")
        
        return success
    def create_standalone_exe(self):
        """Create a standalone executable that includes all dependencies"""
        self.print_header("Creating Standalone Executable")
        
        if not self.is_windows:
            print("Standalone executable is currently only supported on Windows.")
            return False
        
        # Check if PyInstaller is installed
        try:
            import PyInstaller
        except ImportError:
            print("Installing PyInstaller...")
            subprocess.check_call([sys.executable, "-m", "pip", "install", "pyinstaller"])
        
        # Create a temporary directory for PyInstaller
        temp_dir = self.dist_dir / "standalone_build"
        temp_dir.mkdir(parents=True, exist_ok=True)
        
        # Create a main script for the standalone exe
        main_script = temp_dir / "desktop_calendar_main.py"
        
        # Create a comprehensive main script that handles everything - without Unicode check marks
        main_script_content = '''#!/usr/bin/env python3
"""
Desktop Calendar Tracking - Standalone Application
Combines Java GUI and C++ overlay in a single executable
"""

import os
import sys
import subprocess
import threading
import json
import tempfile
import shutil
import atexit
import time
from pathlib import Path

# Get the temporary directory for extracted files
if getattr(sys, 'frozen', False):
    # Running as PyInstaller bundle
    base_path = sys._MEIPASS
else:
    # Running as normal Python script
    base_path = os.path.dirname(os.path.abspath(__file__))

class DesktopCalendarStandalone:
    def __init__(self):
        self.extracted_dir = None
        self.java_process = None
        self.cpp_process = None
        self.config = None
        self.setup_extracted_files()
        self.load_config()
        
    def setup_extracted_files(self):
        """Extract embedded binaries to temp directory"""
        # Create temporary directory for extracted files
        self.extracted_dir = Path(tempfile.mkdtemp(prefix="desktop_calendar_"))
        print(f"Extracting files to: {self.extracted_dir}")
        
        # Files that should be extracted from the bundle
        required_files = [
            "CalendarApp.jar",
            "CalendarWallpaper.exe",
            "desktop_calendar_config.json",
            "README.md",
            "LICENSE"
        ]
        
        # Extract files from bundle
        for file_name in required_files:
            try:
                # Try to get from PyInstaller bundle first
                if getattr(sys, 'frozen', False):
                    src_path = Path(base_path) / file_name
                else:
                    src_path = Path(__file__).parent / file_name
                
                if src_path.exists():
                    dst_path = self.extracted_dir / file_name
                    shutil.copy2(src_path, dst_path)
                    print(f"  Extracted: {file_name}")
            except Exception as e:
                print(f"  Warning: Could not extract {file_name}: {e}")
        
        # Register cleanup function
        atexit.register(self.cleanup)
        
    def cleanup(self):
        """Clean up extracted files"""
        if self.extracted_dir and self.extracted_dir.exists():
            try:
                # Terminate processes first
                self.stop_all()
                
                # Wait a moment
                time.sleep(1)
                
                # Remove directory
                shutil.rmtree(self.extracted_dir, ignore_errors=True)
                print(f"Cleaned up temporary files: {self.extracted_dir}")
            except:
                pass
                
    def load_config(self):
        """Load configuration"""
        default_config = {
            "java_jar": "CalendarApp.jar",
            "cpp_exe": "CalendarWallpaper.exe",
            "cpp_args": ["--wallpaper", "--position", "top-right", "--fullscreen"],
            "auto_start_java": False,
            "auto_start_cpp": True,
            "wallpaper_mode": True
        }
        
        config_path = self.extracted_dir / "desktop_calendar_config.json"
        
        if config_path.exists():
            try:
                with open(config_path, 'r') as f:
                    loaded_config = json.load(f)
                    self.config = {**default_config, **loaded_config}
            except:
                self.config = default_config
        else:
            self.config = default_config
            
    def start_java_gui(self):
        """Launch Java calendar application"""
        jar_path = self.extracted_dir / self.config["java_jar"]
        
        if not jar_path.exists():
            print(f"Error: Java JAR not found at {jar_path}")
            return False
            
        try:
            print(f"Starting Java application...")
            self.java_process = subprocess.Popen(
                ["java", "-jar", str(jar_path)],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                creationflags=subprocess.CREATE_NO_WINDOW,
                cwd=self.extracted_dir
            )
            
            # Monitor output in background
            threading.Thread(target=self.monitor_java_output, daemon=True).start()
            print("[OK] Java application started")
            return True
            
        except Exception as e:
            print(f"Failed to start Java application: {e}")
            return False
            
    def monitor_java_output(self):
        """Monitor Java process output"""
        if self.java_process:
            try:
                for line in iter(self.java_process.stdout.readline, b''):
                    if line:
                        print(f"[Java] {line.decode('utf-8', errors='ignore').strip()}")
            except:
                pass
                
    def start_cpp_overlay(self):
        """Launch C++ wallpaper overlay"""
        exe_path = self.extracted_dir / self.config["cpp_exe"]
        
        if not exe_path.exists():
            print(f"Error: C++ executable not found at {exe_path}")
            return False
            
        try:
            args = [str(exe_path)] + self.config["cpp_args"]
            print(f"Starting C++ overlay...")
            
            self.cpp_process = subprocess.Popen(
                args,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                creationflags=subprocess.CREATE_NO_WINDOW,
                cwd=self.extracted_dir
            )
            
            # Monitor output in background
            threading.Thread(target=self.monitor_cpp_output, daemon=True).start()
            print("[OK] C++ overlay started")
            return True
            
        except Exception as e:
            print(f"Failed to start C++ overlay: {e}")
            return False
            
    def monitor_cpp_output(self):
        """Monitor C++ process output"""
        if self.cpp_process:
            try:
                for line in iter(self.cpp_process.stdout.readline, b''):
                    if line:
                        print(f"[C++] {line.decode('utf-8', errors='ignore').strip()}")
            except:
                pass
                
    def stop_all(self):
        """Stop all running processes"""
        print("Stopping applications...")
        
        if self.cpp_process:
            try:
                self.cpp_process.terminate()
                self.cpp_process.wait(timeout=2)
                print("[OK] C++ overlay stopped")
            except:
                try:
                    self.cpp_process.kill()
                except:
                    pass
                    
        if self.java_process:
            try:
                self.java_process.terminate()
                self.java_process.wait(timeout=2)
                print("[OK] Java application stopped")
            except:
                try:
                    self.java_process.kill()
                except:
                    pass
                    
    def run(self):
        """Main entry point"""
        print("Desktop Calendar - Standalone Application")
        print("=" * 50)
        print(f"Extracted to: {self.extracted_dir}")
        print()
        
        # Start applications based on config
        if self.config.get("auto_start_cpp", True):
            self.start_cpp_overlay()
            
        if self.config.get("auto_start_java", False):
            self.start_java_gui()
            
        if not self.config.get("auto_start_cpp", True) and not self.config.get("auto_start_java", False):
            print("No applications configured to auto-start.")
            print("Edit desktop_calendar_config.json to change auto-start settings.")
            
        print()
        print("Applications are running in the background.")
        print("Press Ctrl+C to stop all applications and exit.")
        print()
        
        try:
            # Keep running until interrupted
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print()
            self.stop_all()
            print("Application stopped.")

def main():
    """Main function"""
    import argparse
    
    parser = argparse.ArgumentParser(description="Desktop Calendar Standalone Application")
    parser.add_argument("--no-cpp", action="store_true", help="Don't start C++ overlay")
    parser.add_argument("--no-java", action="store_true", help="Don't start Java app")
    parser.add_argument("--config", help="Path to custom config file")
    
    args = parser.parse_args()
    
    # Create and run application
    app = DesktopCalendarStandalone()
    
    # Override config based on command line
    if args.no_cpp:
        app.config["auto_start_cpp"] = False
    if args.no_java:
        app.config["auto_start_java"] = False
        
    # Load custom config if specified
    if args.config:
        try:
            with open(args.config, 'r') as f:
                custom_config = json.load(f)
                app.config.update(custom_config)
        except Exception as e:
            print(f"Error loading custom config: {e}")
    
    app.run()

if __name__ == "__main__":
    main()
'''
        
        # Write the main script with UTF-8 encoding
        try:
            with open(main_script, 'w', encoding='utf-8') as f:
                f.write(main_script_content)
        except UnicodeEncodeError:
            # Fallback: write without encoding specification
            with open(main_script, 'w') as f:
                f.write(main_script_content.replace('✓', '[OK]'))
        
        # Collect all files from dist directory
        data_files = []
        for file_path in self.dist_dir.iterdir():
            if file_path.is_file():
                data_files.append((str(file_path), '.'))
        
        # Create PyInstaller spec file
        spec_content = f'''
# -*- mode: python ; coding: utf-8 -*-

block_cipher = None

a = Analysis(
    ['{main_script}'],
    pathex=[],
    binaries=[],
    datas={data_files},
    hiddenimports=[],
    hookspath=[],
    hooksconfig={{}},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='DesktopCalendar',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,  # Set to False to hide console window
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=['{self.project_root / "scripts" / "calendar.ico"}' if (self.project_root / "scripts" / "calendar.ico").exists() else None],
)
'''
        
        spec_file = temp_dir / "desktop_calendar.spec"
        try:
            with open(spec_file, 'w', encoding='utf-8') as f:
                f.write(spec_content)
        except UnicodeEncodeError:
            with open(spec_file, 'w') as f:
                f.write(spec_content)
        
        try:
            # Run PyInstaller
            import PyInstaller.__main__
            
            print("Building standalone executable with PyInstaller...")
            
            pyinstaller_args = [
                '--onefile',
                '--name=DesktopCalendar',
                '--distpath', str(self.dist_dir),
                '--add-data', f'{self.dist_dir / "CalendarApp.jar"};.',
                '--add-data', f'{self.dist_dir / "CalendarWallpaper.exe"};.',
                '--add-data', f'{self.dist_dir / "desktop_calendar_config.json"};.',
                '--add-data', f'{self.dist_dir / "README_DIST.txt"};.' if (self.dist_dir / "README_DIST.txt").exists() else '',
                '--windowed',  # Use --windowed to hide console
                '--clean',
                '--noconfirm',
            ]
            
            # Add icon if available
            icon_path = self.project_root / "scripts" / "calendar.ico"
            if icon_path.exists():
                pyinstaller_args.extend(['--icon', str(icon_path)])
            
            pyinstaller_args.append(str(main_script))
            
            # Filter out empty strings
            pyinstaller_args = [arg for arg in pyinstaller_args if arg]
            
            print(f"Running PyInstaller with args: {pyinstaller_args}")
            
            # Run PyInstaller
            PyInstaller.__main__.run(pyinstaller_args)
            
            # Find and copy the built executable
            built_exe = self.project_root / "dist" / "DesktopCalendar.exe"
            if built_exe.exists():
                final_exe = self.dist_dir / "DesktopCalendar.exe"
                if built_exe.resolve() != final_exe.resolve(): shutil.copy2(built_exe, final_exe)
                
                exe_size = final_exe.stat().st_size / (1024 * 1024)  # MB
                print(f"[OK] Standalone executable created: {final_exe} ({exe_size:.2f} MB)")
                
                # Clean up PyInstaller build directories
                for dir_to_clean in ['build', 'dist']:
                    cleanup_dir = self.project_root / dir_to_clean
                    if cleanup_dir.exists() and cleanup_dir != self.dist_dir:
                        shutil.rmtree(cleanup_dir, ignore_errors=True)
                
                # Clean up temp dir
                shutil.rmtree(temp_dir, ignore_errors=True)
                
                print(f"\n[OK] Standalone executable is ready!")
                print(f"  Location: {final_exe}")
                print(f"\nYou can now distribute DesktopCalendar.exe as a single file.")
                print(f"It includes Java JAR, C++ executable, and all configuration.")
                print(f"\nUsage: DesktopCalendar.exe [--no-cpp] [--no-java]")
                
                return True
            else:
                print(f"[ERROR] PyInstaller did not create the expected executable")
                print(f"Expected at: {built_exe}")
                # Check for other possible locations
                for possible_exe in self.project_root.rglob("DesktopCalendar.exe"):
                    print(f"Found at: {possible_exe}")
                    final_exe = self.dist_dir / "DesktopCalendar.exe"
                    shutil.copy2(possible_exe, final_exe)
                    print(f"Copied to: {final_exe}")
                    return True
                    
                return False
                
        except Exception as e:
            print(f"Failed to create standalone executable: {e}")
            import traceback
            traceback.print_exc()
            return False
def main():
    """Main entry point for master build script"""
    parser = argparse.ArgumentParser(
        description="Master Build Script for Desktop Calendar Tracking",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python build-complete.py              # Build everything
  python build-complete.py --standalone # Build standalone executable
  python build-complete.py --java-only  # Build only Java
  python build-complete.py --cpp-only   # Build only C++  
  python build-complete.py --no-clean   # Incremental build
  python build-complete.py --check-deps # Check dependencies only
        """
    )
    
    parser.add_argument("--java-only", action="store_true", help="Build only Java application")
    parser.add_argument("--cpp-only", action="store_true", help="Build only C++ overlay")
    parser.add_argument("--no-clean", action="store_true", help="Don't clean before building (incremental)")
    parser.add_argument("--check-deps", action="store_true", help="Check dependencies only")
    parser.add_argument("--no-wallpaper", action="store_true", help="Disable wallpaper mode for C++ build")
    parser.add_argument("--create-dist", action="store_true", help="Create distribution package only")
    parser.add_argument("--standalone", action="store_true", help="Create standalone executable (Windows only)")
    
    args = parser.parse_args()
    
    builder = DesktopCalendarBuilder()
    
    if args.check_deps:
        builder.check_dependencies()
        return 0
    
    clean = not args.no_clean
    wallpaper_mode = not args.no_wallpaper
    
    if args.create_dist:
        builder.create_python_launcher()
        builder.create_distribution()
        return 0
    
    if args.standalone and not builder.is_windows:
        print("Standalone executable is only supported on Windows.")
        return 1
    
    if args.java_only:
        success = builder.build_java(clean=clean)
    elif args.cpp_only:
        success = builder.build_cpp(clean=clean, wallpaper_mode=wallpaper_mode)
    elif args.standalone:
        success = builder.build_all(clean=clean, wallpaper_mode=wallpaper_mode, standalone=True)
    else:
        success = builder.build_all(clean=clean, wallpaper_mode=wallpaper_mode)
    
    return 0 if success else 1

if __name__ == "__main__":
    sys.exit(main())