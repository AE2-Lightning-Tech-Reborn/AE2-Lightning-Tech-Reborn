---
navigation:
  title: 苍穹材料
  icon: ae2lt:firmament_alloy_ingot
  parent: materials/materials-index.md
item_ids:
  - ae2lt:firmament_dust
  - ae2lt:firmament_essence
  - ae2lt:firmament_mixture
  - ae2lt:firmament_alloy_ingot
  - ae2lt:firmament_superconducting_wire
  - ae2lt:inactive_firmament_spirit_core
  - ae2lt:firmament_spirit_core_oculus
  - ae2lt:firmament_spirit_core_core
  - ae2lt:firmament_spirit_core_conduit
  - ae2lt:firmament_spirit_core_stride
---

# 苍穹材料

<ItemGrid>
  <ItemIcon id="ae2lt:firmament_dust" />
  <ItemIcon id="ae2lt:firmament_essence" />
  <ItemIcon id="ae2lt:firmament_mixture" />
  <ItemIcon id="ae2lt:firmament_alloy_ingot" />
  <ItemIcon id="ae2lt:firmament_superconducting_wire" />
  <ItemIcon id="ae2lt:inactive_firmament_spirit_core" />
</ItemGrid>

**苍穹材料**是面向末地的高阶精炼路线，用于制造 [苍穹织雷](../celestweave.md) 护甲与 [电磁炮](../electromagnetic-railgun.md)。

## 第一步：在末地采集苍穹粉

1. 前往**末地**（`minecraft:the_end`），在世界最高可建造层铺设承载线缆（面板宿主方块满足 `Y + 1 ≥ 当前世界建造上限`）。
2. 将 <ItemLink id="ae2:annihilation_plane" />（ME破坏面板）**朝上**安装在线缆顶面，并接入处于活动状态且有可用存储的 ME 网络。
3. 每块满足条件的破坏面板独立计时，每 **200 tick（10 秒）** 尝试向 ME 网络写入 1 份**苍穹粉**（受网络供能与剩余存储空间限制）。

> **批量铺设**：手持 ME 破坏面板从顶面**潜行右击**线缆，会在同层连通的线缆上横向搜索至多 8 格范围、自动避开已有部件，一次最多铺设 64 块面板（按实际成功铺设数量消耗物品）。该操作仅简化铺设，各面板的频道、供能与产出周期仍独立计算。

## 第二步：苍穹星舰与苍穹转换核心

**苍穹转换核心**自然生成于末地的**苍穹星舰**结构中，并且**仅在该结构部件范围内**才能执行转换：

* **手动与自动化交互**：手持材料右击核心即可放入，空手右击优先取出产物；物品管道或物流设备也可从任意面自动输入与提取。核心自身运行**不消耗 AE、FE 或闪电**。
* **默认与升级规格**：
  * **标准状态**：输入与输出槽容量均为 64，每轮转换 **1 份**。
  * **原位升级**：手持 <ItemLink id="ae2lt:lightning_collapse_matrix" /> 对核心**潜行右击**，一次性消耗 1 枚矩阵将其原位升级（保留现有库存与进度）。升级后输入槽上限提高至 **256**（输出槽仍为 64），每轮最多并行转换 **16 份**（受实际输入量与输出空间限制）。

## 逐级精炼路线

各阶苍穹材料分别由**苍穹转换核心**与 [过载处理工厂](../machines/overload-processing-factory.md) 交替加工完成（具体材料数量、流体用量与能耗可能随整合包调整，请以配方查看器为准）：

| 产物 | 加工设备 | 主要路线 |
|------|----------|----------|
| **苍穹源质** | 苍穹转换核心 | 由苍穹粉直接转化 |
| **苍穹混合物** | 过载处理工厂 | 由苍穹粉配合辅料、熔岩、FE 与高压闪电加工 |
| **苍穹合金锭** | 苍穹转换核心 | 由过载合金锭、苍穹混合物与下界合金碎片转化 |
| **苍穹超导丝** | 过载处理工厂 | 由过载水晶粉、苍穹源质等辅料配合 FE 与高压闪电加工 |
| **四件苍穹灵核** | 苍穹转换核心 | 将失活灵核活化为四件对应部件的苍穹灵核 |

## 失活灵核与四件苍穹灵核

<ItemGrid>
  <ItemIcon id="ae2lt:firmament_spirit_core_oculus" />
  <ItemIcon id="ae2lt:firmament_spirit_core_core" />
  <ItemIcon id="ae2lt:firmament_spirit_core_conduit" />
  <ItemIcon id="ae2lt:firmament_spirit_core_stride" />
</ItemGrid>

* **失活灵核来源**：自然生成的苍穹转换核心有 **50%** 概率初始预放 1 枚失活灵核；末地城宝藏箱也有 **15%** 概率包含失活灵核。
* **活化结果**：在苍穹转换核心中投入 1 枚失活灵核（耗时 200 tick），可同时产出全部四件苍穹灵核，分别对应苍穹织雷护甲的四个部位：
  * **苍穹灵核·瞳** → 苍穹织雷·瞳（头盔）
  * **苍穹灵核·心** → 苍穹织雷·心（胸甲）
  * **苍穹灵核·径** → 苍穹织雷·径（护腿）
  * **苍穹灵核·阶** → 苍穹织雷·阶（靴子）
