# Call Blocker App - Android Project

## Project Overview
This is a Kotlin-based Android application for call blocking and spam detection. The app helps users manage unwanted calls with features like:
- Block/unblock phone numbers
- Whitelist management
- Multi-source spam detection (APIs and web scraping)
- STIR/SHAKEN verification
- Import/export settings

## Project Architecture
- **Language**: Kotlin
- **Platform**: Android (minimum SDK 29, target SDK 34)
- **Build System**: Gradle with Kotlin DSL
- **Dependencies**: AndroidX, OkHttp, Jsoup, LibPhoneNumber, Material Design
- **Package**: com.addev.listaspam

## Current Status
This is a native Android mobile application that requires:
- Android SDK
- Android build tools
- Android emulator or physical device for testing

## Replit Environment Limitations
Native Android apps cannot run directly in Replit's web-based environment because:
1. Android SDK installation requires significant resources and setup
2. Apps need Android runtime (ART) to execute
3. Mobile UI cannot be displayed in web browsers
4. Hardware access (calls, contacts) requires Android OS

## Alternative Solutions for Development
1. **Code Analysis**: Review and analyze the Kotlin source code
2. **Build Setup**: Configure basic Gradle build (limited without full Android SDK)
3. **Documentation**: Generate project documentation
4. **Code Quality**: Run static analysis tools
5. **Migration**: Convert core logic to a web application

## Recent Changes
- 2025-09-09: Initial project import and analysis
- Java and Gradle tools installed
- Project structure documented