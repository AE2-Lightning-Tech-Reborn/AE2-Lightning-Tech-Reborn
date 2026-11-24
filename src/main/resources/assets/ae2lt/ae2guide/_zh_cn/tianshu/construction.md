---
navigation:
  title: 结构与自动搭建
  icon: ae2lt:tianshu_supercomputer_casing
  parent: tianshu/tianshu-index.md
  position: 10
item_ids:
  - ae2lt:tianshu_supercomputer_casing
  - ae2lt:phase_change_cooling_unit
  - ae2lt:tianshu_supercomputer_glass
---

# 结构与自动搭建

天枢超算为固定 **7×7×7** 的多方块结构。**控制器**位于一侧底边正中：以控制器朝外为基准，结构向后延伸 **6 格**、向左右各延伸 **3 格**、向上延伸 **6 格**。

<GameScene zoom="2.8" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer.snbt" />
  <DiamondAnnotation pos="6.5 0.5 3.5" color="#f2d37a">天枢超算控制器</DiamondAnnotation>
  <DiamondAnnotation pos="3.5 6.5 3.5" color="#85f29e">默认端口位置</DiamondAnnotation>
  <DiamondAnnotation pos="3.5 0.5 3.5" color="#80c6ff">另一端口候选位</DiamondAnnotation>
  <DiamondAnnotation pos="2.5 6.5 2.5" color="#ff9fd7">闭环样板仓示例位置</DiamondAnnotation>
  <DiamondAnnotation pos="4.5 6.5 2.5" color="#9fffe1">闭环种子仓示例位置</DiamondAnnotation>
  <BoxAnnotation min="2 2 2" max="5 5 5" color="#d58cff" alwaysOnTop={true}>3×3×3 核心舱</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 结构组成与材料清单

完整结构共包含 **243** 个方块：

| 结构部分 | 数量与成形规则 |
|----------|----------------|
| 天枢超算控制器 | 1 个（手动放置于底边正中） |
| 天枢超算外壳 | 99 个 |
| 天枢超算玻璃 | 98 个 |
| 天枢超算端口 | 顶部与底部两个候选位中**恰好放置 1 个**（均为空时自动搭建默认置于顶部） |
| 冷却兼容位 | 共 **17 个**格位；每格可自由放置**天枢超算冷却单元**、**天枢超算闭环样板仓**或**天枢超算闭环种子仓** |
| 3×3×3 核心舱 | 共 **27 格**：正中央放置 1 个主核心（如起步的**基准主核心**），外围 26 格全部填满合法单元 |

## 自动搭建流程

1. 手动放置**天枢超算控制器**。
2. 在背包中准备外壳自动搭建材料（共 **215** 个方块：99 外壳 + 98 玻璃 + 17 个冷却兼容位方块 + 1 端口）。
3. 打开控制器界面点击**自动搭建**：
   * 预先放置好的合法方块（例如自行指定的底部端口或提前放入的闭环仓位）会被保留。
   * 若存在无关阻挡方块，界面会报告阻挡位置；材料不足或遇阻时搭建停止，已完成的部分保留。
4. **3×3×3 核心舱（27 格）需手动组装**。全部就位后结构自动扫描并成形。
5. 可对控制器使用配方查看器的用途查询，查看多方块结构预览与各层材料表。
