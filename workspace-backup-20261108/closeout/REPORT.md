# 2026-11-08 万象担架 1.21.1 工作区收尾

已先验证 GitHub 备份成功，再实际清理担架专属旧产物。本轮没有启动 Minecraft、服务端或 ModLauncher；没有发布整合包。

## GitHub 备份

- 源码备份提交：https://github.com/21i0/UselessStretcher/commit/6b6d9374cda42f5b4cbc957efc9c900ba5b1cec8
- 分支：`backup/1.21.1-workspace-20261108`；正式 main、既有标签与 Release 均未改。
- 备份包含当前 1.6.4 开发源码、旧 main 的实际 tracked diff、P/P2/P3/P4 与后续候选源码和构建/验证链、项目原有素材、两份明确标记为实验用途的本项目 JAR、版本记录及精简验收。
- GitHub 完整树核对了 3,106 项文件，全部对象与本地复制字节一致。新增第三方二进制、账号令牌和私人存档为 0。完整远端绑定收据保留本地。

## 实际清理

- 完成 368 个具体目录/旧 JAR 目标，删除 5,102 个文件。
- 删除逻辑字节：13,810,244,156（13.810 GB）。
- 删除前逐文件读取的单链接 NTFS allocation：13,820,208,552 字节；这些文件已确认不存在。
- 本轮删除窗口内 D 盘空闲实测增加 13,820,137,472 字节（13.820 GB）。其它对话的后续清理没有计入本项目归因；目录元数据也未计入文件 allocation 数。
- 清理范围为两处 build 中旧测试端的重复 mods/libraries/natives/downloads、明确编译缓存、P/P2/P3 重复依赖库，以及四个归档目录中精确匹配的旧 1.21.1 担架 JAR。没有清空根 build 或整个共享归档目录。
- 清理前后两处源码的 diff 和 status 指纹一致，526 个受保护路径均存在。清理后仅在 6 份担架 TXT/交接文档顶部新增了带日期收尾说明，旧正文原字节保留。
- 原始剧情通过日志 SHA256 `540FDF18E23548CD79FC312D5E5FB7F049D12FE643F83B4439CB592A5F04DC56` 保持，并在本地保存副本；未上传原始运行日志。

## 保留产物和空间

- latest_candidate：`D:\AI\candidates\useless-stretcher-jei-sponge-fix-20261108-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970-sponge1.jar`
  SHA256：`3AEB59B7AAD8A2BF50D203BB220192FF45D90D0C50F80F6D8878514249F2F6EF`；12,817,555 字节。
- published_stable：`D:\AI\useless_stretcher-1.21.1-1.6.3.jar`
  SHA256：`00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC`；12,691,712 字节。
- 最新候选全目录 22,964,121 字节，冻结 JAR、manifest、原报告和原始验收均未修改。`9556D497…` 仍保留为构建输入/错误负控，不当作可用版本。
- 当前开发源码为 `D:\AI\sources\UselessStretcher-capsule-variants`；旧 main、共享 Git/worktree 元数据和 1.20.1 目录保留。
- 两处 build 仍保留 6,375,331,060 字节（约 6.38 GB），包含测试存档、原始日志/报告以及不能擅动的共享战斗玩偶、arcana、音视频素材。
- 担架候选源码/证据/回退链仍保留 364,089,187 字节（约 364.1 MB）。旧截图/原始素材保守保留；没有进入整合包、DollChronicle、星系或独立玩偶目录清理。

## CurseForge

- 当前 1.21.1 正式文件仍为 [1.6.3 / 9451528](https://www.curseforge.com/minecraft/mc-mods/useless-stretcher/files/9451528)。
- 本地更晚包是实验候选：正式版本归档/升级说明未完成，格式 3 总成迁移、安装交互、源库隔离与同存档进程重启的关键实机尚未验收。现有 runtime-e 是 JEI 冒烟，不能替代这些存档关键验收。
- 因本轮禁止开 MC，未将改名作为验收，未创建新 Release 或上传 CF。详细版本/依赖/协议/升级限制见 `RELEASE-STATUS.md`，并已复制至 `D:\AI\万象担架-CurseForge发布资料\20261108-1.6.4-hold\`。

本目录的 cleanup-plan、cleanup-receipt、postflight-receipt、remote-backup-receipt 和文档更新收据保留精确数字与路径；原冻结候选没有因收尾操作重写。

## 临时检出保留及自动审批阻断

本轮为备份创建的独立临时检出 `D:\AI\backups\useless-stretcher-closeout-20261108` 仍保留：3,617 个文件、271,702,305 逻辑字节（约 272 MB）。它不属于原来的共享 Git/worktree，全部必要源码及收尾资料已经推送并核验。

在确认远端备份、目标绝对路径及无重解析点后，删除这个明确临时检出的终端动作被自动审批拒绝，返回 `blocked by policy`，没有提供更细理由。该动作没有执行，本报告不把这 272 MB 计入释放空间；未继续改用其它接口重复删除。原始 368 个目标的清理已经成功，与此阻断分别记录。
