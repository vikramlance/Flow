# Fix garbled emoji in HomeScreen.kt.
# File stores Windows-1252 mojibake as Unicode chars (misread UTF-8).
# We match the exact Unicode codepoint sequences and replace with Kotlin \uXXXX escapes.

$p = "d:\Android\Flow\app\src\main\java\com\flow\presentation\home\HomeScreen.kt"
$c = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)

# Helper: build string from codepoint array (avoid alias conflict with CP=Copy-Item)
function MakeStr([int[]]$pts) { ($pts | ForEach-Object { [char]$_ }) -join '' }

# 👆 U+1F446  [00F0,0178,2018,2020]
$c = $c.Replace((MakeStr @(0x00F0,0x0178,0x2018,0x2020)), '\uD83D\uDC46')

# → U+2192  [00E2,2020,2019]
$c = $c.Replace((MakeStr @(0x00E2,0x2020,0x2019)), '\u2192')

# ⏳ U+23F3  [00E2,008F,00B3]
$c = $c.Replace((MakeStr @(0x00E2,0x008F,0x00B3)), '\u23F3')

# ✅ U+2705  [00E2,0153,2026]
$c = $c.Replace((MakeStr @(0x00E2,0x0153,0x2026)), '\u2705')

# ✏️ U+270F U+FE0F  [00E2,0153,008F,00EF,00B8,008F]
$c = $c.Replace((MakeStr @(0x00E2,0x0153,0x008F,0x00EF,0x00B8,0x008F)), '\u270F\uFE0F')

# 🌱 U+1F331  [00F0,0178,0152,00B1]
$c = $c.Replace((MakeStr @(0x00F0,0x0178,0x0152,0x00B1)), '\uD83C\uDF31')

# ⏱️ U+23F1 U+FE0F  [00E2,008F,00B1,00EF,00B8,008F]
$c = $c.Replace((MakeStr @(0x00E2,0x008F,0x00B1,0x00EF,0x00B8,0x008F)), '\u23F1\uFE0F')

# 📊 U+1F4CA  [00F0,0178,201C,0160]
$c = $c.Replace((MakeStr @(0x00F0,0x0178,0x201C,0x0160)), '\uD83D\uDCCA')

# 📋 U+1F4CB  [00F0,0178,201C,2039]
$c = $c.Replace((MakeStr @(0x00F0,0x0178,0x201C,0x2039)), '\uD83D\uDCCB')

# 🎯 U+1F3AF  [00F0,0178,017D,00AF]
$c = $c.Replace((MakeStr @(0x00F0,0x0178,0x017D,0x00AF)), '\uD83C\uDFAF')

# ℹ️ U+2139 U+FE0F  [00E2,201E,00B9,00EF,00B8,008F]
$c = $c.Replace((MakeStr @(0x00E2,0x201E,0x00B9,0x00EF,0x00B8,0x008F)), '\u2139\uFE0F')

# ≥ U+2265  [00E2,2030,00A5]  (0x89 in Win-1252 = U+2030)
$c = $c.Replace((MakeStr @(0x00E2,0x2030,0x00A5)), '\u2265')

# 🔋 U+1F50B  [00F0,0178,201D,2039]  ("Take a break! 🔋")
$c = $c.Replace((MakeStr @(0x00F0,0x0178,0x201D,0x2039)), '\uD83D\uDD0B')

# · U+00B7  [00C2,00B7]
$c = $c.Replace((MakeStr @(0x00C2,0x00B7)), '\u00B7')

[System.IO.File]::WriteAllText($p, $c, [System.Text.Encoding]::UTF8)
Write-Host "Done"
