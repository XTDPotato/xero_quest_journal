param([string]$OutputDirectory = "")

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$verificationRoot = Join-Path $repoRoot "build\verification"
if ($OutputDirectory -eq "") { $OutputDirectory = Join-Path $verificationRoot "forge-functions-latest" }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$latest = @{}
foreach ($batch in Get-ChildItem -LiteralPath $verificationRoot -Directory -Filter "forge-functions-*" |
        Where-Object Name -ne "forge-functions-latest" | Sort-Object Name) {
    $resultFile = Join-Path $batch.FullName "results.csv"
    if (!(Test-Path -LiteralPath $resultFile)) { continue }
    foreach ($row in Import-Csv -LiteralPath $resultFile) {
        $latest[$row.Mode] = [pscustomobject]@{
            Mode = $row.Mode; Status = $row.Status; ExitCode = $row.ExitCode
            Detail = $row.Detail; EvidenceDirectory = Join-Path $batch.FullName $row.Mode
        }
    }
}
$rows = @($latest.Values | Sort-Object Mode)
$rows | Export-Csv -LiteralPath (Join-Path $OutputDirectory "results.csv") -NoTypeInformation -Encoding utf8
$rows | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory "results.json") -Encoding utf8
$markdown = [System.Collections.Generic.List[string]]::new()
$markdown.Add("# Forge 1.20.1 Functional Verification")
$markdown.Add("")
$markdown.Add("Latest completed result per suite. Evidence comes from multiple isolated runs; this is not one simultaneous run.")
$markdown.Add("")
$markdown.Add("| Suite | Result |")
$markdown.Add("| --- | --- |")
foreach ($row in $rows) { $markdown.Add("| $($row.Mode) | $($row.Status) |") }
$markdown.Add("")
$markdown.Add("Limits: dedicated/remote server not tested; EULA remains false. TaCZ, KubeJS, recipe viewers and the optional media backend were not installed. Minecraft 1.20.1 background blur comparisons are skipped.")
$markdown.Add("")
foreach ($row in $rows) {
    $markdown.Add("## $($row.Mode): $($row.Status)")
    $markdown.Add("")
    $markdown.Add($row.Detail)
    $markdown.Add("")
}
$markdown | Set-Content -LiteralPath (Join-Path $OutputDirectory "report.md") -Encoding utf8
$rows | Group-Object Status | Select-Object Name, Count | Format-Table
Write-Host "Report: $OutputDirectory"
