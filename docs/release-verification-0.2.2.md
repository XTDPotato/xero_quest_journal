# 0.2.2 Release Verification

[简体中文](../README.md) | **English** · [Compatibility matrix](../porting-matrix.md) · [Release](https://github.com/XTDPotato/xero_quest_journal/releases/tag/v0.2.2)

Verification date: 2026-10-20. This release preserves the existing `0.2.1` release and adds tracked-item HUD notices, configurable placement/scale, minimap-corner avoidance, and the corrected overview/journey detail layouts.

## Changes Verified

- Tracked item pickup notices show the item icon, localized name, picked amount and current/required condition progress.
- The pickup HUD retains at most four entries, newest first, with slide-in and fade-out transitions. Its translucent surfaces reveal the world behind them.
- Notification settings persist overall pickup-notice scale (50-200%), X/Y placement, minimap avoidance and the preferred minimap corner. Older client preference files inherit 100% scale, X=0%, Y=40%, avoidance enabled and top-right corner.
- Task tracking is placed first. Unlock/completion notices and pickup rows move to another free corner when their rectangles overlap.
- Off-screen coordinate-tracking markers use a rotated `<` glyph at the screen edge, oriented toward the target.
- Xaero Minimap/World Map, JourneyMap, VoxelMap and FTB Chunks trigger a reserved minimap area. Since their private live coordinates are not read, the corner is selectable; disable the reservation when a minimap uses another layout.
- The overview's anecdote icon uses the shared icon slot and label origin; its right-side navigation emblem uses the original compact size.
- Journey task details occupy 75% of the viewport, show rewards in the footer, and no longer include a redundant “Open task screen” action.

## Client Verification

Run from the repository root with Java 21:

```powershell
.\gradlew.bat build --offline --console=plain
.\gradlew.bat runClient -PauthoringDocsTest --offline --console=plain
.\gradlew.bat runClient -PtrackingHudTest --offline --console=plain
.\gradlew.bat runClient -PoverviewTest --offline --console=plain
.\gradlew.bat runClient -PjourneyEditingTest --offline --console=plain
.\gradlew.bat runClient -PinterfaceDocsTest -PdocsLanguage=zh_cn --offline --console=plain
.\gradlew.bat runClient -PinterfaceDocsTest -PdocsLanguage=en_us --offline --console=plain
```

The authoring smoke verifies position and scale stepper/input updates, minimap corner and avoidance controls, persistence, and legacy defaults. The tracking HUD smoke simulates reserved minimap areas in all four corners and verifies that task tracking, notifications and pickup rows do not overlap; it also captures the pickup HUD at native 1920x1080. The overview regression checks the single anecdote entry, shared icon/text alignment, compact arrow size and small-window behavior. Journey editing verifies the task popup, rewards and close/return flow.

The broader `journeyTest` was also attempted but did not pass: it stops at the pre-existing chapter-progress drag assertion (stage 9), before reaching task-popup checks. The focused `journeyEditingTest` passes the changed 75%-sized popup and no-navigation-button assertions. Do not interpret the broader journey suite as passed.

The bilingual gallery smoke captures 57 real screens per locale at native 1920x1080. `tools/sync-bilingual-documentation.ps1` synchronizes 114 paired images and their SHA256 manifest. `tools/verify-interface-gallery.ps1` validates both galleries. `tools/verify-authoring-documentation.ps1` checks links, fenced blocks, command routes, locale keys and full-HD screenshot dimensions.

## Production Artifacts

Four installable targets are built and packaged independently. All four `buildSelected`/root build commands completed successfully:

| Target | Java | Command |
| --- | --- | --- |
| NeoForge 1.21.1 | 21 | `.\gradlew.bat build --offline --console=plain` |
| Fabric 1.21.1 | 21 | `.\gradlew.bat buildSelected "-Pmc_target=1.21.1" "-Ploader=fabric" --offline --console=plain` |
| Forge 1.20.1 | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=forge" --offline --console=plain` |
| Fabric 1.20.1 | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=fabric" --offline --console=plain` |

The supported release packager checks loader metadata, embedded version, required authoring/HUD classes, navigation assets, test-source exclusion and JAR size, then writes four independent JARs, an organized ZIP and SHA256 checksums. The 26.1-26.3 projects retain partial migration work and are not release artifacts.

