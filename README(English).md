# Transmutation Convenient

## This mod is an add-on for ProjectE, dedicated to improving its interoperability with other mods and reducing cumbersome operations.

*Currently only supports Minecraft 1.20.1 on Forge.*

### Required mods — missing one may prevent the game from launching:
- Just Enough Items
- ProjectE

### Optional mods — missing one may cause some content to be unavailable:
- Re:Avaritia
- Colytra
- Ice and Fire: Community Edition

---

### Problems this mod solves:

- Fixed an issue in ProjectE where `the vanilla elytra has no EMC value`, `even manually adding an EMC value with commands does not add the expected value`, and `after successfully adding an EMC value with commands, an elytra with less than full durability still has no EMC value`; the elytra is now assigned an EMC value of 5,000.

- Fixed an issue in Re:Avaritia where items crafted with the `Extreme Smithing Table` were not automatically assigned EMC values.

- Fixed an issue where, when Ice and Fire: Community Edition, ProjectE, and ProjectE Integration were loaded together, the EMC values of all items failed to load correctly.

- Fixed an issue with the area-of-effect attack that ProjectE's `Dark Matter Sword`, `Red Matter Sword`, and `Red Matter Katar` trigger by default when the C key is pressed: in `Slay Hostile Mobs` mode, the attack would damage neither tamed nor untamed `dragons` from Ice and Fire: Community Edition.

- Fixed an issue where ProjectE's `Harvest Goddess Band` and `Archangel's Smite` were not registered as `rings` in the Curios API; they can now trigger their proper functions when placed in the Curios API's `ring` slot.

### Features added:

- Once you have learned the transmutation recipe and have enough EMC, using Just Enough Items' one-click material-filling feature in Minecraft's vanilla `Crafting Table`, as well as in Re:Avaritia's `Sculk Crafting Table`, `Nether Crafting Table`, `End Crafting Table`, `Extreme Crafting Table`, and `Extreme Smithing Table`, will automatically purchase any missing materials from your personal EMC account.

- When you carry ProjectE's `Portable Transmutation Table` in your inventory, the transmutation recipes for items in your inventory will be learned automatically.

- ProjectE's `Repair Talisman` can now repair the elytra on Colytra chestplates created by fusing a chestplate with an elytra.

- Added the `EMC Pipe` block, which can transfer EMC according to **priority**. It also supports connecting to a Transmutation Table block to automatically deposit EMC into the personal EMC account of the player who placed the pipe.

- Added the `Auto Vending Machine` block, which automatically sells inserted items for EMC and deposits the proceeds into the personal EMC account of the player who placed the machine.

- Added the `Black Hole Bucket` item, which has three modes for attracting items, attracting fluids, and throwing Re:Avaritia's `Endest Pearl`. It has its own separate storage space and can store fluids. Using the `Black Hole Bucket` on an `Auto Vending Machine` with `Shift + right-click` will sell all of its contents at once, including the fluids inside.

- Added a recipe mapper for Ice and Fire: Community Edition's `Dragon Forge`, so that the items it produces can be automatically assigned EMC values by ProjectE based on their crafting recipes.

- Added the command `/sePE true`, which disables some of ProjectE's items and equipment and changes some crafting recipes. The command `/tcPE false` reverts these changes. It is designed for players who believe some equipment is too powerful and breaks game balance but still want to use some of ProjectE's trinkets.

- When a player is riding a `dragon` from Ice and Fire: Community Edition, dismounting from the `dragon` grants a single instance of fall damage immunity, which is cleared once triggered.

- Registered ProjectE's `Interdiction Torch` as a `charm` in the Curios API, so that when placed in the Curios API's `charm` slot, it can trigger all the functions it has when placed as a block, except for lighting.

### Issues found and fixed:

- Fixed an issue in the Re:Avaritia integration where items crafted with the `Extreme Smithing Table` received automatically assigned EMC values far lower than the total EMC value of their ingredients.

- Fixed an issue where the behavior of `/sePE true` and `/tcPE false` modifying items and crafting recipes did not cause their EMC values to change.

---

## Development background:

I have long been troubled by Re:Avaritia's overly complex and diverse crafting materials. Because I repeatedly fell into the vicious cycle of "searching the Transmutation Table for the corresponding material → discovering that other materials are still needed → continuing to search," I started with adding an automatic purchase-and-fill feature from the EMC account to Just Enough Items. After many iterations, it has become the current mod.

**Every feature comes from a specific problem I, the author, encountered while playing.**

This is also the origin of the name `Transmutation Convenient`; I hope every player can experience the **convenience** I longed for.

---

# Contact:
- bilibili: `@鐵墓-鐵大王`
- QQ: `3178802625`

**You are welcome to report issues, but please do not expect me to fix them quickly.**
