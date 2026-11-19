// 规则核：奇点里的宇宙。无界面、无时钟、无随机。同样的规则、输入和动作序列得到同样的结果。
//
// 两类量：
//   质量 —— BigInt，单位是 1/NQ 个 Q，全程精确对账。
//   势   —— Number。它不是质量，不参与对账。
//
// 质量的去处：
//   投入 = 弥散 + 结构（尘云、晶核、暗晕）+ 抽取口占用 + 已抽取 + 样本胚 + 散失
// “激发态”是弥散质量的一种状态（Number，不超过弥散质量），不是单独的账目。
//
// 每个 tick 的顺序：
//   1. 依次校验并执行本 tick 的意图。
//   2. 自动击发（如果已开启且蓄能达到设定档位）。
//   3. 冻结本 tick 的生产输入。
//   4. 抽取：弥散质量按通量变成已抽取质量或样本胚，按此刻的提取率记价。
//   5. 蓄能、激发态衰减。
//   6. 入账本 tick 产出的势。新产出的势下一 tick 才能花。

import { RULES, KINDS, lawById } from './params.js';

const LONG_MAX = 9223372036854775807n;

export function createRun({ Q0, K = 1, power = 1, rules = RULES, runId = 'run-1' }) {
  const R = rules;
  const q0 = toNano(R, Q0);
  if (q0 <= 0n) throw new Error('Q0 必须为正');
  if (!(K >= 1) || !Number.isFinite(K)) throw new Error('K 必须是不小于 1 的有限数');
  if (!(power >= 0 && power <= 1)) throw new Error('供电满足率必须在 0 到 1 之间');
  return {
    R,
    runId,
    Q0: q0,
    K,
    power,
    t: 0,
    settled: false,
    receipt: null,

    // —— 质量账（BigInt）——
    free: q0, // 弥散质量
    struct: { cloud: 0n, nucleus: 0n, halo: 0n },
    embedded: 0n, // 抽取口占用
    settledQ: 0n, // 已抽取
    blank: 0n, // 样本胚
    loss: 0n, // 散失
    value: 0n, // Σ 抽取量 × 提取率定点值

    // —— 势与宇宙状态（Number）——
    phi: 0,
    phiEra: 0, // 上次坍缩以来累计产出的势
    charge: 0, // 蓄能，单位：格
    heat: 1, // 过热：上一击的格数，不小于 1
    excited: 0, // 激发态质量（弥散与自激结构的合计，Q）
    excitedFree: 0,
    excitedStruct: 0,
    devices: R.devices.map(() => 0),
    auto: { on: false, level: 1 },
    matureR: 0, // 累计成熟投入（已折算）
    laws: [],

    // —— 抽取 ——
    thr: 0, // 抽取通量（Q/秒）
    extracting: false,
    blankShare: 0,
    acc: 0,
    emergency: false,
    emergencyAcc: 0,

    counts: { collapse: 0, strikes: 0, overStrikes: 0, rejected: 0 },
    lastRejection: null,
  };
}

// ———————————————————————— 派生量 ————————————————————————

export function toNano(R, q) {
  if (typeof q === 'bigint') return q * R.NQ;
  if (typeof q === 'string') q = Number(q);
  return BigInt(Math.floor(q)) * R.NQ + BigInt(Math.floor((q % 1) * Number(R.NQ)));
}

export function toQ(R, nano) {
  return Number(nano) / Number(R.NQ);
}

export function hasLaw(st, id) {
  return st.laws.includes(id);
}

// 时代：0 原初，1 凝聚。完成第一次坍缩后进入凝聚时代。
export function era(st) {
  return st.laws.length >= 1 ? 1 : 0;
}

// 特征质量：成熟和坍缩的花费按它计
export function mstar(st) {
  const q0 = toQ(st.R, st.Q0);
  return q0 * (q0 / st.R.scaleRef) ** st.R.scaleExp;
}

export function collapseMstar(st) {
  const q0 = toQ(st.R, st.Q0);
  return q0 * (q0 / st.R.scaleRef) ** st.R.collapseExp;
}

export function milestone(st, key) {
  const m = st.R.milestones[key];
  return st.devices[m.kind] >= m.count;
}

export function lossMult(st) {
  return hasLaw(st, 'conserve') ? st.R.lawConserve : 1;
}

// 每秒蓄多少格
export function chargeRate(st) {
  const R = st.R;
  return (1 / R.chargeTime) * R.devices[0].mult ** st.devices[0];
}

