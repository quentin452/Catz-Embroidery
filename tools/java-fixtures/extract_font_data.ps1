# Generate crates/emb-model/src/font/data.rs — the SIMPLEX cmap + the 96 glyph
# strings, parsed from PEmbroiderFont.java (the model, never a source: the
# Hershey font data is the classic public-domain Hershey table, and this
# generator is the audit trail for the one-time port).
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # the repo root (tools/java-fixtures/../..)
$font_java = Join-Path $root "src\main\java\processing\embroider\PEmbroiderFont.java"
$src = Get-Content $font_java -Raw
$map = @{}
foreach ($ln in (Get-Content $font_java)) {
  foreach ($mm in [regex]::Matches($ln, 'if\(x==(\d+)\)\{return "((?:[^"\\]|\\.)*)"')) {
    $id = [int]$mm.Groups[1].Value
    $lit = $mm.Groups[2].Value
    $map[$id] = $lit
  }
}
$m = [regex]::Match($src, 'public static final int\[\] SIMPLEX = \{(.*?)\n\t\};', 'Singleline')
$simplex = [regex]::Matches($m.Groups[1].Value, '\d+') | ForEach-Object { [int]$_.Value }
if ($simplex.Count -ne 96) { throw "SIMPLEX has $($simplex.Count) entries, expected 96" }

$out = New-Object System.Collections.Generic.List[string]
$out.Add('//! The Hershey SIMPLEX glyph table (public-domain classic font data).')
$out.Add('//!')
$out.Add('//! GENERATED 2026-08-17 from the Java suite PEmbroiderFont.java data() +')
$out.Add('//! SIMPLEX tables (the model, never a source). The generator was the one-time')
$out.Add('//! audit trail; the table is now the source in this repo. Each entry is the')
$out.Add('//! Java glyph string VERBATIM: a 5-char header (3 ignored + xmin/xmax as')
$out.Add('//! R-relative chars), then coordinate pairs (R-relative, one pen-up marker')
$out.Add('//! for the pair " R"), with Java string escapes resolved (a backslash in')
$out.Add('//! the source was an escaped backslash).')
$out.Add('')
$out.Add('/// The SIMPLEX cmap: char (c as u8 - 32) indexes it; the value is a data() id.')
$out.Add('#[rustfmt::skip]')
$out.Add('pub const SIMPLEX: [u16; 96] = [')
for ($i = 0; $i -lt $simplex.Count; $i += 12) {
  $chunk = $simplex[$i..([Math]::Min($i + 11, $simplex.Count - 1))]
  $line = '    ' + (($chunk | ForEach-Object { $_.ToString() }) -join ', ') + ','
  $out.Add($line)
}
$out.Add('];')
$out.Add('')
$out.Add('/// The glyph strings, keyed by data() id (only the ids SIMPLEX references).')
$out.Add('#[rustfmt::skip]')
$out.Add('pub const GLYPHS: &[(u16, &str)] = &[')
foreach ($id in ($simplex | Sort-Object -Unique)) {
  $lit = $map[$id]
  if ($null -eq $lit) { throw "no data for id $id" }
  # Emit the Java string literal VERBATIM into the Rust literal: both
  # languages spell a literal backslash `\\`, so the bytes agree.
  $out.Add('    (' + $id + ', "' + $lit + '"),')
}
$out.Add('];')

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # the repo root (tools/java-fixtures/../..)
$path = Join-Path $root "crates\emb-model\src\font\data.rs"
$text = $out -join "`n"
[System.IO.File]::WriteAllText($path, $text + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Output "wrote $path ($($out.Count) lines)"
