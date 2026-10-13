param([ValidateSet("1.20.1", "1.21.1")][string]$Minecraft = "1.21.1")
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Join-Path $repoRoot "platforms\fabric\$Minecraft"
$reportRoot = Join-Path $repoRoot ("build\verification\fabric-$Minecraft-production-server-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
Push-Location $repoRoot
try {
    & .\gradlew.bat -p $projectRoot exportProductionRuntime *> (Join-Path $reportRoot "build.log")
    if ($LASTEXITCODE -ne 0) { throw "Could not export production runtime: $reportRoot" }
} finally { Pop-Location }
$runtime = Get-Content -LiteralPath (Join-Path $projectRoot "build\production-runtime.json") -Raw | ConvertFrom-Json
$runDir = Join-Path $projectRoot "build\production-server-bootstrap"
$eulaFile = Join-Path $runDir "eula.txt"
if ((Test-Path -LiteralPath $eulaFile) -and
        ((Get-Content -LiteralPath $eulaFile -Raw) -match '(?m)^\s*eula\s*=\s*true\s*$')) {
    throw "Production bootstrap requires an unaccepted EULA; the existing file will not be changed."
}
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$logFile = Join-Path $reportRoot "server.log"
Push-Location $runDir
try {
    & (Join-Path $env:JAVA_HOME "bin\java.exe") "-Dfabric.development=false" `
        "-Dfabric.addMods=$($runtime.mods -join [IO.Path]::PathSeparator)" `
        "-cp" ($runtime.classpath -join [IO.Path]::PathSeparator) `
        "net.fabricmc.loader.impl.launch.knot.KnotServer" "nogui" *> $logFile
    $exitCode = $LASTEXITCODE
} finally { Pop-Location }
$output = Get-Content -LiteralPath $logFile -Raw
if ($exitCode -ne 0 -or $output -notmatch "Xero Quest Journal Fabric runtime initialized" -or
        $output -notmatch "You need to agree to the EULA" -or
        $output -match "Could not execute entrypoint|Failed to start the minecraft server|Mixin apply.*failed") {
    throw "Production Fabric server bootstrap failed; inspect $logFile"
}
if ((Get-Content -LiteralPath $eulaFile -Raw) -notmatch '(?m)^\s*eula\s*=\s*false\s*$') {
    throw "Production EULA refusal was not confirmed."
}
$sha256 = (Get-FileHash -LiteralPath $runtime.jar -Algorithm SHA256).Hash
"PASS: remapped release JAR on physical SERVER with production Fabric API and intermediary game; common mixins and embedded dependencies initialized. EULA remains false. Server gameplay not tested.`nJAR: $($runtime.jar)`nSHA256: $sha256" |
    Set-Content -LiteralPath (Join-Path $reportRoot "result.txt") -Encoding UTF8
Write-Host "PASS Fabric $Minecraft production JAR bootstrap. Report: $reportRoot"
