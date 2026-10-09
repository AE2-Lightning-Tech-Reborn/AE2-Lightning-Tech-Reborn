/**
 * Public, first-party API for AE2 Lightning Tech Reborn (mod id {@code ae2lt}).
 *
 * <p>This package, and its sub-packages
 * {@link com.moakiee.ae2lt.api.lightning lightning},
 * {@link com.moakiee.ae2lt.api.event event}, and
 * {@link com.moakiee.ae2lt.api.ids ids}, are the only stable contract for addon
 * authors. Anything else under {@code com.moakiee.ae2lt.*} is internal and may
 * change between minor versions without notice.
 *
 * <h2>Frozen on release</h2>
 * <ul>
 *   <li>All public type signatures in {@code com.moakiee.ae2lt.api.*}</li>
 *   <li>{@link com.moakiee.ae2lt.api.lightning.LightningTier} constants and their
 *       serialized names ({@code "high_voltage"}, {@code "extreme_high_voltage"})</li>
 *   <li>{@link com.moakiee.ae2lt.api.AE2LTCapabilities} {@code ResourceLocation}s</li>
 *   <li>The block entity and recipe IDs in
 *       {@link com.moakiee.ae2lt.api.ids}</li>
 *   <li>The fields and trigger timing of
 *       {@link com.moakiee.ae2lt.api.event.LightningCollectedEvent}</li>
 * </ul>
 *
 * <h2>Compilation and implementation boundaries</h2>
 * <p>Compile against the complete AE2LT artifact and its declared dependencies.
 * Public facade implementations delegate to internal classes; this package is
 * not a standalone API-only JAR. New contracts expose JDK, Minecraft, Forge,
 * AE2 and public AE2LT types. Legacy signatures that expose implementation types
 * remain for binary compatibility; prefer the documented public alternatives.
 */
package com.moakiee.ae2lt.api;
