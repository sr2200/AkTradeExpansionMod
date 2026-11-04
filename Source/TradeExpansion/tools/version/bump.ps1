# AK Trade Expansion: raises "version" in gradle.properties and the version line of README.md.
#   -Part patch : 0.1.2 -> 0.1.3 (build.bat)
#   -Part minor : 0.1.2 -> 0.2.0 (release.bat)
param(
    [ValidateSet('patch', 'minor')]
    [string]$Part = 'patch'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # Source/TradeExpansion
$props = Join-Path $root 'gradle.properties'
$text = [IO.File]::ReadAllText($props)
$m = [regex]::Match($text, '(?m)^version=(\d+)\.(\d+)\.(\d+)')
if (-not $m.Success) {
    Write-Host 'version not found in gradle.properties'
    exit 1
}
$major = [int]$m.Groups[1].Value
$minor = [int]$m.Groups[2].Value
$patch = [int]$m.Groups[3].Value
$old = "$major.$minor.$patch"
if ($Part -eq 'minor') {
    $new = "$major.$($minor + 1).0"
} else {
    $new = "$major.$minor.$($patch + 1)"
}
$text = $text.Substring(0, $m.Index) + "version=$new" + $text.Substring($m.Index + $m.Length)
[IO.File]::WriteAllText($props, $text)

# README.md at the repository root: "| <version>（" (full-width parenthesis U+FF08).
$readme = Join-Path (Split-Path -Parent (Split-Path -Parent $root)) 'README.md'
if (Test-Path $readme) {
    $u = [IO.File]::ReadAllText($readme, [Text.Encoding]::UTF8)
    $u = $u.Replace('| ' + $old + [char]0xFF08, '| ' + $new + [char]0xFF08)
    [IO.File]::WriteAllText($readme, $u, (New-Object Text.UTF8Encoding $false))
}
Write-Host "version $old -> $new"
