---
navigation:
  title: 闪电装配室
  icon: ae2lt:lightning_assembly_chamber
  parent: machines/machines-index.md
item_ids:
  - ae2lt:lightning_assembly_chamber
---

# 闪电装配室

<ItemImage id="ae2lt:lightning_assembly_chamber" scale="2" float="left" />

**闪电装配室**用于执行独立的多原料装配配方，拥有 9 个输入槽，承担高阶机器、过载网络核心方块与高级部件的组装任务。

## 规格与运行机制

* **物品槽位**：9 个输入槽、1 个输出槽，每槽堆叠上限 **8,192**；另设 1 个矩阵槽（上限 1 枚）。
* **能量缓存**：内置 **20,000,000 FE**。
* **升级支持**：最多安装 4 张 AE2 <ItemLink id="ae2:speed_card" />（加速卡）以提升每 tick 加工速率。
* **独立配方集**：闪电装配室运行独立的无序计数装配配方，与闪电模拟室配方不通用；包括闪电模拟室本身也可以在装配室中自动化生产（具体配方与能耗以配方查看器为准）。
* **矩阵代偿**：矩阵槽装入 1 枚 <ItemLink id="ae2lt:lightning_collapse_matrix" /> 后，当网络中极高压闪电不足时，极高压配方可按 **4 高压闪电∶1 极高压闪电** 整份代偿结算。
* **自动化**：支持输出面配置与自动弹出，与闪电模拟室共用相同的配方锁定、结算重检与内存卡矩阵配置规则。

<OptionalSection modId="appflux">
## 网络 FE 补能

安装 Applied Flux 后，接入存有 FE 的 ME 网络时会自动补充内部 20,000,000 FE 缓存。
</OptionalSection>
