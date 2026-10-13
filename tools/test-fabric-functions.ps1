param(
    [string]$Minecraft = "1.21.1",
    [string[]]$Modes = @(
        "smokeTest", "mailboxTest", "mailReferenceTest", "popupAnimationTest",
        "mailBodyEditorTest", "mailAdminTest", "hotEventsTest", "spotlightUiTest",
        "journeyTest", "journeyEditingTest", "overviewTest", "overviewAuthoringTest",
        "journalFocusTest", "journalOrderTest", "generateTasksTest", "generatedDeletionTest",
        "trackingHudTest", "lootTableTest", "canvasEditorTest", "locationItemTest", "mediaTest",
        "controlGraphTest", "unlockGraphTest", "rewardPopupTest", "claimReceiptTest",
        "rewardQualityTest", "creationTest", "animationTest", "runtimeTest", "structureSceneTest",
        "buttonImageTest", "rewardsTest", "templateTest"
    ),
    [switch]$UseDownloadMirror
)
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Join-Path $repoRoot "platforms\fabric\$Minecraft"
$verificationRoot = Join-Path $projectRoot "build\verification"
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$reportRoot = Join-Path $repoRoot "build\verification\fabric-$Minecraft-functions-$runId"
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$results = [System.Collections.Generic.List[object]]::new()
foreach ($mode in $Modes) {
    if ($mode -notmatch "^[a-zA-Z]+Test$") { throw "Invalid smoke mode: $mode" }
    $property = "fabric" + $mode.Substring(0, 1).ToUpperInvariant() + $mode.Substring(1)
    $outputRoot = Join-Path $reportRoot $mode
    New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
    $logFile = Join-Path $outputRoot "gradle.log"
    $resultFile = Join-Path $verificationRoot "result.txt"
    if (Test-Path -LiteralPath $resultFile) { Remove-Item -LiteralPath $resultFile }
    $startedAt = Get-Date
    Write-Host "RUN Fabric $Minecraft $mode"
    Push-Location $repoRoot
    try {
        $arguments = @("-p", $projectRoot, "runClient", "-P$property",
            "-PfabricSmokeRunDir=build/smoke-runs/$runId/$mode")
        if ($UseDownloadMirror) { $arguments += "-PuseDownloadMirror" }
        & .\gradlew.bat @arguments *> $logFile
        $exitCode = $LASTEXITCODE
    } finally { Pop-Location }
    $detail = if (Test-Path -LiteralPath $resultFile) {
        Get-Content -LiteralPath $resultFile -Raw
    } else { "No client result produced; inspect gradle.log" }
    $status = if ($exitCode -eq 0 -and $detail.StartsWith("PASS:")) { "PASS" } else { "FAIL" }
    if (Test-Path -LiteralPath $verificationRoot) {
        Get-ChildItem -LiteralPath $verificationRoot -File |
            Where-Object { $_.LastWriteTime -ge $startedAt } | Copy-Item -Destination $outputRoot
    }
    $gameLogDir = Join-Path $projectRoot "build\smoke-runs\$runId\$mode\logs"
    if (Test-Path -LiteralPath $gameLogDir) {
        Get-ChildItem -LiteralPath $gameLogDir -File -Filter "*.log" | Copy-Item -Destination $outputRoot
    }
    $results.Add([pscustomobject]@{
        Minecraft = $Minecraft; Mode = $mode; Status = $status; ExitCode = $exitCode; Detail = $detail.Trim()
    })
    $results | Export-Csv -LiteralPath (Join-Path $reportRoot "results.csv") -NoTypeInformation -Encoding utf8
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $reportRoot "results.json") -Encoding utf8
    Write-Host "$status $mode : $($detail.Split([Environment]::NewLine)[0])"
}
Write-Host "Reports: $reportRoot"
if ($results.Status -contains "FAIL") { exit 1 }
