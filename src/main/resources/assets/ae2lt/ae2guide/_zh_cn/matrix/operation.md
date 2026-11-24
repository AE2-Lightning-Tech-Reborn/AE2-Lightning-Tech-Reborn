---
navigation:
  title: 样板、端口与运行
  icon: ae2lt:matter_warping_matrix_pattern_storage_t1
  parent: matrix/matrix-index.md
  position: 30
item_ids:
  - ae2lt:matter_warping_matrix_pattern_storage_t1
  - ae2lt:matter_warping_matrix_pattern_storage_t2
  - ae2lt:matter_warping_matrix_pattern_storage_t3
  - ae2lt:matter_warping_matrix_pattern_storage_upgrade
  - ae2lt:dense_topological_lattice
---

# 样板、端口与运行

<ItemGrid>
  <ItemIcon id="ae2lt:matter_warping_matrix_pattern_storage_t1" />
  <ItemIcon id="ae2lt:matter_warping_matrix_pattern_storage_t2" />
  <ItemIcon id="ae2lt:matter_warping_matrix_pattern_storage_t3" />
  <ItemIcon id="ae2lt:matter_warping_matrix_pattern_storage_upgrade" />
  <ItemIcon id="ae2lt:dense_topological_lattice" />
</ItemGrid>

## 样板仓等级与原位升级

结构上下夹层共有 50 个样板仓位，可任意混装以下三个等级：

| 样板仓 | 单仓槽位 | 满配 50 仓总槽位 |
|--------|---------:|-----------------:|
| **天枢物质扭曲矩阵样板仓 T1** | 36 | 1,800 |
| **天枢物质扭曲矩阵样板仓 T2** | 72 | 3,600 |
| **天枢物质扭曲矩阵样板仓 T3** | 288 | 14,400 |

* **支持的样板范围**：每槽存放 1 张样板，仅接受**可解码且属于分子装配室兼容类别**的样板（工作台合成、切石、锻造等）；需要外部机器加工的普通处理样板无法放入。
* **原位升级**：在控制器界面中执行升级操作，无需拆开结构即可原地替换样板仓并完整保留仓内已有样板：
  * 消耗背包中的**天枢物质扭曲矩阵样板仓升级**，将已有 **T1 升至 T2**。
  * 消耗背包中的**致密拓扑晶格**，将已有 **T2 升至 T3**。
  * 注意：单次升级动作中，本轮刚从 T1 升成的 T2 不会在同一轮中继续升级为 T3；直接破坏样板仓方块会将仓内样板掉落。

## 端口接入与样板管理

* **ME 接入与耗电**：结构成形后，**天枢物质扭曲矩阵端口**六面均可接入 ME 线缆，整座矩阵占 **1 频道**，待机消耗 **8 AE/t**，每次成功接收合成每份额外消耗 **1 AE**。结构解体或所跨区块未完整加载时会停止供给，重新加载后可自动复扫恢复。
* **多种管理入口**：
  * **端口界面**：按仓分页查看、搜索与直接增删样板。
  * **ME样板管理终端**：通过端口聚合库存统一管理矩阵中的样板。
  * **物品能力接口**：通过外部自动化向端口输入样板时，会自动在可写仓位中寻找空槽放入。

## 一键迁移样板

结构成形且在线时，可在控制器界面打开**「一键迁移样板」**，将同 ME 网络中其他兼容来源（如分子装配室旁的样板供应器等）里的分子装配室兼容样板批量迁移至本矩阵：

* **空闲要求**：执行迁移需要网络与相关设备处于空闲状态；扫描或迁移中发现活动合成任务时会暂停或拒绝执行。
* **迁移与去重**：仅移动矩阵可执行的实体样板并校验空槽；检测到重复样板时会自动回收，界面分别统计已移动与已回收数量；点击**「停止迁移」**后，已经完成的迁移会保留。
* **异常与领取**：对于样板磁盘、非普通样板槽、插入失败或来源变动等情况，界面会逐类反馈；若个别回收样板因 ME 网络与背包均满而暂时无法退回，请腾出空间后重新打开迁移界面领取。
