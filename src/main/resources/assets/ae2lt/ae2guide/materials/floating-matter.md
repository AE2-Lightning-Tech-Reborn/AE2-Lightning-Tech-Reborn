---
navigation:
  title: Floating Matter
  icon: ae2lt:floating_matter
  parent: materials/materials-index.md
item_ids:
  - ae2lt:floating_matter
---

# Floating Matter

<ItemImage id="ae2lt:floating_matter" scale="2" float="left" />

**Floating Matter** is captured from Shulker Bullets and used in late-game crafting and flight-related equipment modules.

## Capture Mechanics

1. Enchant an <ItemLink id="ae2:annihilation_plane" /> with **Silk Touch** and connect it to an active, powered ME network with available item storage.
2. Orient the **front face** of the plane toward incoming Shulker Bullets.
3. When a Shulker Bullet impacts the exact side of the cable block hosting that plane, the network pays power and attempts to insert 1 **Floating Matter**:
   * On success, the bullet vanishes without hitting a target.
   * If power or storage is unavailable, or the bullet hits a different face, capture fails and the bullet resolves its normal collision.

> Controlled by the `shulkerBulletCollection` server config option.

<OptionalSection modId="apothic_attributes">
## Apothic Attributes Brewing

When Apothic Attributes is installed and its target potion is registered, **Floating Matter** can be brewed with an Awkward Potion to produce that potion. Check your recipe viewer for the exact effect and duration registered by your modpack.
</OptionalSection>
