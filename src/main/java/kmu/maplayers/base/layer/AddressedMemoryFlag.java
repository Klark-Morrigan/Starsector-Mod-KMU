package kmu.maplayers.base.layer;

import kmlib.starsector.memory.SectorMemoryFlag;

/**
 * One boolean preference held once per {@link MemoryKeyAddress} rather than once per save: the holder
 * declares its base key and its default here, and every read and write names the address whose slot it
 * means. The screen-suffixed key is composed inside, so a holder states what it stores and never how the
 * key is spelled.
 *
 * <p>The underlying wrapper is built per call rather than kept, because it holds only its key - the value
 * itself lives in sector memory - so there is nothing to cache per address and no map of them to grow as
 * screens or scopes are added.
 *
 * <p>Absence resolves to the declared default rather than to {@code false}, which is the distinction a
 * bare memory read cannot make: a save where the box was never touched and one where it was cleared are
 * the same read otherwise, and only one of them should answer with the shipped choice.
 */
public final class AddressedMemoryFlag {

    private final String baseKey;
    private final boolean defaultValue;

    /**
     * @param baseKey      the preference's own sector-memory key, carrying no address segments; stable
     *                     once shipped, since it is what the save serialises - renaming it resets every
     *                     existing save's choice to the default
     * @param defaultValue what a slot reads as while it holds nothing
     */
    public AddressedMemoryFlag(String baseKey, boolean defaultValue) {
        this.baseKey = baseKey;
        this.defaultValue = defaultValue;
    }

    /**
     * @param address the slot being read
     * @return that slot's stored value, or the default when it holds none (never written, or read
     *         before the sector exists)
     */
    public boolean isSet(MemoryKeyAddress address) {
        return resolveSlot(address).isSet();
    }

    /**
     * Records the value in one slot. A no-op before the sector exists, since there is no save to write
     * into yet.
     *
     * @param address the slot being written
     * @param isSet   the value to store
     * @return whether the write landed - false only before the sector exists, so a caller can gate a
     *         follow-on side effect (a repaint request) on a real write
     */
    public boolean set(MemoryKeyAddress address, boolean isSet) {
        return resolveSlot(address).set(isSet);
    }

    private SectorMemoryFlag resolveSlot(MemoryKeyAddress address) {
        return new SectorMemoryFlag(address.resolveKeyFor(baseKey), defaultValue);
    }
}
