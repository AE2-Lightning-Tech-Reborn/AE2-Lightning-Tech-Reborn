// 装置购买建议：下一步买哪种装置最划算。固定策略和试玩界面的自动购买共用。
// 它只读状态、只产生普通动作。

import { toQ, income, steadyExcited, devicePrice, milestone, excitationCap } from './core.js';

// level：打算按几格一击。返回 { intent, busy }：
//   intent —— 现在就该执行的购买动作，没有则为 null
//   busy   —— 激发态还没覆盖全部弥散质量（包括正在攒势）
export function devicePurchase(st, level = 1) {
  const R = st.R;
  const cap = excitationCap(st);
  const steady = steadyExcited(st, level);
  if (steady >= cap) return { intent: null, busy: false };

  const phi = st.phi * (1 - 1e-9);
  const rate = Math.max(income(st), 1e-9);
  let best = null;
  for (let j = 0; j < R.devices.length; j++) {
    const price = devicePrice(st, j, 1);
    let mult = R.devices[j].mult;
    // 这一个正好凑满里程碑时，把里程碑的效果也算进去
    if (j === R.milestones.steady.kind && st.devices[j] + 1 === R.milestones.steady.count) mult *= R.steadyMult;
    if (j === R.milestones.over.kind && st.devices[j] + 1 === R.milestones.over.count && level > 1) {
      mult *= level ** R.overExp / level;
    }
    const gain = rate * (Math.min(steady * mult, cap) / steady - 1);
    if (!(gain > 0)) continue;
    const score = Math.max(0, price - phi) / rate + price / gain;
    if (!best || score < best.score) best = { j, price, score };
  }
  // 自动击发还没解锁时，优先把电容凑够（否则策略以外的玩家要一直手动击发）
  if (!milestone(st, 'auto') && !st.laws.includes('selfexc') && best) {
    const price = devicePrice(st, 0, 1);
    if (price <= best.price * 2) best = { j: 0, price, score: 0 };
  }
  if (!best) return { intent: null, busy: false };
  return { intent: best.price <= phi ? { type: 'buyDevice', kind: best.j, count: 1 } : null, busy: true };
}
