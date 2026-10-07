package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;

/** Origin metadata belongs to the network mount table, and is discarded on unmount. */
public interface PatternMigrationStorageOrigins {
    void ae2lt$registerMigrationStorage(MEStorage inventory, IStorageProvider provider);
}
