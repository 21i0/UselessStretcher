param(
    [string]$SourcePath = 'D:\AI\sources\DollChronicle\build\mygenesis-longhuan-r6-D\logs\gc.log.0',
    [string]$SnapshotName = 'gc-startup-snapshot.log',
    [string]$SummaryName = 'gc-startup-summary.json'
)
$ErrorActionPreference = 'Stop'
$taskSource = $SourcePath
$taskOutput = Join-Path $PSScriptRoot $SnapshotName
if (Test-Path -LiteralPath $taskOutput) { throw 'Snapshot already exists; keep the original evidence.' }

# Capture exactly the byte length visible when the handle opens. Never touch the live file.
$taskStream = [IO.File]::Open($taskSource, [IO.FileMode]::Open, [IO.FileAccess]::Read,
    ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
try {
    $taskBytes = [byte[]]::new([int]$taskStream.Length)
    $taskOffset = 0
    while ($taskOffset -lt $taskBytes.Length) {
        $taskRead = $taskStream.Read($taskBytes, $taskOffset, $taskBytes.Length - $taskOffset)
        if ($taskRead -eq 0) { throw 'Live log shortened during capture.' }
        $taskOffset += $taskRead
    }
} finally { $taskStream.Dispose() }
[IO.File]::WriteAllBytes($taskOutput, $taskBytes)
[string]$taskText = [Text.Encoding]::UTF8.GetString($taskBytes)
$taskPauses = [Collections.Generic.List[object]]::new()
$taskSafes = [Collections.Generic.List[object]]::new()
$taskReader = [IO.StringReader]::new($taskText)
$taskLineNumber = 0
$taskFirst = $null
$taskLast = $null
try {
    while ($null -ne ($taskLine = $taskReader.ReadLine())) {
        $taskLineNumber++
        if ($null -eq $taskFirst) { $taskFirst = $taskLine }
        $taskLast = $taskLine
        $taskMatch = [regex]::Match($taskLine,
            '^\[(?<time>[^\]]+)\]\[[^\]]+\]\[info\]\[gc\s*\]\s+GC\((?<id>\d+)\) (?<kind>Pause .+?) (?<ms>[\d.]+)ms$')
        if ($taskMatch.Success) {
            $taskPauses.Add([pscustomobject]@{
                line=$taskLineNumber; time=$taskMatch.Groups['time'].Value
                id=[int]$taskMatch.Groups['id'].Value; ms=[double]$taskMatch.Groups['ms'].Value
                kind=$taskMatch.Groups['kind'].Value
            })
        }
        $taskMatch = [regex]::Match($taskLine,
            '^\[(?<time>[^\]]+)\].*Safepoint "(?<kind>[^"]+)".*Reaching safepoint: (?<reach>\d+) ns.*Total: (?<ns>\d+) ns$')
        if ($taskMatch.Success) {
            $taskSafes.Add([pscustomobject]@{
                line=$taskLineNumber; time=$taskMatch.Groups['time'].Value
                ms=[double]$taskMatch.Groups['ns'].Value / 1000000
                reach_ms=[double]$taskMatch.Groups['reach'].Value / 1000000
                kind=$taskMatch.Groups['kind'].Value
            })
        }
    }
} finally { $taskReader.Dispose() }

$taskWindows = @(
    @('jei', '2026-11-06T10:43:26.953', '2026-11-06T10:44:44.837'),
    @('second_client_catalog_approximate', '2026-11-06T10:44:47', '2026-11-06T10:45:01'),
    @('startup_observer_window', '2026-11-06T10:43:24', '2026-11-06T10:45:32')
)
$taskWindowStats = foreach ($taskWindow in $taskWindows) {
    $taskP = @($taskPauses | Where-Object { $_.time -ge $taskWindow[1] -and $_.time -lt $taskWindow[2] })
    $taskS = @($taskSafes | Where-Object { $_.time -ge $taskWindow[1] -and $_.time -lt $taskWindow[2] })
    [pscustomobject]@{
        window=$taskWindow[0]; start=$taskWindow[1]; end=$taskWindow[2]
        fully_covered=([regex]::Match($taskFirst, '^\[([^\]]+)\]').Groups[1].Value -le $taskWindow[1] -and
            [regex]::Match($taskLast, '^\[([^\]]+)\]').Groups[1].Value -ge $taskWindow[2])
        gc_pause_count=$taskP.Count; gc_total_ms=($taskP.ms | Measure-Object -Sum).Sum
        gc_max_ms=($taskP.ms | Measure-Object -Maximum).Maximum
        safepoint_count=$taskS.Count; safepoint_total_ms=($taskS.ms | Measure-Object -Sum).Sum
        safepoint_max_ms=($taskS.ms | Measure-Object -Maximum).Maximum
        safepoint_reach_max_ms=($taskS.reach_ms | Measure-Object -Maximum).Maximum
    }
}
$taskSummary = [ordered]@{
    source=$taskSource; snapshot=$taskOutput
    captured_utc=[DateTime]::UtcNow.ToString('o'); captured_bytes=$taskBytes.Length
    sha256=(Get-FileHash -LiteralPath $taskOutput -Algorithm SHA256).Hash
    first_line=$taskFirst; last_line=$taskLast; line_count=$taskLineNumber
    pause_count=$taskPauses.Count
    full_gc_count=@($taskPauses | Where-Object { $_.kind -like 'Pause Full*' }).Count
    gc_pause_total_ms=($taskPauses.ms | Measure-Object -Sum).Sum
    gc_pause_max_ms=($taskPauses.ms | Measure-Object -Maximum).Maximum
    safepoint_count=$taskSafes.Count
    windows=@($taskWindowStats)
    longest_gc=@($taskPauses | Sort-Object ms -Descending | Select-Object -First 5)
    longest_safepoints=@($taskSafes | Sort-Object ms -Descending | Select-Object -First 5)
    safepoint_kinds=@($taskSafes | Group-Object kind | ForEach-Object {
        [pscustomobject]@{
            kind=$_.Name; count=$_.Count
            total_ms=($_.Group.ms | Measure-Object -Sum).Sum
            max_ms=($_.Group.ms | Measure-Object -Maximum).Maximum
        }
    })
    limits='GC pauses are also contained in safepoint totals. Do not add both totals. Safepoint reaching time is not proof that all threads were paused for that duration. Concurrent GC cycles are excluded. Existing diagnostics are observed, not started by this audit.'
}
$taskJson = $taskSummary | ConvertTo-Json -Depth 6
[IO.File]::WriteAllText((Join-Path $PSScriptRoot $SummaryName), $taskJson, [Text.UTF8Encoding]::new($false))
$taskJson
