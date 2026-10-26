package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who is in each star system, as a built map owns it: which owner owns it and how its fill draws
 * inside that owner's border, whether anything stands in it at all, and - while an owner is
 * spotlighted - whether the pick is one of the things standing in it.
 *
 * <p>The four travel together because a map is drawn from all four at once. The owner decides
 * whether the cell fuses into a cluster group or draws alone, and its fill state how the group
 * fills over it; inhabitation tells a settled cell nobody owns from the empty backdrop; and the
 * pick's presence spares such a cell the recede a spotlight sinks the rest of the sector under. Read
 * from two different states of the sector, they produce a cell no single reading of the sector would
 * have drawn - a haven sunk under a spotlight it is the subject of, or a colonised system still
 * drawn as backdrop.
 *
 * <p>Which is why they move together too. A full rebuild resolves all four; the incremental
 * refresh re-derives all four per marked system, through the folds below rather than by editing
 * collections it was handed. Each fold lets the caller tell whether it moved anything, because a
 * change here is exactly what obliges the system's cell or its owner's group to be drawn again - so
 * no fact can be brought up to date without the redraw it owes being noticed.
 *
 * <p>The collections are this type's own, copied on the way in. Anything reachable from a getter is
 * an unmodifiable view of them, so the only way to move a fact is a fold. Loose collections on the
 * built map would be kept from moving by caller convention alone.
 */
public final class SystemOccupancy {

    // Who owns each system. A system with no entry is owned by nobody - which is not the same as
    // nobody living there, and reading it as such is what the inhabited set below exists to stop.
    private final Map<SystemKey, SystemOwner> ownerBySystemKey;

    // How each owned system fills, for the systems that fill other than solid. Written beside the
    // owner and only through the same fold, so a system cannot keep a state its owner no longer
    // draws it in.
    private final Map<SystemKey, FillState> nonSolidFillStateBySystemKey;

    // Every system something stands in, as the owner source counts it.
    private final Set<SystemKey> inhabitedSystemKeys;

    // The settled systems the spotlit owner lives in that no owner was resolved for. Empty off
    // filter, and empty on any view whose ownership accounts for every inhabited system.
    private final Set<SystemKey> spotlitPresenceSystemKeys;

    // The views every reader is answered through, wrapped once here rather than per ask: a getter
    // minting a fresh wrapper would put an allocation inside the per-cell styling loop.
    private final Map<SystemKey, SystemOwner> readableOwnerBySystemKey;
    private final Set<SystemKey> readableInhabitedSystemKeys;
    private final Set<SystemKey> readableSpotlitPresenceSystemKeys;

    private SystemOccupancy(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            Map<SystemKey, FillState> nonSolidFillStateBySystemKey,
            Set<SystemKey> inhabitedSystemKeys,
            Set<SystemKey> spotlitPresenceSystemKeys) {

        this.ownerBySystemKey = ownerBySystemKey;
        this.nonSolidFillStateBySystemKey = nonSolidFillStateBySystemKey;
        this.inhabitedSystemKeys = inhabitedSystemKeys;
        this.spotlitPresenceSystemKeys = spotlitPresenceSystemKeys;

        readableOwnerBySystemKey = Collections.unmodifiableMap(ownerBySystemKey);
        readableInhabitedSystemKeys = Collections.unmodifiableSet(inhabitedSystemKeys);
        readableSpotlitPresenceSystemKeys = Collections.unmodifiableSet(spotlitPresenceSystemKeys);
    }

    /**
     * The occupancy one pass resolved, copied out of what the owner source answered.
     *
     * <p>Copied rather than adopted, because a read is free to answer with an immutable empty
     * collection - the presence read does exactly that off filter - and this type's whole job is
     * that folding a fact cannot depend on which of its callers happened to answer with what.
     * Insertion-ordered copies, so a walk of any of them runs in the order the pass resolved it
     * rather than in a hash's.
     *
     * @param owners who owns each system and how it fills, every system something stands in, and
     *               where the spotlit owner lives among the unowned ones
     * @return the occupancy, ready to be read and folded
     */
    public static SystemOccupancy createCopyOf(ResolvedOwners owners) {

        return new SystemOccupancy(
            new LinkedHashMap<>(owners.ownerBySystemKey()),
            new LinkedHashMap<>(owners.fillStates().getNonSolidFillStateBySystemKey()),
            new LinkedHashSet<>(owners.inhabitedSystemKeys()),
            new LinkedHashSet<>(owners.spotlitPresenceSystemKeys()));
    }

