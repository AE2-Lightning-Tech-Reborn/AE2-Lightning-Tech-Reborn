package com.moakiee.ae2lt.debug.planning;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;

/** Opt-in fixture JAR only: Forge disables its GameTest entry points in production. */
@Mod("ae2lt_planning_qa")
public final class PlanningOptimizationQaMod {
    private boolean registered;

    public PlanningOptimizationQaMod() {
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void commands(RegisterCommandsEvent event) {
        if (ForgeGameTestHooks.isGametestEnabled()) return;
        if (!registered) {
            GameTestRegistry.register(PlanningOptimizationGameTests.class);
            GlobalTestReporter.replaceWith(new Reporter());
            registered = true;
        }
        TestCommand.register(event.getDispatcher());
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !ForgeGameTestHooks.isGametestEnabled()) {
            GameTestTicker.SINGLETON.tick();
        }
    }

    private static final class Reporter implements TestReporter {
        private final Map<String, String> results = new TreeMap<>();
        private final LogTestReporter log = new LogTestReporter();

        public void onTestSuccess(GameTestInfo info) {
            results.putIfAbsent(info.getTestName(), "PASS");
            write();
        }

        public void onTestFailed(GameTestInfo info) {
            results.put(info.getTestName(), "FAIL");
            log.onTestFailed(info);
            write();
        }

        private void write() {
            var output = Path.of(System.getProperty("ae2lt.planningQa.report", "planning-qa-results.json"));
            var report = Map.of("expected", 5, "complete", results.size() == 5, "results", results);
            try {
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
