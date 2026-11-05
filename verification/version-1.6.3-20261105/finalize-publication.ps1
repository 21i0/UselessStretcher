$ErrorActionPreference = 'Stop'
$taskRepo = 'D:/AI/sources/UselessStretcher-capsule-variants'
$taskEvidence = Join-Path $taskRepo 'verification/version-1.6.3-20261105'
$taskJar = 'D:/AI/useless_stretcher-1.21.1-1.6.3.jar'
$taskHash = (Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash
if ($taskHash -ne '00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC') {
    throw 'Delivery JAR no longer matches the published artifact'
}
$taskGithub = Get-Content -LiteralPath (Join-Path $taskEvidence 'github-release-receipt.json') -Raw | ConvertFrom-Json
$taskCf = Get-Content -LiteralPath (Join-Path $taskEvidence 'curseforge-release-receipt.json') -Raw | ConvertFrom-Json
$taskDownload = Get-Content -LiteralPath (Join-Path $taskEvidence 'curseforge-download-audit.json') -Raw | ConvertFrom-Json
if ($taskGithub.result -ne 'GITHUB RELEASE PUBLISHED AND VERIFIED' -or $taskCf.status -ne 'Approved') {
    throw 'Missing verified publication receipts'
}
$taskBlock = @'
[2026-11-05-1.6.3-published]
万象担架 1.6.3 已正式发布（2026-11-05；当前发布状态）

GitHub：源码已推送 main 与 feature/capsule-variants，生产源码提交 2f1fe845343673512829cbf080117257b7958bec。
Release：https://github.com/21i0/UselessStretcher/releases/tag/v1.6.3
附件：useless_stretcher-1.21.1-1.6.3.jar、changelog-1.6.3.txt、usage-1.6.3.txt；标签保留在生产源码提交。
CurseForge：https://www.curseforge.com/minecraft/mc-mods/useless-stretcher/files/9451528
文件 ID：9451528；Release；Approved，公共文件页已可访问并提供下载。
文件名和显示名统一为 useless_stretcher-1.21.1-1.6.3.jar。
Minecraft 1.21.1 / NeoForge / Java 21 / Client + Server。
必需依赖：Some-Useless-Things、Applied Energistics 2；可选依赖：GuideME、Curios API。
项目中英文说明已更新到 1.6.3；保留原有完整功能、1.20.1 移植说明及文件。

本地正式包：D:/AI/useless_stretcher-1.21.1-1.6.3.jar
大小：12,691,712 字节
SHA256：00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC
GitHub 远端附件 digest 及 CurseForge 实际下载回验均与本地包一致。
发布证据：verification/version-1.6.3-20261105/；CF 截图 curseforge-published.png。

旧 CF 文件 9430671 已因重复 9430416 被拒绝，旧 Processing 描述为当时历史；1.6.3 是本轮兼容修复的新二进制。
以下保留修复详情及历史交接；其中早期的待发布、旧版本、旧哈希和旧审核状态均以本段为准。
[/2026-11-05-1.6.3-published]

'@
foreach ($taskPath in @(
        (Join-Path $taskRepo '更新.txt'),
        (Join-Path $taskRepo '万象担架使用说明.txt'),
        (Join-Path $taskRepo '万象担架-项目交接文档.md'),
        'D:/AI/更新.txt', 'D:/AI/万象担架使用说明.txt', 'D:/AI/万象担架-项目交接文档.md')) {
    $taskText = [IO.File]::ReadAllText($taskPath)
    if (!$taskText.StartsWith('[2026-11-05-1.6.3-published]')) {
        [IO.File]::WriteAllText($taskPath, $taskBlock + $taskText, [Text.UTF8Encoding]::new($false))
    }
}
$taskPublicEvidence = 'D:/AI/万象担架-CurseForge发布资料/20261105-1.6.3'
New-Item -ItemType Directory -Path $taskPublicEvidence -Force | Out-Null
foreach ($taskName in @('github-release-receipt.json', 'curseforge-release-receipt.json',
        'curseforge-download-audit.json', 'curseforge-published.png', 'curseforge-file-persisted.txt',
        'curseforge-files-status.txt', 'curseforge-changelog.md', 'curseforge-description-1.6.3.md',
        'curseforge-public-description.txt', 'curseforge-public-file.txt')) {
    Copy-Item -LiteralPath (Join-Path $taskEvidence $taskName) -Destination (Join-Path $taskPublicEvidence $taskName) -Force
}
[IO.File]::WriteAllText('D:/AI/CurseForge-万象担架说明-1.6.3.md',
    [IO.File]::ReadAllText((Join-Path $taskEvidence 'curseforge-description-1.6.3.md')),
    [Text.UTF8Encoding]::new($false))
Write-Output 'Publication receipts and local documentation synchronized; JAR unchanged.'
