// Installed only in the isolated recipe synchronization regression run.
ServerEvents.recipes(event => {
    for (const count of [1, 64, 65, 99, 100, 127, 128, 255, 256, 16384]) {
        event.custom({
            type: 'ae2lt:overload_processing',
            inputs: [{ingredient: 'minecraft:stone', count: count}],
            results: [{id: 'minecraft:diamond', count: count}],
            totalEnergy: 500
        }).id('ae2lt:sync_probe_' + count)
    }
})
