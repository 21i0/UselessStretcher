---
navigation:
  title: 万象担架
  icon: useless_stretcher:useless_stretcher
  parent: useless_stretcher/index.md
  position: 20
item_ids:
  - useless_stretcher:useless_stretcher
---

# 万象担架

<ItemImage id="useless_stretcher:useless_stretcher" scale="5" />

万象担架负责把万物之块中获取的 AE 样板送入无用之物的样板设备。担架本身不消耗样板，也不需要每次重新扫描机器。

## 合成

<RecipeFor id="useless_stretcher:useless_stretcher" />

无序合成：万象万物之块和一个 AE2 空白样板。

## 绑定万物之块

1. 先在万物之块界面获取需要的样板。
2. 手持担架，对准万物之块并按住 Shift 右键。
3. 担架会复制方块中的样板索引和已绑定的 AE 网络引用。

## 送入目标

手持已绑定的担架右键以下目标即可送入样板：

* 无用之物的样板总成（ME Pattern Assembly）。
* 被动合成仓（Passive Crafting Hatch）。
* 小合金炉或兼容的样板容器。

对准目标时会显示绿色线框。目标槽位已满时会关闭相关界面，并显示“样板槽位已满”的大号提示；担架和万物之块不会因此损坏。

样板总成的大容量数据不会写进掉落物。挖掉含有大量样板的总成时，服务端保存完整内容，掉落物只携带 UUID；重新放置会自动恢复。旧版本遗留的总成数据也会在放置时迁移。

## 与 AE2 的关系

这里的“样板”是 AE2 的编码样板数据，不是 AE2 空白样板物品。万物之块负责按机器获取并保存样板，担架负责移动和批量送入；AE2 仍然负责网络库存、合成计算和样板执行。

### 绑定材料来源

绑定 AE 网络后，获取样板会优先选择网络中已有或可合成的物品、流体和标签材料。未绑定时使用万物之块附近的 AE 网络。没有 AE 网络时，仍可获取不依赖网络材料的配方，但不能使用网络库存来填充样板输入。
