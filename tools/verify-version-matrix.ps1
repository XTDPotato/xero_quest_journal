param(
    [string]$Java21 = "",
    [string]$Java17 = "",
    [switch]$IncludeIncomplete
)

$ErrorActionPreference = "Stop"
$reportPath = Join-Path (Get-Location) "build\verification\version-matrix.txt"
New-Item -ItemType Directory -Force (Split-Path -Parent $reportPath) | Out-Null
$results = [System.Collections.Generic.List[string]]::new()
$targets = @(
    @{ Minecraft = "1.20.1"; Loaders = @("fabric", "forge") },
    @{ Minecraft = "1.21.1"; Loaders = @("neoforge", "fabric") },
    @{ Minecraft = "26.1"; Loaders = @("neoforge", "fabric") },
    @{ Minecraft = "26.2"; Loaders = @("neoforge", "fabric") },
    @{ Minecraft = "26.3"; Loaders = @("neoforge", "fabric") }
)

if ($Java21 -ne "") {
    $env:JAVA_HOME = (Resolve-Path $Java21).Path
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
$java21Home = $env:JAVA_HOME
$java21Path = $env:Path

Write-Host "Declared version matrix:"
foreach ($target in $targets) {
    Write-Host ("  {0}: {1}" -f $target.Minecraft, ($target.Loaders -join ", "))
}

Write-Host "`nChecking default regression build..."
$defaultOutput = & .\gradlew.bat build --no-daemon 2>&1 | Out-String
$defaultExitCode = $LASTEXITCODE
if ($defaultExitCode -ne 0) {
    $results.Add("FAIL|1.21.1|neoforge|default build failed")
    throw "Default NeoForge 1.21.1 regression build failed."
}
$results.Add("PASS|1.21.1|neoforge|build")

if ($IncludeIncomplete) {
    Write-Host "`nIncomplete profiles are declared but intentionally fail fast until their adapters are wired."
    foreach ($target in $targets) {
        foreach ($loader in $target.Loaders) {
            if ($target.Minecraft -eq "1.21.1" -and $loader -eq "neoforge") { continue }
            if ($target.Minecraft -eq "1.21.1" -and $loader -eq "fabric") {
                $fabricOutput = & .\gradlew.bat buildSelected "-Pmc_target=1.21.1" "-Ploader=fabric" --no-daemon 2>&1 | Out-String
                $fabricExitCode = $LASTEXITCODE
                if ($fabricExitCode -ne 0) {
                    $results.Add("FAIL|1.21.1|fabric|standalone build failed")
                    throw "Fabric 1.21.1 standalone build failed."
                }
                $results.Add("PASS|1.21.1|fabric|buildSelected/standalone build")
                continue
            }
            if ($target.Minecraft -eq "1.20.1") {
                if ($Java17 -eq "") {
                    $results.Add("SKIPPED|1.20.1|$loader|pass -Java17 to run this target")
                    continue
                }
                $env:JAVA_HOME = (Resolve-Path $Java17).Path
                $env:Path = "$env:JAVA_HOME\bin;$java21Path"
                try {
                    $forgeOutput = & .\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=$loader" --no-daemon 2>&1 | Out-String
                    $forgeExitCode = $LASTEXITCODE
                } finally {
                    $env:JAVA_HOME = $java21Home
                    $env:Path = $java21Path
                }
                if ($forgeExitCode -ne 0) {
                    $results.Add("FAIL|1.20.1|$loader|standalone build failed")
                    throw "$loader 1.20.1 standalone build failed."
                }
                $results.Add("PASS|1.20.1|$loader|buildSelected/standalone build")
                continue
            }
            $previousErrorAction = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                $output = & .\gradlew.bat printPortMatrix "-Pmc_target=$($target.Minecraft)" "-Ploader=$loader" --no-daemon 2>&1 | Out-String
                $exitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $previousErrorAction
            }
            if ($exitCode -eq 0) {
                $results.Add("FAIL|$($target.Minecraft)|$loader|unexpectedly wired")
                throw "Incomplete profile unexpectedly passed: $($target.Minecraft)/$loader"
            }
            $results.Add("EXPECTED_NOT_WIRED|$($target.Minecraft)|$loader|adapter not wired")
        }
    }
}

$results | Set-Content -Path $reportPath -Encoding UTF8
Write-Host "`nMatrix verification completed. Report: $reportPath"
$results | ForEach-Object { Write-Host "  $_" }
