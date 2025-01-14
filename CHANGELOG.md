# Change Log

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

- Add workaround for KSP bug which leads to multiple instances of the same symbol in the same round https://github.com/google/ksp/issues/1993 (fixes [Issue #4](https://github.com/IlyaGulya/anvil-utils/issues/4))

### Security

### Custom Code Generator

### Other Notes & Contributions

## [0.3.0-beta02] - 2024-12-14

### Changed

- Upgraded Dagger to `2.53.1`

### Fixed

- Correctly defer symbols with unresolved references to the next KSP round (fixes [Issue #4](https://github.com/IlyaGulya/anvil-utils/issues/4))

## [0.3.0-beta01] - 2024-05-30

### Changed
- Migrated from `com.squareup.anvil` to `dev.zacsweers.anvil` plugin.
- Upgrade kotlin from `2.0.0` to `2.0.21`
- Upgrade KSP from `2.0.0-1.0.21` to `2.0.21-1.0.25`
- Upgrade Dagger from `2.51.1` to `2.52`
- Upgrade kotlinx binary compatibility checker from `0.14.0` to `0.16.3`
- Upgrade vanniktech maven publish plugin from `0.28.0` to `0.30.0`
- Upgrade Kotlinpoet from `1.17.0` to `2.0.0`
- Upgrade Gradle from `8.7` to `8.10.2`

## [0.2.1-beta01] - 2024-07-22

### Fixed

- Fixed lambda type resolving in KSP processor [\#2](https://github.com/IlyaGulya/anvil-utils/issues/2)

## [0.2.0-beta02] - 2024-05-30

### Changed
- Refactored code generator to be common between KSP and non-KSP implementations
- Made KSP processor a nested class and moved to proper package to prevent possible conflicts with another KSP processors.

## [0.2.0-beta01] - 2024-05-25

### Added
- Support Anvil KSP mode

### Changed
- Updated `Anvil` to `2.5.0-beta09`
- Better support for incremental compilation using new `GeneratedFilesWithSources` API from `Anvil`
- Split `:samples` module to multi-module structure showcasing usage of KSP and non-KSP code generators. See `Module structure in the project` section in README.md for more details.
- Updated `README.md` with explanation on how to use KSP code generators.

## [0.1.0] - 2024-03-21

- Initial release.



[Unreleased]: https://github.com/IlyaGulya/anvil-utils/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/IlyaGulya/anvil-utils/releases/tag/v0.1.0
