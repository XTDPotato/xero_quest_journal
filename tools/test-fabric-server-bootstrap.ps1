param([ValidateSet("1.20.1", "1.21.1")][string]$Minecraft = "1.21.1")
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Join-Path $repoRoot "platforms\fabric\$Minecraft"
$eulaFile = Join-Path $projectRoot "run-server-bootstrap\eula.txt"
if ((Test-Path -LiteralPath $eulaFile) -and
        ((Get-Content -LiteralPath $eulaFile -Raw) -match '(?m)^\s*eula\s*=\s*true\s*$')) {
    throw "This bootstrap check requires an unaccepted EULA; the existing file will not be changed."
}
$reportRoot = Join-Path $repoRoot ("build\verification\fabric-$Minecraft-server-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$logFile = Join-Path $reportRoot "gradle.log"
Push-Location $repoRoot
try {
    & .\gradlew.bat -p $projectRoot runServer *> $logFile
    $exitCode = $LASTEXITCODE
} finally { Pop-Location }
$output = Get-Content -LiteralPath $logFile -Raw
if ($exitCode -ne 0 -or $output -notmatch "Xero Quest Journal Fabric runtime initialized" -or
        $output -notmatch "You need to agree to the EULA" -or
        $output -match "Could not execute entrypoint|Failed to start the minecraft server|Mixin apply.*failed") {
    throw "Fabric server bootstrap failed; inspect $logFile"
}
if ((Get-Content -LiteralPath $eulaFile -Raw) -notmatch '(?m)^\s*eula\s*=\s*false\s*$') {
    throw "EULA refusal was not confirmed."
}
"PASS: physical SERVER mod initialization and common mixins; stopped at EULA refusal (eula=false). Server gameplay not tested." |
    Set-Content -LiteralPath (Join-Path $reportRoot "result.txt") -Encoding UTF8
Write-Host "PASS Fabric $Minecraft server bootstrap. Report: $reportRoot"
