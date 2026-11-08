param([Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{40}$')][string]$Commit)
$ErrorActionPreference = 'Stop'
$taskRepo = '21i0/UselessStretcher'
$taskTag = 'v1.6.3'
$taskJar = 'D:/AI/useless_stretcher-1.21.1-1.6.3.jar'
if ((Get-FileHash -LiteralPath $taskJar).Hash -ne '00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC') {
    throw 'Delivery hash does not match the audited artifact'
}
$taskCredential = "protocol=https`nhost=github.com`n`n" | git -c credential.interactive=false credential fill 2>$null
if ($LASTEXITCODE -ne 0) { throw 'No saved GitHub credential available' }
$taskSecretLine = $taskCredential | Where-Object { $_ -like 'password=*' } | Select-Object -First 1
if (!$taskSecretLine) { throw 'Git credential helper did not supply an authentication token' }
$taskSecret = $taskSecretLine.Substring(9)
$taskHeaders = @{Authorization=('Bearer '+$taskSecret); 'User-Agent'='UselessStretcher-release'; Accept='application/vnd.github+json'; 'X-GitHub-Api-Version'='2022-11-28'}
$taskClient = [Net.Http.HttpClient]::new()
try {
    $taskClient.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$taskSecret)
    $taskClient.DefaultRequestHeaders.Add('User-Agent','UselessStretcher-release')
    $taskClient.DefaultRequestHeaders.Add('X-GitHub-Api-Version','2022-11-28')
    $taskOld = Invoke-RestMethod -Uri "https://api.github.com/repos/$taskRepo/releases/tags/v1.6.0" -Headers $taskHeaders
    $taskTitle = $taskOld.name.Replace('1.6.0','1.6.3')
    if (!$taskTitle.Contains('1.6.3')) { throw 'Cannot preserve the previous release title convention' }
    $taskExisting = @(Invoke-RestMethod -Uri "https://api.github.com/repos/$taskRepo/releases?per_page=100" -Headers $taskHeaders) |
        Where-Object {$_.tag_name -eq $taskTag}
    if ($taskExisting) { throw 'v1.6.3 already exists; inspect it before attempting another publication' }
    $taskBody = [IO.File]::ReadAllText((Join-Path (Get-Location) 'release-1.6.3.md'))
    $taskCreate = @{tag_name=$taskTag; target_commitish=$Commit; name=$taskTitle; body=$taskBody; draft=$true; prerelease=$false; make_latest='true'} | ConvertTo-Json
    $taskRelease = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$taskRepo/releases" -Headers $taskHeaders -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($taskCreate))
    $taskUploadBase = $taskRelease.upload_url -replace '\{.*$',''
    if (([Uri]$taskUploadBase).Host -ne 'uploads.github.com') { throw 'Unexpected GitHub asset upload destination' }
    $taskAssets = @()
    foreach ($taskFile in @($taskJar, (Join-Path (Get-Location) 'changelog-1.6.3.txt'), (Join-Path (Get-Location) 'usage-1.6.3.txt'))) {
        $taskName = [IO.Path]::GetFileName($taskFile)
        $taskContent = [Net.Http.StreamContent]::new([IO.File]::OpenRead($taskFile))
        $taskContent.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/octet-stream')
        try {
            $taskResponse = $taskClient.PostAsync(($taskUploadBase+'?name='+[Uri]::EscapeDataString($taskName)), $taskContent).GetAwaiter().GetResult()
            try {
                $taskReply = $taskResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                if (!$taskResponse.IsSuccessStatusCode) { throw "GitHub asset upload failed for $taskName, HTTP $([int]$taskResponse.StatusCode)" }
                $taskAsset = $taskReply | ConvertFrom-Json
            } finally { $taskResponse.Dispose() }
        } finally { $taskContent.Dispose() }
        $taskHash = (Get-FileHash -LiteralPath $taskFile).Hash.ToLowerInvariant()
        if ($taskAsset.size -ne (Get-Item -LiteralPath $taskFile).Length -or $taskAsset.digest -ne ('sha256:'+$taskHash)) {
            throw "Remote asset digest/size mismatch for $taskName; release remains a draft"
        }
        $taskAssets += [ordered]@{name=$taskAsset.name; bytes=$taskAsset.size; digest=$taskAsset.digest; url=$taskAsset.browser_download_url; id=$taskAsset.id}
    }
    if ($taskAssets.Count -ne 3) { throw 'Missing release attachments' }
    $taskPublished = Invoke-RestMethod -Method Patch -Uri $taskRelease.url -Headers $taskHeaders -ContentType 'application/json' -Body '{"draft":false,"make_latest":"true"}'
    $taskVerify = Invoke-RestMethod -Uri "https://api.github.com/repos/$taskRepo/releases/tags/$taskTag" -Headers $taskHeaders
    $taskRef = Invoke-RestMethod -Uri "https://api.github.com/repos/$taskRepo/git/ref/tags/$taskTag" -Headers $taskHeaders
    if ($taskVerify.draft -or $taskVerify.prerelease -or $taskVerify.assets.Count -ne 3 -or $taskRef.object.sha -ne $Commit) {
        throw 'Published release final verification failed'
    }
    [ordered]@{result='GITHUB RELEASE PUBLISHED AND VERIFIED'; repository=$taskRepo; commit=$Commit; tag=$taskTag; title=$taskVerify.name; releaseUrl=$taskVerify.html_url; releaseId=$taskVerify.id; publishedAt=$taskVerify.published_at; assets=$taskAssets} |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath 'verification/version-1.6.3-20261105/github-release-receipt.json' -Encoding utf8
    Write-Output $taskVerify.html_url
} finally {
    $taskClient.Dispose()
    $taskSecret=$null; $taskSecretLine=$null; $taskCredential=$null; $taskHeaders=$null
}
