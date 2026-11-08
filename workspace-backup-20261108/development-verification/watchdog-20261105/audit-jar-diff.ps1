param([Parameter(Mandatory=$true)][string]$Baseline, [Parameter(Mandatory=$true)][string]$Candidate)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Read-Hashes([string]$Path) {
    $archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path))
    $hashes = @{}
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            $stream = $entry.Open()
            try { $hashes[$entry.FullName] = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)) }
            finally { $stream.Dispose() }
        }
    } finally { $archive.Dispose() }
    return $hashes
}
$old = Read-Hashes $Baseline
$new = Read-Hashes $Candidate
$added = @($new.Keys | Where-Object { !$old.ContainsKey($_) } | Sort-Object)
$removed = @($old.Keys | Where-Object { !$new.ContainsKey($_) } | Sort-Object)
$changed = @($old.Keys | Where-Object { $new.ContainsKey($_) -and $old[$_] -ne $new[$_] } | Sort-Object)
$same = @($old.Keys | Where-Object { $new.ContainsKey($_) -and $old[$_] -eq $new[$_] })
if ($removed.Count) { throw ('Unexpected removed production entries: ' + ($removed -join ', ')) }
$unexpected = @($changed | Where-Object {
    $_ -notmatch '^com/sorrowmist/useless/stretcher/(content/entity/WondrousStaffAcceleration(\$[^/]*)?|mixin/UselessStretcherMixinPlugin)\.class$|^useless_stretcher\.mixins\.json$'
})
if ($unexpected.Count) { throw ('Unexpected changed production entries: ' + ($unexpected -join ', ')) }
foreach ($entry in $added) {
    if ($entry -notmatch '^com/sorrowmist/useless/stretcher/(compat/(CoalescedMachineAccelerationCompat|CoalescedMainThreadSyncQueue|Mbd2MainThreadSyncCompat)(\$[^/]*)?|mixin/Mbd2AsyncSyncMixin)\.class$') {
        throw "Unexpected added production entry: $entry"
    }
}
[ordered]@{
    result='PRODUCTION JAR DIFF CHECKS PASSED'
    baseline=(Resolve-Path -LiteralPath $Baseline).Path
    baseline_sha256=(Get-FileHash -LiteralPath $Baseline).Hash
    candidate=(Resolve-Path -LiteralPath $Candidate).Path
    candidate_sha256=(Get-FileHash -LiteralPath $Candidate).Hash
    unchanged_count=$same.Count
    added=$added
    changed=$changed
    removed=$removed
} | ConvertTo-Json -Depth 5
