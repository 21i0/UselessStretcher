# 1.21.1-1.6.2 — Capsule Variants / 胶囊变体

**Network protocol 23: update BOTH client and server, including older files also labeled 1.6.2. Minecraft 1.21.1 / NeoForge / Java 21 only. Requires Useless Mod 1.21.1-2.4.6+ and AE2 19.2+.**

- Added background layout discovery for structures with one registry name and multiple layouts. Filter candidates, pause/resume searching, and inspect piece-footprint previews.
- Vanilla bastions are identified as treasure, bridge, hoglin-stable or housing. Generation uses the selected saved native plan, with candidates and progress persisted in the world. Selecting it again returns the same instance.
- Fixed adjacent generation potentially clearing existing rooms. Existing buildings, chests, entities and scheduled ticks are protected.
- Verified native treasure loot and magma-cube spawner, recovery across two process restarts, neighbor preservation and two stable round trips. Client UI and GuideME checks passed. Discovery uses bounded sampling and does not exhaust every variation or guarantee every modpack.
- Preserves the earlier 1.6.2 map-flicker, AE2 meteorite geometry and 2i cold-animation fixes. Existing worlds do not need to be reset.

## 中文

**协议升级为 23，客户端与服务端必须同时替换，即使旧包也叫 1.6.2。只适用于 Minecraft 1.21.1 / NeoForge / Java 21，需要无用之物 1.21.1-2.4.6+、AE2 19.2+。**

- 胶囊新增同名结构变体后台发现与选择，支持筛选、暂停继续和片段示意图。
- 原版堡垒识别宝藏室、桥梁、疣猪兽棚和居住区，选中后使用保存的原生计划。候选和搜索进度随存档保存，重复选择回到同一实例。
- 修复相邻结构生成时可能清理旧建筑的问题，保护已有建筑、箱子、实体和计划刻。
- 实测宝藏室原生战利品、岩浆怪刷怪笼、两次进程重启恢复、邻室保护及两次稳定往返；客户端界面和 GuideME 检查通过。采用有限采样，未穷尽全部变体和整合包兼容。
- 保留此前 1.6.2 的地图闪烁、AE2 陨石完整高度和 2i 鼻涕方向修复；无需重置旧存档。

Source commit: [27d55ce](https://github.com/21i0/UselessStretcher/commit/27d55ce41a3e7d053abf1265125bd38b8fc835ee)

SHA256: `D6F5E4F8CCA14A1127EE00D23883E52C1A7D6F06BCC8F7F9BE77D5AA18B94D83`
