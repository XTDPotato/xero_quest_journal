# 0.2.3 Release Verification

[简体中文](../README.md) | **English** · [Compatibility matrix](porting-matrix.md)

Verification date: 2026-10-22. This release follows the existing 0.2.2 release without
reusing its tag or assets. The build artifacts target Forge 1.20.1, Fabric 1.20.1,
NeoForge 1.21.1 and Fabric 1.21.1. The 26.1-26.3 projects remain partial migrations
and are not installable artifacts.

## Anecdote Reward Page

- The floating reward book uses a 20% gray scrim.
- The map watermark is gray, reduced, and placed in the reserved lower area of the left page,
  behind the last two sticker slots rather than across completed stickers.
- Author mode exposes exact X, Y and scale inputs for the ticket, reward-item group and claim button.
- Dragging snaps to 8 design-canvas units. Holding Shift bypasses snapping until released.
- Numeric edits and drag/resize operations update the live preview and participate in undo/redo.
- The selected entry's `rewardLayout` is persisted by the server on save.

## Client Regression

Java 21, NeoForge 1.21.1:

```powershell
.\gradlew.bat runClient -PanecdoteUiTest --offline --console=plain
```

Passed in the real client and integrated server. Assertions cover the 20% gray popup scrim,
watermark placement, exact numeric X updates, snap/Shift bypass, drag, undo/redo, save and
server-side persisted geometry. The run also captured the reward page and layout editor at
1920×1080. Screenshots are stored in `docs/images/`.

Documentation and gallery checks:

```powershell
pwsh -File tools/verify-authoring-documentation.ps1
pwsh -File tools/verify-interface-gallery.ps1
```

Both passed: Markdown links/fences, 73 shared and 74 Forge command routes, 15 documented
script examples, unique locale keys, 169 native 1920×1080 captures, and 67 Chinese gallery
images with verified hashes.

## Installable Targets

| Minecraft | Loader | Java |
| --- | --- | --- |
| 1.20.1 | Forge 47.3.0 | 17 |
| 1.20.1 | Fabric Loader 0.16.10 / Fabric API 0.92.12+1.20.1 | 17 |
| 1.21.1 | NeoForge 21.1.233 | 21 |
| 1.21.1 | Fabric Loader 0.16.9 / Fabric API 0.108.0+1.21.1 | 21 |

All four production builds passed:

```powershell
# Java 21
.\gradlew.bat build --offline --console=plain
.\gradlew.bat buildSelected "-Pmc_target=1.21.1" "-Ploader=fabric" --offline --console=plain

# Java 17, sequential
.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=forge" --offline --console=plain
.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=fabric" --offline --console=plain
```

`test` is `NO-SOURCE`; the functional evidence in this report is the real-client
`anecdoteUiTest`, not a claim that every historical feature suite was rerun. The release
packager verified each loader's metadata/version, required classes/assets, test-source
exclusion and JAR size, then created the four JARs, organized bilingual documentation bundle
and SHA256 manifest.

SHA256:

```text
65a571154d8b7c46298d6d5a643d01d875be11c8d2157bfa65c64cf1da4f79a1  xero_quest_journal-neoforge-1.21.1-0.2.3.jar
2016a4013dbce11c90256f2ad450fc5a837654080ef9a69c6e760f8d038f64fe  xero_quest_journal-forge-1.20.1-0.2.3.jar
adb4c8d3eb77ac7f4d8cfef130c89bcf69f745e0f5bc735c1dfe8a8a747dc1b5  xero_quest_journal-fabric-1.20.1-0.2.3.jar
ec5e8a166d67dd1dd03011c7a43af2faab7d81e9ec5f973ab9cb3225f55cac39  xero_quest_journal-fabric-1.21.1-0.2.3.jar
```

`SHA256SUMS.txt` attached to the release is the source of truth for all four JARs and the
supported-builds ZIP.
