import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  createRun,
  step,
  act,
  ledger,
  assertLedger,
  grade,
  maturity,
  toQ,
  era,
  income,
  chargeRate,
  strikeAmount,
  decayTime,
  maxCharge,
  autoAvailable,
  collapseNeed,
  devicePrice,
  matureCostMult,
  mstar,
  collapseMstar,
  excitationCap,
  quote,
} from '../src/core.js';
import { RULES } from '../src/params.js';
import { runStrategy } from '../src/runner.js';
import { STRATEGIES, plan } from '../src/strategies.js';

function snapshot(st) {
  return JSON.stringify(st, (key, v) => (key === 'R' ? undefined : typeof v === 'bigint' ? `${v}n` : v));
}

function wait(st, seconds) {
  for (let i = 0; i < Math.round(seconds / RULES.dt); i++) step(st, []);
}

// 直接给一条规律，用来测试凝聚时代的规则
function withLaw(st, law) {
  st.phiEra = collapseNeed(st);
  assert.equal(act(st, { type: 'collapse', law }).ok, true);
}

test('开局账本闭合，没有势，也没有激发态', () => {
  const st = createRun({ Q0: 2e6, K: 1.25 });
  assert.equal(ledger(st).balanced, true);
  assert.equal(st.phi, 0);
  assert.equal(st.excited, 0);
  assert.equal(income(st), 0);
  assert.equal(era(st), 0);
});

test('蓄能、击发、衰减', () => {
  const st = createRun({ Q0: 1e6 });
  assert.equal(act(st, { type: 'strike' }).ok, false); // 还没蓄能
  wait(st, RULES.chargeTime);
  assert.ok(Math.abs(st.charge - 1) < 1e-9);
  wait(st, 5);
  assert.ok(Math.abs(st.charge - 1) < 1e-9); // 没解锁超限时最多蓄一格
  const res = act(st, { type: 'strike' });
  assert.equal(res.ok, true);
  assert.equal(res.excited, RULES.strikeBase);
  assert.equal(st.charge, 0);
  assert.equal(income(st), RULES.strikeBase);
  // 过一个维持时间，激发态衰减到 1/e
  wait(st, RULES.decayTime);
  assert.ok(Math.abs(st.excited / RULES.strikeBase - Math.exp(-1)) < 1e-6);
  assert.ok(st.phi > 0);
  assertLedger(st);
});

test('半格也能击发，激发量按比例', () => {
  const st = createRun({ Q0: 1e6 });
  wait(st, RULES.chargeTime / 2);
  const res = act(st, { type: 'strike' });
  assert.ok(Math.abs(res.excited - RULES.strikeBase / 2) < 1e-6);
});

test('激发态不超过弥散质量', () => {
  const st = createRun({ Q0: 100 });
  st.devices[1] = 40;
  wait(st, RULES.chargeTime);
  const res = act(st, { type: 'strike' });
  assert.equal(res.excited, 100);
  assert.equal(st.excited, 100);
  assertLedger(st);
});

test('三种装置各管一个量，逐个涨价', () => {
  const st = createRun({ Q0: 1e6 });
  st.phi = 1e12;
  const base = [chargeRate(st), strikeAmount(st), decayTime(st)];
  const p0 = devicePrice(st, 0, 1);
  assert.equal(act(st, { type: 'buyDevice', kind: 0, count: 1 }).ok, true);
  assert.ok(Math.abs(devicePrice(st, 0, 1) / p0 - RULES.devices[0].growth) < 1e-9);
  act(st, { type: 'buyDevice', kind: 1, count: 2 });
  act(st, { type: 'buyDevice', kind: 2, count: 3 });
  assert.ok(Math.abs(chargeRate(st) / base[0] - RULES.devices[0].mult) < 1e-9);
  assert.ok(Math.abs(strikeAmount(st) / base[1] - RULES.devices[1].mult ** 2) < 1e-9);
  assert.ok(Math.abs(decayTime(st) / base[2] - RULES.devices[2].mult ** 3) < 1e-9);
  assert.equal(act(st, { type: 'buyDevice', kind: 7, count: 1 }).ok, false);
});

