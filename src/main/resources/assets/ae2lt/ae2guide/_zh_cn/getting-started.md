---
navigation:
  title: 入门指南
  icon: ae2lt:overload_crystal
  parent: index.md
  position: 10
---

# 入门指南

本页按顺序说明从零开始建立第一套闪电产线的完整步骤。AE2 闪电科技面向中后期自动化场景，建议在具备稳定的基础 ME 网络与 FE 产能后再推进主要产线。

## 前置准备

开始前请准备以下基础条件：

* 一个可正常运行的 ME 网络（含 <ItemLink id="ae2:controller" /> 或小型无控制器网络）以及 <ItemLink id="ae2:drive" />、<ItemLink id="ae2:inscriber" />
* 若干 <ItemLink id="ae2:certus_quartz_crystal" />、<ItemLink id="ae2:fluix_block" />、<ItemLink id="ae2:quartz_block" /> 与赛特斯石英母岩
* 至少一个 <ItemLink id="minecraft:lightning_rod" />
* 稳定的 FE 供电来源，用于后续驱动加工机器

## 认识闪电类型

本模组将世界中的雷击分为两类，它们决定了不同转化和收集行为的结果：

* **自然闪电**：由世界雷暴天气自然触发的雷击。可用于精制母岩结构转化，在闪电收集器中产出**极高压闪电**，并可培养槽内的电鸣水晶。
* **人工闪电**：由引雷三叉戟、携带或丢弃的过载水晶，或其他模组召唤产生的雷击。可用于简化母岩结构与地面物品雷击转化，在闪电收集器中产出**高压闪电**。

## 第一步：获取过载水晶与母岩

<ItemImage id="ae2lt:overload_crystal" scale="2" float="left" />

**过载水晶**与**过载水晶粉**是整个模组的起点。常规获取方式是通过 3×3 雷击结构将 AE2 的赛特斯石英母岩转化为过载水晶母岩：

1. 搭建 3×3 同高度平台：中心放置对应等级的赛特斯石英母岩，东、西、南、北四条正边各放 1 个 <ItemLink id="ae2:fluix_block" />。
2. 根据阶段选择四角方块：
   * **简化结构（推荐起步）**：四角放置 <ItemLink id="ae2:quartz_block" />。接受**任意闪电**（人工或自然），将无瑕、有瑕、开裂的赛特斯石英母岩分别降一级转化为有瑕、开裂、损坏的过载水晶母岩。
   * **精制结构（无瑕路线）**：四角放置 <ItemLink id="ae2lt:overload_crystal_block" />。仅接受**自然闪电**，同级转化为对应的过载水晶母岩。
3. 在中心母岩正上方放置 1 个 <ItemLink id="minecraft:lightning_rod" />，等待雷击命中避雷针。转化完成后，外圈 8 个方块会被消耗，避雷针保留。

<GameScene zoom="4" background="transparent">
  <ImportStructure src="assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

> 只有**精制结构 + 自然闪电**能够得到不会因生长衰减的 <ItemLink id="ae2lt:flawless_budding_overload_crystal" />。在拥有首批过载水晶产能前，先用简化结构起步即可。

母岩会在相邻面生长水晶芽（小型 → 中型 → 大型 → 过载水晶簇），受 <ItemLink id="ae2:growth_accelerator" /> 加速。初期没有过载水晶粉时，破坏未成熟的水晶芽即可获得过载水晶粉；破坏完全成熟的过载水晶簇可获得过载水晶（受时运影响）。更多掉落与衰减细节见 [过载水晶](materials/overload-crystal.md)。

## 第二步：雷击转化首批材料与机器

部分起步材料与首台机器需要将原料作为**掉落物**丢在地面上，再让雷击（自然或人工均可）命中以完成转化（具体原料清单与数量可能随整合包调整，请以配方查看器为准）。

默认情况下，将 <ItemLink id="ae2lt:overload_crystal" /> 放在**快捷栏任意槽位或副手**，约每 200 tick（10 秒）会在玩家当前位置召唤一次人工闪电；将过载水晶丢在地面上时，物品实体也会独立计时并约每 200 tick 在自身位置引雷。这两种人工引雷路径**均不要求露天**（旁观模式下不触发；将水晶移出检测槽位会重置计时）。

