# 万象担架 / Useless Stretcher

《无用之物 / Useless Mod》的附属模组，为合金炉与 AE2 带来通配模具、远程样板工具、时间加速，以及可以陪伴你的 2i 玩偶和探索结构的维度胶囊。

An addon for **Useless Mod** and **Applied Energistics 2**, featuring a wildcard mold block, remote pattern tools, time acceleration, an animated 2i companion, and structure capsules.

## 安装要求 / Requirements

| 项目 / Component | 要求 / Requirement |
| --- | --- |
| Minecraft | **1.21.1** |
| 加载器 / Loader | **NeoForge 21.1.x** |
| Java | **21** |
| 必需前置 / Required | **Useless Mod 1.21.1-2.4.6+**, **AE2 19.2+** |
| 可选前置 / Optional | **GuideME 21.1.17+**, **Curios 9.5.1+** |
| 安装位置 / Installation | 客户端与服务端都安装，并保持相同版本 / Install on both client and server with matching versions |

本页介绍 **1.21.1-1.6.2** 主线。仓库中的 **1.20.1 移植分支单独维护、单独构建**，请按游戏版本选择对应文件，不能混用。

This page describes the **1.21.1-1.6.2** mainline. The **1.20.1 port is maintained and built separately**; its files are not interchangeable with the 1.21.1 build.

## 中文介绍

### 万象万物之块：一个方块管理模具与样板

按机器名称搜索，或用 `@模组ID` 筛选，选择需要的模具和样板。模具选择与样板获取互相独立，支持批量选择、分批获取和重复样板去重。将方块放入小合金炉模具槽或万象模具集散中心，即可使用已启用的模具功能；配方原有的能源、材料和模具数量要求继续生效。

可绑定 AE 网络，编码样板时优先使用网络已有或可合成的材料。使用无用之物 2.4.6+ 原生的 4096 槽容量，目录与大量选择数据保存在世界存档中。

### 万象担架：远程送样板，也能直接装入设备

潜行右键万物之块绑定样板库，再右键目标设备投送样板。也可把绑定后的担架直接放入 **小合金炉、样板总成和被动合成仓** 的样板槽，让设备读取库中的样板。库内容变化会刷新已装载的设备；能源、材料与模具要求不变。

### 荒辰移晷之杖：统一窗口管理加速与附加功能

继承造化垂青之杖的工具功能，增加机器、AE 目标、实体、昼夜与天气加速。支持单体、范围、普通与永久模式，以及加速名单、自动休眠、放置历史和远程回收。执行使用共享预算，实际加速速度取决于服务器负载与目标机器开销。

默认按 **X** 打开带侧边标签的统一窗口；位置、大小、当前标签、侧栏及展开状态会被记住。可配置吸取周围完整源流体的范围与速率，并沿用无用之物本体的无线节点绑定，将流体存入绑定的 AE 网络。还提供自由穿墙、Mekanism 辐射防护等可选功能；相关模组和服务端配置会影响功能是否可用。

### 2i 玩偶：从掌心到身边的陪伴

2i 可以作为实体跟随、摆件、手持或头戴玩偶；安装 Curios 后也可放入有效饰品槽。各种形态共用外观、细臂、尾巴与动画，包含天气反应、撑伞、朋友共伞和飞行跟随。

所有者专属互动面板提供短聊、送礼、摸头、六章分支剧情、章节回顾和图片欣赏。陪伴进度与好感随存档保存。生存模式亲手打树叶有机会获得 2i，默认概率为万分之一，每名玩家每个存档一次；手杖奖励独立按十万分之一抽取。满背包时保留待领取奖励，首次获得 2i 会向服务器公布所有者。

### 维度胶囊：发现布局，保存自己的结构实例

右键打开可移动缩放的窗口，按模组分类、名称或 `@模组ID` 搜索结构。使用结构自身的原生生成流程与运行时数据包规则；实例按来源环境放入自有空白世界的相邻区域，底部由基岩兜底。规划、生成与地图预览显示阶段进度；关闭窗口后继续加载，就绪时弹出通知。

同一个实例可反复往返，重新进入不会重置建筑或刷新已领取战利品；需要新建筑时可生成相邻实例。计划、进度和实例写入世界存档，已经完成的房间保留玩家改建。

