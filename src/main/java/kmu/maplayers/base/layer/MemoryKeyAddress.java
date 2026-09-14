package kmu.maplayers.base.layer;

/**
 * What a preference's key is partitioned by: whatever turns one preference's base key into the key of
 * the single slot being read or written.
 *
 * <p>Two things answer this. A {@link ScreenMemoryScope} partitions by screen alone, which is every
 * preference a panel holds one of; a picker's selection slot partitions by screen and by the scope its
 * list was listed under, which is every preference a panel holds one of per list. A holder takes
 * whichever its own state is partitioned by and never composes a key itself, so the segments have one
 * spelling and no holder can order them differently - or leave one off, and quietly share a slot between
 * two panels.
 *
 * <p>Named for the address rather than for either implementer, because a holder has no business knowing
 * which of the two it was handed: what it needs is the key, and what it must not do is build one.
 */
public interface MemoryKeyAddress {

    /**
     * @param baseKey the preference's own sector-memory key, carrying none of this address's segments
     * @return the key that preference is stored at for this address
     */
    String resolveKeyFor(String baseKey);
}