// 蓄到 c 格时一击激发的质量（Q），不考虑弥散质量的上限
export function strikeAmount(st, c = 1) {
  const R = st.R;
  return R.strikeBase * R.devices[1].mult ** st.devices[1] * (c <= 1 ? c : c ** R.overExp);
}

// 激发态的维持时间（秒）
export function decayTime(st) {
  const R = st.R;
  return (
    R.decayTime *
    R.devices[2].mult ** st.devices[2] *
    (milestone(st, 'steady') ? R.steadyMult : 1)
  );
}

export function maxCharge(st) {
  return milestone(st, 'over') ? st.R.overMax : 1;
}

export function autoAvailable(st) {
  return milestone(st, 'auto') || hasLaw(st, 'selfexc');
}

// 按 c 格一击、蓄满就击的节奏，激发态质量大致稳定在多少（不考虑弥散质量的上限）
export function steadyExcited(st, c = 1) {
  return ((strikeAmount(st, c) * chargeRate(st)) / c) * decayTime(st);
}

export function structQ(st, kind) {
  return toQ(st.R, st.struct[kind]);
}

function structFrac(st, kind) {
  return structQ(st, kind) / toQ(st.R, st.Q0);
}

export function outputMult(st) {
  return baseOutputMult(st) * (1 + st.R.overGain * (st.heat - 1));
}

function baseOutputMult(st) {
  const R = st.R;
  return (1 + R.haloK * structFrac(st, 'halo')) * (hasLaw(st, 'resonate') ? R.lawResonate : 1);
}

// 每秒产出的势
export function income(st) {
  const R = st.R;
  return R.e0 * (st.excited + R.cloudYield * structQ(st, 'cloud')) * outputMult(st) * st.power;
}

// 成熟度
export function maturity(st) {
  return Math.log2(1 + st.matureR / (st.R.r0 * mstar(st)));
}

// 成熟的花费倍率：投入 1 势，计入 1 ÷ 倍率 的成熟投入
export function matureCostMult(st) {
  const R = st.R;
  return (hasLaw(st, 'crystal') ? R.lawCrystal : 1) / (1 + R.nucleusK * structFrac(st, 'nucleus'));
}

// 提取率，严格小于 C
export function grade(st) {
  const x = maturity(st);
  return (st.R.C * x) / (x + st.R.h);
}

function gradeInt(st) {
  const R = st.R;
  const scale = Number(R.GRADE_SCALE);
  // 提取率在数学上严格小于 C。下面的 min 只防浮点舍入恰好取到 C，不改变任何可达结果。
  return BigInt(Math.min(Math.floor(grade(st) * scale), Math.floor(R.C * scale) - 1));
}

// 下一次坍缩需要本时代累计多少势
export function excitationCap(st) {
  const free = toQ(st.R, st.free);
  if (!hasLaw(st, 'selfexc')) return free;
  const structure = st.struct.cloud + st.struct.nucleus + st.struct.halo;
  return free + st.R.selfexcShare * toQ(st.R, structure);
}

export function collapseNeed(st) {
  const R = st.R;
  return R.collapseRef * collapseMstar(st) * R.collapseGrowth ** st.laws.length;
}

export function devicePrice(st, j, count = 1) {
  const d = st.R.devices[j];
  const first = d.base * d.growth ** st.devices[j];
  return (first * (d.growth ** count - 1)) / (d.growth - 1);
}

export function fluxCap(st) {
  return toQ(st.R, st.Q0) / st.R.tauMin;
}

export const quote = {
  // 把成熟度提到 x 还要花多少势
  matureTo: (st, x) => Math.max(0, st.R.r0 * mstar(st) * (2 ** x - 1) - st.matureR) * matureCostMult(st),
  fluxMatterPerUnit: (st) => st.R.gamma * st.K,
  formLoss: (st) => Math.min(1, st.R.formLoss * st.K * lossMult(st)),
  dissolveLoss: (st) => st.R.dissolveLoss * lossMult(st),
  // 过热时弥散质量每秒散失的比例
  burnRate: (st, h = st.heat) => (h > 1 ? st.R.overBurnRate * (h - 1) * lossMult(st) : 0),
  bookedPoints: (st) => (st.value * BigInt(st.R.P)) / (st.R.NQ * st.R.GRADE_SCALE),
};

// ———————————————————————— 动作 ————————————————————————

