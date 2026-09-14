# Third-Party Notices

本模组（Useless Stretcher）引用了以下第三方项目的设计思路（非代码拷贝），在此声明来源与许可。

## JDT Extras (JDTE)

- 仓库：https://github.com/rulanup/JDTE
- 许可：MIT License
- 引用内容：
  - 手杖倍率档位设计（`TimeMultitoolSpeedMode` 的 1 / 2 / 4 / 16 / 256 / 1024 档位概念）。
  - “retained virtual ticks + 每 tick 执行预算 + pending 上限”的优化思路（`TimeAccelerationWorkQueue` / `ExtendedTimeAccelerationManager`）。
- 相关代码位置：
  - `src/main/java/com/sorrowmist/useless/stretcher/client/WondrousStaffClient.java`
  - `src/main/java/com/sorrowmist/useless/stretcher/content/entity/WondrousStaffAccelerationEntity.java`

## 其它

- 太阳/月亮方向公式来自原版 Minecraft（`LevelRenderer` 天空渲染）。
- AE2 接口（`IGridTickable` 等）来自 Applied Energistics 2。

## 美术素材

- 本模组 v1.3.4 接入的万象担架、荒辰移晷之杖及四种区块维度方块材质由“虾比”制作。
- 荒辰移晷之杖的时运与精准采集模式共用该作者制作的同一套模型和材质。
- 万象万物之块的动画主体复用无用之物（Useless Mod）的“有用锭”纹理，外圈保留本模组的紫色边框。
