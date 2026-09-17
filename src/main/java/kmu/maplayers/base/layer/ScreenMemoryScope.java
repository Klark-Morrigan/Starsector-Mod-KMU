package kmu.maplayers.base.layer;

import kmlib.starsector.memory.MemoryKeyAddress;

/**
 * One screen's segment of a sector-memory key, and the one way a per-screen preference resolves the key
 * it is saved under: {@code <preference key>_<segment>}.
 *
 * <p>A preference the player sets on a screen's sidebar is that screen's own, so every holder of one needs
 * a screen-suffixed key. Composed here rather than at each holder, so the suffix has one spelling and a
 * holder cannot put the segment in a different place, or leave it off, and quietly share one slot between
 * the two screens. A holder is handed the scope of the screen its control was placed on and resolves its
 * own base key through it; the screens themselves are named once, in {@link MapLayerScreens}.
 *
 * <p>A record, because two scopes with the same segment are the same screen: a scope travels with a
 * screen's picks and is compared, never identified, so a holder built under a copy of a screen's scope
 * reads and writes the same slots as one built under the original.
 *
 * <p>Where it sits in a signature says which of its two jobs it is doing. A store takes it first, because
 * there it is the address - {@code set(scope, value, board)} reads "in this slot, put this value". A
 * control builder takes it inside whatever value carries the panel's context down, because there it is
 * not addressing anything yet, only being carried to the store that will. The two meet at a control's
 * click, which unpacks the context and addresses the slot.
 *
 * @param screenSegment the screen's segment of the key, appended after the preference's own; never blank
 */
public record ScreenMemoryScope(String screenSegment) implements MemoryKeyAddress {

    // What separates the preference's key from the screen's segment: the same separator the keys already
    // use between their own words, so a composed key reads as one.
    private static final String SEGMENT_SEPARATOR = "_";

    public ScreenMemoryScope {
        // A blank segment would compose every preference to its bare key with a trailing separator, and
        // both screens to the same slot - the sharing this type exists to make impossible.
        if (screenSegment == null || screenSegment.isBlank()) {
            throw new IllegalArgumentException("A screen's memory segment must not be blank");
        }
    }

    /**
     * @param preferenceKey the preference's own sector-memory key, carrying no screen segment
     * @return the key that preference is saved under for this screen
     */
    @Override
    public String resolveKeyFor(String preferenceKey) {
        return preferenceKey + SEGMENT_SEPARATOR + screenSegment;
    }
}