**同名结构的变体选择：** 后台采样原生生成计划，按实际模板、片段及相对位置区分布局。原版堡垒遗迹可识别宝藏室、桥梁、居住区与疣猪兽棚；其它结构展示已发现的布局、模板 ID、尺寸与片段边界示意。选择布局后使用保存的原计划生成，不再重新抽取。搜索可以暂停和继续，候选保存在存档中；这是有预算限制的发现过程，不代表穷尽所有随机变体。候选示意图展示片段边界，生成后才有真实方块地图。

结构内容依赖已安装模组与数据包。直接检查原维度 ID、依赖第三方私有系统或执行昂贵原生回调的结构，仍需在对应整合包中验证；大型结构有分批处理与安全限制，不承诺任意结构兼容或零卡顿。

### 游戏内指南

安装 GuideME 后，从 AE2 指南左侧进入 **“万象担架附属”**；也可悬停对应物品按指南阅读键查看。Curios 和 GuideME 均为可选前置。

## English Overview

### Omniversal Myriad Block

Manage enabled molds and encoded patterns in one searchable interface. Filter by machine name or `@modid`, select molds and patterns independently, and request patterns in batches with duplicate detection. The block works in the Small Alloy Furnace's mold slot and the mold distribution center. Existing recipe requirements still apply, including energy, ingredients and mold counts.

Bind an AE network to prefer stored or craftable ingredients when encoding patterns. The addon uses the base mod's native 4096-slot capacity, with large selections and catalogs stored in the world save.

### Useless Stretcher

Bind a Myriad Block's pattern library, then deliver patterns remotely. A bound stretcher can also be installed directly in the pattern slots of the **Small Alloy Furnace, Pattern Assembly and Passive Crafting Chamber**. These devices read the bound library and refresh when its contents change, while retaining their normal operating requirements.

### Chronoshift Staff

Accelerate machines, selected AE targets, entities, the day/night cycle and weather. Configure single targets or areas, timed or permanent operation, acceleration lists, idle throttling and remote management. A shared execution budget limits server load; achieved throughput depends on the target and server workload.

Press **X** by default to open the unified tabbed window. Window geometry, the active tab, sidebar and expanded controls are remembered. Collect complete source-fluid blocks into the staff's bound AE network using configurable range and rate, with the base mod's wireless-node binding. Optional controls include free movement through blocks and Mekanism radiation protection, subject to installed mods and server settings.

### 2i Companion

Keep 2i as a following entity, a placed doll, a handheld companion or headwear. Curios adds accessory-slot support. These forms share the same appearance, slim arms, animated tail and weather reactions, including umbrellas and shared umbrellas between friends.

The owner-only interaction panel includes chat, gifts, petting, a six-chapter branching story, chapter reviews and an image gallery. Companion progress is saved with the world. Breaking leaves in survival can award a doll once per player per save, at a default chance of 1 in 10,000. The staff has its own independent once-per-save reward at 1 in 100,000. Full inventories retain pending rewards, and the first doll acquisition announces its owner to the server.

### Dimension Capsule

Browse structures by mod, name or `@modid` in a movable, resizable window. Capsules use native structure generation and the active datapack rules, placing instances in neighboring regions of dedicated worlds associated with their source environments. Stage progress is visible, loading continues after closing the interface, and a toast announces when entry is ready.

Return to the same saved instance without resetting buildings or collected loot, or create an adjacent instance when you want a new structure. Completed rooms preserve player changes.

**Discover and choose layouts:** background sampling identifies layouts from their real templates and relative piece positions. Vanilla bastions receive treasure, bridge, housing and hoglin-stable labels; other structures show discovered layouts, template IDs, dimensions and a piece-footprint diagram. Generation uses the saved selected plan rather than rerolling it. Searches can be paused and continued, and candidates are saved with the world. Discovery is bounded sampling, not an exhaustive catalog of every random variation. Candidate diagrams show piece boundaries; the generated instance has the actual block map.

Compatibility depends on the installed mods and datapacks. Structures that check a specific dimension ID, depend on private third-party systems or perform expensive callbacks require testing in their target modpack. Batched work and safety limits help manage large structures, but universal compatibility and zero pauses are not promised.

### In-game Guide

With GuideME installed, open AE2's guide and select **Useless Stretcher** in the navigation, or use the guide-reading key while hovering over a supported item. GuideME and Curios are optional.

## 链接 / Links

- [源码与更新 / Source and updates](https://github.com/21i0/UselessStretcher)
- [问题反馈 / Issue tracker](https://github.com/21i0/UselessStretcher/issues)
- [无用之物 / Useless Mod](https://github.com/SorrowMist/UselessMod)

MIT License. Third-party attribution is recorded in the repository's `THIRD_PARTY_NOTICES.md`.
