# Change Log

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

### Custom Code Generator

### Other Notes & Contributions

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
