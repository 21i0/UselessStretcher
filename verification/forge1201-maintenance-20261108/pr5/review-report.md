# PR #5 本地审查报告

审查日期：2026-10-29。

PR：[Forge 1.20.1：上游回移、共享 UI、配置翻译与清理](https://github.com/21i0/UselessStretcher/pull/5)，作者 AyaYumi。

审查基线 `e48a35df8c32334e8b3167d85be88be6f9f7ce6d`；审查提交 `a35c9e6732a5fe3b22b8271918c4000ef4076500`。收尾时再次读取 GitHub，head 未变，PR 仍为 open、未合并、可合并状态 clean。

## 结论

本次代码审阅和下列本地回归未发现阻塞合并的问题，建议合并。该结论仅覆盖本报告列出的版本与测试，不代表整合包内所有模组、结构和玩偶交互都已穷尽验证。

本轮没有合并、提交、推送或发布，也没有向 GitHub 发送评论或 review。测试在 PR 精确提交的隔离副本内进行，没有修改生产源码或替换 <WORKSPACE> 根目录的现有交付包。

## 变更及升级要求

- Forge 1.20.1 版本号更新为 `20.1.6.5`，网络协议更新为 `22`。客户端和服务端应同步更新。
- 本体最低依赖由包内声明提升为 `20.2.4.9`，并使用其 Apple UI API；旧本体不能继续配用。实际验证使用官方 `useless_mod-20.2.4.9.jar`，已核对官方发布 digest，未使用开发包替代。
- 手杖八标签设置窗口接入共享 UI，补全简体中文、繁体中文和英文配置编辑界面。
- 新增/完善绑定 AE 网络后的源流体采集，包括预算、权限、部分提交记录和接收量不明时的持久隔离。
- 万象担架整库装入小炉、样板总成和被动仓，支持原生菜单、实时刷新、跨维度引用与被动轮转。
- 修复胶囊地图剪裁和深度状态，以及 AE2 陨石中心、包围盒和旧记录迁移。
- 清理旧源码、文档和测试打包规则。两种构建均已检查测试类排除。

## 实际验证

1. `clean build` 成功。27 个 JUnit 测试全部通过，0 失败、0 错误、0 跳过；65 个生产资源 JSON 全部解析成功。
2. 同时启用 `upstreamChecks`、`capsuleChecks`、`dollChecks` 后再次构建成功。普通构建与启用测试源码的构建产物 SHA256 相同；发行包内测试条目为 0，额外混入类为 0。启用测试源码的构建不等于已运行全部玩偶回归。
3. 独立真实 Forge 服务端回归通过：AE 流体采集、真实 AE 发布与执行、三个菜单与 handler、shift-click、被动轮转、实时删除、跨维度引用和 NBT，以及陨石坐标/迁移规则。
4. 相同服务端存档重启后再次通过；世界库、装槽引用和被动游标恢复检查通过。
5. 客户端回归通过：八标签、搜索和输入、窗口拖动/缩放/复位、窄窗口、滑块、三语言配置以及连续 240 次胶囊地图渲染状态检查。人工查看了手杖和中文配置截图，未见明显布局错误。
6. `ae2:meteorite` 在真实客户端完成后台生成、地图渲染，两次进入同一个实例并两次使用物品返回；往返和渲染隔离检查通过。
7. 额外独立解析上述存档的 NBT 和 Anvil 区块，核对实际方块，而非仅采信 ready 状态：房间边界内有 62 个 `ae2:sky_stone_block`、1 个 `ae2:mysterious_cube`、3 个完整石英母岩、2 个有瑕疵石英母岩、2 个破损石英母岩，以及石英块和石英芽。房间 XZ 范围另有 20,736 个基岩方块，等于 144×144 的底面积。实际陨石半径约 2.25，地图中较小的结构与此相符；入口截图朝空地不能据此判断结构缺失。
8. `git diff --check` 通过。审查仓库工作区仍为空，HEAD 保持基线提交。

日志中存在本体可选兼容项缺失及部分本体战利品资源警告，不应描述成“完全无报错”。流体异常提交测试也会故意产生预期警告；未因此出现本轮验证失败。

## 验证边界

本轮没有跑 ATM10 全整合包、所有结构、全部 2i 剧情/收容/武器兼容测试，也没有据此宣布永爱之刃兼容通过。结构实测针对 AE2 陨石；其他结构只能按各自既有证据评价。没有进行多人长时间压力测试。

## 证据与产物

证据均位于本报告目录：

- `changes.diff`、`pr.json`、`pr-final-state.json`、`github-checks.json`：精确审查范围和 GitHub 状态。
- `build-local-cache.log`、`all-checks-build.log`、`junit-results.json`、`resource-validation.json`：构建、单元测试和资源检查。
- `upstream-server.log`、`upstream-server-restart.log`、`upstream-client.log`：真实服务端、同存档重启与 UI 回归。
- `capsule-meteorite-client.log`、`capsule-physical-block-audit.json`、`inspect_capsule_blocks.py`：陨石生成、往返及独立物理存档核对。
- `release-audit.json`、`all-checks-release-audit.json`：发行包版本和测试类排除检查。
- 截图：`source/run/upstream-review-client/screenshots/`、`source/run/capsule-review-client/screenshots/`。

审查构建 JAR 仅保存在归档内：`artifacts/useless_stretcher-20.1.6.5-normal-build.jar`。

大小：13,281,710 字节；SHA256：`79ACE1FBAF16544CA5861C33A7921A6A0FB2336AB6D23D60876FB39CE453DAF7`。

原交付包 `<WORKSPACE>\useless_stretcher-20.1.6.3.jar` 保持不变，SHA256：`FC09162716BE42B2BE7563AFE5DE495A764429AA09470B80BE441D6B0EB196A0`。

## 后续合并记录

用户随后明确授权“合并吧，然后推送到 GitHub”。已于 2026-10-29 11:03:33 UTC 通过 GitHub 合并 PR #5 到 `1.20.1`，合并提交 `d344267a4036b6dcb01a43f4d96d4c5d664798fe`。上文“本轮没有合并”描述的是审查完成时的状态。

合并前再次确认 PR head 未变且 GitHub build 成功；合并后已核对远端分支指向该提交，并将本地审查仓库快进同步至该提交。合并结果的文件树与实际审查和测试的 PR head 完全相同，tree ID 均为 `c309bf913ded9d75a26e8aae1924e56b0a81e5df`。本地工作区保持干净，原交付包哈希未变。未创建 Release。

合并回执与核验记录：`merge-result.json`、`post-merge-local-state.json`、`post-merge-remote-state.json`。合并后 GitHub 构建记录：`post-merge-checks.json`。

合并后的 GitHub 自动构建与测试已于 2026-10-29 11:13:52 UTC 成功完成，结论 `success`，用时约 10 分 14 秒。[构建记录](https://github.com/21i0/UselessStretcher/actions/runs/40862414647)。
