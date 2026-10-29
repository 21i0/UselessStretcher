# 万象担架 · Useless Stretcher

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3C8527)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.3.10-EA6E1E)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

《无用之物 / Useless Mod》的 Forge 1.20.1 附属模组，提供通配模具、远程样板工具、荒辰移晷之杖、维度胶囊和 2i 玩偶与剧情。

A Forge 1.20.1 addon for **Useless Mod**, with wildcard molds, remote pattern libraries, a time-acceleration staff, dimension capsules, and the 2i doll and story.

## 环境要求 / Requirements

| 项目 | 要求 |
|---|---|
| Minecraft | 1.20.1 |
| 加载器 | Forge 47.3.10+ |
| Java | **17** |
| 必需前置 | [Useless Mod](https://github.com/SorrowMist/UselessMod) `20.2.4.9` 或兼容的 `20.x` 构建（必须包含 Apple 界面 API）、[AE2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) `15.4.8` 及其 GuideME `20.x` 前置 |
| 安装位置 | 客户端与服务端均需安装 |
| 网络协议 | **22**，两端必须使用相同版本 |

本模组通过 Mixin 接入本体内部实现，两者版本需要配套。更新前备份世界与原 JAR；当前 `20.1.6.x` 更新不需要重置存档。已移除的旧四联／九联维度不提供旧世界兼容。

## 主要功能 / Features

- **万象万物之块**：同时匹配选中的多个模具，按模具物品命名空间分类；可绑定 AE 网络。大量选择保存在世界数据中，物品仅携带引用。
- **万象担架**：远程读写与批量投送样板，按机器检索、去重并共享配方缓存。可装入小合金炉、样板总成和被动仓的样板槽，整库随库变更同步；沿用本体原生 4096 槽容量。
- **荒辰移晷之杖**：加速机器、AE 节点、非玩家实体及昼夜天气；支持范围、名单、放置历史和空闲降频。Shift + 滚轮切换倍率，X 打开八标签共享设置窗口，Ctrl + Tab／Ctrl + Shift + Tab 切换标签。可选流体采集仅抽取完整静止源并遵守领地权限，默认关闭；接收量不明的异常提交会保留记录并停止自动重放。
- **维度胶囊**：按模组分类与搜索结构，显示生成进度、可旋转地图并支持往返。地图来自实际已放置的方块，不是生成前模板缩略图。结构计划与实例随世界保存。
- **2i 玩偶与剧情**：动态 3D 玩偶、六章有声分支对话、好感互动与独立星空庭院；支持摆件、手持、头戴与 Curios。
- **其它**：树叶掉手杖彩蛋、独立传送按键，以及 Occultism／RFToolsBuilder 等可选加速兼容。有用锭装甲功能与配方默认关闭。

详细用法见游戏内 AE2 指南的“万象担架附属”。悬停万物之块、担架或手杖并按 GuideME 阅读键可跳转到对应页面；按键显示跟随游戏改键设置。

界面直接复用前置本体的 Apple UI API，保留原生输入、容器槽位、JEI、地图和剧情绘制，不另行捆绑一套 UI 资源。

## 配置 / Configuration

服务端配置为 `useless_stretcher-server.toml`：

- 单机：`saves/<世界>/serverconfig/`。
- 专用服务器：`<level-name>/serverconfig/`，默认 `world/serverconfig/`。
- 新世界模板：`defaultconfigs/`，不会覆盖已有世界的设置。

单机／局域网宿主可从“模组 → 万象担架 → 配置 → 服务端配置”修改，点击“保存本页”写入并同步在线玩家；“完成”关闭界面。专用服务器由服主编辑世界级配置。

配置界面支持简体中文、繁体中文与英文。布尔输入保留 `true`／`false`，悬停说明提供含义与原始配置路径。需要重载世界或重启的设置会在说明中标明，其余运行时选项即时读取。

## 构建与测试 / Building and testing

使用 Java 17，默认从本体 GitHub Release 获取并映射 `20.2.4.9`：

```shell
./gradlew build
```

Windows 使用 `gradlew.bat`，产物位于 `build/libs/`。开发时可显式传入 `-PuselessModDevJar=/path/to/build/devlibs/useless_mod-20.2.4.9.jar`；发布包不能当作未映射的开发包。离线 Maven 仓库可通过 `-PuselessModRepository=/path/to/maven` 指定。构建不会自动使用相邻工作区或本地整合包。

JUnit 与发行包检查随 `build` 执行。真实 Forge 回归需显式启用，使用 `run/` 下的独立测试目录：

| 开关 | 任务 |
|---|---|
| `-PupstreamChecks` | `runUpstreamServerCheck`、`runUpstreamClientCheck`（流体、整库装槽、陨石、UI 与配置翻译） |
| `-PcapsuleChecks` | `runCapsuleCheck`、`runCapsuleClientCheck`（胶囊生成、保存恢复、地图与往返） |
| `-PdollChecks` | `runDollServerCheck`、`runDollClientCheck`（玩偶与剧情）；收容检查另加 `-PdollCaptureChecks` |

运行服务端检查前需在对应隔离目录确认 Minecraft EULA。检查类与测试数据始终排除出发行 JAR。可用 `python tools/audit_forge_release.py build/libs/useless_stretcher-<版本>.jar` 独立审计发行包。

## 许可与链接 / License and links

[MIT](LICENSE)。第三方代码与素材声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

- [源码](https://github.com/21i0/UselessStretcher) · [问题反馈](https://github.com/21i0/UselessStretcher/issues) · [下载](https://github.com/21i0/UselessStretcher/releases)
- [Useless Mod](https://github.com/SorrowMist/UselessMod)