前期通常依次通过地面雷击转化与压印器制作以下起步物品：

1. **基础材料**：先经雷击转化获取 <ItemLink id="ae2lt:overload_inscriber_press" /> 与 <ItemLink id="ae2lt:overload_alloy" />，在 <ItemLink id="ae2:inscriber" /> 中压印出**未过载的电路板**，经雷击转化为**过载电路板**后，再回到压印器合成为 <ItemLink id="ae2lt:overload_processor" />。
2. **首套闪电设备**：随后经雷击转化获得 <ItemLink id="ae2lt:lightning_collector" />、<ItemLink id="ae2lt:lightning_cell_component_i" /> 与首台 <ItemLink id="ae2lt:lightning_simulation_room" />。

<OptionalSection modId="extendedae">
安装 ExtendedAE 后，也可以通过**电路切片器**与**水晶装配器**简化未过载的电路板和过载处理器的制作流程（具体配方请查阅配方查看器）。
</OptionalSection>

## 第三步：组装存储元件并启动第一套产线

雷击转化产出的是中间材料 <ItemLink id="ae2lt:lightning_cell_component_i" />，无法直接放入驱动器。需要将其与 <ItemLink id="ae2lt:lightning_item_cell_housing" /> 合成（或使用有序配方），制成 <ItemLink id="ae2lt:lightning_storage_component_i" /> 后再装入 <ItemLink id="ae2:drive" />。

<RecipeFor id="ae2lt:lightning_storage_component_i" />

按「收集 → 存储 → 消耗」连接首套最小产线：

1. **收集**：放置 <ItemLink id="ae2lt:lightning_collector" />，在其正上方紧贴放置 1 个 <ItemLink id="minecraft:lightning_rod" />，并接入 ME 网络。雷击落入避雷针周围 3×3×3 范围时，闪电资源会直接写入 ME 网络。
2. **存储**：将组装好的**闪电存储元件 I** 放入 ME驱动器。之后即可在 ME 终端中查看已存入的高压闪电与极高压闪电。
3. **消耗**：放置 <ItemLink id="ae2lt:lightning_simulation_room" />，接入同一 ME 网络并提供 FE 电能。机器在推进加工时消耗 FE，结算成品时从 ME 网络扣除配方所需的闪电。

## 第四步：后续扩展方向

首套闪电产线稳定运行后，可按需求逐步推进：

* **稳定闪电供给**：建造 [特斯拉线圈](lightning/tesla-coil.md) 主动消耗过载水晶粉与 FE 批量制造高压闪电，或使用 [大气电离仪](machines/atmospheric-ionizer.md) 切换世界天气以创造自然雷暴条件。
* **高阶加工产线**：依次解锁 [闪电装配室](machines/lightning-assembly-chamber.md)、[过载处理工厂](machines/overload-processing-factory.md)、[水晶催化器](machines/crystal-catalyzer.md) 与 [挖掘工厂](machines/mining-factory.md)，并制造 [闪电坍缩矩阵](materials/lightning-collapse-matrix.md) 解锁机器的代偿与并行能力。
* **过载网络扩展**：部署 [过载ME控制器](overloaded-network/overloaded-controller.md)、[过载线缆](overloaded-network/overloaded-cable.md)、[无线网络](overloaded-network/wireless-network.md)，以及配备 36 个样板槽的 [过载样板供应器](overloaded-network/overloaded-pattern-provider.md) 与配备 36 个配置槽的 [过载ME接口](overloaded-network/overloaded-interface.md)。
* **天枢与终局装备**：搭建 [天枢物质扭曲矩阵](matrix/matrix-index.md) 与 [天枢超算](tianshu/tianshu-index.md)，前往末地采集 [苍穹材料](materials/firmament-materials.md) 并制作 [苍穹织雷](celestweave.md) 与 [电磁炮](electromagnetic-railgun.md)。
