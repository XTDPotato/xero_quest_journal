param([string]$Preview = "preview.5")
$ErrorActionPreference = "Stop"
if ($Preview -notmatch '^preview\.[0-9]+$') { throw "Expected preview.N" }
$repoRoot = Split-Path -Parent $PSScriptRoot
$versionLine = Get-Content -LiteralPath (Join-Path $repoRoot "gradle.properties") |
    Where-Object { $_ -match '^mod_version=' } | Select-Object -First 1
$modVersion = ($versionLine -split '=', 2)[1].Trim()
if ($modVersion -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') { throw "Invalid mod_version" }
$output = Join-Path $repoRoot "build/releases/$Preview"
if (Test-Path -LiteralPath $output) { throw "Release output already exists: $output" }
$targets = @(
    @{ Loader="neoforge"; Minecraft="1.21.1"; Source="build/libs/xero_quest_journal-$modVersion.jar" },
    @{ Loader="forge"; Minecraft="1.20.1"; Source="platforms/forge/1.20.1/build/libs/xero_quest_journal-forge-$modVersion.jar" },
    @{ Loader="fabric"; Minecraft="1.20.1"; Source="platforms/fabric/1.20.1/build/libs/xero_quest_journal-fabric-1.20.1-$modVersion.jar" },
    @{ Loader="fabric"; Minecraft="1.21.1"; Source="platforms/fabric/1.21.1/build/libs/xero_quest_journal-fabric-1.21.1-$modVersion.jar" }
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($target in $targets) {
    $source = Join-Path $repoRoot $target.Source
    if (!(Test-Path -LiteralPath $source)) { throw "Missing release JAR: $source" }
    if ((Get-Item -LiteralPath $source).Length -gt 5000000) { throw "Core JAR exceeds 5MB: $source" }
    $jar = [IO.Compression.ZipFile]::OpenRead($source)
    try {
        if ($jar.Entries.FullName -match '(^|/)(smoke|productioncheck)/') { throw "Test classes leaked into $source" }
        $metadata = if ($target.Loader -eq "fabric") { "fabric.mod.json" }
            elseif ($target.Loader -eq "forge") { "META-INF/mods.toml" } else { "META-INF/neoforge.mods.toml" }
        if (!$jar.GetEntry($metadata)) { throw "Wrong loader metadata: $source" }
        foreach ($class in @("client/ScriptEditorScreenBase", "core/model/QuestScriptReference",
                "client/JourneyAppearanceScreen", "client/JourneyPreviewScene", "client/JourneyQuestPopupScreen",
                "client/QuestSearchFilterScreen", "client/CoordinateTrackAnimation",
                "core/runtime/QuestVariableMath", "client/AnecdoteScreen", "client/AnecdoteRewardsScreen",
                "core/runtime/AnecdoteRuntime", "core/model/AnecdoteDefinition",
                "core/io/QuestAuthoringHistory", "core/model/LootDefinition")) {
            if (!$jar.GetEntry("xtdpotato/xero_quest_journal/$class.class")) {
                throw "Authoring changes are absent from $source"
            }
        }
    } finally { $jar.Dispose() }
}
New-Item -ItemType Directory -Force -Path $output | Out-Null
$bundle = Join-Path $output "supported-builds"
New-Item -ItemType Directory -Force -Path $bundle | Out-Null
$checksums = [Collections.Generic.List[string]]::new()
foreach ($target in $targets) {
    $name = "xero_quest_journal-$($target.Loader)-$($target.Minecraft)-$modVersion.jar"
    $asset = Join-Path $output $name
    Copy-Item -LiteralPath (Join-Path $repoRoot $target.Source) -Destination $asset
    $folder = Join-Path $bundle "$($target.Loader)/$($target.Minecraft)"
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    Copy-Item -LiteralPath $asset -Destination $folder
    $checksums.Add((Get-FileHash -LiteralPath $asset -Algorithm SHA256).Hash.ToLowerInvariant() + "  " + $name)
}
Copy-Item -LiteralPath (Join-Path $repoRoot "LICENSE"),(Join-Path $repoRoot "README.md") -Destination $bundle
# Select public files through Git so ignored plans, audits and reference images stay local.
$publicDocs = & git -C $repoRoot -c core.quotepath=false ls-files --cached --others --exclude-standard -- docs
if ($LASTEXITCODE -ne 0) { throw "Could not enumerate public documentation" }
foreach ($relative in $publicDocs) {
    $destination = Join-Path $bundle $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot $relative) -Destination $destination
}
$bundleZip = Join-Path $output "xero_quest_journal-supported-builds-$Preview.zip"
Compress-Archive -LiteralPath $bundle -DestinationPath $bundleZip
$checksums.Add((Get-FileHash -LiteralPath $bundleZip -Algorithm SHA256).Hash.ToLowerInvariant() + "  " + (Split-Path $bundleZip -Leaf))
[IO.File]::WriteAllLines((Join-Path $output "SHA256SUMS.txt"), $checksums, [Text.UTF8Encoding]::new($false))
Write-Output "PASS: four production JARs, public docs/images, organized bundle and SHA256SUMS: $output"
