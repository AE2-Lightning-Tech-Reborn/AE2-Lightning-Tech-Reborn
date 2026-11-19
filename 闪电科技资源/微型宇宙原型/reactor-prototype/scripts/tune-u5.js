// U5允许参数的单因素筛选。结果逐轮写入JSONL，重跑时跳过已完成组合。
// 用法：node scripts/tune-u5.js

import { appendFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs';
import { runStrategy } from '../src/runner.js';
import { plan } from '../src/strategies.js';
import { RULES } from '../src/params.js';

const reportDir = new URL('../reports/', import.meta.url);
const dir = decodeURIComponent(reportDir.pathname.replace(/^\//, '').replace(/\/$/, ''));
mkdirSync(dir, { recursive: true });
const path = `${dir}/U5-tune.jsonl`;
const parameters = {
  collapseExp: [0, 0.05, 0.1, 0.15, 0.2],
  selfexcShare: [0.3, 0.5, 0.7],
  afterglowYield: [1000, 5000, 10000, 20000],
  cloudYield: [0.4, 0.6, 0.9],
  nucleusK: [4, 8, 12],
  haloK: [4, 6, 8],
};
const profiles = {
  quick: { laws: ['crystal'], over: 3, matureX: 3 },
  mixed: { laws: ['resonate', 'crystal'], over: 2, struct: { cloud: 0.15, nucleus: 0.15, halo: 0.15 }, matureX: 5 },
  cloudFirst: { laws: ['resonate', 'crystal'], struct: { cloud: 0.3 }, collapseWhen: 'first', matureX: 5 },
  selfexcHeavy: { laws: ['selfexc', 'resonate', 'crystal'], struct: { nucleus: 0.3, halo: 0.3 }, collapseWhen: 'first', matureX: 5 },
  afterglowHot: { laws: ['resonate', 'afterglow'], over: 3, matureX: 3 },
  nucleus: { laws: ['crystal', 'resonate'], struct: { nucleus: 0.3 }, matureX: 7 },
  halo: { laws: ['resonate', 'crystal'], struct: { halo: 0.3 }, over: 3, matureX: 5 },
};
const conditions = [
  [1e6, 1.25], [1e6, 5], [1e7, 1.25], [1e7, 5],
  [1e8, 1.25], [1e8, 5], [1e9, 1.25], [1e9, 5],
];
const saved = existsSync(path) ? readFileSync(path, 'utf8').split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line)) : [];
const seen = new Set(saved.map((row) => `${row.parameter}|${row.value}|${row.profile}|${row.Q0}|${row.K}`));
let count = 0;
for (const [parameter, values] of Object.entries(parameters)) {
  for (const value of values) for (const [profile, options] of Object.entries(profiles)) for (const [Q0, K] of conditions) {
    const key = `${parameter}|${value}|${profile}|${Q0}|${K}`;
    if (seen.has(key)) continue;
    const rules = { ...RULES, [parameter]: value };
    const out = runStrategy({ Q0, K, rules, strategy: plan(options), maxSeconds: 4 * 3600 });
    const row = {
      parameter, value, profile, Q0, K, timedOut: out.timedOut,
      seconds: out.summary.seconds, recovery: out.summary.recovery,
      recoveryPerHour: out.summary.recoveryPerHour, lossShare: out.summary.lossShare,
    };
    appendFileSync(path, `${JSON.stringify(row)}\n`);
    seen.add(key);
    count += 1;
    if (count % 50 === 0) console.log(`${count} tuning runs completed`);
  }
}
console.log(`U5单因素参数筛选完成：${count}新结果，文件 ${path}`);
