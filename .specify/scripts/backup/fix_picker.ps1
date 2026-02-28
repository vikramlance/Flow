$file = "D:\Android\Flow\app\src\main\java\com\flow\presentation\home\HomeScreen.kt"
$lines = [System.IO.File]::ReadAllLines($file)

# T010: AddTask startDate (line 625, 0-indexed 624)
$lines[624] = $lines[624].TrimEnd() -replace 'startDate = it$', 'startDate = utcDateToLocalMidnight(it)'

# T011: AddTask dueDate (line 672, 0-indexed 671)
$lines[671] = $lines[671].TrimEnd() -replace 'dueDate = it$', 'dueDate = utcDateToLocalMidnight(it)'

# T012: EditTask startDate (line 872, 0-indexed 871)
$lines[871] = $lines[871].TrimEnd() -replace 'startDate = it$', 'startDate = utcDateToLocalMidnight(it)'

# T013: EditTask dueDate (line 919, 0-indexed 918)
$lines[918] = $lines[918].TrimEnd() -replace 'dueDate = it$', 'dueDate = utcDateToLocalMidnight(it)'

[System.IO.File]::WriteAllLines($file, $lines)

Write-Host "Done. Verifying:"
Write-Host ("Line 625: " + $lines[624])
Write-Host ("Line 672: " + $lines[671])
Write-Host ("Line 872: " + $lines[871])
Write-Host ("Line 919: " + $lines[918])
