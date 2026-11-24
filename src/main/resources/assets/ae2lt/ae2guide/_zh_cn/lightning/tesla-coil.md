---
navigation:
  title: 特斯拉线圈
  icon: ae2lt:tesla_coil
  parent: lightning/lightning-index.md
item_ids:
  - ae2lt:tesla_coil
---

# 特斯拉线圈

<ItemImage id="ae2lt:tesla_coil" scale="2" float="left" />

**特斯拉线圈**是占用两格高度的 ME 网络设备，用于主动制造高压闪电或将高压闪电升压为极高压闪电，并直接写入 ME 网络。它与通过 [闪电收集器](lightning-collector.md) 捕获世界落雷属于两条独立的产出路径。

## 两种工作模式

| 模式 | 单份投入 | 加工时长 | 单份产出 | 前置条件 |
|------|----------|---------:|----------|----------|
| **高压模式** | 过载水晶粉 ×2 + 25,000 FE | 5 tick | 1 高压闪电 | 接入具备高压闪电存储空间的 ME 网络 |
| **极高压模式** | 8 高压闪电 + 500,000 FE | 5 tick | 1 极高压闪电 | 矩阵槽中装有 1 枚 <ItemLink id="ae2lt:lightning_collapse_matrix" />，且 ME 网络具备极高压闪电存储空间 |

运作规则：

* **动态批量**：高压模式启动每轮加工时，会根据槽内过载水晶粉数量与 ME 网络剩余闪电容量锁定本轮批量；极高压模式每轮固定将 8 高压闪电转换为 1 极高压闪电。
* **能耗与结算**：两个模式均需经过 5 个推进 tick，FE 在推进过程中逐步扣除；结算时会再次校验输入材料与网络存储空间，随后直接写入 ME 网络。

## 供电方式

特斯拉线圈内置 **16,000,000 FE** 能量缓存，外部 FE 能源线缆接入时按请求量与剩余缓存接收（不设固定单 tick 输入限速）。

<OptionalSection modId="appflux">
安装 Applied Flux 后，若线圈所接入的 ME 网络中存有 FE，线圈也会在满足条件时直接从网络自动补充内部缓存，无需额外铺设外部能源线。
</OptionalSection>

## 红石控制

特斯拉线圈拥有 1 个升级槽，可安装 AE2 <ItemLink id="ae2:redstone_card" />，上下两格均可读取红石信号：

* 暂停时会保留当前已锁定的工作模式、批量与加工进度，恢复后继续完成。
* 若配合 <ItemLink id="ae2:level_emitter" /> 按网络中的闪电库存控制启停，请注意单轮批量按整轮结算，实际写入量可能会略微超过发信器设定阈值。
