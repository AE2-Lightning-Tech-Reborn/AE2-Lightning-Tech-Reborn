# AE2 Lightning Tech Reborn — Forge 1.19.2

本分支将 Reborn 的 `1.20.1` 基线移植到 Minecraft 1.19.2、Forge 43.5.2、Java 17 和 AE2 12.9.12。

保留闪电能量、机器与材料链、过载网络、无线样板分发、天枢与闭环合成、星穹织甲和电磁轨道炮。
必须安装同为 1.19.2 的 Thunderbolt Core Reborn；Applied Flux Reborn 独立提供可选的 FE 存储与感应卡。

## 构建

先构建 TB 和 Applied Flux 的 `1.19.2` 分支，再执行：

```sh
./gradlew test build -Pappflux_build_libs=/path/to/Applied-Flux-Reborn/build/libs
./gradlew runGameTestServer -Pappflux_build_libs=/path/to/Applied-Flux-Reborn/build/libs
```

TB 默认取相邻仓库的 `build/libs`，可用 `-Pthunderbolt_build_libs` 覆盖。分发 `build/libs/ae2lt-forge-1.19.2-2.1.2-beta2.jar`，不要分发 `-slim.jar`。

## 兼容边界

客户端验证组合为 JEI 11.68.0.1087、Jade 8.9.2、Curios 1.19.2-5.1.6.4、AE2WTLib 12.9.7-forge。Applied Flux 使用 Reborn 1.19.2-1.3.7-reborn.1。

AE2 12 没有新版原生 EMI 编码处理器，本分支保留自有 EMI 配方展示，样板转移以已验证的 JEI 路径为准。AdvancedAE 专用反应配方及配置页不在此分支提供；Veil 特效走原生渲染回退。AE2 的石英生长加速器与振动仓用于替换新版才存在的对应配方部件。

运行测试覆盖注册机器构造、朝向、接口末槽配置、40 台设备频道、闭环合成、合成状态包、FE 单元掉落和护甲伤害语义。英文 [README](README.md) 包含版本与 API 说明。

保留 [LGPL 3.0 源码许可](LICENSE)、[素材许可](LICENSE_ASSETS.md) 和 [第三方署名](THIRD_PARTY_NOTICES.md)。
