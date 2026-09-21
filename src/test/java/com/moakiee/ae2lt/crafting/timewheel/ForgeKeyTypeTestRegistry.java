package com.moakiee.ae2lt.crafting.timewheel;
import appeng.api.stacks.AEKeyType;
import net.minecraftforge.registries.*;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;
final class ForgeKeyTypeTestRegistry {
    static Supplier<IForgeRegistry<AEKeyType>> create(AEKeyType type) throws Exception {
        var name = new ResourceLocation("ae2lt", "timewheel_test_keys");
        var create = RegistryManager.class.getDeclaredMethod("createRegistry", ResourceLocation.class, RegistryBuilder.class);
        create.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registry = (IForgeRegistry<AEKeyType>) create.invoke(new RegistryManager("timewheel-test"), name,
                new RegistryBuilder<AEKeyType>().setName(name).disableSaving().disableSync());
        registry.register(type.getId(), type);
        return () -> registry;
    }
}
