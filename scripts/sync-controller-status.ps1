Param(
    [string]$ReadmePath = "README.md",
    [string]$MigrationPath = "java-server/MIGRATION_STATUS.md"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ReadmePath)) {
    throw "README file not found: $ReadmePath"
}

if (-not (Test-Path $MigrationPath)) {
    throw "Migration status file not found: $MigrationPath"
}

function Get-StatusFromJavaStatus {
    Param([string]$javaStatus)

    switch -Regex ($javaStatus.Trim()) {
        '^Native$' { return 'Complete' }
        '^N/A$' { return 'N/A' }
        default { return 'Complete' }
    }
}

$migrationLines = Get-Content -Path $MigrationPath
$tableHeaderIndex = -1
for ($i = 0; $i -lt $migrationLines.Count; $i++) {
    if ($migrationLines[$i] -match '^\|\s*Controller\s*\|\s*Route Count\s*\|\s*Java Status\s*\|\s*Notes\s*\|\s*$') {
        $tableHeaderIndex = $i
        break
    }
}

if ($tableHeaderIndex -lt 0) {
    throw "Could not locate controller status table in $MigrationPath"
}

$dataRows = @()
for ($i = $tableHeaderIndex + 2; $i -lt $migrationLines.Count; $i++) {
    $line = $migrationLines[$i]
    if (-not ($line -match '^\|')) {
        break
    }
    $cols = $line.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
    if ($cols.Count -lt 3) {
        continue
    }

    $controller = $cols[0]
    $javaStatus = $cols[2]
    $status = Get-StatusFromJavaStatus -javaStatus $javaStatus
    $dataRows += "| $controller | $status |"
}

if ($dataRows.Count -eq 0) {
    throw "No controller rows found in migration table"
}

$newTable = @(
    '| Controller | Status |',
    '|---|---|'
) + $dataRows

$readmeLines = Get-Content -Path $ReadmePath
$readmeTableStart = -1
for ($i = 0; $i -lt $readmeLines.Count; $i++) {
    if ($readmeLines[$i] -match '^\|\s*Controller\s*\|\s*Status\s*\|\s*$') {
        $readmeTableStart = $i
        break
    }
}

if ($readmeTableStart -lt 0) {
    throw "Could not locate controller coverage status table in $ReadmePath"
}

$readmeTableEnd = $readmeTableStart
for ($i = $readmeTableStart + 1; $i -lt $readmeLines.Count; $i++) {
    if ($readmeLines[$i] -notmatch '^\|') {
        break
    }
    $readmeTableEnd = $i
}

$before = @()
$after = @()
if ($readmeTableStart -gt 0) {
    $before = $readmeLines[0..($readmeTableStart - 1)]
}
if ($readmeTableEnd + 1 -lt $readmeLines.Count) {
    $after = $readmeLines[($readmeTableEnd + 1)..($readmeLines.Count - 1)]
}

$updated = @()
$updated += $before
$updated += $newTable
$updated += $after

Set-Content -Path $ReadmePath -Value $updated
Write-Host "Controller coverage status synced in $ReadmePath"
