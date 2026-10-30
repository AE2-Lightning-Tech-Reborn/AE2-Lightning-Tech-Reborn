let modInfo = {
    name: '闪电科技 · 资源实验室', id: 'lightning-resources-tmt-prototype-v1',
    author: 'Lightning Tech prototype', pointsName: '资源点', modFiles: [],
    discordName: '', discordLink: '', initialStartPoints: new Decimal(0), offlineLimit: 0,
};
let VERSION = { num: '0.1.0', name: '两层周目' };
let changelog = '<h3>0.1.0 · 双层周目原型</h3><p>有限物质、对数科技点、自然专精、跨子轮科技与外层结算。</p>';
let winText = '关键科技在本次外层运行内保留。继续实验，或结算并开始下一轮。';
var doNotCallTheseFunctionsEveryTick = [];
function getStartPoints() { return new Decimal(0); }
function canGenPoints() { return false; }
function getPointGen() { return new Decimal(0); }
function addedPlayerData() { return {}; }
var displayThings = [];
function isEndgame() { return false; }
var backgroundStyle = {};
function maxTickLength() { return 0.25; }
function fixOldSave() {}
