const fs = require('node:fs');
const path = require('node:path');
const M = require('../dist/js/reactor-model.js');
const STEP = 0.25;

function setup(mass, efficiency) {
    const s = M.state(); s.draft = { mass, efficiency }; M.start(s); return s;
}
function build(s, target) {
    for (const id of Object.keys(target)) while (s.facilities[id] < target[id] && M.buyFacility(s, id)) {}
}
function researchPath(s, path, maxSeconds = 600) {
    const start = s.runTime; s.allocation = 100;
    build(s, { processor: 3, lab: 3, condenser: 2 });
    const queue = M.TECH.filter(t => t.path === path);
    while (s.runTime - start < maxSeconds && M.material(s) > 1e-5) {
        const remaining = queue.filter(t => !M.owns(s, t.id));
        const neededTP = s.techSpent + remaining.reduce((n,t) => n + t.tp, 0);
        const scienceMatter = Math.max(0, M.researchForPoints(neededTP) - s.research) / M.CONFIG.researchPerMatter;
        const upgradeMatter = remaining.reduce((n,t) => n + t.mass * s.locked.k, 0);
        if (scienceMatter + upgradeMatter > M.material(s)) return false;
        for (const tech of queue) M.buyTech(s, tech.id);
        if (queue.every(t => M.owns(s, t.id))) return true;
        M.advance(s, STEP, false);
    }
    return false;
}
function harvest(s, until = 3600) {
    s.allocation = 0;
    build(s, { processor: 4, condenser: 5 });
    while (M.material(s) > 1e-5 && s.runTime < until) M.advance(s, STEP, false);
}
function result(s, name) {
    return {
        strategy: name, mass: s.locked.mass, efficiency: s.locked.efficiency,
        points: +s.productPoints.toFixed(2), seconds: +s.runTime.toFixed(2), pointsPerSecond: +(s.productPoints / Math.max(1, s.runTime)).toFixed(3),
        pointsPerInput: +(s.productPoints / s.locked.mass).toFixed(3), subrounds: s.subround,
        keys: Object.keys(s.keys).filter(k => s.keys[k]), remaining: +M.material(s).toFixed(2),
        researchMatter: +s.lostResearch.toFixed(2), upgradeMatter: +s.lostUpgrades.toFixed(2),
        constructionMatter: +(s.installed + s.lostRebuild).toFixed(2), ledgerError: +M.ledger(s).difference.toExponential(2),
    };
}
function simulate(name, mass, efficiency) {
    const s = setup(mass, efficiency);
    if (name === 'short') {
        harvest(s, 300);
    } else if (name === 'flow' || name === 'precision') {
        researchPath(s, name);
        M.selectProcess(s, name === 'flow' ? 'pulse' : 'refine');
        harvest(s);
    } else if (name === 'split') {
        build(s, { processor: 3, lab: 3, condenser: 2 }); s.allocation = 100;
        const queue = ['flow1', 'precision1', 'flow2', 'precision2', 'flow3', 'precision3'];
        while (s.runTime < 120 && M.material(s) > 1e-5) {
            const next = queue.find(id => !M.owns(s, id)); if (next) M.buyTech(s, next);
            M.advance(s, STEP, false);
        }
        harvest(s);
    } else if (name === 'cascade') {
        const a = researchPath(s, 'flow');
        if (a && M.resetSubround(s).ok) {
            const b = researchPath(s, 'precision');
            if (b && M.resetSubround(s).ok) researchPath(s, 'cascade');
        }
        if (!M.selectProcess(s, 'cascade') && !M.selectProcess(s, 'refine')) M.selectProcess(s, 'pulse');
        harvest(s);
    } else if (name === 'cascade-refine') {
        const a = researchPath(s, 'flow');
        if (a && M.resetSubround(s).ok) {
            const b = researchPath(s, 'precision');
            if (b && M.resetSubround(s).ok && researchPath(s, 'cascade')) {
                s.allocation = 100;
                const start = s.runTime;
                while (s.runTime - start < 600 && !M.owns(s, 'precision2') && M.material(s) > 1e-5) {
                    M.buyTech(s, 'precision1'); M.buyTech(s, 'precision2'); M.advance(s, STEP, false);
                }
            }
        }
        if (!M.selectProcess(s, 'cascade') && !M.selectProcess(s, 'refine')) M.selectProcess(s, 'pulse');
        harvest(s);
    }
    return result(s, name);
}
function checkpoint(path, seconds = 60) {
    const s = setup(20000, 80); build(s, { processor: 3, lab: 3, condenser: 2 }); s.allocation = 100;
    const queue = path === 'split' ? ['flow1', 'precision1', 'flow2', 'precision2', 'flow3', 'precision3'] : ['flow1', 'flow2', 'flow3'];
    while (s.runTime < seconds) {
        const next = queue.find(id => !M.owns(s, id)); if (next) M.buyTech(s, next);
        M.advance(s, STEP, false);
    }
    return { ...result(s, path + '-60s'), earnedTP: M.techTotal(s), spentTP: s.techSpent, owned: s.owned };
}
const results = [];
for (const [mass, efficiency] of [[5000, 80], [20000, 80], [60000, 80], [20000, 10]])
    for (const name of ['short', 'flow', 'precision', 'split', 'cascade', 'cascade-refine']) results.push(simulate(name, mass, efficiency));
const report = { rules: '0.1.0', step: STEP, notes: 'Deterministic prototype policies; not optimal strategies or Minecraft economy validation. Short strategy ends at 300 simulated seconds, others at depletion or 3600 seconds.', checkpoints: [checkpoint('flow'), checkpoint('split')], results };
const output = path.join(__dirname, '../balance-results.json'); fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
console.table(report.checkpoints.map(r => ({ strategy: r.strategy, points: r.earnedTP, spent: r.spentTP, keys: r.keys.join(',') || 'none', material: r.remaining })));
console.table(results.map(({ strategy, mass, efficiency, points, seconds, pointsPerSecond, pointsPerInput, keys, remaining }) => ({ strategy, mass, efficiency, points, seconds, pointsPerSecond, pointsPerInput, keys: keys.join(',') || 'none', remaining })));