function reject(reason) {
  return { ok: false, reason };
}
const OK = { ok: true };

function clampExcited(st) {
  const free = toQ(st.R, st.free);
  const structCap = hasLaw(st, 'selfexc')
    ? st.R.selfexcShare * toQ(st.R, st.struct.cloud + st.struct.nucleus + st.struct.halo)
    : 0;
  st.excitedFree = Math.min(st.excitedFree, free);
  st.excitedStruct = Math.min(st.excitedStruct, structCap);
  st.excited = st.excitedFree + st.excitedStruct;
}

function nano(st, q) {
  return BigInt(Math.floor(q * Number(st.R.NQ)));
}

function doStrike(st) {
  const R = st.R;
  const c = st.charge;
  const freeRoom = Math.max(0, toQ(R, st.free) - st.excitedFree);
  const structure = st.struct.cloud + st.struct.nucleus + st.struct.halo;
  const structCap = hasLaw(st, 'selfexc') ? R.selfexcShare * toQ(R, structure) : 0;
  const structRoom = Math.max(0, structCap - st.excitedStruct);
  const added = Math.max(0, Math.min(strikeAmount(st, c), freeRoom + structRoom));
  const toFree = Math.min(added, freeRoom);
  st.excitedFree += toFree;
  st.excitedStruct += Math.min(added - toFree, structRoom);
  st.excited = st.excitedFree + st.excitedStruct;
  st.charge = 0;
  st.counts.strikes += 1;
  st.heat = Math.max(1, c);
  if (c > 1 + 1e-9) st.counts.overStrikes += 1;
  return { ok: true, excited: added, charge: c };
}

const handlers = {
  // 击发：把蓄的能打进宇宙，激发一部分弥散质量
  strike(st) {
    if (st.charge < st.R.minStrike) return reject('蓄能不足');
    return doStrike(st);
  },

  setAuto(st, { on, level = 1 }) {
    if (on && !autoAvailable(st)) return reject(`需要 ${st.R.milestones.auto.count} 个电容，或持有规律“自激”`);
    if (!(level >= st.R.minStrike) || level > maxCharge(st) + 1e-9) return reject('档位超出当前可蓄的范围');
    st.auto = { on: Boolean(on), level };
    return OK;
  },

  buyDevice(st, { kind: j, count = 1 }) {
    if (!Number.isInteger(j) || j < 0 || j >= st.R.devices.length) return reject('装置不存在');
    if (!Number.isInteger(count) || count < 1 || count > 1000) return reject('数量无效');
    const price = devicePrice(st, j, count);
    if (price > st.phi) return reject('势不足');
    st.phi -= price;
    st.devices[j] += count;
    return OK;
  },

  // 成熟：投入势，提高成熟度。坍缩时保留。
  mature(st, { phi }) {
    if (!(phi > 0) || !Number.isFinite(phi)) return reject('数量无效');
    if (phi > st.phi) return reject('势不足');
    st.phi -= phi;
    st.matureR += phi / matureCostMult(st);
    return OK;
  },

  // 坍缩重启：清掉装置、蓄能、激发态和势，换一条规律
  collapse(st, { law }) {
    const R = st.R;
    if (!lawById(R, law)) return reject('规律不存在');
    if (hasLaw(st, law)) return reject('已经持有这条规律');
    if (st.laws.length >= R.lawSlots) return reject('规律已满');
    if (st.phiEra < collapseNeed(st)) return reject('本时代累计的势不足');
    const scattered = nano(st, toQ(R, st.free) * R.collapseLoss * lossMult(st));
    st.free -= scattered;
    st.loss += scattered;
    st.devices = R.devices.map(() => 0);
    st.charge = 0;
    st.heat = 1;
    st.excited = 0;
    st.excitedFree = 0;
    st.excitedStruct = 0;
    st.phi = 0;
    st.phiEra = 0;
    st.laws.push(law);
    if (!autoAvailable(st)) st.auto.on = false;
    if (st.auto.level > maxCharge(st)) st.auto.level = 1;
    st.counts.collapse += 1;
    return { ok: true, scattered: toQ(R, scattered) };
  },

  // 凝聚：把弥散质量变成结构
  form(st, { kind, amount }) {
    if (era(st) < 1) return reject('要先完成一次坍缩，进入凝聚时代');
    if (!KINDS.includes(kind)) return reject('结构不存在');
    if (!(amount > 0) || !Number.isFinite(amount)) return reject('数量无效');
    const take = nano(st, amount);
    if (take > st.free) return reject('弥散质量不足');
    const lost = nano(st, amount * quote.formLoss(st));
    st.free -= take;
    st.struct[kind] += take - lost;
    st.loss += lost;
    clampExcited(st);
    return OK;
  },

  // 溶解：把结构变回弥散质量
  dissolve(st, { kind, amount }) {
    if (!KINDS.includes(kind)) return reject('结构不存在');
    if (!(amount > 0) || !Number.isFinite(amount)) return reject('数量无效');
    let take = nano(st, amount);
    // 允许“全部溶解”时有 1 个最小单位以内的舍入差
    if (take > st.struct[kind]) {
      if (take - st.struct[kind] > st.R.NQ / 1000n) return reject('这种结构没有这么多');
      take = st.struct[kind];
    }
    if (take <= 0n) return reject('这种结构没有质量');
    const lost = nano(st, toQ(st.R, take) * quote.dissolveLoss(st));
    st.struct[kind] -= take;
    st.free += take - lost;
    st.loss += lost;
    return OK;
  },

  // 扩建抽取口
  buyFlux(st, { amount }) {
    if (era(st) < 1) return reject('要先完成一次坍缩，进入凝聚时代');
    if (!(amount > 0) || !Number.isFinite(amount)) return reject('数量无效');
    if (amount > fluxCap(st) - st.thr + 1e-9) return reject('超过抽取通量上限');
    const price = st.R.pt * amount;
    if (price > st.phi) return reject('势不足');
    const embed = nano(st, st.R.gamma * amount);
    const cost = BigInt(Math.ceil(st.R.gamma * amount * st.K * Number(st.R.NQ)));
    if (cost > st.free) return reject('弥散质量不足');
    st.phi -= price;
    st.free -= cost;
    st.embedded += embed;
    st.loss += cost - embed;
    st.thr += amount;
    clampExcited(st);
    return OK;
  },

  setExtract(st, { on, blankShare = 0 }) {
    if (era(st) < 1) return reject('要先完成一次坍缩，进入凝聚时代');
    if (!(blankShare >= 0 && blankShare <= 1)) return reject('比例无效');
    st.extracting = Boolean(on);
    st.blankShare = blankShare;
    return OK;
  },

  setEmergency(st, { on }) {
    st.emergency = Boolean(on);
    return OK;
  },

  settle(st, { sampleTier = null }) {
    return settle(st, sampleTier);
  },
};

