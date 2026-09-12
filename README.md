# 万象担架 · Useless Stretcher

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-3C8527)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-EA6E1E)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

《无用之物 / Useless Mod》的附属模组：把模具变成万能通配、把样板投送变成远程，
样板与被动仓扩到 **4096 槽**，新增 **四联 / 九联区块维度**方块，
以及一根能加速机器、AE、动物、昼夜与引雷的 **荒辰移晷之杖**。

An addon for **Useless Mod**: a wildcard mold block, remote pattern delivery,
**4096** pattern slots, four chunk-dimension blocks, and a time-acceleration staff.

---

## 环境要求 / Requirements

| 项目 | 要求 |
|---|---|
| Minecraft | 1.21.1 |
| Mod 加载器 | NeoForge 21.1.x（构建于 21.1.219） |
| Java | **21** |
| 必需前置 | [无用之物 / Useless Mod](https://github.com/SorrowMist/UselessMod)（≥ `1.21.1-2.2.4`）、[AE2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2)（≥ `19.2`） |
| 安装位置 | 客户端 **与** 服务端都要装 |

> ⚠️ 本模组通过 Mixin 修改了无用之物的内部实现，**两者版本需要配套**。
> 无用之物大更新后若无法启动，请更新本模组到对应版本。

## 主要内容 / Features

### 万象万物之块（Omniversal Myriad Block）— 通配模具
一个能"携带"任意多个模具的方块：勾选后，对合金炉而言它同时满足这些模具的任意需求。
可绑定指定 AE 网络（只记录 UUID），不绑定时自动使用附近 8 格内的网络。

### 万象担架（Useless Stretcher）— 远程样板工具
远程写入 / 读取方块里的样板；按模组一键拉取该模组所有机器的样板；
按锚点批量取 / 移除一段模具；重复样板自动去重；标签材料优先选用 AE 里已有或可合成的。

### 4096 槽扩容 + 分页界面
样板总成与被动合成仓扩到 4096 格，分页每页 90 格（10×9）。
老存档里的 540 槽数据会**原地升级**，方块不需要重新放置。

### 四个维度方块
四联 / 九联区块维度，各带"偶数中心"与"奇数中心"变体，四种摆法各有配方。
地板方块支持黑白名单配置（含 `modid:*` 通配），被禁用的方块回退为默认发光塑料。

### 荒辰移晷之杖（Chronoshift Staff）
由「万象担架」+「造化垂青之杖」合成，完整继承造化垂青之杖的功能，并新增：

- **Shift + 右键**：加速机器 / AE 节点；加速引雷（对避雷针，雷暴时引来真实闪电）；对太阳月亮可加速昼夜
- **Shift + 左键**：催熟幼年动物 / 缩短成年动物繁殖冷却
- **Shift + 滚轮**：切换倍率 关 / x2 / x4 / x16 / x32 / x64 / x128 / x256 / x512 / x1024
- **G 轮盘**：加速总开关（关掉后手杖完全不参与加速）
- **独立按键**：循环切换普通模式（30 秒、不降频）/ 永久模式（动态降频）/ 永久模式（无休眠降频），默认未绑定
- 拿起手杖可看到正在被加速的机器高亮框（满速工作时流光溢彩，动态降频时固定金色）

### 其它
- 内置 8 个工作台配方（龙息、鞘翅、海龟鳞片、附魔金苹果、凋零玫瑰、潜行壳等）
- 配置项：EIO 半自磨机磨珠配方屏蔽、高亮框透视、维度地板黑白名单、空闲降频
- 加速采用"待执行 tick 缓存"（每游戏刻最多 256 次），高倍率不会卡服

## 配置文件 / Config

`config/useless_stretcher-common.toml`（改动后重启游戏，说明均为中文）

```toml
[acceleration]
    idle_throttle = true      # 空闲机器自动降频省性能（有活动立刻回满速）；永久（无休眠降频）模式会忽略此项

[recipe_compat]
    hide_enderio_grinding_balls = true   # 屏蔽 EIO 半自磨机磨珠的重复配方

[highlight]
    highlight_see_through = false        # 机器高亮框是否透视

[dimension_floor]
    blacklist = []            # 四联/九联维度地板方块黑名单（支持 modid:*）
    whitelist = []            # 非空时只允许名单内的方块
```

## 从源码构建 / Building

```bash
# 需要 JDK 21
./gradlew build          # 产物在 build/libs/
```

编译需要把下面两个 jar 放进 `libs/compile/`（它们不在 Maven 上，且只能各放一份）：

- `useless_mod-<版本>.jar`（无用之物本体）
- `appliedenergistics2-<版本>.jar`（AE2）

## 许可 / License

[MIT](LICENSE)。第三方代码与素材的来源声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 相关链接 / Links

- 源码：https://github.com/21i0/UselessStretcher
- 问题反馈：https://github.com/21i0/UselessStretcher/issues
- 下载：https://github.com/21i0/UselessStretcher/releases
- 父模组：无用之物 / Useless Mod — https://github.com/SorrowMist/UselessMod
