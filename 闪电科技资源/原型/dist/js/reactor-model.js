/* Lightning Resources prototype rules. Engine integration lives in layers.js. */
(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.ReactorModel = factory();
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';
    const VERSION = 1;
    const EPS = 1e-8;
    const CONFIG = Object.freeze({
        defaultMass: 20000, defaultEfficiency: 80, minMass: 100, maxMass: 1e12,
        minEfficiency: 10, maxEfficiency: 100, starterCost: 90,
        scienceScale: 10, scienceBase: 100, researchPerMatter: 45,
        processorRate: 6, laboratoryRate: 6, condenserRate: 4,
        completeGoal: 1000, maxFacilities: 12, timeStep: 0.25,
    });
    const TECH = Object.freeze([
        { id: 'flow1', path: 'flow', tier: 1, name: '并行处理', tp: 12, mass: 120, requires: [], description: '处理与凝聚吞吐 ×1.45。', effect: '吞吐 ×1.45' },
        { id: 'flow2', path: 'flow', tier: 2, name: '脉冲调度', tp: 22, mass: 240, requires: ['flow1'], description: '处理与凝聚吞吐再 ×1.55。', effect: '吞吐 ×1.55' },
        { id: 'flow3', path: 'flow', tier: 3, name: '高通量原理', tp: 46, mass: 450, requires: ['flow2'], key: 'flow', description: '吞吐再 ×1.80；掌握跨子轮的脉冲工艺。', effect: '关键科技 · 脉冲工艺' },
        { id: 'precision1', path: 'precision', tier: 1, name: '结构筛选', tp: 12, mass: 120, requires: [], description: '成果产额 ×1.25，凝聚吞吐 ×1.05。', effect: '产额 ×1.25' },
        { id: 'precision2', path: 'precision', tier: 2, name: '有序凝聚', tp: 22, mass: 240, requires: ['precision1'], description: '成果产额再 ×1.30，凝聚吞吐再 ×1.05。', effect: '产额 ×1.30' },
        { id: 'precision3', path: 'precision', tier: 3, name: '精炼原理', tp: 46, mass: 450, requires: ['precision2'], key: 'precision', description: '成果产额再 ×1.40，凝聚吞吐再 ×1.10；掌握精炼工艺。', effect: '关键科技 · 精炼工艺' },
        { id: 'cascade', path: 'cascade', tier: 1, name: '级联工艺', tp: 50, mass: 600, requires: [], key: 'cascade', keys: ['flow', 'precision'], description: '组合两种原理：解锁产额 ×3.60、凝聚吞吐 ×1.25 的级联工艺。', effect: '组合科技 · 级联生产' },
    ]);
    const FACILITIES = Object.freeze([
        { id: 'processor', name: '处理单元', base: 60, scale: 1.65, description: '基础物质 → 活化材料', unit: '6 物质/秒' },
        { id: 'lab', name: '研究单元', base: 80, scale: 1.75, description: '消耗活化材料，积累研究量', unit: '6 材料/秒 · 45 研究/材料' },
        { id: 'condenser', name: '凝聚单元', base: 70, scale: 1.65, description: '活化材料 → 可结算成果', unit: '4 材料/秒' },
    ]);
    const PROCESSES = Object.freeze([
        { id: 'standard', name: '基础凝聚', key: null, speed: 1, yield: 1, description: '吞吐 ×1.00 · 产额 ×1.00' },
        { id: 'pulse', name: '脉冲工艺', key: 'flow', speed: 1.65, yield: 1.1, description: '吞吐 ×1.65 · 产额 ×1.10' },
        { id: 'refine', name: '精炼工艺', key: 'precision', speed: 0.60, yield: 2, description: '吞吐 ×0.60 · 产额 ×2.00' },
        { id: 'cascade', name: '级联工艺', key: 'cascade', speed: 1.25, yield: 3.60, description: '吞吐 ×1.25 · 产额 ×3.60' },
    ]);
    function state() {
        return {
            version: VERSION, status: 'idle', draft: { mass: CONFIG.defaultMass, efficiency: CONFIG.defaultEfficiency },
            locked: { mass: 0, efficiency: CONFIG.defaultEfficiency, k: 1.25 }, runId: 0, subround: 1,
            raw: 0, feed: 0, productMass: 0, productPoints: 0, installed: 0,
            lostResearch: 0, lostUpgrades: 0, lostRebuild: 0,
            research: 0, techSpent: 0, owned: [], keys: { flow: false, precision: false, cascade: false },
            facilities: { processor: 0, lab: 0, condenser: 0 }, allocation: 60, process: 'standard',
            runTime: 0, subTime: 0, speed: 1, cascadePoints: 0, fullComplete: false,
            automationUnlocked: false, autoEnabled: false, autoPlan: 'complete', autoRepeat: false,
            totalPoints: 0, completedRuns: 0, history: [], journal: [], trace: [], nextTrace: 0,
        };
    }
    const active = s => s.status === 'running' || s.status === 'paused';
    const material = s => s.raw + s.feed;
    const techTotal = s => Math.floor(CONFIG.scienceScale * Math.log2(1 + s.research / CONFIG.scienceBase) + 1e-9);
    const techAvailable = s => Math.max(0, techTotal(s) - s.techSpent);
    const nextResearch = s => CONFIG.scienceBase * Math.expm1((techTotal(s) + 1) / CONFIG.scienceScale * Math.LN2);
    const researchForPoints = n => CONFIG.scienceBase * Math.expm1(n / CONFIG.scienceScale * Math.LN2);
    const owns = (s, id) => s.owned.includes(id);
    function validateDraft(draft) {
        if (!draft || !Number.isFinite(Number(draft.mass)) || Number(draft.mass) < CONFIG.minMass || Number(draft.mass) > CONFIG.maxMass)
            return `外部物质量须为 ${CONFIG.minMass} 至 1e12 之间的有限数值。`;
        if (!Number.isFinite(Number(draft.efficiency)) || Number(draft.efficiency) < 10 || Number(draft.efficiency) > 100)
            return '升级材料利用率必须在 10%–100% 之间，低于 10% 无法启动。';
        return '';
    }
    function log(s, message, kind = 'info') {
        s.journal.unshift({ time: s.runTime, subround: s.subround, message, kind });
        if (s.journal.length > 18) s.journal.length = 18;
    }
    function spend(s, amount, bucket) {
        if (!Number.isFinite(amount) || amount < 0 || material(s) + EPS < amount) return false;
        const raw = Math.min(s.raw, amount);
        s.raw = Math.max(0, s.raw - raw);
        s.feed = Math.max(0, s.feed - (amount - raw));
        s[bucket] += amount;
        return true;
    }
    function start(s) {
        if (active(s)) return { ok: false, message: '本次外层运行尚未结束，不能补料或修改输入。' };
        const error = validateDraft(s.draft);
        if (error) return { ok: false, message: error };
        const next = state();
        const preserve = ['draft', 'speed', 'automationUnlocked', 'autoEnabled', 'autoPlan', 'autoRepeat', 'totalPoints', 'completedRuns', 'history'];
        for (const key of preserve) next[key] = s[key];
        next.draft = { mass: Number(s.draft.mass), efficiency: Number(s.draft.efficiency) };
        next.locked = { mass: next.draft.mass, efficiency: next.draft.efficiency, k: 100 / next.draft.efficiency };
        next.runId = s.runId + 1;
        next.raw = next.locked.mass;
        next.status = 'running';
        spend(next, CONFIG.starterCost, 'installed');
        next.facilities = { processor: 1, lab: 1, condenser: 1 };
        log(next, '外层启动：投入与利用率已锁定，基础设施占用 90 物质。', 'start');
        Object.assign(s, next);
        return { ok: true, message: '外层运行已启动。' };
    }
    function facilityCost(s, id) {
        const def = FACILITIES.find(x => x.id === id);
        return def ? Math.ceil(def.base * Math.pow(def.scale, Math.max(0, s.facilities[id] - 1))) : Infinity;
    }
    function buyFacility(s, id) {
        if (!active(s) || !FACILITIES.some(x => x.id === id) || s.facilities[id] >= CONFIG.maxFacilities) return false;
        const cost = facilityCost(s, id);
        if (!spend(s, cost, 'installed')) return false;
        s.facilities[id]++;
        return true;
    }
    function techReason(s, id) {
        const def = TECH.find(x => x.id === id);
        if (!def) return '未知科技';
        if (!active(s)) return '请先启动外层运行';
        if (owns(s, id)) return '本子轮已研究';
        if (def.keys && def.keys.some(k => !s.keys[k])) return '需先掌握高通量与精炼两种关键科技';
        if (def.requires.some(k => !owns(s, k))) return '需完成本子轮的上一级研究';
        if (techAvailable(s) < def.tp) return `还需 ${def.tp - techAvailable(s)} 科技点`;
        if (material(s) + EPS < def.mass * s.locked.k) return '剩余物质不足以支付升级';
        return '';
    }
    function buyTech(s, id) {
        const reason = techReason(s, id);
        if (reason) return { ok: false, message: reason };
        const def = TECH.find(x => x.id === id);
        if (!spend(s, def.mass * s.locked.k, 'lostUpgrades')) return { ok: false, message: '物质不足' };
        s.techSpent += def.tp;
        s.owned.push(id);
        if (def.key) {
            const fresh = !s.keys[def.key];
            s.keys[def.key] = true;
            log(s, `${def.name}已完成${fresh ? '，关键科技将在内部重置后保留' : '，本子轮加成已恢复'}。`, 'research');
        } else log(s, `完成${def.name}。`, 'research');
        return { ok: true, message: `已完成${def.name}` };
    }
    function canReset(s) { return active(s) && material(s) + EPS >= CONFIG.starterCost; }
    function resetSubround(s) {
        if (!canReset(s)) return { ok: false, message: '剩余物质不足 90，无法重建基础设施；仍可最终结算。' };
        s.lostRebuild += s.installed;
        s.installed = 0;
        s.research = 0;
        s.techSpent = 0;
        s.owned = [];
        s.facilities = { processor: 1, lab: 1, condenser: 1 };
        spend(s, CONFIG.starterCost, 'installed');
        s.subround++;
        s.subTime = 0;
        s.allocation = 60;
        log(s, '内部重置：保留关键科技、剩余物质与成果；普通设施和科技点已清空。', 'reset');
        return { ok: true, message: `已进入子轮 ${s.subround}` };
    }
    function settle(s) {
        if (!active(s)) return { ok: false, message: '没有等待结算的外层运行。' };
        const reward = Math.floor(s.productPoints + EPS);
        const record = {
            id: s.runId, mass: s.locked.mass, efficiency: s.locked.efficiency, reward,
            seconds: s.runTime, subrounds: s.subround, keys: Object.keys(s.keys).filter(k => s.keys[k]),
            remaining: material(s), researchMass: s.lostResearch, upgradeMass: s.lostUpgrades,
            buildingMass: s.installed + s.lostRebuild, productMass: s.productMass, complete: s.fullComplete,
        };
        const next = state();
        for (const key of ['draft', 'speed', 'autoEnabled', 'autoPlan', 'autoRepeat']) next[key] = s[key];
        next.runId = s.runId;
        next.totalPoints = s.totalPoints + reward;
        next.completedRuns = s.completedRuns + 1;
        next.automationUnlocked = s.automationUnlocked || s.fullComplete;
        next.history = [record, ...s.history].slice(0, 20);
        Object.assign(s, next);
        return { ok: true, reward, message: `已结算 ${reward} 资源点。内部科技和库存已清空。` };
    }
    function processUnlocked(s, id) {
        const p = PROCESSES.find(x => x.id === id);
        return !!p && (!p.key || s.keys[p.key]);
    }
    function selectProcess(s, id) {
        if (!active(s) || !processUnlocked(s, id)) return false;
        s.process = id; return true;
    }
    function rates(s) {
        let flow = 1, precision = 1, precisionSpeed = 1;
        if (owns(s, 'flow1')) flow *= 1.45;
        if (owns(s, 'flow2')) flow *= 1.55;
        if (owns(s, 'flow3')) flow *= 1.8;
        if (owns(s, 'precision1')) { precision *= 1.25; precisionSpeed *= 1.05; }
        if (owns(s, 'precision2')) { precision *= 1.30; precisionSpeed *= 1.05; }
        if (owns(s, 'precision3')) { precision *= 1.40; precisionSpeed *= 1.10; }
        const process = PROCESSES.find(x => x.id === s.process && processUnlocked(s, x.id)) || PROCESSES[0];
        const share = Math.max(0, Math.min(100, s.allocation)) / 100;
        return {
            process: CONFIG.processorRate * s.facilities.processor * flow,
            research: CONFIG.laboratoryRate * s.facilities.lab * share,
            condense: CONFIG.condenserRate * s.facilities.condenser * flow * precisionSpeed * process.speed * (1 - share),
            yield: process.yield * precision, researchYield: CONFIG.researchPerMatter,
        };
    }
    function snapshot(s) {
        return { time: s.runTime, points: s.productPoints, material: material(s), research: techTotal(s), subround: s.subround };
    }
    function tick(s, dt) {
        if (s.status !== 'running' || !Number.isFinite(dt) || dt <= 0) return;
        const r = rates(s);
        const processed = Math.min(s.raw, r.process * dt);
        s.raw -= processed; s.feed += processed;
        const requestedResearch = r.research * dt;
        const requestedProduction = r.condense * dt;
        const demand = requestedResearch + requestedProduction;
        const share = demand > 0 ? Math.min(1, s.feed / demand) : 0;
        const researchMass = requestedResearch * share, productionMass = requestedProduction * share;
        s.feed = Math.max(0, s.feed - researchMass - productionMass);
        s.lostResearch += researchMass;
        s.research += researchMass * r.researchYield;
        s.productMass += productionMass;
        s.productPoints += productionMass * r.yield;
        if (s.process === 'cascade') s.cascadePoints += productionMass * r.yield;
        s.runTime += dt; s.subTime += dt;
        if (!s.fullComplete && s.keys.cascade && s.cascadePoints + EPS >= CONFIG.completeGoal) {
            s.fullComplete = true;
            log(s, '已完成核心流程：级联成果达到 1,000。最终结算后保留自动化资格。', 'complete');
        }
        if (s.runTime >= s.nextTrace) {
            s.trace.push(snapshot(s));
            if (s.trace.length > 160) s.trace.splice(1, 1);
            s.nextTrace = s.runTime + 10;
        }
    }
    function automation(s) {
        if (!s.autoEnabled || !s.automationUnlocked) return;
        if (!active(s)) { if (s.autoRepeat) start(s); return; }
        if (s.status !== 'running') return;
        let path = s.autoPlan === 'precision' ? 'precision' : 'flow';
        if (s.autoPlan === 'complete') {
            if (!s.keys.flow) path = 'flow';
            else if (!s.keys.precision) path = 'precision';
            else path = 'cascade';
        }
        const needsResearch = path === 'cascade' ? !s.keys.cascade : !owns(s, path + '3');
        if (needsResearch && s.autoPlan === 'complete') {
            const previousKeyThisSubround = (path === 'precision' && owns(s, 'flow3')) || (path === 'cascade' && s.techSpent > 0);
            if (previousKeyThisSubround && canReset(s)) { resetSubround(s); return; }
        }
        const desired = needsResearch ? { processor: 3, lab: 3, condenser: 2 } : { processor: 4, lab: 1, condenser: 5 };
        const reserve = needsResearch ? (path === 'cascade' ? 600 : 810) * s.locked.k + 90 : 0;
        for (const def of FACILITIES) {
            if (s.facilities[def.id] < desired[def.id] && material(s) >= facilityCost(s, def.id) + reserve) buyFacility(s, def.id);
        }
        if (needsResearch) {
            const queue = TECH.filter(t => t.path === path && !owns(s, t.id));
            if (queue.length) {
                const next = queue[0];
                if (material(s) + EPS < next.mass * s.locked.k) s.allocation = 0;
                else { s.allocation = 80; buyTech(s, next.id); }
            }
        } else s.allocation = 0;
        if (s.keys.cascade) selectProcess(s, 'cascade');
        else if (s.keys.precision && (s.autoPlan === 'precision' || s.autoPlan === 'complete')) selectProcess(s, 'refine');
        else if (s.keys.flow) selectProcess(s, 'pulse');
        if (material(s) < 0.001) settle(s);
    }
    function advance(s, seconds, withAutomation = true) {
        if (!Number.isFinite(seconds) || seconds <= 0 || seconds > 3600) return false;
        let left = seconds;
        while (left > 1e-9) {
            if (withAutomation) automation(s);
            if (s.status !== 'running') break;
            const dt = Math.min(CONFIG.timeStep, left);
            tick(s, dt); left -= dt;
        }
        return true;
    }
    function ledger(s) {
        const accounted = s.raw + s.feed + s.productMass + s.installed + s.lostResearch + s.lostUpgrades + s.lostRebuild;
        return { accounted, difference: s.locked.mass - accounted };
    }
    function nextTarget(s, path) { return TECH.find(t => t.path === path && !owns(s, t.id)); }
    function hint(s) {
        if (!active(s)) return '先设定外部物质量与利用率，启动后两项参数将锁定。';
        if (s.status === 'paused') return '已暂停：没有内部推进和资源消耗，可以检查方案或选择重置。';
        if (s.fullComplete) return '核心目标已完成。可以继续加工剩余物质，或最终结算并解锁后续自动化。';
        if (material(s) < 0.001) return '可用物质已耗尽。已形成的成果仍可最终结算。';
        if (s.keys.flow && s.keys.precision && !s.keys.cascade) return s.techSpent > 0 ? '两种原理已经掌握。内部重置后，从新的科技点预算研究级联工艺。' : '两种原理已经掌握，获得 50 科技点即可研究级联工艺。';
        if (owns(s, 'flow3') || owns(s, 'precision3')) return '已取得本路线关键科技。可转为成果生产，或内部重置后专精另一条路线。';
        const affordable = TECH.find(t => !techReason(s, t.id));
        if (affordable) return `现在可以研究「${affordable.name}」。集中一条路线共需 80 科技点；辅修会占用同一预算。`;
        if (s.allocation === 0) return '当前全部用于生产。提高科研分配后，研究量与科技点才会继续增长。';
        return '科研消耗活化材料，科技点按对数增长。扩建研究单元能加快研究，但不会增加物质预算。';
    }
    function validateSave(s) {
        if (!s || s.version !== VERSION || !['idle', 'running', 'paused'].includes(s.status)) return false;
        if (!s.draft || !s.locked || !s.facilities || !s.keys || !Array.isArray(s.owned) || !Array.isArray(s.history) || !Array.isArray(s.journal) || !Array.isArray(s.trace)) return false;
        if (['flow', 'precision', 'cascade'].some(k => typeof s.keys[k] !== 'boolean')) return false;
        if (['fullComplete', 'automationUnlocked', 'autoEnabled', 'autoRepeat'].some(k => typeof s[k] !== 'boolean')) return false;
        if (!['flow', 'precision', 'complete'].includes(s.autoPlan)) return false;
        if (['runId', 'subround', 'completedRuns'].some(k => !Number.isSafeInteger(s[k]) || s[k] < 0) || s.subround < 1) return false;
        if (!Number.isFinite(s.nextTrace) || s.nextTrace < 0) return false;
        if (s.journal.some(x => !x || !Number.isFinite(x.time) || !Number.isSafeInteger(x.subround) || typeof x.message !== 'string')) return false;
        if (s.trace.some(x => !x || !Number.isFinite(x.time) || !Number.isFinite(x.points))) return false;
        if (s.history.some(x => !x || !Number.isSafeInteger(x.id) || !Array.isArray(x.keys) || ['mass', 'efficiency', 'reward', 'seconds', 'subrounds', 'remaining', 'researchMass', 'upgradeMass', 'buildingMass', 'productMass'].some(k => !Number.isFinite(x[k]) || x[k] < 0))) return false;
        const fields = ['raw', 'feed', 'productMass', 'productPoints', 'installed', 'lostResearch', 'lostUpgrades', 'lostRebuild', 'research', 'techSpent', 'runTime', 'subTime', 'totalPoints', 'cascadePoints'];
        if (fields.some(k => !Number.isFinite(s[k]) || s[k] < 0)) return false;
        if (!Number.isInteger(s.techSpent) || s.techSpent > techTotal(s)) return false;
        if (FACILITIES.some(f => !Number.isInteger(s.facilities[f.id]) || s.facilities[f.id] < 0 || s.facilities[f.id] > CONFIG.maxFacilities)) return false;
        if (s.owned.some(id => !TECH.some(t => t.id === id)) || new Set(s.owned).size !== s.owned.length) return false;
        if (active(s) && (validateDraft(s.locked) || Math.abs(s.locked.k - 100 / s.locked.efficiency) > 1e-9)) return false;
        if (active(s) && Math.abs(ledger(s).difference) > Math.max(1e-5, s.locked.mass * 1e-9)) return false;
        if (!Number.isFinite(s.allocation) || s.allocation < 0 || s.allocation > 100 || ![1, 5, 20].includes(s.speed)) return false;
        if (!PROCESSES.some(p => p.id === s.process) || !processUnlocked(s, s.process)) return false;
        return true;
    }
    return { VERSION, CONFIG, TECH, FACILITIES, PROCESSES, state, active, material, techTotal, techAvailable, nextResearch, researchForPoints, owns, validateDraft, start, facilityCost, buyFacility, techReason, buyTech, canReset, resetSubround, settle, processUnlocked, selectProcess, rates, tick, advance, automation, ledger, nextTarget, hint, validateSave };
});
