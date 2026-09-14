package kmu.maplayers.base.sidebar;

import kmu.maplayers.base.layer.MemoryKeyAddress;
import kmu.maplayers.base.layer.ScreenMemoryScope;

/**
 * Where one mod keeps a sidebar answer that a screen holds whole: the mod the store belongs to and the
 * screen whose panel the answer was picked on. The two together compose the key such an answer is saved
 * at, {@code <namespace prefix><store key><screen>}.
 *
 * <p>The column count is what that shape is for - a panel is a fixed width the player laid their list
 * out inside, so every list on one screen wraps to one count - and it is also the axis pair
 * {@link SelectionSlot} narrows: a picker's selection is this same address with the list's own scope
 * added, so the two are built from one value rather than composing their shared segments twice.
 *
 * <p>One value for the reason {@link SelectionSlot} is one: an answer read under the right screen but
 * the wrong mod is another mod's pick, and it compiles and reads as the feature working until the two
 * mods' panels move each other's lists.
 *
 * @param namespace   the mod whose store this answer belongs to; there is no default to stand in for
 *                    a missing one
 * @param memoryScope the screen whose panel holds this answer
 */
public record ScreenSelectionSlot(
    MapLayerStoreNamespace namespace,
    ScreenMemoryScope memoryScope) implements MemoryKeyAddress {

    /**
     * @param storeKeySuffix the store's own key, carrying neither namespace nor screen
     * @return the key that store is saved under for this mod and screen
     */
    @Override
    public String resolveKeyFor(String storeKeySuffix) {
        return memoryScope.resolveKeyFor(namespace.resolveNamespacedKey(storeKeySuffix));
    }
}
