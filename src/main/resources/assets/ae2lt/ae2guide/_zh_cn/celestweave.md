---
navigation:
  title: 苍穹织雷
  icon: ae2lt:celestweave_core
  parent: devices-index.md
  position: 20
item_ids:
  - ae2lt:celestweave_oculus
  - ae2lt:celestweave_core
  - ae2lt:celestweave_conduit
  - ae2lt:celestweave_stride
  - ae2lt:module_night_vision
  - ae2lt:module_water_breathing
  - ae2lt:module_saturation
  - ae2lt:module_reach_extension
  - ae2lt:module_matrix_shield
  - ae2lt:module_phase_shield
  - ae2lt:module_reflect
  - ae2lt:module_overload_protection
  - ae2lt:module_multidimensional_protection
  - ae2lt:module_purification
  - ae2lt:module_radiation_protection
  - ae2lt:module_laser_protection
  - ae2lt:module_creative_flight
  - ae2lt:module_phase_flight
  - ae2lt:module_phase_lock
  - ae2lt:phase_lock_projection
  - ae2lt:module_dash
  - ae2lt:module_dig_affinity
  - ae2lt:module_movement_assist
---

# 苍穹织雷

<ItemGrid>
  <ItemIcon id="ae2lt:celestweave_oculus" />
  <ItemIcon id="ae2lt:celestweave_core" />
  <ItemIcon id="ae2lt:celestweave_conduit" />
  <ItemIcon id="ae2lt:celestweave_stride" />
</ItemGrid>

**苍穹织雷**是由四件独立模块化部件构成的终局护甲，分别是**苍穹织雷·瞳**（头盔）、**苍穹织雷·心**（胸甲）、**苍穹织雷·径**（护腿）与**苍穹织雷·阶**（靴子）。每件部件都需要在 [过载装备工作站](devices-index.md) 中单独安装极限过载核心与对应模块，并在「过载设备」配置菜单（<KeyBind id="key.ae2lt.open_config" />）中启用。

## 能量容量扩展

每件苍穹织雷部件默认内置 **10,000,000 FE**，可安装 1 枚能量模块扩容：

| 能量模块 | 单件护甲 FE 容量 |
|----------|-----------------:|
| 无 | 10,000,000 FE |
| 能量模块 T1 | 1,000,000,000 FE |
| 能量模块 T2 | 5,000,000,000 FE |
| 能量模块 T3 | 20,000,000,000 FE |

## 各部件可用模块

| 部件 | 可安装模块与说明 |
|------|------------------|
| **苍穹织雷·瞳**（头盔） | **夜视模块**（维持夜视）、**水下呼吸模块**（维持水下呼吸）、**饱和模块**（周期补充饥饿值与饱和度） |
| **苍穹织雷·心**（胸甲） | **触及扩展模块**（调节交互距离）、**防护组四选一**（矩阵护盾 / 相位护盾 / 过载防护 / 多维防护）、**反伤模块**（对符合条件的生物攻击者最多反弹原伤害的 **30%**）、**净化模块**、**相位锁定模块**、**过载内核模块**（与电磁炮共用，为胸甲提供 16∶1 高压闪电代偿） |
| **苍穹织雷·径**（护腿） | **飞行组二选一**（创造飞行模块 / 相位飞行模块）：空中双击空格切换悬停，滑翔时按住空格推进，悬停时通过空格与 `Shift` 升降；相位飞行另提供**相位模式**（关闭 / 仅创造飞行 / 创造加鞘翅） |
| **苍穹织雷·阶**（靴子） | **冲刺模块**（按键：<KeyBind id="key.ae2lt.dash" />，默认 `V`）、**挖掘适应模块**（水下与空中挖掘加速）、**步行辅助模块**（调节步行、奔跑、潜行速度与自动爬坡） |

<OptionalSection modId="mekanism">
### Mekanism 防护模块（胸甲）

安装 Mekanism 后，可在胸甲上安装以下两个专用防护模块：

* **享辐模块**：处理来自 Mekanism 的辐射影响。
* **激光防护模块**：针对 Mekanism 激光伤害提供防护。
</OptionalSection>

## 护盾、免死能耗与闪电代偿

常规功能模块、事件型护盾与免死各自按触发或周期计费：

* **相位护盾**：单次受击最多支付 **1,024 点**伤害的抵扣额度，每点消耗 **20,000 FE + 2 极高压闪电**。
* **过载防护**：护盾抵挡与免死共享一个固定的 **20 tick** 计费窗口：
  * 常规护盾仅按本窗口内的最高单次伤害补差计费，单窗口上限为 **2 GFE** 与 **1,024 极高压闪电**。
  * 触发免死时按窗口内触发次数逐档递增，单窗口最高 **20 GFE** 与 **16,384 极高压闪电**。
* **多维防护**：无需支付上述护盾与免死费用。
* **过载内核代偿**：胸甲安装与电磁炮共用的**过载内核模块**后，若绑定 ME 网络中极高压闪电不足，会优先耗尽现有极高压闪电，剩余缺口按 **16 高压闪电∶1 极高压闪电** 代偿。

## 相位锁定模块

安装在胸甲的**相位锁定模块**在「过载设备」中提供四项独立开关：

* **盔甲锁定**：将真实护甲安全保存在私有槽位，并在装备栏显示**相位锁定投影**；当投影被外部手段替换或移除时，模块会支付资源重建投影，若资源不足则自动解除锁定并恢复真实护甲。
* **飞行锁定**：保持当前能力允许的飞行状态不被意外中断。
* **阻止外力拉动**：抵御外部牵引与拉动效果。
* **阻止外部传送**：按来源拦截外部强制传送。

## 玩家所属实体支持

支持由玩家直接拥有的活体实体（如驯服宠物等受承认的所属生物）穿戴苍穹织雷并运行其支持的模块：

* 服务端会直接处理该实体穿戴护甲的供电、闪电消耗与受支持的被动/防护效果；即使所有者离线，已确认归属关系的实体仍可继续运行。
* 看向交互距离内且视线可达的己方穿戴实体，按下「打开过载设备配置」键（<KeyBind id="key.ae2lt.open_config" />）即可直接打开其「过载设备」配置界面。
* 界面仅开放该实体适用的模块选项（与玩家手动按键触发相关的功能仅适用于玩家本人）。
