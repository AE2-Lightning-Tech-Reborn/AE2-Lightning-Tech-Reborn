---
navigation:
  title: 闪电模拟室
  icon: ae2lt:lightning_simulation_room
  parent: machines/machines-index.md
item_ids:
  - ae2lt:lightning_simulation_room
---

# 闪电模拟室

<ItemImage id="ae2lt:lightning_simulation_room" scale="2" float="left" />

**闪电模拟室**是第一台进入闪电加工体系的机器，用于在受控环境下执行 3 输入以内的无序计数闪电配方，替代手工地面雷击转化并生产中后期材料。

## 获取方式

* **起步雷击转化**：将配方所需原料作为掉落物丢在地面，经任意雷击转化得到第一台闪电模拟室。
* **机器自动生产**：后续可在 [闪电装配室](lightning-assembly-chamber.md) 中批量合成（具体材料与能耗请查阅配方查看器）。

## 规格与运行机制

* **物品槽位**：3 个输入槽、1 个输出槽，每槽堆叠上限 **8,192**；另设 1 个矩阵槽（上限 1 枚）。
* **能量缓存**：内置 **20,000,000 FE**，可通过外部能源线缆供电。
* **升级支持**：最多可安装 4 张 AE2 <ItemLink id="ae2:speed_card" />（加速卡）以提高每 tick 最大加工 FE 消耗速率。
* **配方锁定与结算**：检测到匹配的无序配方后锁定加工；推进过程消耗内部 FE，进度完成时重新校验输入材料、ME 网络中的闪电库存与输出槽空间，条件满足后扣除对应闪电并产出成品。
* **矩阵代偿**：在矩阵槽中放入 1 枚 <ItemLink id="ae2lt:lightning_collapse_matrix" /> 后，若执行要求极高压闪电的配方且网络中极高压闪电不足，机器会改为按 **4 高压闪电∶1 极高压闪电** 整份代偿消耗高压闪电。
* **自动化**：可在界面中开启自动弹出并设定输出面，配合样板供应器直接将产物回传至 ME 网络；矩阵槽对常规物品自动化不可见，防止误抽。

<OptionalSection modId="appflux">
## 网络 FE 补能

安装 Applied Flux 后，若模拟室接入的 ME 网络中存有 FE，机器会自动从网络为内部 20,000,000 FE 缓存补能。
</OptionalSection>
