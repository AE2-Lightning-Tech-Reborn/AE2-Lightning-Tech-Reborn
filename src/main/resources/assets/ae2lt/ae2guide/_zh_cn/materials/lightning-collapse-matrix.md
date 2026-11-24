---
navigation:
  title: 闪电坍缩矩阵
  icon: ae2lt:lightning_collapse_matrix
  parent: materials/materials-index.md
  position: 60
item_ids:
  - ae2lt:lightning_collapse_matrix
---

# 闪电坍缩矩阵

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

**闪电坍缩矩阵**是本模组的核心高阶物品，放入多种加工机器与传输设备的矩阵槽中即可解锁代偿、增产、升压或高并行模式。

`以高强度合金将庞大的闪电压缩并约束为稳定形态；其能量波形恰与暴躁的过载 TNT 相反。`

> 注意区分名称：**闪电坍缩矩阵**是此处的物品组件，而 [天枢物质扭曲矩阵](../matrix/matrix-index.md) 是用于大规模自动合成的多方块结构。

## 获取途径

闪电坍缩矩阵可在 [闪电模拟室](../machines/lightning-simulation-chamber.md) 或 [过载处理工厂](../machines/overload-processing-factory.md) 中制造（消耗极高压闪电与高额 FE 电能，具体原料、数量与能耗请以配方查看器为准）。

## 各设备中的用途

在机器矩阵槽中使用时，闪电坍缩矩阵**不会被消耗**，取出即失去对应加成；用于升级苍穹转换核心时则会**一次性消耗 1 枚**：

| 目标设备 | 最大装入量 | 效果 |
|----------|-----------:|------|
| [闪电模拟室](../machines/lightning-simulation-chamber.md) / [闪电装配室](../machines/lightning-assembly-chamber.md) | 1 枚 | 极高压闪电不足时，允许以 **4 高压闪电∶1 极高压闪电** 整份代偿 |
| [过载处理工厂](../machines/overload-processing-factory.md) | 32 枚 | 支持 4∶1 补足极高压缺口，且默认每枚提供 8 并行（32 枚达 256 并行）；潜行右击可快速装入 |
| [水晶催化器](../machines/crystal-catalyzer.md) | 1 枚 | 水晶与粉化模式下均使单次产出提升至 **8 倍**（猪咪水晶催化器无矩阵槽） |
| [特斯拉线圈](../lightning/tesla-coil.md) | 1 枚 | 解锁极高压模式，将高压闪电升压为极高压闪电 |
| [挖掘工厂](../machines/mining-factory.md) | 8 枚 | 每枚提供 256 并行上限（无矩阵为 8 块/批） |
| [过载 IO 端口](../overloaded-network/overloaded-io-port.md) | 8 枚 | 按矩阵数量提高每轮尝试次数与单次同种资源搬运上限（8 枚时单次上限达到 64 位整数极值） |
| [苍穹转换核心](firmament-materials.md) | 消耗 1 枚 | 潜行右击原位升级，输入槽容量由 64 提升至 256，每轮批量上限由 1 提升至 16 |

## 观测备忘

> 外场小组曾提交过一份额外的实验报告：矩阵在**掉落在世界中**时会出现一种非预期反应；反应被点燃的场景记在 [过载 TNT](../lightning/overload-tnt.md) 那一页。

产物是一枚不在记录中的元件，可以放入 <ItemLink id="ae2:drive" /> 中解析；这个元件只能被读取一次，不可写入。报告中指出，读取的样板分布极不均匀。多次实验后，实验小组总结出了一份特别的报告。
