# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Avoid using clipboard if a `deepLink` is already available

## [1.1.0] - 2025-11-10

### Changed
- Adapted to version 0.5.0 of Traceback API

### Added
- Added failure reporting for Traceback resolution failures

## [1.0.1] - 2025-09-25

### Fixed
- Fixed analytics reporting issues

### Added
- Added utility methods for SDK functionality

## [1.0.0] - 2025-09-25

### Added
- Initial release of Traceback SDK for Android
- Deep link handling and resolution
- Automatic SDK initialization via ContentProvider
- Integration with Firebase Traceback Extension
- Heuristics match-type support for link attribution
- Analytics gathering capabilities
- Install referrer handling
- Clipboard-based link resolution fallback
- Configurable SDK behavior via TracebackConfigProvider
- Content provider for automatic initialization
- Coroutine-based asynchronous link resolution
- Documentation and README

### Changed
- Upgraded Android Gradle Plugin
- Use first install time as threshold for attribution
- Minor naming improvements

### Fixed
- Fixed Content provider ID configuration
- Prevented exceptions from getInstallReferrer
- Added coroutine active state checks

[Unreleased]: https://github.com/InQBarna/traceback-sdk-android/compare/1.1.0...HEAD
[1.1.0]: https://github.com/InQBarna/traceback-sdk-android/compare/1.0.1...1.1.0
[1.0.1]: https://github.com/InQBarna/traceback-sdk-android/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/InQBarna/traceback-sdk-android/releases/tag/1.0.0