    /**
     * An empty occupancy: nobody owns anything, nothing stands anywhere, no pick lives anywhere.
     *
     * <p>What a build that never ran carries, and what a build fills in as it resolves. Named here
     * rather than spelt out as empty collections at each such caller, so "nothing resolved yet" is
     * one value.
     *
     * @return an empty occupancy, ready to be folded into
     */
    public static SystemOccupancy createEmpty() {
        return createCopyOf(ResolvedOwners.createEmpty());
    }

    /**
     * @return who owns each system, keyed by {@link SystemKey}; a system nobody owns is absent
     *         rather than present under a null
     */
    public Map<SystemKey, SystemOwner> getOwnerBySystemKey() {
        return readableOwnerBySystemKey;
    }

    /**
     * Who owns one system.
     *
     * <p>The read most callers want, and the reason the whole map is not it: a caller asking about
     * one system would otherwise take the entire ownership and look a key up in it, which hands out
     * far more than the question needs and answers one hop further away, through the unmodifiable
     * view rather than off the ownership itself. The map read stays for the folds that really do span
     * every system - the cell grouping, the owner-ID projection the shaper compares against, and
     * the label styling snapshot.
     *
     * <p>Answers null for a system nobody owns, which is what an absent entry means. A cell drawn as
     * no star names no system, and answers nobody too: it is answered here rather than probing the
     * owner map with a null key, so a caller with such a cell asks like any other.
     *
     * @param systemKey the system to read, or null for a cell with no star of its own
     * @return its owner, or null where nobody owns it or there is no system to own
     */
    public SystemOwner readOwnerOf(SystemKey systemKey) {

        if (systemKey == null) {
            return null;
        }
        return ownerBySystemKey.get(systemKey);
    }

    /**
     * Who owns one system and how its fill draws, as the one answer a re-derive compares its own
     * against.
     *
     * @param systemKey the system to read
     * @return its owner and fill state; {@link ResolvedSystemOwner#UNOWNED} where nobody owns it
     */
    public ResolvedSystemOwner readResolvedOwnerOf(SystemKey systemKey) {

        var owner = ownerBySystemKey.get(systemKey);
        return owner == null
            ? ResolvedSystemOwner.UNOWNED
            : new ResolvedSystemOwner(
                owner,
                nonSolidFillStateBySystemKey.getOrDefault(systemKey, FillState.SOLID));
    }

    /**
     * How every system fills as this occupancy now stands, copied for one cluster-group builder.
     *
     * <p>A copy rather than a view, so a builder traces every owner of its pass against one state
     * of the map even where a fold lands between two of its owners.
     *
     * @return the state each system fills in; solid for any system not named
     */
    public SystemFillStates snapshotFillStates() {
        return SystemFillStates.createOf(nonSolidFillStateBySystemKey);
    }

    /**
     * Who owns the cells one cluster group is made of.
     *
     * <p>Asked of the first member alone, since the grouping keyed every member by the owner of the
     * system it draws as, so each answers the same. Read through the system a cell draws as rather
     * than the cell, which is how an absorbed cell with no star of its own still finds its owner.
     *
     * @param cellGrouping   how the drawn cells group under these owners
     * @param memberCellKeys the group's cells, at least one
     * @return the owner they were grouped under
     */
    public SystemOwner readOwnerOfMembers(
            CellGrouping cellGrouping,
            List<SystemKey> memberCellKeys) {

        return readOwnerOf(cellGrouping.resolveDrawnSystemKeyOf(memberCellKeys.get(0)));
    }

