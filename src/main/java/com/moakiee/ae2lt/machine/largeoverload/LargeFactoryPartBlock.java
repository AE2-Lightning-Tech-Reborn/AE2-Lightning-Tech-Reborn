package com.moakiee.ae2lt.machine.largeoverload;

import net.minecraft.world.level.block.Block;

public class LargeFactoryPartBlock extends Block implements LargeFactoryRegistration.Part {
    private final LargeFactoryComponent component;
    public LargeFactoryPartBlock(Properties properties, LargeFactoryComponent component) {
        super(properties);
        this.component = component;
    }
    @Override public LargeFactoryComponent component() { return component; }
}
