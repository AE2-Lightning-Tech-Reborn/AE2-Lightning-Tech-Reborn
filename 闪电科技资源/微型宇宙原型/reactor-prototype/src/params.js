// 规则参数表：奇点里的宇宙（原初、凝聚两个时代）。所有数值都是候选，改动时提升 version。

export const KINDS = ['cloud', 'nucleus', 'halo']; // 尘云 / 晶核 / 暗晕

export const RULES = Object.freeze({
  version: '0.5.0-U5',

  // —— 时间与精度 ——
  dt: 0.05, // 一个真实 tick 的秒数（20 TPS）
  NQ: 10n ** 9n, // 质量账本精度：每 Q 的最小单位数
  GRADE_SCALE: 10n ** 9n, // 提取率的定点精度
  maxIntentsPerTick: 16,

  // —— 抽取与结算 ——
  C: 1.0, // 提取率上限（渐近线）
  h: 1.5, // 成熟度 x = h 时提取率为 C/2
  P: 562949, // 每 Q 在提取率 1 时的资源点

  // —— 规模 ——
  // 成熟花费按 M* = Q0 × (Q0 / scaleRef)^scaleExp 计；坍缩门槛使用独立的 collapseExp。
  scaleRef: 1e6,
  scaleExp: 0.25,
  collapseExp: 0.10,

  // —— 原初时代：击发 ——
  e0: 1, // 每 Q 激发态质量每秒产生的势
  chargeTime: 4, // 蓄满一格需要的秒数
  strikeBase: 30, // 满格一击激发的质量（Q）
  decayTime: 20, // 激发态的维持时间（秒），按指数衰减回弥散态
  minStrike: 0.25, // 至少蓄到多少格才能击发
  overMax: 3, // 超限蓄能的上限（格）
  overExp: 1.6, // 超限一击激发的质量 = 满格的 c^overExp 倍
  // 超限一击会让宇宙过热，直到下一次击发：过热 h = 这一击的格数。
  //   势的产出 ×(1 + overGain × (h − 1))；弥散质量每秒散失 overBurnRate × (h − 1)
  overGain: 0.5,
  overBurnRate: 1e-4,

  // 三种装置，效果相乘：稳定时的激发质量 ≈ 每击激发量 × 蓄能速度 × 维持时间
  devices: Object.freeze([
    Object.freeze({ id: 'cap', name: '电容', text: '蓄能速度', mult: 1.12, base: 120, growth: 1.5 }),
    Object.freeze({ id: 'coil', name: '击发线圈', text: '每击激发的质量', mult: 1.25, base: 60, growth: 1.5 }),
    Object.freeze({ id: 'ring', name: '约束环', text: '激发态的维持时间', mult: 1.1, base: 300, growth: 1.6 }),
  ]),
  // 装置数量达到后解锁的能力（坍缩会清掉装置，能力随之失效，重建后恢复）
  milestones: Object.freeze({
    auto: Object.freeze({ kind: 0, count: 5, name: '自动击发', text: '蓄满一格时自动击发' }),
    over: Object.freeze({ kind: 1, count: 10, name: '超限蓄能', text: '可以蓄到 3 格再击发：宇宙过热，势更多，但弥散质量持续散失' }),
    steady: Object.freeze({ kind: 2, count: 10, name: '稳态约束', text: '激发态的维持时间 ×2' }),
  }),
  steadyMult: 2,

  // —— 成熟度 ——
  // x = log2(1 + 累计成熟投入 ÷ (r0 × M*))，坍缩时保留
  r0: 20,

  // —— 坍缩与规律 ——
  collapseRef: 60, // 第一次坍缩需要本时代累计产出 collapseRef × M* 的势
  collapseGrowth: 1.5, // 每多持有一条规律，下一次的需求 ×1.5
  lawSlots: 3,
  collapseLoss: 0.01, // 坍缩时散失的弥散质量比例
  laws: Object.freeze([
    Object.freeze({ id: 'resonate', name: '共振', text: '势的产出 ×2.5' }),
    Object.freeze({ id: 'selfexc', name: '自激', text: '部分结构质量也能被激发，自动击发不需要电容' }),
    Object.freeze({ id: 'afterglow', name: '余辉', text: '过热散失的质量会放出势' }),
    Object.freeze({ id: 'conserve', name: '守恒', text: '一切质量散失降到四分之一（过热、坍缩、凝聚、溶解）' }),
    Object.freeze({ id: 'crystal', name: '结晶', text: '成熟的花费降到四分之一' }),
  ]),
  lawResonate: 2.5,
  lawConserve: 0.25,
  lawCrystal: 0.25,
  afterglowYield: 5000, // 每损失1 Q产生的基础势，按 M* / Q0 缩放
  selfexcShare: 0.5, // 自激允许计入激发态的结构质量比例

  // —— 凝聚时代：把弥散质量分给结构 ——
  // 结构里的质量不能被激发，也不能直接抽取；要先溶解回弥散态。
  cloudYield: 0.6, // 尘云：每 Q 每秒被动产生的势（相对激发态的 1），不用击发、不衰减；坍缩后立刻有收入
  nucleusK: 8, // 晶核：成熟花费 ÷ (1 + nucleusK × 晶核质量占比)
  haloK: 6, // 暗晕：势的总产出 × (1 + haloK × 暗晕质量占比)
  formLoss: 0.01, // 凝聚时损失的比例，再乘 K
  dissolveLoss: 0.02, // 溶解时损失的比例

  // —— 抽取口（原型里凝聚时代起可建；视界时代的黑洞是它的高效版本，尚未实现）——
  pt: 3000, // 每 (Q/秒) 抽取通量的势价
  tauMin: 120, // 抽取通量上限 = Q0 / tauMin
  gamma: 3, // 每 (Q/秒) 抽取通量占用的质量（Q），实付再乘 K

  // —— 兜底抽取 ——
  emergencySeconds: 43200, // 12 小时抽完全部投入
  emergencyGrade: 0.05,

  // —— 样本（转化率是占位）——
  sampleNames: ['凝聚样本'],
  sampleRate: [1e3], // 多少 Q 的样本胚换一份该级样本
});

export function lawById(R, id) {
  return R.laws.find((l) => l.id === id);
}
