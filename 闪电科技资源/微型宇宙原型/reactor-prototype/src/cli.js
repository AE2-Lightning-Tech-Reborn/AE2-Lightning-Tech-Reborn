#!/usr/bin/env node
// 命令行入口。
//   node src/cli.js run   --q 2e6 --k 1.25 --strategy seedFull
//   node src/cli.js sweep [--k 1,1.25,2] [--q 1e6,1e7,1e8,1e9] [--strategies a,b]

import { runStrategy } from './runner.js';
import { STRATEGIES } from './strategies.js';
import { RULES } from './params.js';

function parseArgs(argv) {
  const args = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('--')) {
      args[a.slice(2)] = argv[i + 1];
      i += 1;
    } else {
      args._.push(a);
    }
  }
  return args;
}

function fmtTime(sec) {
  if (sec < 60) return `${sec.toFixed(0)}秒`;
  if (sec < 3600) return `${(sec / 60).toFixed(1)}分`;
  return `${(sec / 3600).toFixed(2)}时`;
}

function pct(x) {
  return `${(x * 100).toFixed(1)}%`;
}

function pad(s, n) {
  s = String(s);
  // 中文字符按两格宽度对齐
  let w = 0;
  for (const ch of s) w += ch.charCodeAt(0) > 0x2e7f ? 2 : 1;
  return s + ' '.repeat(Math.max(0, n - w));
}

function cmdRun(args) {
  const Q0 = Number(args.q ?? 2e6);
  const K = Number(args.k ?? 1.25);
  const name = args.strategy ?? 'balanced';
  if (!STRATEGIES[name]) throw new Error(`未知策略 ${name}，可选：${Object.keys(STRATEGIES).join(', ')}`);
  const out = runStrategy({ Q0, K, strategy: STRATEGIES[name]() });
  console.log(`规则 ${RULES.version}  投入 ${Q0.toExponential(2)} Q  K=${K}  策略 ${name}`);
  for (const e of out.events) console.log(`  ${pad(fmtTime(e.sec), 9)} ${e.text}`);
  const s = out.summary;
  console.log(`轮长 ${fmtTime(s.seconds)}${out.timedOut ? '（超时强制结算）' : ''}`);
  console.log(`回收率 ${pct(s.recovery)}   点数 ${out.receipt.points}`);
  console.log(
    `质量去向：已抽取 ${pct(s.settledShare)}  散失 ${pct(s.lossShare)}  样本胚 ${pct(s.blankShare)}  结算时作废 ${pct(s.discardedShare)}`,
  );
  console.log(`坍缩 ${s.collapse} 次  成熟度 ${s.maturity.toFixed(2)}  样本 ${out.receipt.samples}  被拒动作 ${s.rejected} 个`);
}

function cmdSweep(args) {
  const qs = (args.q ?? '1e6,1e7,1e8,1e9').split(',').map(Number);
  const ks = (args.k ?? '1.25').split(',').map(Number);
  const names = args.strategies ? args.strategies.split(',') : Object.keys(STRATEGIES);
  console.log(`规则 ${RULES.version}`);
  for (const K of ks) {
    console.log(`\nK = ${K}`);
    console.log(
      [pad('策略', 13), pad('投入', 8), pad('轮长', 9), pad('回收率', 8), pad('散失', 7), pad('坍缩', 5), pad('成熟度', 7), '回收率/小时'].join(' '),
    );
    for (const name of names) {
      for (const Q0 of qs) {
        const out = runStrategy({ Q0, K, strategy: STRATEGIES[name]() });
        const s = out.summary;
        console.log(
          [
            pad(name, 13),
            pad(Q0.toExponential(0), 8),
            pad(fmtTime(s.seconds) + (out.timedOut ? '*' : ''), 9),
            pad(pct(s.recovery), 8),
            pad(pct(s.lossShare), 7),
            pad(s.collapse, 5),
            pad(s.maturity.toFixed(1), 7),
            pct(s.recoveryPerHour),
          ].join(' '),
        );
      }
    }
  }
  console.log('\n* 表示超时后强制结算');
}

const args = parseArgs(process.argv.slice(2));
const cmd = args._[0] ?? 'sweep';
if (cmd === 'run') cmdRun(args);
else if (cmd === 'sweep') cmdSweep(args);
else {
  console.log('用法：node src/cli.js run|sweep [选项]');
  process.exitCode = 1;
}
