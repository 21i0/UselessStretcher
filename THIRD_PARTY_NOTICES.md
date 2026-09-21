# Third-Party Notices

本模组（Useless Stretcher）引用了以下第三方项目的设计思路，并对少量调度实现作了适配改写，在此声明来源与许可。

## JDT Extras (JDTE)

- 仓库：https://github.com/rulanup/JDTE
- 许可：MIT License
- 引用内容：
  - 手杖倍率档位设计（`TimeMultitoolSpeedMode` 的 1 / 2 / 4 / 16 / 256 / 1024 档位概念）。
  - “retained virtual ticks + 每 tick 执行预算 + pending 上限”的优化思路（`TimeAccelerationWorkQueue` / `ExtendedTimeAccelerationManager`）。
  - AE2 多端点按节点身份去重、直接调用 `IGridTickable`、收到 `TickRateModulation.SLEEP` 后停止本轮空转的调度思路（`ExtendedTimeAcceleratorAE2Integration`）。
- 相关代码位置：
  - `src/main/java/com/sorrowmist/useless/stretcher/client/WondrousStaffClient.java`
  - `src/main/java/com/sorrowmist/useless/stretcher/content/acceleration/AccelerationExecutionBudget.java`
  - `src/main/java/com/sorrowmist/useless/stretcher/content/entity/WondrousStaffAcceleration.java`
  - `src/main/java/com/sorrowmist/useless/stretcher/content/entity/WondrousStaffAccelerationEntity.java`

说明：本模组的 MSPT 采样与自适应预算为自行实现；JDTE 0.6.0-pre6 使用固定全局预算，未实现按 TPS/MSPT 自动调频。`jdte_matrix` 也未修改 AE2 TickManager 或移除其 tick 上限。

### JDTE MIT License

```text
MIT License

Copyright (c) 2026 JDTE contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## BeyondDimensions

- 仓库：https://github.com/Frostbite-time/BeyondDimensions
- 许可：MIT License，Copyright (c) 2025 Frostbite-time。
- 参考范围：阅读 `ItemStackKey` / `UnifiedStorage` 的物品与组件键索引设计，用于分析大量物品逐项比较的成本。
- 本项目未逐字复制其实现；样板及挖掘掉落索引使用已有 AE2 `AEItemKey`，模具归属引用计数和 palette 存储自行实现。
- 不包含或要求安装 BeyondDimensions 运行依赖。

## 其它

- 太阳/月亮方向公式来自原版 Minecraft（`LevelRenderer` 天空渲染）。
- AE2 接口（`IGridTickable` 等）来自 Applied Energistics 2。

## 美术素材

- 本模组 v1.3.4 接入的万象担架、荒辰移晷之杖及四种区块维度方块材质由“虾比”制作。
- 荒辰移晷之杖的时运与精准采集模式共用该作者制作的同一套模型和材质。
- 万象万物之块的动画主体复用无用之物（Useless Mod）的“有用锭”纹理，外圈保留本模组的紫色边框。