// 最终结算：只发生一次。重复调用返回同一份收据。
function settle(st, sampleTier) {
  const R = st.R;
  if (st.settled) return { ok: true, receipt: st.receipt, repeated: true };
  if (sampleTier !== null) {
    if (!Number.isInteger(sampleTier) || sampleTier < 0 || sampleTier >= R.sampleRate.length) {
      return reject('样本级别不存在');
    }
    if (era(st) < sampleTier + 1) return reject('这个宇宙没有到过这个时代');
  }
  const points = quote.bookedPoints(st);
  if (points > LONG_MAX) throw new Error('点数超出 long 范围：P 的标定与最大投料量不一致');
  let samples = 0n;
  if (sampleTier !== null) samples = st.blank / (BigInt(Math.round(R.sampleRate[sampleTier])) * R.NQ);
  const inStruct = st.struct.cloud + st.struct.nucleus + st.struct.halo;
  st.receipt = Object.freeze({
    runId: st.runId,
    rulesVersion: R.version,
    ticks: st.t,
    seconds: st.t * R.dt,
    points,
    sampleTier,
    sampleName: sampleTier === null ? null : R.sampleNames[sampleTier],
    samples,
    recovery: Number(points) / (R.P * toQ(R, st.Q0)),
    era: era(st),
    maturity: maturity(st),
    laws: Object.freeze([...st.laws]),
    matter: Object.freeze({
      Q0: st.Q0,
      settled: st.settledQ,
      blank: st.blank,
      loss: st.loss,
      discardedFree: st.free,
      discardedStruct: inStruct,
      discardedEmbedded: st.embedded,
    }),
    counts: Object.freeze({ ...st.counts }),
  });
  st.settled = true;
  return { ok: true, receipt: st.receipt };
}

// ———————————————————————— 推进 ————————————————————————

export function act(st, intent) {
  if (st.settled && intent.type !== 'settle') return reject('本轮已结算');
  const handler = handlers[intent.type];
  if (!handler) return reject('未知动作');
  const result = handler(st, intent);
  if (!result.ok) {
    st.counts.rejected += 1;
    st.lastRejection = { t: st.t, intent, reason: result.reason };
  }
  return result;
}

