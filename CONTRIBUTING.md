# Contributing to Desktop Calendar Tracking

First off, thank you for considering contributing to **Desktop Calendar Tracking**! 🎉 Your help is essential for making this project better.

This document provides guidelines and instructions for contributing. Please take a moment to read it – it will make the process easier for everyone.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
  - [Report Bugs](#report-bugs)
  - [Suggest Features](#suggest-features)
  - [Code Contributions](#code-contributions)
  - [Documentation](#documentation)
  - [Testing & Feedback](#testing--feedback)
- [Development Setup](#development-setup)
  - [Prerequisites](#prerequisites)
  - [Building the Project](#building-the-project)
  - [Running the Application](#running-the-application)
- [Coding Standards](#coding-standards)
  - [Java](#java)
  - [C++ (Windows Overlay)](#c-windows-overlay)
  - [Swift (macOS Overlay)](#swift-macos-overlay)
- [Pull Request Process](#pull-request-process)
- [Testing Guidelines](#testing-guidelines)
- [Issue and Feature Request Templates](#issue-and-feature-request-templates)
- [Community & Support](#community--support)

---

## Code of Conduct

This project adheres to the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to [richard@richardsblogs.com](mailto:richard@richardsblogs.com).

---

## How Can I Contribute?

### Report Bugs

If you find a bug, please [open an issue](https://github.com/richie-rich90454/desktop-calendar-tracking/issues) and include:

- A clear, descriptive title.
- Steps to reproduce the behavior.
- Expected vs. actual behavior.
- Screenshots (if applicable).
- Your environment: OS, Java version, overlay enabled/disabled, etc.

### Suggest Features

Feature requests are welcome! When suggesting a feature:

- Use the "Feature Request" issue template.
- Describe the problem you're trying to solve.
- Provide a clear description of the proposed feature.
- Include mockups or examples if possible.
- Explain why this would benefit most users.

### Code Contributions

We accept contributions via pull requests (PRs). Please see [Pull Request Process](#pull-request-process) for details.

**Areas that need help:**
- UI/UX improvements (Java Swing)
- AI prompt engineering for better event generation
- Performance optimizations
- Linux overlay port (GTK/Qt)
- Internationalization (i18n)
- Unit tests and test coverage

### Documentation

Good documentation is crucial. You can help by:

- Fixing typos or clarifying existing docs.
- Adding examples or tutorials.
- Translating documentation.
- Improving inline code comments.

### Testing & Feedback

- Test the application on different platforms (Windows, macOS) and report issues.
- Try new features in development branches and give feedback.
- Participate in discussions and help answer questions from other users.

---

## Development Setup

### Prerequisites

| Component      | Windows                          | macOS                            | Linux (partial)                  |
|----------------|----------------------------------|----------------------------------|----------------------------------|
| Java           | Java 17+ (Temurin/Adoptium)      | Java 17+ (Homebrew)              | Java 17+ (apt/yum)               |
| Build Tools    | Visual Studio 2022 (C++ workload)| Xcode 12+ & Swift 5.3+           | GCC 9+ & CMake 3.16+             |
| AI Backend     | OpenAI API key OR Ollama          | OpenAI API key OR Ollama          | OpenAI API key OR Ollama          |
| Git            | Git for Windows                  | Git (built-in or Homebrew)        | Git                               |

### Building the Project

1. **Fork and clone the repository**
   ```bash
   git clone https://github.com/richie-rich90454/desktop-calendar-tracking.git
   cd desktop-calendar-tracking
   ```

2. **Build the Java application**
   ```bash
   cd scripts
   # On Windows
   build-java.bat
   # On macOS/Linux
   chmod +x build-java.sh && ./build-java.sh
   ```
   The JAR file will be created at `dist/CalendarApp.jar`.

3. **Build the Windows overlay (optional)**
   ```powershell
   cd overlay-windows
   mkdir build
   cd build
   cmake .. -G "Visual Studio 17 2022" -A x64 -DCMAKE_BUILD_TYPE=Release
   cmake --build . --config Release --target CalendarOverlay
   # Output: build/Release/CalendarOverlay.exe
   ```

4. **Build the macOS overlay (optional)**
   ```bash
   cd overlay-macos
   swift build -c release --arch arm64 --arch x86_64
   # Output: .build/release/CalendarOverlay
   ```

### Running the Application

- **Java app only:** `java -jar dist/CalendarApp.jar`
- **With overlay:** The overlay automatically launches the Java app when needed. You can also run the overlay executable directly.

---

## Coding Standards

### Java

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).
- Use 4 spaces for indentation (no tabs).
- Class names: `PascalCase`, method/variable names: `camelCase`.
- Add Javadoc comments for all public classes and methods.
- Keep methods short and focused; aim for single responsibility.
- Use `final` where applicable for immutability.
- Organise imports: no wildcards, static imports for constants only.

### C++ (Windows Overlay)

- Use C++17 features where appropriate.
- Follow [Microsoft GSL](https://github.com/microsoft/GSL) guidelines.
- Indentation: 4 spaces.
- Class names: `PascalCase`, methods: `PascalCase`, variables: `snake_case`.
- Use `nullptr` instead of `NULL` or `0`.
- Prefer standard library over Win32 APIs where possible (but Win32 is necessary for window management).
- Comment complex sections, especially Win32 calls.

### Swift (macOS Overlay)

- Follow [Swift API Design Guidelines](https://swift.org/documentation/api-design-guidelines/).
- Indentation: 4 spaces.
- Use `let` over `var` when possible.
- Name classes, structs, enums with `PascalCase`, methods/properties with `camelCase`.
- Add `// MARK:` comments to organise code.
- Use optionals safely; avoid force unwrapping.

### General

- Write clear, self-documenting code.
- Avoid deep nesting; refactor when needed.
- Keep line length reasonable (100-120 characters).
- Use meaningful variable/function names.
- Write unit tests for new functionality (see [Testing Guidelines](#testing-guidelines)).

---

## Pull Request Process

1. **Create a branch** from `main` with a descriptive name:
   - `feature/your-feature-name`
   - `bugfix/issue-number-description`
   - `docs/improve-readme`
2. **Make your changes**, adhering to coding standards.
3. **Test your changes** thoroughly (see [Testing Guidelines](#testing-guidelines)).
4. **Commit your changes** with a clear commit message:
   - Use the imperative mood ("Add feature" not "Added feature").
   - Reference issues if applicable: `Fixes #123`.
5. **Push your branch** to your fork.
6. **Open a Pull Request** against the `main` branch of the original repository.
   - Fill in the PR template with details about your changes.
   - Link any related issues.
   - If your PR is a work in progress, mark it as draft.
7. **Wait for CI checks** to pass and for maintainers to review.
   - Address any feedback promptly.
   - Once approved, a maintainer will merge your PR.

**Note:** PRs must pass all automated checks (build, tests) and be reviewed before merging.

---

## Testing Guidelines

- **Java unit tests:** Place tests in `control-app/test/` following the same package structure. Use JUnit 5 (or similar) to write tests for new classes and methods.
- **Manual testing:** For UI changes, test on both Windows and macOS if possible. Verify that the overlay renders correctly and responds to events.
- **AI integration:** Test with both OpenAI and Ollama backends (if applicable). Ensure token tracking works.
- **Regression testing:** Make sure existing features still work after your changes.

We aim to maintain high test coverage. If you're unsure how to test a particular change, ask in the PR comments.

---

## Issue and Feature Request Templates

When creating an issue, please use the appropriate template (if one exists). This helps us understand your request quickly. The templates are located in `.github/ISSUE_TEMPLATE/`.

If you don't see a template, just provide as much detail as you can.

---

## Community & Support

- **Discussions:** Use [GitHub Discussions](https://github.com/richie-rich90454/desktop-calendar-tracking/discussions) for questions, ideas, and general chat.
- **Issues:** For bugs and feature requests.
- **Email:** You can reach the maintainer at [richard@richardsblogs.com](mailto:richard@richardsblogs.com) for sensitive matters.

We're excited to have you on board! 🚀

---

**Thank you for contributing!**
