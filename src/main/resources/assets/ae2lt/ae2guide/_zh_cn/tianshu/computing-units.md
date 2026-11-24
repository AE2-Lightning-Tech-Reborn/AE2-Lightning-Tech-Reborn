---
navigation:
  title: 主核心、计算单元与性能
  icon: ae2lt:tianshu_baseline_main_core
  parent: tianshu/tianshu-index.md
  position: 20
item_ids:
  - ae2lt:tianshu_baseline_main_core
  - ae2lt:tianshu_quantum_main_core
  - ae2lt:tianshu_overload_main_core
  - ae2lt:tianshu_multidimensional_main_core
  - ae2lt:tianshu_blank_unit
  - ae2lt:storage_supercomputing_unit
  - ae2lt:parallel_supercomputing_unit
  - ae2lt:tianshu_amplifier_unit
  - ae2lt:closed_loop_pattern_storage
  - ae2lt:closed_loop_seed_storage
---

# 主核心、计算单元与性能

中央 **3×3×3 核心舱**共有 27 格：正中央固定放置 1 个主核心，周围 **26 格**必须全部填满合法单元。

<GameScene zoom="4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer_core.snbt" />
  <BoxAnnotation min="3 3 3" max="4 4 4" color="#f2d37a" alwaysOnTop={true}>唯一的主核心位置</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 主核心档位与性能上限

| 主核心 | 内置合成存储 | 每 tick 成功派发上限 | 单任务每 tick 复制上限 | 允许增幅单元 | 端口待机耗电 |
|--------|-------------:|---------------------:|-----------------------:|--------------|-------------:|
| **天枢超算基准主核心** | 1 MiB | 512 | 1,024 | 0 | 256 AE/t |
| **天枢超算量子主核心** | 256 MiB | 6,144 | 20,480 | 0–15 | 8,192 AE/t |
| **天枢超算过载主核心** | 64 GiB | 16,384 | 4,194,304 | 0–15 | 262,144 AE/t |
| **天枢超算多维主核心** | 无限 | 16,384 | 无限 | 0（26 格全用天枢空白单元） | 8 AE/t |

> **起步配置**：**天枢超算基准主核心**可直接通过工作台合成；即使外围 26 格全部填充**天枢空白单元**也可正常成形运行，此时提供 **1 MiB** 合成存储、每 tick **128 次**成功派发与 **256 份**单任务批量复制；装入 1 个并行单元升至 256 次/t，装入 3 个即触及基准档 512 次/t 派发上限。

## 性能计算公式

设核心舱外围安装的**存储单元**数为 `S`、**并行单元**数为 `P`、**天枢增幅单元**数为 `A`，令 `R = 1 + A`（多维主核心直接使用自身固定预算，不依赖外围增益）：

| 主核心 | 派发增益 | 外部存储增益 | 复制增益 |
|--------|----------|--------------|----------|
| **基准主核心** | `1` | `1` | `2` |
| **量子主核心** | `2R` | `2R` | `R` |
| **过载主核心** | `2R` | `(2R)²` | `R²` |

* **总合成容量**：`内置存储 + 64 MiB × S × 外部存储增益`。每个已接受任务会按其计划所需预留合成容量，完成或取消后立即释放。
* **每 tick 成功派发**：`128 × (1 + P) × 派发增益`（不超过该主核心的派发上限），由当前所有并发任务共享。
* **单任务每 tick 复制预算**：兼容批量执行的样板在每次成功派发时受复制增益与单任务复制上限约束；普通逐份处理机器与兼容批量执行路径（如天枢物质扭曲矩阵）的实际推进速度各有差异。每份复制仍完整执行一份样板的原料消耗与产出。

### 配置验算示例（控制器界面可直接核对）

以**量子主核心**搭配 `S = 6`（存储单元）、`P = 5`（并行单元）、`A = 15`（天枢增幅单元，即 `R = 16`）为例：

* **合成容量**：`256 MiB + 64 MiB × 6 × (2 × 16) = 12,544 MiB`（界面显示约 `12.25 GiB`）。
* **成功派发**：`128 × (1 + 5) × (2 × 16) = 24,576`，按量子档上限截断为 **6,144/t**（界面会以上限提示标记）。
* **单任务复制**：受量子档单任务上限限制，显示为 **20,480/t**。

## 闭环仓位说明

<ItemGrid>
  <ItemIcon id="ae2lt:closed_loop_pattern_storage" />
  <ItemIcon id="ae2lt:closed_loop_seed_storage" />
</ItemGrid>

**天枢超算闭环样板仓**与**天枢超算闭环种子仓**安装在外壳的 17 个冷却兼容位中，**不占用** 3×3×3 核心舱的存储/并行/增幅单元格位：

* **闭环样板仓**：每个提供 **36 个**闭环样板槽。
* **闭环种子仓**：每个提供 **10 个** ME 存储元件槽；装入匹配种子资源类型的存储元件后，即可在 [天枢样板编码终端](../terminals/pattern-encoding-terminal.md) 中一键补种。
* **功能解锁**：所有有效主核心（含基准主核心）均完整支持闭环规划与库存维持（上限 **2,048 条**规则）。