test('装置数量解锁能力：自动击发、超限蓄能、稳态约束', () => {
  const st = createRun({ Q0: 1e6 });
  st.phi = 1e15;
  assert.equal(act(st, { type: 'setAuto', on: true, level: 1 }).ok, false);
  act(st, { type: 'buyDevice', kind: 0, count: RULES.milestones.auto.count });
  assert.equal(autoAvailable(st), true);
  assert.equal(act(st, { type: 'setAuto', on: true, level: 1 }).ok, true);
  assert.equal(act(st, { type: 'setAuto', on: true, level: 3 }).ok, false); // 还没解锁超限
  assert.equal(maxCharge(st), 1);
  act(st, { type: 'buyDevice', kind: 1, count: RULES.milestones.over.count });
  assert.equal(maxCharge(st), RULES.overMax);
  assert.equal(act(st, { type: 'setAuto', on: true, level: 3 }).ok, true);
  const before = decayTime(st);
  act(st, { type: 'buyDevice', kind: 2, count: RULES.milestones.steady.count });
  assert.ok(Math.abs(decayTime(st) / before - RULES.devices[2].mult ** 10 * RULES.steadyMult) < 1e-6);
  // 自动击发真的会击
  const strikes = st.counts.strikes;
  wait(st, 30);
  assert.ok(st.counts.strikes > strikes);
});

test('超限一击让宇宙过热：势更多，弥散质量持续散失，直到下一次普通击发', () => {
  const st = createRun({ Q0: 1e6 });
  st.devices[1] = RULES.milestones.over.count;
  wait(st, RULES.chargeTime * 3);
  assert.ok(Math.abs(st.charge - 3) < 1e-9);
  const res = act(st, { type: 'strike' });
  // 3 格一击的激发量是 1 格的 3^1.6 倍，比连击三次（3 倍）多
  assert.ok(Math.abs(res.excited / strikeAmount(st, 1) - 3 ** RULES.overExp) < 1e-6);
  assert.equal(st.heat, 3);
  assert.ok(Math.abs(income(st) / st.excited - (1 + RULES.overGain * 2)) < 1e-9);
  const loss = st.loss;
  wait(st, 2);
  assert.ok(st.loss > loss);
  assertLedger(st);
  // 一次不超过一格的击发让它冷却
  act(st, { type: 'strike' });
  assert.equal(st.heat, 1);
  const cooled = st.loss;
  wait(st, 2);
  assert.equal(st.loss, cooled);
});

test('成熟度随投入对数增长，提取率严格小于 C', () => {
  const st = createRun({ Q0: 1e6 });
  st.phi = 1e300;
  const unit = RULES.r0 * mstar(st);
  act(st, { type: 'mature', phi: unit });
  assert.ok(Math.abs(maturity(st) - 1) < 1e-9);
  act(st, { type: 'mature', phi: unit * 2 });
  assert.ok(Math.abs(maturity(st) - 2) < 1e-9);
  assert.ok(Math.abs(quote.matureTo(st, 3) - unit * 4) < 1e-3);
  act(st, { type: 'mature', phi: 1e299 });
  assert.ok(grade(st) < RULES.C);
});

test('成熟特征质量保持原指数，坍缩门槛改用独立规模指数', () => {
  const a = createRun({ Q0: 1e6 });
  const b = createRun({ Q0: 1e10 });
  assert.equal(mstar(a), 1e6);
  assert.ok(Math.abs(mstar(b) / 1e10 - 10) < 1e-9);
  assert.equal(collapseMstar(a), 1e6);
  assert.ok(Math.abs(collapseMstar(b) / 1e10 - (1e4 ** RULES.collapseExp)) < 1e-9);
  assert.ok(collapseNeed(b) < RULES.collapseRef * mstar(b));
});

test('坍缩：清掉装置、蓄能、激发态和势，保留成熟度与质量，换一条规律', () => {
  const st = createRun({ Q0: 1e6 });
  st.phi = 1e12;
  act(st, { type: 'buyDevice', kind: 0, count: 6 });
  act(st, { type: 'mature', phi: 1e8 });
  const x = maturity(st);
  wait(st, 4);
  act(st, { type: 'strike' });
  assert.match(act(st, { type: 'collapse', law: 'resonate' }).reason, /累计的势不足/);
  assert.equal(act(st, { type: 'collapse', law: 'nope' }).ok, false);
  st.phiEra = collapseNeed(st);
  const free = st.free;
  const res = act(st, { type: 'collapse', law: 'resonate' });
  assert.equal(res.ok, true);
  assert.deepEqual(st.devices, [0, 0, 0]);
  assert.equal(st.phi, 0);
  assert.equal(st.excited, 0);
  assert.equal(st.charge, 0);
  assert.equal(maturity(st), x);
  assert.equal(era(st), 1);
  assert.equal(free - st.free, BigInt(Math.floor(1e6 * RULES.collapseLoss * Number(RULES.NQ))));
  assert.equal(act(st, { type: 'collapse', law: 'resonate' }).reason, '已经持有这条规律');
  // 下一次坍缩更贵
  assert.ok(Math.abs(collapseNeed(st) / (RULES.collapseRef * mstar(st)) - RULES.collapseGrowth) < 1e-9);
  assertLedger(st);
});

