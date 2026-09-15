# 万象担架 · Useless Stretcher

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-3C8527)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-EA6E1E)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

《无用之物 / Useless Mod》的附属模组：把模具变成万能通配、把样板投送变成远程，
样板与被动仓在旧版前置上扩到 **4096 槽**（无用之物 2.3.7+ 使用上游原生实现），新增 **四联 / 九联区块维度**方块，
以及一根能完整加速机器、AE、实体、昼夜、天气与云层，并可放置独立范围的 **荒辰移晷之杖**。

An addon for **Useless Mod**: a wildcard mold block, remote pattern delivery,
**4096** pattern slots with legacy compatibility (native in Useless Mod 2.3.7+), four chunk-dimension blocks, and a time-acceleration staff.

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
方块内框由无用之物“有用锭”的两帧材质放大铺满，并保留本模组原有的紫色边框；
放置、手持、掉落及容器物品栏中均使用同一套动画资源。

### 万象担架（Useless Stretcher）— 远程样板工具
远程写入 / 读取方块里的样板；按模组一键拉取该模组所有机器的样板；
开启模具和获取样板分别支持按锚点 Shift 区间选择；重复样板自动去重；
标签材料优先选用 AE 里已有或可合成的。

### 4096 槽兼容 + 分页界面
旧版无用之物由本模组将样板总成与被动合成仓扩到 4096 格，分页每页 90 格（10×9）；
无用之物 2.3.7 及更新版本已原生提供容量，本模组自动跳过旧版扩容 Mixin。
老存档里的 540 槽数据会**原地升级**，方块不需要重新放置。
样板总成被挖掉时，完整库存保存在服务端，掉落物仅携带 UUID 引用；
旧版物品会自动迁移，避免大容量样板 NBT 超过原版实体同步包上限。

### 四个维度方块
四联 / 九联区块维度，各带"偶数中心"与"奇数中心"变体，四种摆法各有配方。
地板方块支持黑白名单配置（含 `modid:*` 通配），被禁用的方块回退为默认发光塑料。
四个固定时间维度会明确报告为白天、非夜晚且无雨无雷，太阳能设备可像在无用之物维度中一样工作；
已按 Dyson Cube Project 的射线接收器和电磁轨道发射器判定完成兼容。

### 荒辰移晷之杖（Chronoshift Staff）
由「万象担架」+「造化垂青之杖」合成，完整继承造化垂青之杖的功能，并新增：

- **Shift + 右键方块/实体**：加速机器、AE 节点或非玩家实体的完整 tick；Mob 加速期间可关闭 AI
- **瞄准日月 Shift + 右键**：同步加速昼夜、天气状态与原版云层；对避雷针可加速引来真实闪电
- **Shift + 滚轮**：切换倍率 关 / x2 / x4 / x16 / x32 / x64 / x128 / x256 / x512 / x1024
- **G 按钮配置页**：加速总开关（关掉后手杖完全不参与加速）
- **X 键**：打开文字清晰的紧凑按钮式加速配置，可直接选择倍率及普通 / 永久 / 永久（无休眠降频）模式；右上角“时间加速”开关与 G 配置页同步
- 拿起手杖可看到正在被加速的机器高亮框（满速工作时流光溢彩，动态降频时固定金色）
- **范围加速**：X/Y/Z 沿用减号/加号调节并在中间加入原版滑块，范围为 1~15 格；透明旋转钟可与机器同格；每范围独立倍率、缓存、加速名单及休眠名单
- **名单标记模式**与范围放置互斥，只能修改玩家本人已开启范围内的机器：右键切换单格，Ctrl + 右键切换范围内全部同类型机器；再次操作即可取消
- 加速黑/白名单决定机器是否参与范围加速，休眠黑/白名单独立决定机器是否允许空闲降频；两个名单按钮兼作当前编辑目标，右侧分别显示已标记数量
- 标记期间在动作栏上方的独立位置持续显示当前名单，机器表面显示对应红/绿名单文字和流彩框；退出标记模式后才恢复普通右键和加速
- 放置历史按时间记录维度和坐标，可远程启停或确认回收；“关”只暂停并保留配置，“回收”才永久移除范围和时间流逝标记
- 放置预览与已放置范围使用同一套流彩线框；所有范围共享服务器级执行预算，极端负载时工作顺延而不阻塞服务器
- 实体加速会在目标头顶显示随实体移动的世界内小进度条；普通模式显示剩余时间，永久模式显示满条与无限标记；机器表面进度条已外移以避免贴图闪烁
- 每名玩家在每个存档第一次把手杖拿到主手或副手时，会用大标题提示“手杖的加速UI为X”；之后不重复显示
- 时运与精准采集模式共用虾比制作的手杖模型和材质

### 其它
- 内置 11 个原版物品工作台配方（龙息、鞘翅、幻翼膜、海龟鳞片、附魔金苹果、凋零玫瑰、潜影壳等）
- 每玩家每存档有一次默认十万分之一的树叶掉手杖彩蛋，支持原版及模组树叶（含未正确加入树叶标签的常见实现），满包时奖励会安全跟随，并有紫色隐藏挑战
- 配置项：实体加速关闭 AI、树叶掉落概率、EIO 磨珠配方屏蔽、高亮框透视、维度地板名单、空闲降频
- x512/x1024 执行完整档位；高倍率和大范围会真实增加 tick 工作量，建议配合名单及休眠降频

## 配置文件 / Config

`config/useless_stretcher-common.toml`。游戏内从“模组 → 万象担架 → 配置”即可直接修改，点页面底部“完成/保存”后写入文件；
除配方目录过滤需要重载世界/重启外，其余运行时读取的选项立即生效，界面说明均为中文。

```toml
[acceleration]
    idle_throttle = true      # 空闲机器自动降频省性能（有活动立刻回满速）；永久（无休眠降频）模式会忽略此项
    entity_disable_ai = true  # 实体完整加速时关闭 Mob AI，效果结束后恢复

[staff_leaf_drop]
    enable = true             # 每玩家每存档一次的树叶掉手杖彩蛋
    probability = 0.00001     # 默认十万分之一，范围 0~1

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
