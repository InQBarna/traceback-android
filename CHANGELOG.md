# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]


## [1.1.2] - 2025-11-25
### Changed
- Remove unnecessary condition to check for referral

## [1.1.1] - 2025-11-12

### Changed
- Avoid using clipboard if a `deepLink` is already available
### Fixed
- Resolution errors for campaign fixes

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
