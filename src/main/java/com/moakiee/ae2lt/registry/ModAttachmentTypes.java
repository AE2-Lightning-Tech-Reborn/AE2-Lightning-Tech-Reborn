package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AE2LightningTech.MODID);

    /** Rendering metadata; the authoritative origin remains the bolt's persistent weather tag. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> NATURAL_LIGHTNING =
            ATTACHMENT_TYPES.register("natural_lightning",
                    () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    private ModAttachmentTypes() {
    }
}
