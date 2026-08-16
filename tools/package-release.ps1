# Builds the release exes and assembles the distributable folder (M5
# packaging: "exe builds").
#
# Usage: powershell -ExecutionPolicy Bypass -File tools/package-release.ps1
#
# Output: <repo>/dist/ — the five exes next to each other, which is exactly
# the layout the launcher expects (it spawns its siblings beside itself),
# plus a README.txt. The folder is git-ignored (build output, not source).
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root "dist"
$apps = @("emb-editor", "emb-converter", "emb-viewer", "emb-infinitedraw", "emb-launcher")

Push-Location $root
try {
    cargo build --release -p emb-editor -p emb-converter -p emb-viewer -p emb-infinitedraw -p emb-launcher
    if ($LASTEXITCODE -ne 0) { throw "cargo build --release failed" }
}
finally {
    Pop-Location
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null
foreach ($app in $apps) {
    Copy-Item -Force (Join-Path $root "target\release\$app.exe") (Join-Path $dist "$app.exe")
}

$versionLine = Select-String -Path (Join-Path $root "Cargo.toml") -Pattern '^version = "([^"]+)"' | Select-Object -First 1
$version = if ($versionLine -and $versionLine.Matches[0].Groups[1]) { $versionLine.Matches[0].Groups[1].Value } else { "0.1.0" }

$readme = @"
Catz-Embroidery $version - the embroidery suite (editor, converter, viewer, launcher).

Run emb-launcher.exe to pick an app. Each app can also be run directly.

The launcher checks GitHub for a newer release at startup and offers the
releases page when one exists: https://github.com/quentin452/Catz-Embroidery/releases
"@
Set-Content -Path (Join-Path $dist "README.txt") -Value $readme -Encoding UTF8

Write-Host "Packaged $($apps.Count) exes + README.txt into $dist"
