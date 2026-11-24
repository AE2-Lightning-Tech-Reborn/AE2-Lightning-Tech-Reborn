---
navigation:
  title: 过载处理器
  icon: ae2lt:overload_processor
  parent: materials/materials-index.md
item_ids:
  - ae2lt:overload_processor
  - ae2lt:overload_circuit_board
  - ae2lt:unoverloaded_circuit_board
  - ae2lt:overload_inscriber_press
---

# 过载处理器

<ItemGrid>
  <ItemIcon id="ae2lt:overload_processor" />
  <ItemIcon id="ae2lt:overload_circuit_board" />
  <ItemIcon id="ae2lt:unoverloaded_circuit_board" />
  <ItemIcon id="ae2lt:overload_inscriber_press" />
</ItemGrid>

**过载处理器**是制造多数过载机器与网络设备的基础电路元件，需要按步骤压印并经过载转化完成。

## 基础制造流程

1. **获取过载压印模板**：将逻辑压印模板、工程压印模板与运算压印模板丢在地面经任意雷击转化获得（具体原料与数量以配方查看器为准）。
2. **压印未过载的电路板**：在 AE2 <ItemLink id="ae2:inscriber" /> 中使用**过载压印模板**压印**过载水晶**。
3. **转化为过载电路板**：通过地面雷击转化，或在 [闪电模拟室](../machines/lightning-simulation-chamber.md) 中配合过载水晶粉、FE 与高压闪电加工。
4. **合成过载处理器**：在 AE2 压印器中将**过载电路板**、红石粉与硅板组合。

<RecipeFor id="ae2lt:unoverloaded_circuit_board" />

<RecipeFor id="ae2lt:overload_processor" />

## 高效批量路线

* **过载处理工厂**：可在 [过载处理工厂](../machines/overload-processing-factory.md) 中跳过压印步骤，直接使用过载水晶块、红石块与硅块配合 FE 和高压闪电批量生产（具体数量与能耗请查阅配方查看器）。

<OptionalSection modId="extendedae">
* **ExtendedAE 联动**：安装 ExtendedAE 后，可用**电路切片器**直接将过载水晶块切为**未过载的电路板**，在配置允许时还可用**水晶装配器**批量组装**过载处理器**。
</OptionalSection>

## 物品一览

| 物品 | 说明 |
|------|------|
| 过载压印模板 | 在压印器中用于压印未过载的电路板 |
| 未过载的电路板 | 中间产物，需经雷击转化或闪电模拟室加工为过载电路板 |
| 过载电路板 | 与红石粉、硅板在压印器中合成为过载处理器 |
| 过载处理器 | 成品处理器，广泛用于机器、网络设备与高阶部件配方 |
