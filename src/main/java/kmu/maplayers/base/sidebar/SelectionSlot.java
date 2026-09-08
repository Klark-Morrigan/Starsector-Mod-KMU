package kmu.maplayers.base.sidebar;

import kmu.maplayers.base.layer.MemoryKeyAddress;

/**
 * Where one sidebar picker keeps a selection: the mod and screen its panel's answers are kept under,
 * and the scope its list is listed under. The two together compose the key every such selection is
 * saved at, {@code <namespace prefix><store key><scope id>_<screen>}.
 *
 * <p>One value because the axes never travel apart and none means anything alone. A selection read
 * under the right scope but the wrong screen is the other panel's pick; one read under the right screen
 * but the wrong scope names something that list never offered; one read under another mod's namespace
 * is that mod's pick, made on a list this one never drew. All of them compile, and all of them read as
 * the feature working until the player sets the same preference twice and finds one of the two moved.
 * Handed over separately they are chosen again at every store call, where they can be crossed one way
 * and not the other; handed over as this they are chosen once, where the picker is built.
 *
 * <p>It is also the one place the axes are laid out in an order. A holder composing the key for
 * itself could put the screen before the scope, which reads the same and stores somewhere else - and a
 * key that stores somewhere else reads as absent, which is the un-picked state.
 *
 * @param screenSlot the mod and screen this selection is kept under - the same address the answers a
 *                   whole panel shares are kept at, which this one narrows
 * @param scopeId    the scope the selection was made in, opaque to every holder that takes one; never
 *                   blank
 */
public record SelectionSlot(
    ScreenSelectionSlot screenSlot,
    String scopeId) implements MemoryKeyAddress {

    public SelectionSlot {
        // A blank id composes every scope to one key, which is one third of the partitioning this type
        // carries: the picker that lost its id would quietly share a slot with every other picker on
        // the screen.
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("A selection's scope id must not be blank");
        }
    }

    /**
     * @param storeKeySuffix the store's own key, carrying none of this address's segments
     * @return the key that store is saved under for this mod, screen and scope
     */
    @Override
    public String resolveKeyFor(String storeKeySuffix) {
        // The scope rides with the store's own key rather than after the screen's segment, so a scoped
        // answer and the whole-panel answer beside it compose through the one screen slot.
        return screenSlot.resolveKeyFor(storeKeySuffix + scopeId);
    }
}
