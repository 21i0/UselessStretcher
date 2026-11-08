[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Left,
    [Parameter(Mandatory)][string]$Right,
    [string]$Report = (Join-Path $PSScriptRoot 'compare-tuples-report.json')
)
# Streaming verifier/comparator for CatalogTupleEvidence v1. No game or mod code is loaded.
$ErrorActionPreference = 'Stop'
$utf8Strict = [Text.UTF8Encoding]::new($false, $true)
$reportPath = [IO.Path]::GetFullPath($Report)
if ([IO.Path]::GetPathRoot($reportPath) -ine 'D:\') { throw 'Comparison reports must stay on D:.' }
$result = [ordered]@{ utc=[DateTime]::UtcNow.ToString('o'); left=$Left; right=$Right; status='RUNNING'; orderCompared=$false; firstDifference=$null; sourceDifferences=@(); errors=@() }
$openStates = [Collections.Generic.List[object]]::new()

function Append-Number($Digest, [long]$Value, [int]$Bytes) {
    $buffer = if ($Bytes -eq 4) { [BitConverter]::GetBytes([int]$Value) } else { [BitConverter]::GetBytes($Value) }
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($buffer) }
    $Digest.AppendData([byte[]]$buffer)
}
function New-Digest {
    $digest = [Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
    $digest.AppendData([Text.Encoding]::ASCII.GetBytes("USCTUPLE`0"))
    Append-Number $digest 1 4
    return $digest
}
function Add-Tuple($Digest, [string[]]$Fields) {
    $Digest.AppendData([byte[]]@(1))
    foreach ($field in $Fields) {
        $bytes = $utf8Strict.GetBytes($field)
        Append-Number $Digest $bytes.Length 4
        $Digest.AppendData($bytes)
    }
}
function Finish-Digest($Digest, [long]$Count) {
    $Digest.AppendData([byte[]]@(0))
    Append-Number $Digest $Count 8
    return [Convert]::ToHexString($Digest.GetHashAndReset()).ToLowerInvariant()
}
function Decode-Field([string]$Text) {
    $bytes = [Convert]::FromBase64String($Text)
    if ([Convert]::ToBase64String($bytes) -cne $Text) { throw 'Noncanonical base64 field.' }
    return $utf8Strict.GetString($bytes)
}
function Read-Number([string]$Text) {
    if ($Text -cnotmatch '^(0|[1-9][0-9]*)$') { throw "Invalid count/index: $Text" }
    return [long]::Parse($Text, [Globalization.CultureInfo]::InvariantCulture)
}
function Open-Tuples([string]$File) {
    $reader = [IO.StreamReader]::new((Resolve-Path -LiteralPath $File).Path, $utf8Strict, $false)
    $state = [pscustomobject]@{
        Path=$File; Reader=$reader; Count=[long]0; Digest=(New-Digest); Done=$false; Summary=$null
        Sources=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    }
    $openStates.Add($state)
    if ($reader.ReadLine() -cne "USCTUPLE`t1`tSHA-256`tUTF8-LP32BE") { throw "Unsupported or missing v1 header: $File" }
    return $state
}
function Next-Tuple($State) {
    if ($State.Done) { return $null }
    $line = $State.Reader.ReadLine()
    if ($null -eq $line) { throw "Truncated tuple file: $($State.Path)" }
    [string[]]$fields = $line.Split([char]9)
    if ($fields[0] -ceq 'T') {
        if ($fields.Length -ne 5) { throw "Malformed tuple at $($State.Count) in $($State.Path)" }
        if ((Read-Number $fields[1]) -ne $State.Count) { throw "Out-of-order tuple index at $($State.Count) in $($State.Path)" }
        [string[]]$tuple = @((Decode-Field $fields[2]), (Decode-Field $fields[3]), (Decode-Field $fields[4]))
        Add-Tuple $State.Digest $tuple
        if (-not $State.Sources.ContainsKey($tuple[2])) {
            $State.Sources.Add($tuple[2], [pscustomobject]@{ Count=[long]0; Digest=(New-Digest); Sha256=$null })
        }
        $source = $State.Sources[$tuple[2]]
        Add-Tuple $source.Digest $tuple
        $source.Count++
        $index = $State.Count++
        return [pscustomobject]@{ index=$index; recipeId=$tuple[0]; fingerprint=$tuple[1]; sourceId=$tuple[2] }
    }
    if ($fields[0] -cne 'TOTAL' -or $fields.Length -ne 3) { throw "Expected TOTAL footer: $($State.Path)" }
    $actualHash = Finish-Digest $State.Digest $State.Count
    if ((Read-Number $fields[1]) -ne $State.Count -or $fields[2] -cne $actualHash) { throw "Total count/SHA256 mismatch: $($State.Path)" }
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($source in $State.Sources.Values) { $source.Sha256 = Finish-Digest $source.Digest $source.Count }
    while ($true) {
        $line = $State.Reader.ReadLine()
        if ($null -eq $line) { throw "Missing END footer: $($State.Path)" }
        if ($line -ceq 'END') { break }
        [string[]]$fields = $line.Split([char]9)
        if ($fields.Length -ne 4 -or $fields[0] -cne 'S') { throw "Malformed source footer: $($State.Path)" }
        $sourceId = Decode-Field $fields[1]
        if (-not $seen.Add($sourceId) -or -not $State.Sources.ContainsKey($sourceId)) { throw "Duplicate/unknown source footer: $sourceId" }
        $source = $State.Sources[$sourceId]
        if ((Read-Number $fields[2]) -ne $source.Count -or $fields[3] -cne $source.Sha256) { throw "Source count/SHA256 mismatch: $sourceId in $($State.Path)" }
    }
    if ($seen.Count -ne $State.Sources.Count) { throw "Missing source footer: $($State.Path)" }
    if ($null -ne $State.Reader.ReadLine()) { throw "Trailing data after END: $($State.Path)" }
    $State.Summary = [ordered]@{
        count=$State.Count; sha256=$actualHash
        sources=@($State.Sources.GetEnumerator() | Sort-Object Key -CaseSensitive | ForEach-Object { [ordered]@{sourceId=$_.Key; count=$_.Value.Count; sha256=$_.Value.Sha256} })
    }
    $State.Done = $true
    return $null
}

try {
    $missing = @($Left,$Right | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) })
    if ($missing.Count -gt 0) {
        $result.status = 'EVIDENCE_MISSING'
        $result.errors = @('A tuple export is absent. No tuple order, count or digest can be inferred for that run.', $missing)
    } else {
        $a = Open-Tuples $Left
        $b = Open-Tuples $Right
        while (-not $a.Done -or -not $b.Done) {
            $ta = Next-Tuple $a
            $tb = Next-Tuple $b
            if ($null -eq $result.firstDifference) {
                if (($null -eq $ta) -ne ($null -eq $tb)) {
                    $result.firstDifference = [ordered]@{ index=([Math]::Min($a.Count,$b.Count)); left=$ta; right=$tb; reason='length' }
                } elseif ($null -ne $ta -and ($ta.recipeId -cne $tb.recipeId -or $ta.fingerprint -cne $tb.fingerprint -or $ta.sourceId -cne $tb.sourceId)) {
                    $result.firstDifference = [ordered]@{ index=$ta.index; left=$ta; right=$tb; reason='ordered-tuple' }
                }
            }
        }
        $result.leftSummary = $a.Summary; $result.rightSummary = $b.Summary
        $keys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($key in $a.Sources.Keys) { [void]$keys.Add($key) }
        foreach ($key in $b.Sources.Keys) { [void]$keys.Add($key) }
        $differences = @()
        foreach ($key in ($keys | Sort-Object -CaseSensitive)) {
            $as = if ($a.Sources.ContainsKey($key)) { $a.Sources[$key] } else { $null }
            $bs = if ($b.Sources.ContainsKey($key)) { $b.Sources[$key] } else { $null }
            if ($null -eq $as -or $null -eq $bs -or $as.Count -ne $bs.Count -or $as.Sha256 -cne $bs.Sha256) {
                $differences += [ordered]@{ sourceId=$key; leftCount=$(if($as){$as.Count}else{$null}); rightCount=$(if($bs){$bs.Count}else{$null}); leftSha256=$(if($as){$as.Sha256}else{$null}); rightSha256=$(if($bs){$bs.Sha256}else{$null}) }
            }
        }
        $result.sourceDifferences = $differences
        $result.orderCompared = $true
        $result.status = if ($null -eq $result.firstDifference -and $a.Count -eq $b.Count -and $a.Summary.sha256 -ceq $b.Summary.sha256 -and $differences.Count -eq 0) { 'MATCH' } else { 'DIFFERENT' }
    }
} catch { $result.status='INVALID_EVIDENCE'; $result.errors=@($_.Exception.Message) }
finally {
    foreach ($state in $openStates) {
        $state.Reader.Dispose(); $state.Digest.Dispose()
        foreach ($source in $state.Sources.Values) { $source.Digest.Dispose() }
    }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($reportPath)) | Out-Null
    $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8
}
Write-Output ("Tuple comparison {0}: {1}" -f $result.status,$reportPath)
if ($null -ne $result.firstDifference) { $result.firstDifference | ConvertTo-Json -Depth 6 | Write-Output }
if ($result.status -ne 'MATCH') { exit 1 }
