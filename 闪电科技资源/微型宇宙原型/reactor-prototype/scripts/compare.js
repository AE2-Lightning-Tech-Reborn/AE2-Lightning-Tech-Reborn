// 打法比较：在同样的投入下，逐项改变一个选择，看回收率、轮长和损耗怎么变。
// 用来检查“每个机制是否有该用和不该用的时候”，以及有没有一种打法通吃。
//   node scripts/compare.js [投入量] [K]

import { runStrategy } from '../src/runner.js';
import { plan } from '../src/strategies.js';
import { RULES } from '../src/params.js';

const Q0 = Number(process.argv[2] ?? 1e6);
const K = Number(process.argv[3] ?? 1.25);

const base = { laws: ['resonate', 'crystal'], matureX: 6 };
const groups = {
  '第一条规律（各配成熟度 4）': Object.fromEntries(RULES.laws.map((l) => [l.name, { laws: [l.id], matureX: 4 }])),
  '规律顺序（同一组合）': {
    '共振→结晶': { laws: ['resonate', 'crystal'], matureX: 6 },
    '结晶→共振': { laws: ['crystal', 'resonate'], matureX: 6 },
    '共振→自激': { laws: ['resonate', 'selfexc'], matureX: 6 },
    '自激→共振': { laws: ['selfexc', 'resonate'], matureX: 6 },
  },
  '凝聚时机（尘云15%）': {
    '首次坍缩后': { laws: ['resonate', 'crystal'], struct: { cloud: 0.15 }, collapseWhen: 'first', matureX: 6 },
    '拿齐规律后': { laws: ['resonate', 'crystal'], struct: { cloud: 0.15 }, collapseWhen: 'all', matureX: 6 },
  },
  '溶解时机（尘云15%）': {
    '开始抽取前': { laws: ['resonate', 'crystal'], struct: { cloud: 0.15 }, dissolveTiming: 'before', matureX: 6 },
    '弥散抽完后': { laws: ['resonate', 'crystal'], struct: { cloud: 0.15 }, dissolveTiming: 'after', matureX: 6 },
  },
  '第二条规律（第一条为共振，成熟度 6）': Object.fromEntries(
    RULES.laws.filter((l) => l.id !== 'resonate').map((l) => [l.name, { laws: ['resonate', l.id], matureX: 6 }]),
  ),
  '击发档位（基准：共振+结晶，成熟度 6）': { '1 格': { ...base }, '2 格': { ...base, over: 2 }, '3 格': { ...base, over: 3 } },
  '结构比例（基准同上）': {
    不凝聚: { ...base },
    '尘云 5%': { ...base, struct: { cloud: 0.05 } },
    '尘云 15%': { ...base, struct: { cloud: 0.15 } },
    '尘云 30%': { ...base, struct: { cloud: 0.3 } },
    '晶核 5%': { ...base, struct: { nucleus: 0.05 } },
    '晶核 30%': { ...base, struct: { nucleus: 0.3 } },
    '暗晕 15%': { ...base, struct: { halo: 0.15 } },
    '暗晕 45%': { ...base, struct: { halo: 0.45 } },
    '尘云15% + 晶核15%': { ...base, struct: { cloud: 0.15, nucleus: 0.15 } },
    '晶核15% + 暗晕15%': { ...base, struct: { nucleus: 0.15, halo: 0.15 } },
  },
  '成熟度目标（基准同上）': Object.fromEntries([3, 4, 5, 6, 7, 8, 9].map((x) => [`成熟度 ${x}`, { ...base, matureX: x }])),
  '规律条数（成熟度 6）': {
    '1 条': { laws: ['resonate'], matureX: 6 },
    '2 条': { laws: ['resonate', 'crystal'], matureX: 6 },
    '3 条': { laws: ['resonate', 'crystal', 'afterglow'], matureX: 6 },
  },
};

const pad = (s, n) => {
  let w = 0;
  for (const ch of String(s)) w += ch.charCodeAt(0) > 0x2e7f ? 2 : 1;
  return s + ' '.repeat(Math.max(0, n - w));
};
const time = (s) => (s < 3600 ? `${(s / 60).toFixed(1)}分` : `${(s / 3600).toFixed(2)}时`);

console.log(`规则 ${RULES.version} · 投入 ${Q0.toExponential(0)} · K=${K}`);
for (const [title, variants] of Object.entries(groups)) {
  console.log(`\n${title}`);
  console.log([pad('打法', 22), pad('轮长', 9), pad('回收率', 8), pad('散失', 7), '回收率/小时'].join(' '));
  for (const [name, opts] of Object.entries(variants)) {
    const out = runStrategy({ Q0, K, strategy: plan(opts), maxSeconds: 6 * 3600 });
    const s = out.summary;
    console.log(
      [
        pad(name, 22),
        pad(time(s.seconds) + (out.timedOut ? '*' : ''), 9),
        pad(`${(s.recovery * 100).toFixed(1)}%`, 8),
        pad(`${(s.lossShare * 100).toFixed(1)}%`, 7),
        `${(s.recoveryPerHour * 100).toFixed(0)}%`,
      ].join(' '),
    );
  }
}
console.log('\n* 表示 6 小时内没有完成，被强制结算');
