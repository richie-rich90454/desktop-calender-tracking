#!/usr/bin/env python3

import os
import sys
import shutil
import subprocess
import platform
from pathlib import Path

class JavaBuilder:
    def __init__(self, project_root="."):
        self.project_root = Path(project_root).absolute()
        self.control_app = self.project_root / "control-app"
        self.src_dir = self.control_app / "src"
        self.lib_dir = self.control_app / "lib"
        self.build_dir = self.project_root / "build"
        self.dist_dir = self.project_root / "dist"
    def clean(self):
        print("Cleaning Java build directories...")
        if self.build_dir.exists():
            shutil.rmtree(self.build_dir)
        jar = self.dist_dir / "CalendarApp.jar"
        if jar.exists():
            jar.unlink()

        print("Java clean complete")
        
    def compile(self):
        print("Compiling Java source files...")
        
        self.build_dir.mkdir(parents=True, exist_ok=True)
        
        java_files = []
        for root, dirs, files in os.walk(self.src_dir):
            for file in files:
                if file.endswith(".java"):
                    java_files.append(str(Path(root) / file))
        
        if not java_files:
            print("No Java source files found!")
            return False
        
        print(f"Found {len(java_files)} Java source files")
        
        classpath = []
        if self.lib_dir.exists() and any(self.lib_dir.iterdir()):
            classpath.append(str(self.lib_dir / "*"))
        classpath.append(str(self.src_dir))
        
        if platform.system() == "Windows":
            cp_str = ";".join(classpath)
        else:
            cp_str = ":".join(classpath)
        
        compile_cmd = [
            "javac",
            "-d", str(self.build_dir),
            "-cp", cp_str,
            "-encoding", "UTF-8",
            "--release", "17",
            "-Xlint:unchecked"
        ] + java_files
        
        try:
            result = subprocess.run(
                compile_cmd, 
                capture_output=True, 
                text=True,
                shell=platform.system() == "Windows"
            )
            
            if result.returncode != 0:
                print("Compilation errors:")
                print(result.stderr)
                return False
            
            if result.stdout or result.stderr:
                print(result.stdout)
                if result.stderr and "warning" in result.stderr.lower():
                    print("Warnings:")
                    print(result.stderr)
            
            print("Compilation successful!")
            return True
            
        except subprocess.CalledProcessError as e:
            print(f"Compilation failed: {e.stderr}")
            return False
        except FileNotFoundError:
            print("Error: Java compiler (javac) not found. Make sure JDK is installed.")
            return False
    
    def copy_resources(self):
        print("Copying resource files...")
        
        resource_count = 0
        for root, dirs, files in os.walk(self.src_dir):
            for file in files:
                if not file.endswith(".java"):
                    src_path = Path(root) / file
                    rel_path = src_path.relative_to(self.src_dir)
                    dst_path = self.build_dir / rel_path
                    dst_path.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(src_path, dst_path)
                    resource_count += 1
        
        if resource_count > 0:
            print(f"Copied {resource_count} resource files")
        else:
            print("No resource files found")
    
    def create_jar(self, jar_name="CalendarApp.jar"):
        print(f"Creating JAR file: {jar_name}...")
        
        self.dist_dir.mkdir(parents=True, exist_ok=True)
        jar_path = self.dist_dir / jar_name
        
        manifest_path = self.build_dir / "MANIFEST.MF"
        with open(manifest_path, "w", encoding="utf-8") as f:
            f.write("""Manifest-Version: 1.0
Main-Class: app.Main
Class-Path: .
""")
        
        jar_cmd = [
            "jar", "cfm",
            str(jar_path),
            str(manifest_path),
            "-C", str(self.build_dir), "."
        ]
        
        try:
            subprocess.run(jar_cmd, check=True, capture_output=True)
            print(f"JAR created successfully: {jar_path}")
            print(f"JAR size: {jar_path.stat().st_size / 1024:.2f} KB")
            return True
        except subprocess.CalledProcessError as e:
            print(f"Failed to create JAR: {e.stderr}")
            return False
        except FileNotFoundError:
            print("Error: 'jar' command not found. Make sure JDK is installed (not just JRE).")
            return False
    
    def run_tests(self):
        print("Running tests...")
        
        test_cmd = ["java", "-cp", str(self.build_dir), "app.Main", "--test"]
        
        try:
            subprocess.run(test_cmd, check=True)
            print("Tests completed")
            return True
        except subprocess.CalledProcessError:
            print("Some tests failed")
            return False
        except FileNotFoundError:
            print("Java not found for running tests")
            return False
    
    def quick_build(self):
        self.clean()
        if self.compile():
            self.copy_resources()
            if self.create_jar():
                print("\n" + "="*50)
                print("BUILD SUCCESSFUL!")
                print(f"Output: {self.dist_dir}/CalendarApp.jar")
                print("="*50)
                return True
        return False
    
    def incremental_build(self):
        if self.build_dir.exists():
            print("Performing incremental build...")
        else:
            print("Build directory not found, performing full build...")
            return self.quick_build()
        
        if self.compile():
            self.copy_resources()
            if self.create_jar():
                print("Incremental build successful!")
                return True
        return False

def main():
    import argparse
    
    parser = argparse.ArgumentParser(description="Build script for Java Calendar Application")
    parser.add_argument("--clean", action="store_true", help="Clean build directories only")
    parser.add_argument("--compile", action="store_true", help="Compile only (no JAR)")
    parser.add_argument("--jar", action="store_true", help="Create JAR only (assumes compilation done)")
    parser.add_argument("--incremental", action="store_true", help="Incremental build")
    parser.add_argument("--test", action="store_true", help="Run tests after build")
    parser.add_argument("--name", type=str, default="CalendarApp.jar", help="Output JAR filename")
    
    args = parser.parse_args()
    
    builder = JavaBuilder()
    
    if args.clean:
        builder.clean()
    elif args.compile:
        builder.clean()
        builder.compile()
    elif args.jar:
        builder.create_jar(args.name)
    elif args.incremental:
        builder.incremental_build()
    else:
        success = builder.quick_build()
        if args.test and success:
            builder.run_tests()
    
    return 0

if __name__ == "__main__":
    sys.exit(main())