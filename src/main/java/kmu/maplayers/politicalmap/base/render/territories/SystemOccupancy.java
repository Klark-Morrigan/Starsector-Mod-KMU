package kmu.maplayers.politicalmap.base.render.territories;

import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Who is in each star system, as a built map holds it: which bloc holds it, whether anything
 * stands in it at all, and - while a bloc is spotlighted - whether the pick is one of the things
 * standing in it.
 *
 * <p>The three travel together because a cell is drawn from all three at once. The holder decides
 * whether the cell fuses into a territory or draws alone; inhabitation tells a settled cell nobody
 * holds from the empty backdrop; and the pick's presence spares such a cell the recede a spotlight
 * sinks the rest of the sector under. Read from two different states of the sector, they produce a
 * cell no single reading of the sector would have drawn - a haven sunk under a spotlight it is the
 * subject of, or a colonised system still drawn as backdrop.
 *
 * <p>Which is why they move together too. A full rebuild resolves all three; the incremental
 * refresh re-derives all three per marked system, through the folds below rather than by editing
 * collections it was handed. Each fold reports whether it moved anything, because a change here is
 * exactly what obliges the system's cell to be drawn again - so no fact can be brought up to date
 * without the redraw it owes being noticed.
 *
 * <p>The collections are this type's own, copied on the way in. Anything reachable from a getter is
 * an unmodifiable view of them, so the only way to move a fact is a fold. That is the whole point
 * of the type rather than three loose collections on the built map: those had to be mutable by
 * caller convention, which a caller passing an immutable empty set kept perfectly until the first
 * fold threw.
 */
public final class SystemOccupancy {

    // Who holds each system. A system with no entry is held by nobody - which is not the same as
    // nobody living there, and reading it as such is what the inhabited set below exists to stop.
    private final Map<String, DominantHolder> holderBySystemId;

    // Every system something stands in, live colony or known ruin.
    private final Set<String> inhabitedSystemIds;

    // The settled systems the spotlit bloc lives in that no holder was resolved for. Empty off
    // filter, and empty on any view whose holding accounts for every inhabited system.
    private final Set<String> spotlitPresenceSystemIds;

    // The views every reader is answered through, wrapped once here rather than per ask: a getter
    // minting a fresh wrapper would put an allocation inside the per-cell styling loop.
    private final Map<String, DominantHolder> readableHolderBySystemId;
    private final Set<String> readableInhabitedSystemIds;
    private final Set<String> readableSpotlitPresenceSystemIds;

    private SystemOccupancy(
            Map<String, DominantHolder> holderBySystemId,
            Set<String> inhabitedSystemIds,
            Set<String> spotlitPresenceSystemIds) {

        this.holderBySystemId = holderBySystemId;
        this.inhabitedSystemIds = inhabitedSystemIds;
        this.spotlitPresenceSystemIds = spotlitPresenceSystemIds;

        readableHolderBySystemId = Collections.unmodifiableMap(holderBySystemId);
        readableInhabitedSystemIds = Collections.unmodifiableSet(inhabitedSystemIds);
        readableSpotlitPresenceSystemIds = Collections.unmodifiableSet(spotlitPresenceSystemIds);
    }

    /**
     * The occupancy one pass resolved, copied out of whatever the pass's three reads answered
     * with.
     *
     * <p>Copied rather than adopted, because a read is free to answer with an immutable empty
     * collection - the presence read does exactly that off filter - and this type's whole job is
     * that folding a fact cannot depend on which of its callers happened to answer with what.
     * Insertion-ordered copies, so a walk of any of the three runs in the order the pass resolved
     * it rather than in a hash's.
     *
     * @param holderBySystemId         who holds each system; a system nobody holds is absent
     * @param inhabitedSystemIds       every system something stands in
     * @param spotlitPresenceSystemIds the settled systems the spotlit bloc lives in that nobody
     *                                 holds; empty off filter
     * @return the occupancy, ready to be read and folded
     */
    public static SystemOccupancy createCopyOf(
            Map<String, DominantHolder> holderBySystemId,
            Set<String> inhabitedSystemIds,
            Set<String> spotlitPresenceSystemIds) {

        return new SystemOccupancy(
            new LinkedHashMap<>(holderBySystemId),
            new LinkedHashSet<>(inhabitedSystemIds),
            new LinkedHashSet<>(spotlitPresenceSystemIds));
    }

    /**
     * An empty occupancy: nobody holds anything, nothing stands anywhere, no pick lives anywhere.
     *
     * <p>What a build that never ran carries, and what a build fills in as it resolves. Named here
     * rather than spelt out as three empty collections at each such caller, so "nothing resolved
     * yet" is one value.
     *
     * @return an empty occupancy, ready to be folded into
     */
    public static SystemOccupancy createEmpty() {
        return createCopyOf(Map.of(), Set.of(), Set.of());
    }

    /**
     * @return who holds each system, keyed by system id; a system nobody holds is absent rather
     *         than present under a null
     */
    public Map<String, DominantHolder> getHolderBySystemId() {
        return readableHolderBySystemId;
    }

    /**
     * @return every system something is standing in, whoever holds it and whether or not this
     *         layer's holding accounts for them
     */
    public Set<String> getInhabitedSystemIds() {
        return readableInhabitedSystemIds;
    }

    /**
     * @return the settled systems the spotlit bloc lives in that no holder was resolved for; empty
     *         off filter
     */
    public Set<String> getSpotlitPresenceSystemIds() {
        return readableSpotlitPresenceSystemIds;
    }

    /**
     * Records who holds one system, or that nobody does.
     *
     * <p>Takes the absent holder as a null rather than through a second method, because the two
     * are one answer from the resolve that produced it: a system holding no counted colony
     * resolves to nobody, and the caller that has just asked should not have to branch on which
     * kind of answer it got before writing it down.
     *
     * <p>Reports nothing about what moved, unlike the folds below, because a holder change is not
     * a yes-or-no: what the redraw needs is both sides of the transfer, so the caller reads the
     * standing holder before it writes and has the pair without being told.
     *
     * @param systemId the system to record
     * @param holder   who holds it now, or null when nobody does
     */
    public void recordHolderOf(String systemId, DominantHolder holder) {

        if (holder == null) {
            holderBySystemId.remove(systemId);
        } else {
            holderBySystemId.put(systemId, holder);
        }
    }

    /**
     * Records whether anything stands in one system.
     *
     * @param systemId    the system to record
     * @param isInhabited whether something stands in it now
     * @return whether that moved, which is what obliges the system's cell to be drawn again
     */
    public boolean foldInhabitationOf(String systemId, boolean isInhabited) {
        return foldMembershipOf(inhabitedSystemIds, systemId, isInhabited);
    }

    /**
     * Records whether the spotlit bloc lives in one system no holder was resolved for.
     *
     * @param systemId  the system to record
     * @param isPresent whether the pick owns a counted colony there now
     * @return whether that moved, which is what obliges the system's cell to be drawn again
     */
    public boolean foldSpotlitPresenceOf(String systemId, boolean isPresent) {
        return foldMembershipOf(spotlitPresenceSystemIds, systemId, isPresent);
    }

    // Folds one system's membership of one of the sets above, reporting whether that moved. Both
    // folds are written through here so neither can report a change by a different rule than the
    // other - a cell is redrawn on either, and the two answers have to mean the same thing.
    private static boolean foldMembershipOf(
            Set<String> systemIds,
            String systemId,
            boolean isMember) {

        return isMember
            ? systemIds.add(systemId)
            : systemIds.remove(systemId);
    }
}
