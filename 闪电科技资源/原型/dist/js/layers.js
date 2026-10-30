addLayer('r', {
    name: '资源实验室', symbol: 'R', color: '#bce5b5', row: 0, position: 0,
    type: 'none', resource: '资源点', baseResource: '物质',
    startData() { return { unlocked: true, points: new Decimal(0), sim: ReactorModel.state() }; },
    baseAmount() { return new Decimal(0); },
    layerShown() { return true; },
    tabFormat: [['display-text', '资源实验室']],
    update(diff) {
        if (document.hidden) return;
        ReactorModel.advance(player.r.sim, Math.max(0, diff) * player.r.sim.speed);
    },
});
