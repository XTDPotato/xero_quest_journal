param(
    [string[]]$Modes = @("authoringDocsTest", "smokeTest", "controlGraphTest",
        "animationTest", "popupAnimationTest", "rewardsTest", "runtimeTest"),
    [string[]]$ExtraMods = @()
)
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$runDirectories = @{
    authoringDocsTest = "run-authoring-docs"; smokeTest = "run-smoke"
    controlGraphTest = "run-control-graphs"; templateTest = "run-template"
    trackingHudTest = "run-tracking-hud"; animationTest = "run-animation"
    popupAnimationTest = "run-popup-animation"; rewardsTest = "run-rewards"; runtimeTest = "run-runtime"
    journeyEditingTest = "run-journey-editing"; journeyTest = "run-journey-continuous"
}
$verificationRoot = Join-Path $repoRoot "build/verification"
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$reportRoot = Join-Path $verificationRoot "neoforge-functions-$runId"
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$results = [Collections.Generic.List[object]]::new()
foreach ($mode in $Modes) {
    if (!$runDirectories.ContainsKey($mode)) { throw "Unsupported NeoForge suite: $mode" }
    $output = Join-Path $reportRoot $mode
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $resultFile = Join-Path $verificationRoot "result.txt"
    if (Test-Path -LiteralPath $resultFile) { Remove-Item -LiteralPath $resultFile }
    $startedAt = Get-Date
    Write-Host "RUN NeoForge 1.21.1 $mode"
    $runDirectory = "run-$mode-$runId"
    if ($ExtraMods.Count -gt 0) {
        foreach ($extraMod in $ExtraMods) {
            if (!(Test-Path -LiteralPath $extraMod -PathType Leaf)) { throw "Optional test mod not found: $extraMod" }
        }
        $mods = Join-Path $repoRoot "$runDirectory/mods"
        New-Item -ItemType Directory -Force -Path $mods | Out-Null
        foreach ($extraMod in $ExtraMods) { Copy-Item -LiteralPath $extraMod -Destination $mods }
    }
    Push-Location $repoRoot
    try {
        & .\gradlew.bat runClient "-P$mode" "-PneoSmokeRunDir=$runDirectory" *> (Join-Path $output "gradle.log")
        $exitCode = $LASTEXITCODE
    } finally { Pop-Location }
    $detail = if (Test-Path -LiteralPath $resultFile) {
        Get-Content -LiteralPath $resultFile -Raw
    } else { "No client result produced; inspect gradle.log" }
    $status = if ($exitCode -eq 0 -and $detail.StartsWith("PASS:")) { "PASS" } else { "FAIL" }
    Get-ChildItem -LiteralPath $verificationRoot -File |
        Where-Object { $_.LastWriteTime -ge $startedAt } | Copy-Item -Destination $output
    $logs = Join-Path (Join-Path $repoRoot $runDirectory) "logs"
    if (Test-Path -LiteralPath $logs) {
        Get-ChildItem -LiteralPath $logs -File -Filter "*.log" | Copy-Item -Destination $output
    }
    $results.Add([pscustomobject]@{
        Minecraft = "1.21.1"; Loader = "NeoForge"; Mode = $mode
        Status = $status; ExitCode = $exitCode; Detail = $detail.Trim()
    })
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $reportRoot "results.json") -Encoding utf8
    $results | Export-Csv -LiteralPath (Join-Path $reportRoot "results.csv") -NoTypeInformation -Encoding utf8
    Write-Host "$status $mode : $($detail.Split([Environment]::NewLine)[0])"
}
Write-Host "Reports: $reportRoot"
if ($results.Status -contains "FAIL") { exit 1 }