test('规律各自的效果', () => {
  // 共振：产出翻倍多
  let st = createRun({ Q0: 1e6 });
  withLaw(st, 'resonate');
  wait(st, 4);
  act(st, { type: 'strike' });
  assert.ok(Math.abs(income(st) / st.excited - RULES.lawResonate) < 1e-9);

  // 自激：结构质量按份额可激发，自动击发不需要电容，坍缩仍会清空装置
  st = createRun({ Q0: 1e6 });
  st.devices = [2, 3, 4];
  withLaw(st, 'selfexc');
  assert.deepEqual(st.devices, [0, 0, 0]);
  assert.equal(autoAvailable(st), true);
  assert.equal(act(st, { type: 'form', kind: 'cloud', amount: 4e5 }).ok, true);
  st.devices[1] = 60;
  wait(st, 4);
  const excited = act(st, { type: 'strike' }).excited;
  assert.ok(st.excitedStruct > 0);
  assert.ok(Math.abs(st.excited - excitationCap(st)) < 1e-6);
  assert.ok(Math.abs(excited - st.excited) < 1e-6);
  assert.ok(Math.abs(income(st) - (st.excited + RULES.cloudYield * toQ(RULES, st.struct.cloud))) < 1e-6);
  st.phiEra = collapseNeed(st);
  act(st, { type: 'collapse', law: 'resonate' });
  assert.deepEqual(st.devices, [0, 0, 0]);
  assert.equal(st.excited, 0);

  // 结晶：成熟花费降到四分之一
  st = createRun({ Q0: 1e6 });
  withLaw(st, 'crystal');
  assert.equal(matureCostMult(st), RULES.lawCrystal);

  // 守恒：散失降到四分之一
  st = createRun({ Q0: 1e6 });
  withLaw(st, 'conserve');
  assert.ok(Math.abs(quote.dissolveLoss(st) - RULES.dissolveLoss * RULES.lawConserve) < 1e-12);

  // 余辉：只有本tick的过热散失产势，不重复乘过热倍率
  st = createRun({ Q0: 1e6 });
  withLaw(st, 'afterglow');
  st.free -= 10n * RULES.NQ;
  st.settledQ = 10n * RULES.NQ;
  assert.equal(income(st), 0);
  wait(st, 1);
  assert.equal(st.phi, 0);
  st.heat = 3;
  const burnedBefore = st.loss;
  const phiBefore = st.phi;
  wait(st, RULES.dt);
  const burnedQ = toQ(RULES, st.loss - burnedBefore);
  assert.ok(burnedQ > 0);
  assert.ok(Math.abs((st.phi - phiBefore) / burnedQ - RULES.afterglowYield) < 1e-6);
  assert.equal(st.phi, st.phiEra);

  st = createRun({ Q0: 1e6 });
  withLaw(st, 'afterglow');
  withLaw(st, 'resonate');
  st.heat = 3;
  const resonantLoss = st.loss;
  const resonantPhi = st.phi;
  wait(st, RULES.dt);
  const resonantBurned = toQ(RULES, st.loss - resonantLoss);
  assert.ok(Math.abs((st.phi - resonantPhi) / resonantBurned - RULES.afterglowYield * RULES.lawResonate) < 1e-6);

  // 最多三条
  st = createRun({ Q0: 1e6 });
  for (const law of ['resonate', 'crystal', 'conserve']) withLaw(st, law);
  st.phiEra = collapseNeed(st);
  assert.equal(act(st, { type: 'collapse', law: 'selfexc' }).reason, '规律已满');
});

