package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.network.railgun.RailgunBeamChainFxPacket;
import com.moakiee.ae2lt.registry.ModSounds;

/**
 * Beam-chain arcs, hit sparks and positional sound. Short lifetimes prevent
 * overlapping effects when continuous fire repeats the chain four times a second.
 */
public final class RailgunBeamChainFx {

    private RailgunBeamChainFx() {}

    public static void play(RailgunBeamChainFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var path = p.chainPath();
        if (path.isEmpty()) return;

        // Each (i, i+1) is one arc segment. Same encoding as the charged-shot
        // chainPath — the server builds it that way in RailgunBeamService.
        for (int i = 0; i + 1 < path.size(); i += 2) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            // Short-lived arc: beam refreshes chains up to ~4/sec; longer
            // lifetimes would visually pile up.
            if (p.ehv()) {
                RailgunArcRenderer.spawnChain(a, b, 14);
            } else {
                RailgunArcRenderer.spawnHighVoltageChain(a, b, 14);
            }
            // Endpoint sparks at each chained target so the hit registers
            // clearly even when several enemies are stacked.
            for (int s = 0; s < 3; s++) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z,
                        (mc.level.random.nextDouble() - 0.5D) * 0.35D,
                        (mc.level.random.nextDouble() - 0.5D) * 0.35D,
                        (mc.level.random.nextDouble() - 0.5D) * 0.35D);
            }
        }

        // A faint flash + crackle at the primary impact so the chain initiation
        // is obvious. Volume kept low because the beam already loops a humming
        // SFX on its own; we don't want cumulative noise.
        Vec3 first = p.firstHit();
        mc.level.addParticle(ParticleTypes.FLASH, first.x, first.y, first.z, 0, 0, 0);
        if (p.soundEnabled()) {
            mc.level.playLocalSound(first.x, first.y, first.z,
                    ModSounds.RAILGUN_BEAM_CHAIN.get(), SoundSource.PLAYERS,
                    0.35F, 1.6F, false);
        }
    }
}
