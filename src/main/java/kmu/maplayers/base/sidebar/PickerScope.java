package kmu.maplayers.base.sidebar;

/**
 * Which picker's list an answer belongs to: the mod that built it and the scope its list was listed
 * under. {@link SelectionSlot} without the screen, for the answers a sector holds rather than a save.
 *
 * <p>Screen-less on purpose, and it is the one address here that is. A hover is previewed over paint
 * already on the map, so only the screen being looked at can have one - and the pass that reads it
 * back is drawing that screen, with no pick of its own to resolve a screen from.
 *
 * <p>The mod is not optional even here. The scope ID is opaque and every consumer picks its own, so
 * two mods listing under {@code "factions"} would preview each other's rows - the same collision the
 * persisted stores take {@link MapLayerStoreNamespace} for, arriving unpersisted and per sector.
 *
 * @param namespace the mod whose picker this list belongs to
 * @param scopeId   the scope the list was listed under, opaque to every holder that takes one; never
 *                  blank
 */
public record PickerScope(
    MapLayerStoreNamespace namespace,
    String scopeId) {

    public PickerScope {
        // A blank ID composes every list to one entry, so the picker that lost its ID would preview
        // whatever row the pointer last rested on in any other list on the panel.
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("A picker's scope id must not be blank");
        }
    }

    /**
     * The scope of the picker one selection slot was built for, so a hover reported through that
     * picker and the pick filed beside it cannot end up naming two different lists.
     *
     * @param slot the slot the picker files its persisted answers under
     * @return that picker's scope
     */
    public static PickerScope resolveScopeOf(SelectionSlot slot) {
        return new PickerScope(slot.screenSlot().namespace(), slot.scopeId());
    }
}
