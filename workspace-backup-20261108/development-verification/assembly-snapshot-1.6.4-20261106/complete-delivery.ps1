$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$taskVersion = '1.21.1-1.6.4'
$taskHash = 'AC78F41EAA7883434F6845CF7323829D77E66B994C82EDCF2B81AEC4AFCD9220'
$taskJar = Join-Path $taskRoot "build\libs\useless_stretcher-$taskVersion.jar"
$taskDelivery = 'D:\AI\1.6.4-候选'
$taskTarget = Join-Path $taskDelivery "useless_stretcher-$taskVersion.jar"
if ((Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash -ne $taskHash) { throw 'Candidate changed since verification' }
$taskReleased = 'D:\AI\useless_stretcher-1.21.1-1.6.3.jar'
$taskReleaseHash = (Get-FileHash -LiteralPath $taskReleased -Algorithm SHA256).Hash
if ($taskReleaseHash -ne '00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC') { throw 'Published 1.6.3 bytes changed' }

function Replace-TaskText([string]$path, [string]$before, [string]$after) {
    $taskText = [IO.File]::ReadAllText($path)
    if (-not $taskText.Contains($before)) { throw "Expected text not found: $path" }
    [IO.File]::WriteAllText($path, $taskText.Replace($before, $after), [Text.UTF8Encoding]::new($false))
}
$taskStatus = 'Java 21 生产编译、40 项独立 JVM 测试和最终 build 已通过；更新后的游戏回归源码也已编译。包内版本及测试类排除已核对，本轮尚未运行真实游戏回归。'
$taskPathLine = '本地候选包：D:/AI/1.6.4-候选/useless_stretcher-1.21.1-1.6.4.jar（12,699,916 字节）。'
$taskHashLine = 'SHA256：' + $taskHash + '。验证：verification/assembly-snapshot-1.6.4-20261106/verification.md。'
Replace-TaskText (Join-Path $taskRoot 'README.md') '当前生产代码已通过 Java 21 编译；安装快照的独立测试及最终构建核对仍在进行，尚未运行本轮游戏回归。' ($taskStatus + "`n`n" + $taskPathLine)
Replace-TaskText (Join-Path $taskRoot '更新.txt') '生产代码已通过 Java 21 编译；本轮安装快照测试及最终构建核对尚在进行，未运行本轮游戏回归。' ($taskStatus + "`n" + $taskPathLine + "`n" + $taskHashLine)
Replace-TaskText (Join-Path $taskRoot '万象担架使用说明.txt') '生产代码已通过 Java 21 编译；本轮安装快照测试及最终构建核对仍在进行，尚未运行本轮游戏回归。' ($taskStatus + "`n" + $taskPathLine + "`n" + $taskHashLine)
Replace-TaskText (Join-Path $taskRoot '万象担架-项目交接文档.md') '1.6.4 样板总成担架安装快照（实现中，尚未发布）' '1.6.4 样板总成担架安装快照（本地候选包，尚未发布）'
Replace-TaskText (Join-Path $taskRoot '万象担架-项目交接文档.md') '当前生产代码已通过 Java 21 编译。独立 JVM 单测仍在处理不启动游戏的 vanilla registry bootstrap，最终结果由本轮后续验证补充；不能据此宣称 Mixin 已在游戏加载或全部安装路径已经过真实服务端回归。' ($taskStatus + '不能据此宣称 Mixin 已在游戏加载或全部安装路径已经过真实服务端回归。' + "`n`n" + $taskPathLine + "`n`n" + $taskHashLine)

$taskDocBackup = Join-Path $PSScriptRoot 'ai-root-docs-before-snapshot-delivery'
if (Test-Path -LiteralPath $taskDocBackup) { throw 'Document backup already exists; inspect before rerunning' }
New-Item -ItemType Directory -Path $taskDocBackup | Out-Null
$taskDocs = @('更新.txt', '万象担架使用说明.txt', '万象担架-项目交接文档.md')
foreach ($taskName in $taskDocs) {
    $taskRootDoc = Join-Path 'D:\AI' $taskName
    if (Test-Path -LiteralPath $taskRootDoc) { Copy-Item -LiteralPath $taskRootDoc -Destination (Join-Path $taskDocBackup $taskName) }
    Copy-Item -LiteralPath (Join-Path $taskRoot $taskName) -Destination $taskRootDoc
}
New-Item -ItemType Directory -Path $taskDelivery -Force | Out-Null
if (Test-Path -LiteralPath $taskTarget) {
    if ((Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash -ne $taskHash) { throw 'A different candidate already occupies delivery path' }
} else { Copy-Item -LiteralPath $taskJar -Destination $taskTarget }
if ((Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash -ne $taskHash) { throw 'Delivery hash mismatch' }
Copy-Item -LiteralPath (Join-Path $taskRoot '更新.txt') -Destination (Join-Path $taskDelivery '更新.txt')
Copy-Item -LiteralPath (Join-Path $taskRoot '万象担架使用说明.txt') -Destination (Join-Path $taskDelivery '万象担架使用说明.txt')
$taskRecord = [ordered]@{
    date = '2026-11-06'; version = $taskVersion; status = 'local-candidate-not-published'
    path = $taskTarget; bytes = (Get-Item -LiteralPath $taskTarget).Length; sha256 = $taskHash
    tests = 40; failures = 0; gameRegressionRun = $false; regressionSourcesCompiled = $true
    published163 = @{ path = $taskReleased; sha256 = $taskReleaseHash; untouched = $true }
    guide = 'assets/ae2/ae2guide/useless_stretcher/stretcher.md'
    rollback = 'Use a complete pre-upgrade save backup; 1.6.3 cannot safely read format 3'
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'delivery.json'), ($taskRecord | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
$taskRecord | ConvertTo-Json -Depth 5
