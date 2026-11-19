// 运行器：把一个策略接到规则核上，跑到结算或超时，并记录关键事件。

import { createRun, step, assertLedger, toQ, grade, maturity } from './core.js';
import { RULES, lawById } from './params.js';

export function runStrategy({
  Q0,
  K = 1,
  power = 1,
  strategy,
  rules = RULES,
  maxSeconds = 24 * 3600,
  checkEvery = 2000,
}) {
  const st = createRun({ Q0, K, power, rules });
  const events = [];
  const maxTicks = Math.ceil(maxSeconds / rules.dt);
  let timedOut = false;

  while (!st.settled) {
    if (st.t >= maxTicks) {
      timedOut = true;
      step(st, [{ type: 'settle' }]);
      break;
    }
    const intents = strategy(st);
    const results = step(st, intents);
    for (let i = 0; i < intents.length; i++) {
      const it = intents[i];
      const res = results[i];
      if (!res || !res.ok) continue;
      const sec = st.t * rules.dt;
      if (it.type === 'collapse') {
        events.push({ sec, text: `坍缩，取得规律“${lawById(rules, it.law).name}”（成熟度 ${maturity(st).toFixed(2)}）` });
      } else if (it.type === 'form') {
        events.push({ sec, text: `凝聚 ${it.kind} ${(it.amount / toQ(rules, st.Q0) * 100).toFixed(0)}%` });
      } else if (it.type === 'setExtract' && it.on) {
        events.push({ sec, text: `开始抽取，成熟度 ${maturity(st).toFixed(2)}，提取率 ${(grade(st) * 100).toFixed(1)}%` });
      } else if (it.type === 'dissolve') {
        events.push({ sec, text: `溶解 ${it.kind}` });
      } else if (it.type === 'settle') {
        events.push({ sec, text: `结算，成熟度 ${maturity(st).toFixed(2)}` });
      }
    }
    if (st.t % checkEvery === 0) assertLedger(st);
  }
  assertLedger(st);

  const r = st.receipt;
  const q0 = toQ(rules, st.Q0);
  const m = r.matter;
  return {
    state: st,
    receipt: r,
    events,
    timedOut,
    summary: {
      Q0: q0,
      K,
      seconds: r.seconds,
      recovery: r.recovery,
      settledShare: toQ(rules, m.settled) / q0,
      lossShare: toQ(rules, m.loss) / q0,
      discardedShare: toQ(rules, m.discardedFree + m.discardedStruct + m.discardedEmbedded) / q0,
      blankShare: toQ(rules, m.blank) / q0,
      collapse: r.counts.collapse,
      maturity: r.maturity,
      rejected: r.counts.rejected,
      recoveryPerHour: r.recovery / (r.seconds / 3600),
    },
  };
}
