param([ValidateSet("1.20.1", "1.21.1")][string]$Minecraft = "1.21.1")
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Join-Path $repoRoot "platforms\fabric\$Minecraft"
$reportRoot = Join-Path $repoRoot ("build\verification\fabric-$Minecraft-production-client-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
Push-Location $repoRoot
try {
    & .\gradlew.bat -p $projectRoot prepareProductionClientCheck *> (Join-Path $reportRoot "build.log")
    if ($LASTEXITCODE -ne 0) { throw "Could not prepare production client check: $reportRoot" }
} finally { Pop-Location }
$runtime = Get-Content -LiteralPath (Join-Path $projectRoot "build\production-runtime.json") -Raw | ConvertFrom-Json
$runDir = Join-Path $projectRoot "build\production-client-check"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$mods = @($runtime.mods) + @($runtime.clientCheck)
if (!$runtime.assetIndex) { throw "Minecraft assets are missing; run the development client preparation first." }
Push-Location $runDir
try {
    & (Join-Path $env:JAVA_HOME "bin\java.exe") "-Xmx2G" "-Dfabric.development=false" `
        "-Dfabric.addMods=$($mods -join [IO.Path]::PathSeparator)" `
        "-Djava.library.path=$($runtime.natives)" "-Dxero.productionCheckDir=$reportRoot" `
        "-cp" ($runtime.classpath -join [IO.Path]::PathSeparator) `
        "net.fabricmc.loader.impl.launch.knot.KnotClient" `
        "--username" "Dev" "--version" $Minecraft "--accessToken" "0" `
        "--gameDir" $runDir "--assetsDir" $runtime.assets "--assetIndex" $runtime.assetIndex `
        "--width" "1920" "--height" "1080" *> (Join-Path $reportRoot "client.log")
    $exitCode = $LASTEXITCODE
} finally { Pop-Location }
$resultFile = Join-Path $reportRoot "result.txt"
if ($exitCode -ne 0 -or !(Test-Path -LiteralPath $resultFile) -or
        !(Get-Content -LiteralPath $resultFile -Raw).StartsWith("PASS:")) {
    throw "Production client check failed; inspect $reportRoot"
}
(Get-FileHash -LiteralPath $runtime.jar -Algorithm SHA256).Hash |
    Set-Content -LiteralPath (Join-Path $reportRoot "jar-sha256.txt") -Encoding UTF8
Write-Host "PASS Fabric $Minecraft production client. Report: $reportRoot"