export function step(st, intents = []) {
  const R = st.R;
  const results = [];
  const n = Math.min(intents.length, R.maxIntentsPerTick);
  for (let i = 0; i < n; i++) results.push(act(st, intents[i]));
  for (let i = n; i < intents.length; i++) {
    st.counts.rejected += 1;
    results.push(reject('超过每 tick 的动作上限'));
  }
  if (st.settled) return results;

  // 自动击发
  if (st.auto.on && autoAvailable(st) && st.charge >= Math.min(st.auto.level, maxCharge(st)) - 1e-9) doStrike(st);

  const nq = Number(R.NQ);
  let produced = income(st) * R.dt;

  // 抽取
  if (st.extracting && st.thr > 0 && st.free > 0n) {
    st.acc += st.thr * R.dt * st.power * nq;
    const whole = Math.floor(st.acc);
    if (whole >= 1) {
      st.acc -= whole;
      let amount = BigInt(whole);
      if (amount > st.free) amount = st.free;
      const toBlank = (amount * BigInt(Math.round(st.blankShare * 1e6))) / 1000000n;
      const toSettle = amount - toBlank;
      st.free -= amount;
      st.blank += toBlank;
      st.settledQ += toSettle;
      st.value += toSettle * gradeInt(st);
    }
  }
  if (st.emergency && st.free > 0n) {
    st.emergencyAcc += (toQ(R, st.Q0) / R.emergencySeconds) * R.dt * st.power * nq;
    const whole = Math.floor(st.emergencyAcc);
    if (whole >= 1) {
      st.emergencyAcc -= whole;
      let amount = BigInt(whole);
      if (amount > st.free) amount = st.free;
      st.free -= amount;
      st.settledQ += amount;
      st.value += amount * BigInt(Math.floor(R.emergencyGrade * Number(R.GRADE_SCALE)));
    }
  }

  // 过热：弥散质量持续散失；余辉只按这笔烧失量产势。
  if (st.heat > 1 && st.free > 0n) {
    let burned = nano(st, toQ(R, st.free) * quote.burnRate(st) * R.dt);
    if (burned > st.free) burned = st.free;
    st.free -= burned;
    st.loss += burned;
    if (burned > 0n && hasLaw(st, 'afterglow')) {
      const burnedQ = toQ(R, burned);
      const q0 = toQ(R, st.Q0);
      produced += burnedQ * R.afterglowYield * (mstar(st) / q0) * baseOutputMult(st) * st.power;
    }
  }

  // 蓄能与衰减
  st.charge = Math.min(maxCharge(st), st.charge + chargeRate(st) * R.dt * st.power);
  const decay = Math.exp(-R.dt / decayTime(st));
  st.excitedFree *= decay;
  st.excitedStruct *= decay;
  st.excited = st.excitedFree + st.excitedStruct;
  clampExcited(st);

  // 入账：新产出的势下一 tick 才能花
  st.phi += produced;
  st.phiEra += produced;
  st.t += 1;
  return results;
}

// ———————————————————————— 对账 ————————————————————————

export function ledger(st) {
  const structure = st.struct.cloud + st.struct.nucleus + st.struct.halo;
  const total = st.free + structure + st.embedded + st.settledQ + st.blank + st.loss;
  return {
    Q0: st.Q0,
    free: st.free,
    structure,
    embedded: st.embedded,
    settled: st.settledQ,
    blank: st.blank,
    loss: st.loss,
    total,
    balanced: total === st.Q0,
  };
}

export function assertLedger(st) {
  const l = ledger(st);
  if (!l.balanced) throw new Error(`质量账不平：投入 ${l.Q0}，合计 ${l.total}（tick ${st.t}）`);
  if (st.free < 0n || st.loss < 0n) throw new Error('出现负余额');
  if (st.excitedFree > toQ(st.R, st.free) + 1e-6) throw new Error('弥散激发态超过了弥散质量');
  if (st.excitedStruct > st.R.selfexcShare * toQ(st.R, st.struct.cloud + st.struct.nucleus + st.struct.halo) + 1e-6) {
    throw new Error('结构激发态超过了自激上限');
  }
  if (st.excited > excitationCap(st) + 1e-6) throw new Error('激发态超过了可激发质量');
  return l;
}
