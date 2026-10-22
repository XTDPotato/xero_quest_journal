# Compatibility And Verification

[简体中文](../porting-matrix.md) | **English** · [Documentation](README.md)

## Installable Targets

| Minecraft | Loader tested in project | Java |
| --- | --- | --- |
| 1.20.1 | Forge 47.3.0 | 17 |
| 1.20.1 | Fabric Loader 0.16.10, Fabric API 0.92.12+1.20.1 | 17 |
| 1.21.1 | NeoForge 21.1.233 | 21 |
| 1.21.1 | Fabric Loader 0.16.9, Fabric API 0.108.0+1.21.1 | 21 |

Each target has a distinct JAR, sharing business/UI source and resources through platform adaptation.
The root build targets NeoForge 1.21.1.
The 26.1-26.3 projects have partial Java 25/build/persistence work, with incomplete full migration.
No 26.x installable artifacts are published in 0.2.3.

## Reproduce Builds

```powershell
# Java 21
.\gradlew.bat build
.\gradlew.bat -p platforms/fabric/1.21.1 build
# Java 17, sequential
.\gradlew.bat -p platforms/forge/1.20.1 build
.\gradlew.bat -p platforms/fabric/1.20.1 build
```

Fabric 1.20.1 uses the Forge-generated source adaptation; do not run those two builds concurrently.
Use `-PuseDownloadMirror` for the project's configured Fabric download mirrors where needed.
`--offline` works only with the necessary dependencies already cached.

The core build checks size below 5,000,000 bytes and media dependency isolation.
Production packaging checks loader metadata, expected feature classes, required toolbar icons
and absence of smoke-test classes.

## Client Workflows And Screenshots

Focused tests use a real client and integrated server:

```powershell
.\gradlew.bat runClient -PcandidatePickerTest
.\gradlew.bat runClient -PlootTableTest
.\gradlew.bat runClient -PjourneyEditingTest -PneoSmokeRunDir=run-new-journey-test
.\gradlew.bat runClient -PjournalFocusTest -PneoSmokeRunDir=run-new-journal-test
```

Use a fresh run directory when a test creates named fixtures.
Screenshots have their own explicit capture tasks:

```powershell
.\gradlew.bat runClient -PinterfaceDocsTest -PdocsLanguage=zh_cn
.\gradlew.bat runClient -PinterfaceDocsTest -PdocsLanguage=en_us
```

These tasks use equivalent localized fixtures, require native 1920×1080 and correct locale,
and record manifests with scene, screen class, timestamp and SHA256.
They additionally check the underline default, numeric/stepper setting, persistence, old-config
fallback and rendered 1/3/5px sizes. A capture pass does not imply every interaction passed.

## Historical And Current Results

The project has historical multi-loader functional tests from October 13 and 16, 2026,
and focused October 18 checks for candidates/jigsaw, loot metadata, categories, journey editing
and tooltip rendering. Those results belong to their dates and tested builds.

The 0.2.3 verification report records this release's actual build/capture/inspection results:
[0.2.3 report](../release-verification-0.2.3.md).
Subsequent generator, objective popup and actual backpack tests are recorded in the
[October 19 fix report](../generator-and-objective-verification-2026-10-18.md).
The [October 18 preview report](../release-verification-2026-10-18.md) preserves previous evidence.

Known unresolved broad checks:

- Broad creation workflow: `context-copy_id` click failure.
- Continuous journey suite: progress-pointer rounding and an outdated inline condition-detail
  assertion; diagnostic continuation later failed an appearance undo assertion.
- Earlier full canvas run had a native client exit after some checks; the narrower canvas-history
  subset has its own result.

The full journey editing test passed in preview 20; that does not erase a separate appearance
history failure in the continuous browsing suite.

## Multiplayer, Integrations And Recovery

Fabric production JARs have historical dedicated-server initialization checks that stopped
at the EULA gate with `eula=false`. This is not a multiplayer gameplay or remote-authoring pass.
All third-party viewer, team and media combinations are not exhaustively tested.

Scripts have budgets and restricted APIs, not a universal sandbox for untrusted code.
Definition files, inventory and external effects do not form one crash-proof cross-file transaction.
Back up configuration and world progress independently.
