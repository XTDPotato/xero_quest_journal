param(
    [Parameter(Mandatory)][string]$Jar,
    [Parameter(Mandatory)][ValidateSet("1.20.1", "1.21.1")][string]$Minecraft
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = (Resolve-Path -LiteralPath $Jar).Path
if ((Get-Item -LiteralPath $jarPath).Length -gt 5000000) { throw "Fabric JAR exceeds 5 MB" }
$archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    function Read-ZipText([string]$name) {
        $entry = $archive.GetEntry($name)
        if (!$entry) { throw "Missing JAR entry: $name" }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    $metadata = Read-ZipText "fabric.mod.json" | ConvertFrom-Json
    if ($metadata.id -ne "xero_quest_journal" -or $metadata.depends.minecraft -ne $Minecraft) {
        throw "Wrong Fabric mod metadata"
    }
    foreach ($point in @($metadata.entrypoints.main) + @($metadata.entrypoints.client)) {
        if (!$archive.GetEntry($point.Replace(".", "/") + ".class")) { throw "Missing entry point: $point" }
    }
    if (!$archive.GetEntry($metadata.accessWidener)) { throw "Missing access widener" }
    foreach ($config in $metadata.mixins) {
        $mixins = Read-ZipText $config | ConvertFrom-Json
        if (!$mixins.required -or !$mixins.refmap -or !$archive.GetEntry($mixins.refmap)) {
            throw "Missing required production Mixin/refmap"
        }
        foreach ($mixin in @($mixins.mixins) + @($mixins.client)) {
            if (!$archive.GetEntry(($mixins.package + "." + $mixin).Replace(".", "/") + ".class")) {
                throw "Missing Mixin class: $mixin"
            }
        }
    }
    foreach ($dependency in $metadata.jars) {
        if (!$archive.GetEntry($dependency.file)) { throw "Missing embedded dependency: $($dependency.file)" }
    }
    $major = if ($Minecraft -eq "1.20.1") { 61 } else { 65 }
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '/smoke/|/productioncheck/|META-INF/(?:neoforge\.)?mods\.toml') {
            throw "Test or other loader files packaged: $($entry.FullName)"
        }
        if (!$entry.FullName.EndsWith(".class")) { continue }
        $buffer = [System.IO.MemoryStream]::new()
        $stream = $entry.Open()
        try {
            $stream.CopyTo($buffer)
            $bytes = $buffer.ToArray()
            if ($bytes[6] * 256 + $bytes[7] -ne $major) { throw "Wrong Java level: $($entry.FullName)" }
            $constants = [System.Text.Encoding]::UTF8.GetString($bytes)
            if ($constants -match 'net/(?:neoforged|minecraftforge)/') {
                throw "Other loader bytecode reference: $($entry.FullName)"
            }
        } finally {
            $stream.Dispose()
            $buffer.Dispose()
        }
    }
    "PASS: Fabric $Minecraft JAR metadata, Java level, Mixin/refmap, embedded dependencies, 5 MB limit and test/loader isolation."
    Get-FileHash -LiteralPath $jarPath -Algorithm SHA256
} finally { $archive.Dispose() }
