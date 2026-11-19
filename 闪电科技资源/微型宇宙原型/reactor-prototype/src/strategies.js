// 固定策略。它们只通过和界面相同的动作操作规则核，没有任何特权。
// 这些策略用来验证规则、比较打法，不是最优解。

import { toQ, maturity, maxCharge, autoAvailable, collapseNeed, era, fluxCap, quote } from './core.js';
import { KINDS } from './params.js';
import { devicePurchase } from './advisor.js';

// 通用规划器。按策略参数推进：击发并买装置、坍缩取得规律、凝聚结构、成熟、抽取、溶解、结算。
export function plan(options = {}) {
  const o = {
    laws: ['resonate'], // 依次通过坍缩取得的规律
    over: 1, // 按几格一击；大于 1 是超限蓄能
    coolExtract: true, // 开始抽取时改回 1 格，避免抽取期间继续散失
    struct: {}, // 结构占投入质量的比例，如 { halo: 0.3 }
    collapseWhen: 'all', // 'first'：首次坍缩后凝聚；'all'：拿齐规律后凝聚
    matureX: 4, // 开始抽取前要达到的成熟度
    tau: 120, // 目标抽取时长（秒）
    blankShare: 0, // 抽取通量中转成样本胚的比例
    sampleTier: null,
    dissolve: true,
    dissolveTiming: 'after', // 'before'：开始抽取时先全部溶解；'after'：弥散质量抽完后溶解
    passiveAfterFirst: false, // 退化路线测试：首坍缩后不再击发或购买装置
    ...options,
  };
  let formed = false;
  let dissolvedBefore = false;
  let extracting = false;

  return function strategy(st) {
    const R = st.R;
    const acts = [];
    const spend = () => st.phi * (1 - 1e-9);

    // 击发
    const passive = o.passiveAfterFirst && st.laws.length >= 1;
    const level = Math.min(extracting && o.coolExtract ? 1 : o.over, maxCharge(st));
    if (!passive) {
      if (autoAvailable(st)) {
        if (!st.auto.on || st.auto.level !== level) acts.push({ type: 'setAuto', on: true, level });
      } else if (st.charge >= level - 1e-9) {
        acts.push({ type: 'strike' });
      }
    }

    if (extracting) {
      if (st.free === 0n) {
        const left = KINDS.filter((k) => st.struct[k] > 0n);
        if (o.dissolve && left.length > 0) {
          for (const k of left) acts.push({ type: 'dissolve', kind: k, amount: toQ(R, st.struct[k]) });
        } else {
          acts.push({ type: 'settle', sampleTier: o.sampleTier });
        }
      } else if (spend() > 0) {
        // 抽取期间没有别的开销，势全部用来继续成熟：后抽的质量提取率更高
        acts.push({ type: 'mature', phi: spend() });
      }
      return acts;
    }

    const shouldForm = o.collapseWhen === 'first' ? st.laws.length >= 1 : st.laws.length >= o.laws.length;
    if (!formed && era(st) >= 1 && shouldForm) {
      formed = true;
      const q0 = toQ(R, st.Q0);
      for (const k of KINDS) if (o.struct[k] > 0) acts.push({ type: 'form', kind: k, amount: q0 * o.struct[k] });
      return acts;
    }

    // 买装置，直到激发态覆盖全部弥散质量
    const g = passive ? { intent: null, busy: false } : devicePurchase(st, level);
    if (g.intent) acts.push(g.intent);
    if (g.busy) return acts;

    // 还有规律要拿：够了就坍缩；等待期间把势投入成熟（成熟度在坍缩时保留）
    if (st.laws.length < o.laws.length) {
      if (st.phiEra >= collapseNeed(st)) {
        acts.push({ type: 'collapse', law: o.laws[st.laws.length] });
        return acts;
      }
    }
    const need = quote.matureTo(st, o.matureX);
    if (need > 1e-6) {
      const phi = Math.min(spend(), need);
      if (phi > 0) acts.push({ type: 'mature', phi });
      return acts;
    }
    if (st.laws.length < o.laws.length) return acts; // 成熟度够了，继续等坍缩

    if (o.dissolve && o.dissolveTiming === 'before' && !dissolvedBefore) {
      dissolvedBefore = true;
      const left = KINDS.filter((k) => st.struct[k] > 0n);
      if (left.length > 0) {
        for (const k of left) acts.push({ type: 'dissolve', kind: k, amount: toQ(R, st.struct[k]) });
        return acts;
      }
    }

    // 抽取
    const want = Math.min(toQ(R, st.free) / o.tau, fluxCap(st));
    const missing = want - st.thr;
    if (missing > want * 1e-3) {
      const amount = Math.min(missing, spend() / R.pt);
      if (amount > 0) acts.push({ type: 'buyFlux', amount });
      return acts;
    }
    acts.push({ type: 'setExtract', on: true, blankShare: o.blankShare });
    extracting = true;
    return acts;
  };
}

// 只开兜底抽取，什么都不做。用作下限对照。
export function emergencyOnly() {
  let started = false;
  return function strategy(st) {
    if (!started) {
      started = true;
      return [{ type: 'setEmergency', on: true }];
    }
    return st.free === 0n ? [{ type: 'settle' }] : [];
  };
}

export const STRATEGIES = {
  // 首轮教学：坍缩一次拿“共振”，两成质量转样本胚，带出凝聚样本
  tutorial: () => plan({ laws: ['resonate'], matureX: 4, blankShare: 0.2, sampleTier: 0 }),
  // 快：一条规律，成熟度低
  quick: () => plan({ laws: ['resonate'], matureX: 3 }),
  // 快而费料：超限蓄能
  overQuick: () => plan({ laws: ['resonate'], over: 3, matureX: 3 }),
  // 均衡：两条规律
  balanced: () => plan({ laws: ['resonate', 'crystal'], matureX: 6 }),
  // 榨：三条规律加晶核，高成熟度
  squeeze: () => plan({ laws: ['resonate', 'crystal', 'afterglow'], struct: { nucleus: 0.3 }, matureX: 9, tau: 300 }),
  // 暗晕：牺牲可激发的质量换总产出
  halo: () => plan({ laws: ['resonate', 'crystal'], struct: { halo: 0.3 }, matureX: 6 }),
  // 尘云：被动产出，抽取期间仍有收入
  cloud: () => plan({ laws: ['resonate', 'crystal'], struct: { cloud: 0.3 }, matureX: 6 }),
  emergency: () => emergencyOnly(),
};

export { maturity };
