package com.moakiee.ae2lt.menu;

import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.logic.craft.migration.PatternMigrationSnapshot;
import net.minecraft.core.BlockPos;

/** Both controller and port expose the same server-owned task through their own menu token. */
public interface MatrixMigrationMenu {
    BlockPos getMigrationMenuPos();
    MatrixControllerBlockEntity getMigrationController();
    PatternMigrationSnapshot getMigrationSnapshot();
    void acceptMigrationSnapshot(PatternMigrationSnapshot snapshot);
}
