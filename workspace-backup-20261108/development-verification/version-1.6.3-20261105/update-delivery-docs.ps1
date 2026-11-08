$ErrorActionPreference = 'Stop'
$taskJar = 'D:/AI/useless_stretcher-1.21.1-1.6.3.jar'
$taskHash = (Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash
$taskBytes = (Get-Item -LiteralPath $taskJar).Length
if ($taskHash -ne '00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC') {
    throw 'Refusing to document an unverified delivery artifact'
}
$taskRepo = 'D:/AI/sources/UselessStretcher-capsule-variants'
$taskBegin = '[2026-11-05-watchdog-compat]'
$taskEnd = '[/2026-11-05-watchdog-compat]'
$taskUpdate = @"
$taskBegin
万象担架 1.6.3 兼容修复（2026-11-05）

文件：D:/AI/useless_stretcher-1.21.1-1.6.3.jar
大小：$taskBytes 字节
SHA256：$taskHash
版本迭代为 1.21.1-1.6.3，网络协议仍为 23。客户端、服务端同步替换；无需重置存档。

修复手杖加速 Productive Bees Genesis Extra 离心机的调用方式：单体和范围使用机器自身的批处理接口，合并多来源额外工作，不再重复完整 ticker 和 AE 服务。正常机器 tick 保留；旧积压优先处理，异常提交不回退重放。无 JDTE 时支持 PBG 自有接口。
兼容 MBD2 21.1.2 / LDLib2 2.2.44 / AE2 19.2.18 / AE2LT 2.1.3 的节点同步：合并后交主线程处理，保留 AE2LT 线程保护；失败完整重试、退避及限频，卸载和停服不复活旧任务。该桥接仅对已核对的 LDLib2 2.2.44 启用，其它版本告警后不盲目注入。

实际验证：准确官方 PBG 1.0.9 真实 Extra 工厂四路线配方，原生/单体/范围/叠加分别用 800/2/3/2 tick；8 蜂脾产出 8 蜡和 800mB 蜂蜜。联网满储连续 40 tick、真实部分接收与零接收、原生 NBT 重载、独立进程同存档重启均保持总量，不重新投料或重复产出。MBD2 5000 次真实异步派发合并到主线程，同存档重启保留 37 钻石、原 claim 及 431/7 两种余额。普通 AE/闪电科技配方、手杖相关回归、无 JDTE 与兼容模组全部缺席启动通过。
JAR 审计通过：269 生产源码、480 class、137 资源、86 JSON/MCMeta；480 个交付 class 与实际测试的生产字节码一致。测试类和第三方接口打包数 0。此次版本迭代仅修改包内版本和两页 GuideME，其余 615 个文件条目逐字节相同，原玩偶、剧情、胶囊与 GuideME 资源保留。

验证记录：D:/AI/sources/UselessStretcher-capsule-variants/verification/1.6.3-watchdog-compat-20261105.md
日志及哈希清单：同目录 watchdog-20261105/。
旧包和根目录旧文档可恢复归档：D:/AI/archive/1.6.2-watchdog-before-1.6.3-20261105/。
源码：feature/capsule-variants，基于 727f0fc；按用户授权先更新 GitHub 源码与 Release，再更新 CurseForge；发布回执另行记录。原主工作区未提交玩偶改动、其它文件及 1.20.1 分支保留。

边界：这是隔离兼容环境与可观测 AE 测试后端，不是原服务器存档/整包。合作式预算不能中断第三方单次同步调用，不能承诺所有 AE 网络都不再超时；13:57 的独立登录 watchdog 仍无对应日志定因，不与已修补的 13:29 路径混为一谈。

以下为历史内容，其中旧的“当前”状态和哈希不代表本次本地交付。
$taskEnd

"@
$taskUsage = @"
$taskBegin
万象担架 1.6.3 兼容修复使用补充（2026-11-05，当前本地包）

文件：D:/AI/useless_stretcher-1.21.1-1.6.3.jar
大小：$taskBytes 字节
SHA256：$taskHash
客户端和服务端同步替换，网络协议保持 23，无需重置存档；旧包已归档。

手杖单体和范围加速现在自动接入 Productive Bees Genesis Extra 离心机的原生批处理。PBG 1.0.9、JDTE 0.6.4.3 组合以及不装 JDTE 的路径已验证，无需新增操作；机器自身的正常 tick 继续执行。遇到第三方批处理回调异常时，该机器实例的额外加速暂停并记录一次告警，不自动重放未知提交。
MBD2 21.1.2、LDLib2 2.2.44、AE2 19.2.18、AE2LT 2.1.3 组合的同步已改为主线程处理，保留节点与余额；桥接仅对已验证的 LDLib2 2.2.44 自动启用。
真实配方、联网满储、部分/零接收、原生 NBT 重载及同存档进程重启已验证。其它玩偶、胶囊、手杖和 GuideME 用法沿用下文。

完整更新与验证：D:/AI/changelog-1.6.3.txt；D:/AI/sources/UselessStretcher-capsule-variants/verification/1.6.3-watchdog-compat-20261105.md。
旧包/旧文档：D:/AI/archive/1.6.2-watchdog-before-1.6.3-20261105/。按用户授权更新 GitHub 后再更新 CurseForge；发布回执另行记录。
以下保留历史使用说明，其中旧“当前”状态、旧文件哈希以本段为准。
$taskEnd

"@
function Write-Preface([string]$Path, [string]$Preface) {
    $existing = if (Test-Path -LiteralPath $Path) { [IO.File]::ReadAllText($Path) } else { '' }
    if ($existing.StartsWith($taskBegin)) {
        $endIndex = $existing.IndexOf($taskEnd)
        if ($endIndex -lt 0) { throw "Incomplete prior update marker in $Path" }
        $existing = $existing.Substring($endIndex + $taskEnd.Length).TrimStart([char[]]"`r`n")
    }
    [IO.File]::WriteAllText($Path, $Preface + $existing, [Text.UTF8Encoding]::new($false))
}
foreach ($taskPath in @(
        (Join-Path $taskRepo '更新.txt'), (Join-Path $taskRepo '万象担架-项目交接文档.md'),
        'D:/AI/更新.txt', 'D:/AI/万象担架-项目交接文档.md', 'D:/AI/changelog-1.6.3.txt')) {
    Write-Preface $taskPath $taskUpdate
}
foreach ($taskPath in @((Join-Path $taskRepo '万象担架使用说明.txt'),
        'D:/AI/万象担架使用说明.txt', 'D:/AI/usage-1.6.3.txt')) {
    Write-Preface $taskPath $taskUsage
}
