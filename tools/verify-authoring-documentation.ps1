#Requires -Version 7.0
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$documents = @("README.md", "docs/guide.md", "docs/commands.md", "docs/javascript.md",
    "docs/trigger-conditions.md", "docs/visual-graph-and-inventory-shortcuts.md",
    "docs/images/README.md", "docs/奖励与条件配置.md", "docs/interface-gallery.md",
    "docs/background-and-journey-editor.md", "docs/release-verification-2026-10-16.md",
    "docs/task-edit-undo.md")
foreach ($relative in $documents) {
    $file = Join-Path $repoRoot $relative
    $content = Get-Content -LiteralPath $file -Raw -Encoding utf8
    $fences = [regex]::Matches($content, '(?m)^```').Count
    if ($fences % 2 -ne 0) { throw "Unclosed code fence: $relative" }
    foreach ($match in [regex]::Matches($content, '!?\[[^\]]*\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value
        if ($target -match '^(https?://|#)') { continue }
        $path = Join-Path (Split-Path -Parent $file) ($target.Split("#")[0])
        if (!(Test-Path -LiteralPath $path)) { throw "Broken link in ${relative}: $target" }
    }
}
$commands = Get-Content -LiteralPath (Join-Path $repoRoot "docs/reference/commands-runtime.json") -Raw | ConvertFrom-Json
$commandText = Get-Content -LiteralPath (Join-Path $repoRoot "docs/commands.md") -Raw
foreach ($route in $commands.PSObject.Properties) {
    $row = '| `' + $route.Name + '` | ' + $route.Value + ' |'
    if (!$commandText.Contains($row)) { throw "Missing command/permission: $($route.Name)" }
}
$forgeCommands = Get-Content -LiteralPath (Join-Path $repoRoot "docs/reference/commands-forge-runtime.json") -Raw | ConvertFrom-Json
foreach ($route in $forgeCommands.PSObject.Properties) {
    if ($route.Name -eq "/xero_quest status") {
        if ($route.Value -ne 0 -or !$commandText.Contains($route.Name)) { throw "Forge status command is undocumented" }
    } elseif (!$commands.PSObject.Properties[$route.Name] -or $commands.($route.Name) -ne $route.Value) {
        throw "Undocumented Forge command/permission: $($route.Name)"
    }
}
$examples = Get-Content -LiteralPath (Join-Path $repoRoot "docs/reference/script-reference-examples.json") -Raw | ConvertFrom-Json
$scriptText = (Get-Content -LiteralPath (Join-Path $repoRoot "docs/javascript.md") -Raw).Replace("`r`n", "`n")
foreach ($example in $examples) {
    if (!$scriptText.Contains($example.source)) { throw "Missing reference example: $($example.id)" }
}
foreach ($locale in @("zh_cn", "en_us")) {
    $localeFile = Join-Path $repoRoot "src/main/resources/assets/xero_quest_journal/lang/$locale.json"
    $json = [System.Text.Json.JsonDocument]::Parse([IO.File]::ReadAllText($localeFile))
    try {
        $keys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($property in $json.RootElement.EnumerateObject()) {
            if (!$keys.Add($property.Name)) { throw "Duplicate locale key: $locale $($property.Name)" }
        }
        foreach ($example in $examples) {
            $key = "screen.xero_quest_journal.script.example." + $example.id
            if (!$keys.Contains($key)) { throw "Missing example translation: $locale $key" }
        }
    } finally { $json.Dispose() }
}
Add-Type -AssemblyName System.Drawing
$photos = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "docs/images") -File -Filter "*-zh-1920.png")
foreach ($photo in $photos) {
    $image = [Drawing.Image]::FromFile($photo.FullName)
    try {
        if ($image.Width -ne 1920 -or $image.Height -ne 1080) { throw "Incorrect photo size: $($photo.Name)" }
    } finally { $image.Dispose() }
}
Write-Output "PASS: Markdown links/fences, $(@($commands.PSObject.Properties).Count) shared / $(@($forgeCommands.PSObject.Properties).Count) Forge command routes/permissions, $($examples.Count) shared examples, unique locale keys, $($photos.Count) original 1920x1080 screenshots."
