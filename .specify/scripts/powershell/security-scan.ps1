# .specify/scripts/powershell/security-scan.ps1
# Detects private machine paths in git-tracked files.
# Exit 0 = clean. Exit 1 = violations found.
#
# Usage:  .\.specify\scripts\powershell\security-scan.ps1

$ErrorActionPreference = "Stop"

# Patterns that identify real private paths.
# Uses git grep (PCRE) so paths inside package names like
# "com/flow/presentation/home/HomeScreen.kt" are NOT matched.
#   - C:\Users\<realname>\ requires backslash-separated Windows path
#   - /Users/<realname>/  requires slash-separated path with trailing slash, lowercase start
#   - /home/<realname>/   same, linux
$patterns = @(
    'C:\\\\Users\\\\(?!<username>)[A-Za-z][A-Za-z0-9._-]+'
    '/Users/(?!<username>)[a-z][a-z0-9._-]+/'
    '/home/(?!<username>)[a-z][a-z0-9._-]+/'
)

$extensions = @('*.md','*.kt','*.kts','*.xml','*.properties','*.toml','*.json','*.ps1','*.sh','*.bat','*.gradle')
$extArgs = $extensions | ForEach-Object { "--", $_ }

$allViolations = @()
foreach ($pattern in $patterns) {
    $hits = & git grep -nPi $pattern -- $extensions 2>&1
    if ($LASTEXITCODE -eq 0) {
        $allViolations += $hits
    }
}

if ($allViolations.Count -eq 0) {
    Write-Host "PASS: No private paths or PII found in tracked files." -ForegroundColor Green
    exit 0
}

Write-Host ""
Write-Host "FAIL: SECURITY VIOLATION -- $($allViolations.Count) match(es) found." -ForegroundColor Red
Write-Host "Violates Constitution Principle VI (Security and Privacy)."
Write-Host ""
$allViolations | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
Write-Host ""
Write-Host "Fix: replace real usernames with <username> placeholder."
Write-Host "     Use `$env:ANDROID_HOME in code examples, never a literal path."
Write-Host "     Store local config in .local\env.ps1 (gitignored)."
Write-Host ""
exit 1
