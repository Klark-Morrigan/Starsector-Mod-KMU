package kmu.maplayers.base.sidebar;

import kmu.maplayers.base.layer.MemoryKeyAddress;
import kmu.maplayers.base.layer.ScreenMemoryScope;

/**
 * Where one sidebar picker keeps a selection: the screen whose panel the picker was built for, and the
 * scope its list is listed under. The two together compose the key every such selection is saved at,
 * {@code <preference key prefix><scope id>_<screen>}.
 *
 * <p>One value because the two never travel apart and neither means anything alone. A selection read
 * under the right scope but the wrong screen is the other panel's pick; one read under the right screen
 * but the wrong scope names something that list never offered. Both compile, and both read as the
 * feature working until the player sets the same preference twice and finds one of the two moved.
 * Handed over separately they are chosen again at every store call, where they can be crossed one way
 * and not the other; handed over as this they are chosen once, where the picker is built.
 *
 * <p>It is also the one place the two axes are laid out in an order. A holder composing the key for
 * itself could put the screen before the scope, which reads the same and stores somewhere else - and a
 * key that stores somewhere else reads as absent, which is the un-picked state.
 *
 * @param memoryScope the screen whose panel holds this selection
 * @param scopeId     the scope the selection was made in, opaque to every holder that takes one; never
 *                    blank
 */
public record SelectionSlot(
    ScreenMemoryScope memoryScope,
    String scopeId) implements MemoryKeyAddress {

    public SelectionSlot {
        // A blank id composes every scope to one key, which is half the partitioning this type carries:
        // the picker that lost its id would quietly share a slot with every other picker on the screen.
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("A selection's scope id must not be blank");
        }
    }

    /**
     * @param preferenceKeyPrefix the preference's own key prefix, carrying neither scope nor screen
     * @return the key that preference is saved under for this screen and scope
     */
    @Override
    public String resolveKeyFor(String preferenceKeyPrefix) {
        return memoryScope.resolveKeyFor(preferenceKeyPrefix + scopeId);
    }
}
