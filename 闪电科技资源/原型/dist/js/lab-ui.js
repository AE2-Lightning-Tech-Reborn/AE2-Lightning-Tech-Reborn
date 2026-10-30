/* Chinese prototype interface registered into TMT's Vue application. */
Vue.component('reactor-lab', {
    data() {
        return {
            view: 'production', modal: '', pendingImport: null, toast: '', toastBad: false,
            report: null, reportMass: 20000, reportEfficiency: 80,
            facilityDefs: ReactorModel.FACILITIES, techDefs: ReactorModel.TECH, processDefs: ReactorModel.PROCESSES,
            tabs: [{ id: 'production', icon: '◈', name: '运行实验' }, { id: 'research', icon: '⌘', name: '研究网络' }, { id: 'records', icon: '≋', name: '轮次与数据' }, { id: 'guide', icon: '⊙', name: '玩法说明' }],
        };
    },
    computed: {
        s() { return this.$root.player.r.sim; },
        active() { return ReactorModel.active(this.s); },
        running() { return this.s.status === 'running'; },
        r() { return ReactorModel.rates(this.s); },
        remaining() { return ReactorModel.material(this.s); },
        availableTP() { return ReactorModel.techAvailable(this.s); },
        totalTP() { return ReactorModel.techTotal(this.s); },
        inputError() { return ReactorModel.validateDraft(this.s.draft); },
        kPreview() { return Number(this.s.draft.efficiency) >= 10 && Number(this.s.draft.efficiency) <= 100 ? 100 / Number(this.s.draft.efficiency) : null; },
        statusName() { return { idle: '待启动', running: '运行中', paused: '已暂停' }[this.s.status]; },
        hint() { return ReactorModel.hint(this.s); },
        nextResearchNeeded() { return Math.max(0, ReactorModel.nextResearch(this.s) - this.s.research); },
        scienceProgress() {
            const lo = ReactorModel.researchForPoints(this.totalTP), hi = ReactorModel.nextResearch(this.s);
            return Math.max(0, Math.min(100, (this.s.research - lo) / (hi - lo) * 100));
        },
        massRange() { return Math.max(2, Math.min(12, Math.log10(Math.max(100, Number(this.s.draft.mass) || 100)))); },
        lastRun() { return this.s.history[0] || null; },
        keyCount() { return Object.values(this.s.keys).filter(Boolean).length; },
        ledger() { return ReactorModel.ledger(this.s); },
        plot() {
            const samples = [...this.s.trace, this.active ? { time: this.s.runTime, points: this.s.productPoints } : { time: 0, points: 0 }];
            const maxTime = Math.max(1, ...samples.map(x => x.time)), maxPoints = Math.max(1, ...samples.map(x => x.points));
            const line = samples.map(x => `${12 + x.time / maxTime * 696},${130 - x.points / maxPoints * 116}`).join(' ');
            return { line, area: `12,130 ${line} 708,130`, maxTime, maxPoints };
        },
        reportRows() { return this.report ? this.report.results.filter(r => r.mass === Number(this.reportMass) && r.efficiency === Number(this.reportEfficiency)) : []; },
    },
    mounted() {
        fetch('balance-results.json').then(r => r.ok ? r.json() : null).then(r => { this.report = r; }).catch(() => {});
    },
    methods: {
        n(value, digits = 1) {
            if (value === null || !Number.isFinite(Number(value))) return '—';
            const x = Number(value);
            if (Math.abs(x) >= 1e9) return x.toExponential(2);
            return x.toLocaleString('zh-CN', { maximumFractionDigits: digits });
        },
        duration(seconds) {
            const sec = Math.max(0, Math.floor(seconds));
            if (sec >= 3600) return `${Math.floor(sec / 3600)}h ${Math.floor(sec % 3600 / 60)}m`;
            return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`;
        },
        notice(message, bad = false) {
            this.toast = message; this.toastBad = bad;
            clearTimeout(this.toastTimer); this.toastTimer = setTimeout(() => { this.toast = ''; }, 4500);
        },
        persist() { save(); },
        launch() {
            const result = ReactorModel.start(this.s); this.notice(result.message, !result.ok);
            if (result.ok) { this.view = 'production'; this.persist(); }
        },
        changeMass(event) { if (!this.active) this.s.draft.mass = event.target.value; },
        slideMass(event) { if (!this.active) this.s.draft.mass = Math.round(Math.pow(10, Number(event.target.value))); },
        changeEfficiency(event) { if (!this.active) this.s.draft.efficiency = event.target.value; },
        preset(mass, efficiency) { if (!this.active) this.s.draft = { mass, efficiency }; },
        allocation(value) { if (this.active && Number.isFinite(Number(value))) this.s.allocation = Math.max(0, Math.min(100, Number(value))); },
        togglePause() { if (this.active) { this.s.status = this.running ? 'paused' : 'running'; this.persist(); } },
        speed(value) { this.s.speed = value; this.persist(); },
        fastForward() { if (this.running) { ReactorModel.advance(this.s, 60); this.persist(); this.notice('已按相同规则推进 60 秒内部时间。'); } },
        facilityCost(id) { return ReactorModel.facilityCost(this.s, id); },
        canBuy(id) { return this.active && this.s.facilities[id] < ReactorModel.CONFIG.maxFacilities && this.remaining + 1e-8 >= this.facilityCost(id); },
        buyFacility(id) { if (ReactorModel.buyFacility(this.s, id)) this.persist(); },
        has(id) { return ReactorModel.owns(this.s, id); },
        techReason(id) { return ReactorModel.techReason(this.s, id); },
        actualCost(t) { return t.mass * (this.active ? this.s.locked.k : this.kPreview || 1); },
        buyTech(id) {
            const result = ReactorModel.buyTech(this.s, id); this.notice(result.message, !result.ok); if (result.ok) this.persist();
        },
        pathTech(path) { return this.techDefs.filter(t => t.path === path); },
        processUnlocked(id) { return ReactorModel.processUnlocked(this.s, id); },
        changeProcess(event) { ReactorModel.selectProcess(this.s, event.target.value); this.persist(); },
        canReset() { return ReactorModel.canReset(this.s); },
        confirmModal() {
            let result;
            if (this.modal === 'inner') result = ReactorModel.resetSubround(this.s);
            else if (this.modal === 'outer') result = ReactorModel.settle(this.s);
            else if (this.modal === 'import') {
                Vue.set(player.r, 'sim', this.pendingImport); this.pendingImport = null;
                result = { ok: true, message: '存档已导入。' };
            } else if (this.modal === 'wipe') {
                Vue.set(player.r, 'sim', ReactorModel.state()); result = { ok: true, message: '原型存档已清空。' };
            }
            this.modal = ''; if (result) this.notice(result.message, !result.ok); this.persist();
        },
        exportSave() {
            this.persist();
            const blob = new Blob([JSON.stringify({ format: 'lightning-resources-v1', state: this.s }, null, 2)], { type: 'application/json' });
            const link = document.createElement('a'); const url = URL.createObjectURL(blob);
            link.href = url; link.download = 'lightning-resources-save.json'; link.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000); this.notice('存档已导出。');
        },
        importSave(event) {
            const file = event.target.files[0]; if (!file) return;
            if (file.size > 2e6) { this.notice('文件超过 2 MB，无法作为原型存档导入。', true); return; }
            const reader = new FileReader();
            reader.onload = () => {
                try {
                    const data = JSON.parse(reader.result);
                    if (data.format !== 'lightning-resources-v1' || !ReactorModel.validateSave(data.state)) throw new Error('invalid');
                    this.pendingImport = data.state; this.modal = 'import';
                } catch (_) { this.notice('存档格式或物质收支校验失败，当前进度没有改变。', true); }
                event.target.value = '';
            };
            reader.readAsText(file);
        },
        strategyName(id) { return { short: '300 秒短轮', flow: '高通量专精', precision: '精炼专精', split: '分散研究', cascade: '三子轮级联', 'cascade-refine': '级联＋浅层精炼' }[id] || id; },
        keyName(id) { return { flow: '高通量', precision: '精炼', cascade: '级联' }[id]; },
        setAuto(event) { if (this.s.automationUnlocked) { this.s.autoEnabled = event.target.checked; this.persist(); } },
    },
    template: window.ReactorTemplate,
});

window.addEventListener('load', () => {
    try {
        load();
        options.offlineProd = false;
        delete player.offTime;
        if (!ReactorModel.validateSave(player.r.sim)) throw new Error('保存的原型状态未通过校验，请导入有效存档或清除本原型存档。');
        document.getElementById('boot-message').remove();
    } catch (error) {
        const message = document.getElementById('boot-message');
        message.textContent = '载入失败：' + error.message;
        message.classList.add('boot-error');
        console.error(error);
    }
});
window.addEventListener('beforeunload', () => { if (player && player.r) save(); });
document.addEventListener('visibilitychange', () => { if (document.hidden && player && player.r) save(); });