    /**
     * @return every system something is standing in, whoever owns it and whether or not this
     *         layer's ownership accounts for them
     */
    public Set<SystemKey> getInhabitedSystemKeys() {
        return readableInhabitedSystemKeys;
    }

    /**
     * @return the settled systems the spotlit owner lives in that no owner was resolved for; empty
     *         off filter
     */
    public Set<SystemKey> getSpotlitPresenceSystemKeys() {
        return readableSpotlitPresenceSystemKeys;
    }

    /**
     * Which of the given systems nobody owns.
     *
     * <p>The candidate rule for a spotlit owner's presence, which is only ever asked about systems
     * the ownership could attribute to nobody: one somebody owns already draws in that owner's
     * cluster group, so where the pick also lives there changes nothing. Asked over a caller's own
     * candidates rather than over the whole map, since both callers already know the narrow set
     * they care about - every settled system for a full build, the marked ones for a refresh.
     *
     * @param candidateSystemKeys the systems to filter, in the order they are to be answered in
     * @return those of them no owner was resolved for
     */
    public Set<SystemKey> selectUnownedSystemKeysAmong(Set<SystemKey> candidateSystemKeys) {
        // The owner side's own rule rather than a copy of it, so the source that resolves the
        // whole sector and this fold cannot come to disagree about what "unowned" means.
        return SystemOwner.selectUnownedSystemKeysAmong(ownerBySystemKey, candidateSystemKeys);
    }

    /**
     * Records who owns one system and how it fills, or that nobody owns it.
     *
     * <p>Takes the absent owner as {@link ResolvedSystemOwner#UNOWNED} rather than through a second
     * method, because the two are one answer from the resolve that produced it: a system with
     * nothing counted in it resolves to nobody, and the caller that has just asked should not have
     * to branch on which kind of answer it got before writing it down.
     *
     * <p>Reports nothing about what moved, unlike the folds below, because an owner change is not
     * a yes-or-no: what the redraw needs is both sides of the transfer, so the caller reads the
     * standing answer before it writes and has the pair without being told.
     *
     * @param systemKey the system to record
     * @param resolved  who owns it now and how it fills
     */
    public void recordOwnerOf(SystemKey systemKey, ResolvedSystemOwner resolved) {

        if (resolved.owner() == null) {
            ownerBySystemKey.remove(systemKey);
        } else {
            ownerBySystemKey.put(systemKey, resolved.owner());
        }

        if (resolved.fillState() == FillState.SOLID) {
            nonSolidFillStateBySystemKey.remove(systemKey);
        } else {
            nonSolidFillStateBySystemKey.put(systemKey, resolved.fillState());
        }
    }

    /**
     * Records whether anything stands in one system.
     *
     * @param systemKey   the system to record
     * @param isInhabited whether something stands in it now
     * @return whether that moved, which is what obliges the system's cell to be drawn again
     */
    public boolean foldInhabitationOf(SystemKey systemKey, boolean isInhabited) {
        return foldMembershipOf(inhabitedSystemKeys, systemKey, isInhabited);
    }

    /**
     * Records whether the spotlit owner lives in one system no owner was resolved for.
     *
     * @param systemKey the system to record
     * @param isPresent whether the pick has something counted standing there now
     * @return whether that moved, which is what obliges the system's cell to be drawn again
     */
    public boolean foldSpotlitPresenceOf(SystemKey systemKey, boolean isPresent) {
        return foldMembershipOf(spotlitPresenceSystemKeys, systemKey, isPresent);
    }

    // Folds one system's membership of one of the sets above, reporting whether that moved. Both
    // folds are written through here so neither can report a change by a different rule than the
    // other - a cell is redrawn on either, and the two answers have to mean the same thing.
    private static boolean foldMembershipOf(
            Set<SystemKey> systemKeys,
            SystemKey systemKey,
            boolean isMember) {

        return isMember
            ? systemKeys.add(systemKey)
            : systemKeys.remove(systemKey);
    }
}
