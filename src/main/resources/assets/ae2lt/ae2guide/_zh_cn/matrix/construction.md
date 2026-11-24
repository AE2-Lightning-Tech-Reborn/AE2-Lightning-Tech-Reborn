---
navigation:
  title: 结构与自动搭建
  icon: ae2lt:matter_warping_matrix_casing
  parent: matrix/matrix-index.md
  position: 10
item_ids:
  - ae2lt:matter_warping_matrix_casing
  - ae2lt:matter_warping_matrix_constraint_frame
  - ae2lt:matter_warping_matrix_glass
  - ae2lt:matter_warping_matrix_controller
  - ae2lt:matter_warping_matrix_port
---

# 结构与自动搭建

天枢物质扭曲矩阵为固定 **7×11×7** 的多方块结构。**控制器**位于其中一面 7×11 立面的正中央：以控制器朝外为基准，结构向后延伸 **6 格**、向左右各延伸 **3 格**、向上下各延伸 **5 格**。

<GameScene zoom="2.4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/matter_warping_matrix.snbt" />
  <DiamondAnnotation pos="0.5 5.5 3.5" color="#f2d37a">天枢物质扭曲矩阵控制器</DiamondAnnotation>
  <DiamondAnnotation pos="6.5 5.5 3.5" color="#85f29e">默认端口位置</DiamondAnnotation>
  <DiamondAnnotation pos="1.5 1.5 1.5" color="#80c6ff">样板仓示例位置</DiamondAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 结构组成与材料清单

完整结构共包含 **434** 个实际方块（最低 1 个样板仓配置）：

| 结构部分 | 数量与成形规则 |
|----------|----------------|
| 天枢物质扭曲矩阵控制器 | 1 个（手动放置于立面正中央） |
| 天枢物质扭曲矩阵外壳 | 174 个 |
| 天枢物质扭曲矩阵约束框架 | 132 个（含 2 个未安装端口的候选位） |
| 天枢物质扭曲矩阵玻璃 | 44 个 |
| 天枢物质扭曲矩阵端口 | 3 个候选位中**恰好放置 1 个**（默认在控制器正对面，也可改至左右两侧端口候选位） |
| 样板仓（T1 / T2 / T3） | 上下夹层共 **50** 个候选位；可留空，但成形**至少需要 1 个**，T1、T2、T3 可任意混装 |
| 核心舱（81 格） | 中央固定放 1 个主核心，其余 80 格全部填满合法外围单元（稳态/量子/过载主核心至少含 1 个线程单元；多维主核心其余 80 格全用天枢空白单元） |

> 仅当无关方块占据了实际需要安装的格位时才会阻挡成形或自动搭建；结构内部规定的忽略位允许留空。

## 自动搭建流程

1. 手动放置**天枢物质扭曲矩阵控制器**，确保目标 7×11×7 空间内待安装格位无阻挡方块。
2. 在背包中准备外壳自动搭建材料（最低 **352** 个方块：174 外壳 + 132 约束框架 + 44 玻璃 + 1 端口 + 至少 1 个样板仓；若背包中有更多 T1/T2/T3 样板仓，会自动继续填满可用候选位，直至材料耗尽或达到 50 个）。
3. 打开控制器界面点击**自动搭建**：
   * 已正确放置的方块与已有端口位置会被保留；若 3 个端口候选位均为空，默认在控制器正对面放置端口。
   * **81 格核心舱需要手动组装**，完成后结构自动扫描并成形。
4. 可在配方查看器（JEI / EMI）中查看矩阵的多方块结构预览与完整材料表。
