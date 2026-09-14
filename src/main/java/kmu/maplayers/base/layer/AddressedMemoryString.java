package kmu.maplayers.base.layer;

import kmlib.starsector.memory.SectorMemoryString;

/**
 * One stored string held once per {@link MemoryKeyAddress} rather than once per save - the string sibling
 * of {@link AddressedMemoryFlag}. The holder declares its base key here, and every read and write names
 * the address whose slot it means, so a holder states what it stores and never how the key is spelled.
 *
 * <p>Built per call for the reason the flag's slot is, and carries no default: an absent key reads back
 * as null, which the holder resolves - what a stored key means, and what standing in for a missing one
 * amounts to, is the holder's own vocabulary rather than this class's.
 */
public final class AddressedMemoryString {

    private final String baseKey;

    /**
     * @param baseKey the preference's own sector-memory key, carrying no address segments; stable once
     *                shipped, since it is what the save serialises - renaming it reads as absent and
     *                silently drops every existing save's stored value
     */
    public AddressedMemoryString(String baseKey) {
        this.baseKey = baseKey;
    }

    /**
     * @param address the slot being read
     * @return that slot's stored string, or null when it holds none (never written, or read before the
     *         sector exists) - the caller resolves null to its own default
     */
    public String get(MemoryKeyAddress address) {
        return resolveSlot(address).get();
    }

    /**
     * Records the value in one slot. A no-op before the sector exists.
     *
     * @param address the slot being written
     * @param value   the string to store
     * @return whether the write landed - false only before the sector exists, so a caller can gate a
     *         follow-on side effect on a real write
     */
    public boolean set(MemoryKeyAddress address, String value) {
        return resolveSlot(address).set(value);
    }

    /**
     * Removes one slot's stored value. A no-op before the sector exists or when the slot holds nothing.
     *
     * @param address the slot being cleared
     * @return whether a stored value was removed, so a caller can gate a follow-on side effect on a
     *         real removal
     */
    public boolean clear(MemoryKeyAddress address) {
        return resolveSlot(address).clear();
    }

    private SectorMemoryString resolveSlot(MemoryKeyAddress address) {
        return new SectorMemoryString(address.resolveKeyFor(baseKey));
    }
}
