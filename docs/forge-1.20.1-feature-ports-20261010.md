# Forge 1.20.1 / GTL 主线与功能分支移植

生产基线是 Forge 47.1.47、Java 17、AE2 15.4.10 和 Thunderbolt Reborn GTL 2.0.0。原用户工作区保持独立，移植与验证在隔离 worktree 完成。

| 上游快照 | 对应 GTL 分支 |
| --- | --- |
| main `2d8369e1` | `main-1.20.1_GTL` |
| feat/large-overload-factory `ee16ce3d` | `feat/large-overload-factory-1.20.1_GTL` |
| feat/celestweave-armor-art `fee0ad22` | `feat/celestweave-armor-art-1.20.1_GTL` |
| feat/cs-overload-parallel-20261002 `36a3f089` | `feat/cs-overload-parallel-20261002-1.20.1_GTL` |

此主线提交包含天枢有线/无线合成终端、五类真实工作站、过载合金砧、合成报告与缺料收藏/重新计算、EAEP 强制下单等待、增强无线终端和 Useless 寰宇样板编辑边界。三个独立功能分支由主线派生，其具体移植和验证记录随各分支提交。

NBT 持有真实输入，结果槽仅为原版配方预览。Forge 原生 SmithingMenu、AnvilMenu、StonecutterMenu 和 CellWorkbenchBlockEntity 承担消耗、回调、XP 和配置规则。多玩家消费同步失效其他视图；终端关闭不返还输入。无线 WUT 合并预览不修改输入，拒绝同时占用的库存、升级/能源溢出及冲突状态。无线供料扫描全部背包和 Curios 终端，逐次检查链接、范围、能源和菜单身份。

网络使用 Forge SimpleChannel，主线协议为 `gtl-11`；保留原消息顺序并在末尾追加。物品数据组件使用 1.20.1 NBT。可选 AE2WTLib 和 Useless 通过缺失判断和反射边界隔离，避免服务器在未安装库时链接客户端或外部类。AE2 15 的库存快照使用 IStorageService.getCachedInventory。

GTL 的独占规划器、节点缺失 requester、Transfinite/Thunderbolt 接口、闭环种子归属、同一物理 tick 去重和接口回入缓存均保留。完成后的独立 CPU 产物若无法存入 ME，则由 CPU 保留并显示，存储恢复后再次送入。

1.20.1 ExtendedAE 没有上游 1.21 的 crystal_assembler serializer 或 config condition，故未注册该专属配方；本地原有过载处理器配方保留。Forge AdvancedAE 1.3.6 没有上游专属 EMI 类，本地 EMI 过载加工目录已经提供工厂和借用反应配方。JEI 使用其真实原生类别。

客户端穿戴效果、互动界面和 Useless 实装模组运行不能由服务器测试代替；此记录不把编译通过描述为这些路径的完整运行验证。测试日志和临时适配脚本位于忽略的 build/feature-port-20261009，不进入生产 JAR。

主线最终运行验证：工作站 65/65、兼容服务器 80/80、导入/导出行为与压力场景 77/77、能源输出专项 7/7。能源专项只注册了 7 项，不将旧基线测试数量计入本轮。最终 JUnit 1463/1463，adaptiveBatchStress 6/6，build/reobfJarJar 成功。JAR SHA-256：`9ae4edea083cc843ceda0d800ac624d864860bedc67482a1518b36f2e694629a`。