test('凝聚和溶解：结构不能被激发，也不能直接抽取', () => {
  const st = createRun({ Q0: 1e6, K: 2 });
  assert.match(act(st, { type: 'form', kind: 'halo', amount: 1e5 }).reason, /凝聚时代/);
  withLaw(st, 'resonate');
  const free = toQ(RULES, st.free);
  assert.equal(act(st, { type: 'form', kind: 'halo', amount: 3e5 }).ok, true);
  // 凝聚的散失 = 数量 × formLoss × K
  const lost = 3e5 * RULES.formLoss * 2;
  assert.ok(Math.abs(toQ(RULES, st.struct.halo) - (3e5 - lost)) < 1e-3);
  assert.ok(Math.abs(toQ(RULES, st.free) - (free - 3e5)) < 1e-3);
  assertLedger(st);
  // 激发态被弥散质量封顶
  st.devices[1] = 60;
  wait(st, 4);
  act(st, { type: 'strike' });
  assert.ok(st.excited <= toQ(RULES, st.free) + 1e-6);
  // 溶解回来要再损失一点
  const halo = toQ(RULES, st.struct.halo);
  assert.equal(act(st, { type: 'dissolve', kind: 'halo', amount: halo }).ok, true);
  assert.equal(st.struct.halo, 0n);
  assert.equal(act(st, { type: 'dissolve', kind: 'halo', amount: 1 }).ok, false);
  assertLedger(st);
});

test('三种结构各自的作用', () => {
  const make = (kind) => {
    const st = createRun({ Q0: 1e6 });
    withLaw(st, 'conserve');
    act(st, { type: 'form', kind, amount: 4e5 });
    return st;
  };
  // 尘云：不用击发就有收入
  const cloud = make('cloud');
  assert.ok(Math.abs(income(cloud) - RULES.cloudYield * toQ(RULES, cloud.struct.cloud)) < 1e-6);
  // 晶核：成熟更便宜
  const nucleus = make('nucleus');
  assert.ok(matureCostMult(nucleus) < 1 / (1 + RULES.nucleusK * 0.39));
  // 暗晕：放大激发态的产出
  const halo = make('halo');
  wait(halo, 4);
  act(halo, { type: 'strike' });
  assert.ok(income(halo) / halo.excited > 1 + RULES.haloK * 0.39);
});

test('抽取按此刻的提取率记价，后抽的更值钱', () => {
  const st = createRun({ Q0: 1e6 });
  withLaw(st, 'resonate');
  st.phi = 1e12;
  act(st, { type: 'mature', phi: RULES.r0 * mstar(st) }); // 成熟度 1
  act(st, { type: 'buyFlux', amount: 1000 });
  act(st, { type: 'setExtract', on: true });
  wait(st, 10);
  const q1 = st.settledQ;
  const v1 = st.value;
  act(st, { type: 'mature', phi: RULES.r0 * mstar(st) * 6 }); // 成熟度 3
  wait(st, 10);
  const perQ1 = Number(v1) / Number(q1);
  const perQ2 = Number(st.value - v1) / Number(st.settledQ - q1);
  assert.ok(Math.abs(perQ1 / 1e9 - 0.4) < 1e-6);
  assert.ok(Math.abs(perQ2 / 1e9 - 3 / 4.5) < 1e-6);
  assertLedger(st);
});

test('点数严格小于 P × Q0 × C', () => {
  const st = createRun({ Q0: 1e6 });
  withLaw(st, 'resonate');
  st.phi = 1e300;
  act(st, { type: 'mature', phi: 1e299 });
  act(st, { type: 'buyFlux', amount: 1e6 / RULES.tauMin });
  act(st, { type: 'setExtract', on: true });
  while (st.free > 0n) step(st, []);
  const { receipt } = act(st, { type: 'settle' });
  assert.ok(receipt.points < BigInt(Math.floor(RULES.P * 1e6 * RULES.C)));
  assert.ok(receipt.recovery < RULES.C);
});

test('样本只能选这个宇宙到过的时代', () => {
  const st = createRun({ Q0: 1e6 });
  assert.match(act(st, { type: 'settle', sampleTier: 0 }).reason, /没有到过/);
  withLaw(st, 'resonate');
  st.phi = 1e12;
  act(st, { type: 'buyFlux', amount: 1000 });
  act(st, { type: 'setExtract', on: true, blankShare: 0.5 });
  wait(st, 10);
  assert.ok(st.blank > 0n);
  const { receipt } = act(st, { type: 'settle', sampleTier: 0 });
  assert.equal(receipt.samples, st.blank / (1000n * RULES.NQ));
  assert.equal(receipt.sampleName, '凝聚样本');
});

