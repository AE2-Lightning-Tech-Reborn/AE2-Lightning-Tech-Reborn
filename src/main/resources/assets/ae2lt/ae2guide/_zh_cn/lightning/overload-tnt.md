---
navigation:
  title: 过载 TNT
  icon: ae2lt:overload_tnt
  parent: lightning/lightning-index.md
  position: 90
item_ids:
  - ae2lt:overload_tnt
---

# 过载 TNT

<Row>
  <BlockImage id="ae2lt:overload_tnt" scale="4" />
</Row>

**过载 TNT** 是以过载奇点为核心的高危险爆炸方块，可通过打火石、火焰弹或红石信号点燃。引爆后不会产生普通爆炸，而是触发多阶段的闪电爆破。

## 合成

<RecipeFor id="ae2lt:overload_tnt" />

## 引爆行为

点燃后常规引信为 **80 tick（4 秒）**。引爆后会调度一段持续性的闪电爆破任务，包括初始落雷、大范围地表破坏、范围内生物受击以及后续连环落雷；每 tick 的破坏与落雷受服务器性能预算控制。

* **天气影响**：在支持天气变化的维度中，引爆会将雷暴天气设为 **160 tick（约 8 秒）**，之后由世界天气计时继续推进。
* **保护与配置**：遵循防爆特殊方块与已安装的区域保护模组规则；服务器配置 `ae2lt-common.toml` 中的 `overloadTnt.enableTerrainDamage` 控制常规爆破破坏与相关效果的开关。

> 过载 TNT 一旦点燃即无法回收，请在远离重要设施的开阔区域使用。

## 现场日志：一次异常爆炸

> **第 7 号实验。** 预期与前六次一致；然而实际曲线偏离。起爆时落点附近散着一枚 [闪电坍缩矩阵](../materials/lightning-collapse-matrix.md) ，爆炸后过载TNT内部庞大能量不知去向，爆点中心只有几道闪电和一枚不在记录中的元件。

> 初次复现失败。扩大样本并逐项控制变量后，结论指向同一个条件：**矩阵需要掉落在世界中**。
