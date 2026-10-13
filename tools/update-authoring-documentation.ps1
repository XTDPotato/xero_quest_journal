param([Parameter(Mandatory)][string]$ReportDirectory)
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$report = (Resolve-Path -LiteralPath $ReportDirectory).Path
if (!(Get-Content -LiteralPath (Join-Path $report "result.txt") -Raw).StartsWith("PASS:")) {
    throw "Only a passing authoring report can update documentation."
}
$commands = Get-Content -LiteralPath (Join-Path $report "commands-runtime.json") -Raw | ConvertFrom-Json
$routeLines = @("## 实际命令叶子路径", "", "| 命令 | 最低权限等级 |", "| --- | --- |")
foreach ($route in $commands.PSObject.Properties) {
    $routeLines += '| `' + $route.Name + '` | ' + $route.Value + ' |'
}
$commandFile = Join-Path $repoRoot "docs\commands.md"
$commandText = Get-Content -LiteralPath $commandFile -Raw -Encoding utf8
$commandPattern = '(?s)(<!-- COMMAND_ROUTES_START -->).*?(<!-- COMMAND_ROUTES_END -->)'
if ($commandText -notmatch $commandPattern) { throw "Command reference markers are missing" }
$commandText = [regex]::Replace($commandText, $commandPattern, {
    param($match)
    $match.Groups[1].Value + "`n" + ($routeLines -join "`n") + "`n" + $match.Groups[2].Value
})
[IO.File]::WriteAllText($commandFile, $commandText, [Text.UTF8Encoding]::new($false))

$examples = Get-Content -LiteralPath (Join-Path $report "script-reference-examples.json") -Raw | ConvertFrom-Json
$kindNames = @{
    GRAPH_PREDICATE = "事件图 JS 判断"
    CONDITION_FILE = "任务条件文件"
    REWARD_FILE = "奖励动作文件"
    KUBEJS = "KubeJS 动作（需另装并匹配第三方 API）"
}
$exampleLines = @("## 已校验示例索引", "",
    "以下源码与游戏内参考面板共享，内置示例经过实际服务端引擎求值。KubeJS 示例只作为独立环境写法参考，未安装第三方模组联合实测。", "")
foreach ($example in $examples) {
    $exampleLines += @("### $($kindNames[$example.kind]) / $($example.id)", "", "``````javascript",
        $example.source, "``````", "")
}
$scriptFile = Join-Path $repoRoot "docs\javascript.md"
$scriptText = Get-Content -LiteralPath $scriptFile -Raw -Encoding utf8
$scriptPattern = '(?s)(<!-- SCRIPT_EXAMPLES_START -->).*?(<!-- SCRIPT_EXAMPLES_END -->)'
if ($scriptText -notmatch $scriptPattern) { throw "Script reference markers are missing" }
$scriptText = [regex]::Replace($scriptText, $scriptPattern, {
    param($match)
    $match.Groups[1].Value + "`n" + ($exampleLines -join "`n") + "`n" + $match.Groups[2].Value
})
[IO.File]::WriteAllText($scriptFile, $scriptText, [Text.UTF8Encoding]::new($false))

Add-Type -AssemblyName System.Drawing
$imageRoot = Join-Path $repoRoot "docs\images"
$photos = @(Get-ChildItem -LiteralPath $report -File -Filter "*-zh-1920.png")
if ($photos.Count -lt 12) { throw "Expected the complete screenshot series." }
foreach ($photo in $photos) {
    $image = [Drawing.Image]::FromFile($photo.FullName)
    try {
        if ($image.Width -ne 1920 -or $image.Height -ne 1080) { throw "Wrong screenshot resolution: $($photo.Name)" }
    } finally { $image.Dispose() }
    Copy-Item -LiteralPath $photo.FullName -Destination (Join-Path $imageRoot $photo.Name)
}
$referenceDirectory = Join-Path $repoRoot "docs\reference"
New-Item -ItemType Directory -Force -Path $referenceDirectory | Out-Null
Copy-Item -LiteralPath (Join-Path $report "commands-runtime.json"),(Join-Path $report "script-reference-examples.json") `
    -Destination $referenceDirectory
Write-Output "Updated $(@($commands.PSObject.Properties).Count) command routes, $($examples.Count) examples and $($photos.Count) Chinese 1920x1080 screenshots."
