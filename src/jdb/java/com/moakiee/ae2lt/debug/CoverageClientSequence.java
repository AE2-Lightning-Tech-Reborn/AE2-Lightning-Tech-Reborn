package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;

public final class CoverageClientSequence {
    private static final List<String> PROBES = List.of("patternMigrationClientProbe", "miningClientProbe",
            "ioClientProbe", "jeiTransferCompatibilityProbe", "craftReportProbe", "pigmeeLightningClientProbe");
    private static final List<String> RESULTS = List.of("migration-client-result.txt", "mining-client-probe.txt",
            "io-port-result.txt", "jei-transfer-result.txt", "report-result.txt", "pigmee-lightning-client-probe.txt");
    private static int completed;

    private CoverageClientSequence() {
    }

    public static void finish(String probe) throws Exception {
        var mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("ae2lt.coverageAllClient")) {
            mc.stop();
            return;
        }
        if (completed >= PROBES.size() || !PROBES.get(completed).equals(probe)) {
            throw new AssertionError("Unexpected client probe completion: " + probe + " at " + completed);
        }
        var text = Files.readString(mc.gameDirectory.toPath().resolve(RESULTS.get(completed)));
        if (!text.startsWith("PASS")) throw new AssertionError("Client probe did not pass: " + probe + ": " + text);
        System.out.println("CLIENT_SEQUENCE_COMPLETED " + (completed + 1) + "/" + PROBES.size() + " " + probe);
        Files.writeString(mc.gameDirectory.toPath().resolve("client-sequence-progress.txt"),
                (completed + 1) + "/" + PROBES.size() + " " + probe + "\n");
        System.clearProperty("ae2lt." + PROBES.get(completed++));
        if (mc.player != null) mc.player.closeContainer();
        mc.setScreen(null);
        if (completed < PROBES.size()) {
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            System.setProperty("ae2lt." + PROBES.get(completed), "true");
            return;
        }
        var result = new StringBuilder("PASS: migration, mining, IO Port, JEI transfer, crafting report and Pigmee lightning\n");
        for (String id : List.of("ae2lt", "neoecoae", "extendedae_plus", "configuration", "ldlib", "jei")) {
            var mod = ModList.get().getModContainerById(id).orElseThrow();
            result.append(id).append('=').append(mod.getModInfo().getVersion()).append('\n');
        }
        ModList.get().getModContainerById("emi").ifPresent(mod -> result.append("emi=")
                .append(mod.getModInfo().getVersion()).append('\n'));
        Files.writeString(mc.gameDirectory.toPath().resolve("all-client-result.txt"), result);
        mc.stop();
    }
}
