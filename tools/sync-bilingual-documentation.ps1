$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$utf8 = [Text.UTF8Encoding]::new($false)

$routes = Get-Content -LiteralPath (Join-Path $repo "docs/reference/commands-runtime.json") -Raw | ConvertFrom-Json
$rows = @("| Command | Minimum permission |", "| --- | --- |")
foreach ($route in $routes.PSObject.Properties) { $rows += '| `' + $route.Name + '` | ' + $route.Value + ' |' }
$file = Join-Path $repo "docs/en/commands.md"
$content = [IO.File]::ReadAllText($file)
$content = [regex]::Replace($content, '(?s)(<!-- COMMAND_ROUTES_START -->).*?(<!-- COMMAND_ROUTES_END -->)',
    { param($match) $match.Groups[1].Value + "`n`n" + ($rows -join "`n") + "`n`n" + $match.Groups[2].Value })
[IO.File]::WriteAllText($file, $content, $utf8)

$examples = Get-Content -LiteralPath (Join-Path $repo "docs/reference/script-reference-examples.json") -Raw | ConvertFrom-Json
$kinds = @{GRAPH_PREDICATE="Graph predicate"; CONDITION_FILE="Condition file"; REWARD_FILE="Reward file"; KUBEJS="KubeJS action"}
$rows = @()
foreach ($example in $examples) {
    $rows += @("### $($kinds[$example.kind]) / $($example.id)", "", '```javascript', $example.source, '```', "")
}
$file = Join-Path $repo "docs/en/javascript.md"
$content = [IO.File]::ReadAllText($file)
$content = [regex]::Replace($content, '(?s)(<!-- SCRIPT_EXAMPLES_START -->).*?(<!-- SCRIPT_EXAMPLES_END -->)',
    { param($match) $match.Groups[1].Value + "`n`n" + ($rows -join "`n") + "`n" + $match.Groups[2].Value })
[IO.File]::WriteAllText($file, $content, $utf8)

Add-Type -AssemblyName System.Drawing
$all = @()
foreach ($locale in @("zh_cn", "en_us")) {
    $directory = Join-Path $repo "build/verification/documentation/$locale"
    if (!([IO.File]::ReadAllText((Join-Path $directory "result.txt"))).StartsWith("PASS:")) {
        throw "Capture has not passed: $locale"
    }
    $manifest = Get-Content -LiteralPath (Join-Path $directory "manifest.json") -Raw | ConvertFrom-Json
    if ($manifest.Count -lt 45) { throw "Incomplete gallery: $locale" }
    foreach ($photo in $manifest) {
        $path = Join-Path $directory $photo.file
        $digest = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($photo.locale -ne $locale -or $photo.width -ne 1920 -or $photo.height -ne 1080 -or $photo.sha256 -ne $digest) {
            throw "Capture manifest mismatch: $path"
        }
        $image = [Drawing.Image]::FromFile($path)
        try {
            if ($image.Width -ne 1920 -or $image.Height -ne 1080) { throw "Not native 1080p: $path" }
        } finally { $image.Dispose() }
        Copy-Item -LiteralPath $path -Destination (Join-Path $repo "docs/images/$($photo.file)")
        $all += $photo
    }
}
$zh = @($all | Where-Object locale -eq "zh_cn" | ForEach-Object scene | Sort-Object)
$en = @($all | Where-Object locale -eq "en_us" | ForEach-Object scene | Sort-Object)
if (Compare-Object $zh $en) { throw "Chinese/English capture scenes differ" }
[IO.File]::WriteAllText((Join-Path $repo "docs/reference/interface-captures.json"),
    ($all | ConvertTo-Json -Depth 6), $utf8)
Write-Output "PASS: $(@($routes.PSObject.Properties).Count) command routes, $($examples.Count) examples, $($all.Count) paired native 1080p screenshots synchronized."