test('被拒的动作不改变状态', () => {
  const st = createRun({ Q0: 2e6 });
  step(st, []);
  const before = JSON.parse(snapshot(st));
  const tries = [
    { type: 'strike' },
    { type: 'buyDevice', kind: 0, count: 1 },
    { type: 'buyDevice', kind: 0, count: 0 },
    { type: 'mature', phi: 1e30 },
    { type: 'mature', phi: -1 },
    { type: 'collapse', law: 'resonate' },
    { type: 'form', kind: 'cloud', amount: 10 },
    { type: 'dissolve', kind: 'cloud', amount: 10 },
    { type: 'buyFlux', amount: 1 },
    { type: 'setExtract', on: true },
    { type: 'setAuto', on: true, level: 1 },
    { type: 'settle', sampleTier: 0 },
    { type: 'nonsense' },
  ];
  for (const intent of tries) assert.equal(act(st, intent).ok, false, intent.type);
  st.counts.rejected = 0;
  st.lastRejection = null;
  before.counts.rejected = 0;
  before.lastRejection = null;
  assert.equal(snapshot(st), JSON.stringify(before));
});

test('结算只发生一次，之后的动作全部拒绝', () => {
  const st = createRun({ Q0: 1e6 });
  const first = act(st, { type: 'settle' });
  const again = act(st, { type: 'settle' });
  assert.equal(again.repeated, true);
  assert.equal(again.receipt, first.receipt);
  assert.equal(act(st, { type: 'strike' }).ok, false);
  const t = st.t;
  step(st, []);
  assert.equal(st.t, t);
});

test('每 tick 的动作数有上限', () => {
  const st = createRun({ Q0: 1e6 });
  const intents = Array.from({ length: RULES.maxIntentsPerTick + 3 }, () => ({ type: 'setEmergency', on: true }));
  const results = step(st, intents);
  assert.equal(results.filter((r) => r.ok).length, RULES.maxIntentsPerTick);
  assert.equal(results.filter((r) => !r.ok).length, 3);
});

test('同样的输入得到同样的结果', () => {
  const a = runStrategy({ Q0: 3e6, K: 1.25, strategy: STRATEGIES.balanced() });
  const b = runStrategy({ Q0: 3e6, K: 1.25, strategy: STRATEGIES.balanced() });
  assert.equal(a.receipt.points, b.receipt.points);
  assert.equal(a.receipt.ticks, b.receipt.ticks);
  assert.equal(snapshot(a.state), snapshot(b.state));
});

test('各策略全程账本闭合，且没有被拒动作', () => {
  for (const name of Object.keys(STRATEGIES)) {
    const out = runStrategy({ Q0: 1e6, K: 1.25, strategy: STRATEGIES[name](), checkEvery: 200 });
    assert.equal(out.timedOut, false, name);
    assert.equal(out.summary.rejected, 0, name);
    const m = out.receipt.matter;
    assert.equal(
      m.settled + m.blank + m.loss + m.discardedFree + m.discardedStruct + m.discardedEmbedded,
      m.Q0,
      name,
    );
    assert.ok(out.receipt.recovery > 0 && out.receipt.recovery < RULES.C, name);
    if (name === 'tutorial') assert.ok(out.summary.seconds < 12 * 60, `教学策略用时 ${out.summary.seconds} 秒`);
  }
});

test('兜底抽取随时可用，按固定低提取率记价', () => {
  const out = runStrategy({ Q0: 5e6, strategy: STRATEGIES.emergency() });
  assert.ok(Math.abs(out.receipt.recovery - RULES.emergencyGrade) < 1e-6);
  assert.ok(Math.abs(out.receipt.seconds - RULES.emergencySeconds) < 1);
});

test('超限是用回收率换时间', () => {
  const calm = runStrategy({ Q0: 1e6, K: 1.25, strategy: plan({ laws: ['resonate', 'crystal'], matureX: 6, over: 1 }) });
  const hot = runStrategy({ Q0: 1e6, K: 1.25, strategy: plan({ laws: ['resonate', 'crystal'], matureX: 6, over: 3 }) });
  assert.ok(hot.receipt.seconds < calm.receipt.seconds);
  assert.ok(hot.receipt.recovery < calm.receipt.recovery);
});

test('K 越大回收率越低', () => {
  let last = Infinity;
  for (const K of [1, 2, 5, 10]) {
    const r = runStrategy({ Q0: 1e6, K, strategy: plan({ laws: ['resonate'], struct: { halo: 0.3 }, matureX: 4 }) }).receipt.recovery;
    assert.ok(r < last, `K=${K}`);
    last = r;
  }
});

test('精确处理超过 2^53 的质量', () => {
  const st = createRun({ Q0: 16384000000000n });
  assert.equal(st.Q0, 16384000000000n * RULES.NQ);
  withLaw(st, 'resonate');
  st.phi = 1e30;
  act(st, { type: 'buyFlux', amount: 3000 });
  act(st, { type: 'setExtract', on: true });
  wait(st, 5);
  assertLedger(st);
});
