// 分阶段打法扫描器：U4基线与U5复测共用，单进程运行并逐轮写入检查点。
// 用法：node scripts/winners.js U4|U5

import { appendFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs';
import { runStrategy } from '../src/runner.js';
import { plan } from '../src/strategies.js';
import { RULES } from '../src/params.js';

const phase = String(process.argv[2] ?? 'U4').toUpperCase();
if (!['U4', 'U5'].includes(phase)) throw new Error('阶段只能是 U4 或 U5');
const outDir = new URL('../reports/', import.meta.url);
const dir = decodeURIComponent(outDir.pathname.replace(/^\//, '').replace(/\/$/, ''));
mkdirSync(dir, { recursive: true });

const lawIds = RULES.laws.map((law) => law.id);
const lawSequences = [];
function addLawSequences(prefix, remaining) {
  if (prefix.length > 0) lawSequences.push(prefix);
  if (prefix.length === RULES.lawSlots) return;
  for (const law of remaining) addLawSequences([...prefix, law], remaining.filter((id) => id !== law));
}
addLawSequences([], lawIds);

const shares = [0, 0.05, 0.15, 0.3, 0.45];
const structures = [];
for (const cloud of shares) for (const nucleus of shares) for (const halo of shares) {
  if (cloud + nucleus + halo <= 0.9 + 1e-9) structures.push({ cloud, nucleus, halo });
}

const round = (value) => Math.round(value * 1e6) / 1e6;
const rowKey = (row) => JSON.stringify(row.options);
const fileName = (Q0, K) => `${phase}-${Q0.toExponential(0)}-K${K}.jsonl`;
const qValues = [1e6, 1e7, 1e8, 1e9];
const kValues = [1.25, 5];
const time = (sec) => sec < 3600 ? `${(sec / 60).toFixed(1)}分` : `${(sec / 3600).toFixed(2)}时`;

function readRows(path) {
  if (!existsSync(path)) return [];
  return readFileSync(path, 'utf8').split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
}
function uniqueRows(rows) {
  const seen = new Set();
  return rows.filter((row) => {
    const key = rowKey(row);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
function picks(rows) {
  const completed = rows.filter((row) => !row.timedOut && Number.isFinite(row.seconds));
  const by = (fn) => [...completed].sort(fn);
  const recovery = by((a, b) => b.recovery - a.recovery);
  const rate = by((a, b) => b.recoveryPerHour - a.recoveryPerHour);
  const fast = by((a, b) => a.seconds - b.seconds);
  const acceptableFast = fast.filter((row) => row.recovery >= 0.5);
  const fastest = acceptableFast[0] ?? fast[0];
  const recoveryCandidates = recovery.filter((row) => !fastest || row.seconds <= fastest.seconds * 4);
  const bestRecovery = recoveryCandidates[0] ?? recovery[0];
  const selected = new Map();
  const take = (row) => { if (row) selected.set(rowKey(row), row); };
  for (const row of [...recovery.slice(0, 3), ...rate.slice(0, 3), ...acceptableFast.slice(0, 3)]) take(row);
  for (const law of lawIds) {
    const containing = completed.filter((row) => row.options.laws.includes(law));
    take([...containing].sort((a, b) => b.recovery - a.recovery)[0]);
  }
  return { completed, recovery, rate, fast, fastest, bestRecovery, selected: [...selected.values()] };
}

function selectedStructureSeeds(rows) {
  const chosen = new Map();
  const add = (row) => { if (row) chosen.set(JSON.stringify(row.options.laws), row); };
  const state = picks(rows);
  for (const row of state.selected) add(row);
  // 明确让每条规律进入结构搜索，降低首轮筛选遗漏结构协同的风险。
  for (const law of lawIds) {
    const candidates = state.completed.filter((row) => row.options.laws.includes(law));
    add([...candidates].sort((a, b) => b.recovery - a.recovery)[0]);
  }
  return [...chosen.values()];
}

function refinementSeeds(rows) {
  const state = picks(rows);
  const chosen = new Map();
  const add = (row) => { if (row) chosen.set(rowKey(row), row); };
  for (const row of state.selected) add(row);
  for (const kind of ['cloud', 'nucleus', 'halo']) {
    const candidates = state.completed.filter((row) => (row.options.struct?.[kind] ?? 0) > 0);
    add([...candidates].sort((a, b) => b.recovery - a.recovery)[0]);
    add([...candidates].sort((a, b) => b.recoveryPerHour - a.recoveryPerHour)[0]);
  }
  return [...chosen.values()];
}

for (const Q0 of qValues) for (const K of kValues) {
  const path = `${dir}/${fileName(Q0, K)}`;
  const saved = readRows(path);
  const seen = new Set(saved.map(rowKey));
  const rows = [...saved];
  let count = 0;

  const run = (options, stage) => {
    const normalized = {
      laws: options.laws,
      over: options.over ?? 1,
      struct: options.struct ?? {},
      collapseWhen: options.collapseWhen ?? 'all',
      matureX: options.matureX ?? 5,
      dissolveTiming: options.dissolveTiming ?? 'after',
      tau: options.tau ?? 120,
      ...options,
    };
    const key = JSON.stringify(normalized);
    if (seen.has(key)) return;
    const out = runStrategy({ Q0, K, strategy: plan(normalized), maxSeconds: 8 * 3600 });
    const record = {
      stage,
      options: normalized,
      timedOut: out.timedOut,
      seconds: out.summary.seconds,
      recovery: out.summary.recovery,
      recoveryPerHour: out.summary.recoveryPerHour,
      lossShare: out.summary.lossShare,
      collapse: out.summary.collapse,
      maturity: out.summary.maturity,
      rejected: out.summary.rejected,
    };
    appendFileSync(path, `${JSON.stringify(record)}\n`);
    rows.push(record);
    seen.add(key);
    count += 1;
    if (count % 50 === 0) console.log(`${phase} ${Q0.toExponential(0)} K=${K}: ${count} new, ${rows.length} total`);
  };

  // A: 85种有序规律序列，每个规律都做第一条；先在共同基准下比较规律位置。
  for (const laws of lawSequences) run({ laws, over: 1, struct: {}, collapseWhen: 'all', matureX: 5, dissolveTiming: 'after' }, 'laws');

  // B: 为初筛中每条规律和各指标前沿保留的序列，扫描全部合法结构配比。
  const seeds = selectedStructureSeeds(uniqueRows(rows));
  for (const seed of seeds) for (const struct of structures) {
    run({ ...seed.options, struct }, 'structures');
  }

  // C: 对当前各指标与各结构类型的前沿，逐坐标扫描剩余离散维度。
  const refine = refinementSeeds(uniqueRows(rows));
  for (const seed of refine) {
    const base = seed.options;
    for (const over of [1, 2, 3]) run({ ...base, over }, 'refine-over');
    for (const matureX of [3, 5, 7, 9]) run({ ...base, matureX }, 'refine-maturity');
    for (const collapseWhen of ['first', 'all']) run({ ...base, collapseWhen }, 'refine-collapse-timing');
    for (const dissolveTiming of ['before', 'after']) run({ ...base, dissolveTiming }, 'refine-dissolve-timing');
  }

  const finalRows = uniqueRows(rows);
  const state = picks(finalRows);
  const show = (row) => row ? `${row.options.laws.join('>')} / ${JSON.stringify(row.options.struct)} / ${row.options.over}格 / x${row.options.matureX} / ${row.options.collapseWhen}坍缩凝聚 / ${row.options.dissolveTiming}溶解（${time(row.seconds)}，${(row.recovery * 100).toFixed(2)}%，${(row.recoveryPerHour * 100).toFixed(1)}%/时）` : '无';
  console.log(`\n${phase} ${Q0.toExponential(0)} Q K=${K}: ${finalRows.length} configs (${count} new)`);
  console.log(`  回收率赢家（快轮限时）：${show(state.bestRecovery)}`);
  console.log(`  回收率/小时赢家：${show(state.rate[0])}`);
  console.log(`  最快且回收率≥50%：${show(state.fastest)}`);
}

console.log(`扫描结束：规则 ${RULES.version}；有序规律序列 ${lawSequences.length}；合法结构比例向量 ${structures.length}；每轮结果保存在 ${dir}`);
