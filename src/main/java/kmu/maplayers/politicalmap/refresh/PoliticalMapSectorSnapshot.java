package kmu.maplayers.politicalmap.refresh;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.visibility.systems.MapVisibilityFingerprint;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.politicalmap.dominance.BlocCandidacy;
import kmu.maplayers.politicalmap.dominance.HolderRankingRules;
import kmu.maplayers.politicalmap.dominance.SystemDominance;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A snapshot of the political map's two refresh inputs, taken in one sector walk:
 * a scalar fingerprint of which systems are drawn, and a per-system map of who
 * holds each of them. {@link PoliticalMapStalenessSource} scans and diffs it; it
 * lives beside that source rather than in the domain layer because it is a refresh
 * input, not a rule of the map's model.
 *
 * <p>The two are shaped to match how their refreshes work, not for symmetry.
 * Visibility drives a geometry rebuild, which is inherently whole-map - the
 * Voronoi partition depends on the entire set of sites - so a scalar hash that
 * only answers "did the drawn set change?" is all the geometry step can act on.
 * Holder drives a per-system re-derive: the political overlay reshapes just the
 * systems whose holder changed and their neighbours, so a per-system holder map lets
 * the watcher name exactly which systems went stale rather than forcing a
 * whole-map re-colour. Keeping holding system-scoped is what lets the watcher
 * feed the same targeted stale set the event listeners do, so a change a listener
 * already marked and the watcher's own diff dedupe to one reshape.
 *
 * <p>Both come from a single walk, and that walk is the poll's rather than this scan's own.
 * Each system's colonies are selected once - through the pass the caller opened, so the same
 * selection answers the poll's other passengers - and that one set answers both outputs here:
 * whether anybody lives there, which the pass composes membership from, and the footprints the
 * dominance rule weighs. Membership is asked of the pass rather than derived from those
 * footprints, since a footprint is weighed only for a colony the
 * economy lists - a system settled by an unregistered one alone lives, and would go
 * missing from the fingerprint that notices it appear. The concerns stay separated: the
 * visibility contribution is {@link MapVisibilityFingerprint}'s and the dominant holder is
 * {@link SystemDominance}'s; this coordinator only sequences the shared walk.
 *
 * @param visibilityFingerprint what the drawn set hashes to, contributed per system under its
 *                              {@link SystemKey}, so two systems answering to one ID are two
 *                              contributions rather than one
 * @param ownerBySystemKey      the dominant holder of each owned drawn system, by faction ID.
 *                              Addressed by key so that two systems answering to one ID are two
 *                              entries, each diffed against its own last holder - keyed by ID,
 *                              the pair would be one entry holding whichever the walk reached
 *                              last, and a flip in the other could never be noticed
 */
public record PoliticalMapSectorSnapshot(
    int visibilityFingerprint,
    Map<SystemKey, String> ownerBySystemKey) {

    /**
     * Walks the sector once over the poll's own reading of it, reading the
     * dominance-weighting rules itself so the whole walk resolves every system under one rule
     * even if the player applies a settings change mid-scan. Lets a caller that shares one
     * pass across several walks (the staleness poll, which drives both this scan and the
     * motion walk from a single reading) pass it in while leaving weighting - which only this
     * scan needs - encapsulated here.
     *
     * @param pass the poll's reading of the sector: which systems are drawn, what may be shown
     *             of a colony, and the one walk of each system every reader shares; a pass
     *             over no sector yields an empty snapshot
     * @return the visibility fingerprint and the dominant holder (by faction ID) of each
     *         owned on-map system; a drawn-but-unowned system (a decivilised shell) is
     *         absent from the holder map
     */
    public static PoliticalMapSectorSnapshot scan(MapVisibilityPass pass) {
        return scan(pass, DominanceRules.readFromLunaSettings());
    }

    /**
     * Walks the sector once under an explicit weighting rule, for a caller that resolves it
     * itself rather than letting this class read the live settings.
     *
     * <p>Given the poll's pass rather than the sector behind it, so that a system this walk
     * selects is a system the poll's other walks are handed rather than select again. A scan
     * holding a sector could open a second reading of every system in the same tick, which is
     * the arrangement this signature makes unstateable.
     *
     * <p>Reaches past the pass's own membership answer for two things it alone needs: the
     * colonies each system holds, which the dominance fold weighs, and the revealed-decivilised
     * flag, which salts a drawn system's fingerprint. Both come off the pass rather than beside
     * it, so what this walk weighs is what the same walk drew.
     *
     * @param pass  the poll's reading of the sector: which systems are drawn, what may be shown
     *              of a colony, and the one walk of each system every reader shares; a pass
     *              over no sector yields an empty snapshot
     * @param rules the dominance-weighting rules for this pass - whether stability scales each
     *              rating and whether an attached station lifts it - before dominance is
     *              compared
     * @return the visibility fingerprint and the dominant holder (by faction ID) of
     *         each owned on-map system; a drawn-but-unowned system (a decivilised
     *         shell) is absent from the holder map
     */
    public static PoliticalMapSectorSnapshot scan(
            MapVisibilityPass pass,
            DominanceRules rules) {

        var sector = pass.sector();

        if (sector == null) {
            return new PoliticalMapSectorSnapshot(0, Map.of());
        }
        var visibility = 0;
        var ownerBySystemKey = new LinkedHashMap<SystemKey, String>();

        for (var system : sector.getStarSystems()) {

            // Asked of the pass rather than derived from the footprints below, which is the
            // narrower question: a footprint is only ever weighed for an economy-listed colony,
            // so a system settled by an unregistered one alone would read as empty here while the
            // drawn set - which asks the pass - draws it. The fingerprint would then never move
            // for it, and the map would go on showing whatever it last built there.
            //
            // Asked first so that an undrawn system costs nothing beyond it: everything below is
            // spent per drawn system, and the colony read it makes is the one the pass has already
            // memoised answering this.
            if (!pass.isDrawn(system)) {
                continue;
            }

            // Taken off the pass rather than read again: membership folds the decivilised world in
            // and cannot report it, but the fingerprint needs it on its own to salt a drawn
            // system's contribution, so a live-to-decivilised flip moves the hash without the
            // drawn set changing.
            var hasRevealedDecivilised = pass.isRevealedDecivilised(system);

            // The system's key, which this walk holds the system to read, and which both outputs
            // are addressed by: two systems answering to one ID contribute two values to the
            // fingerprint rather than one - keyed by ID, one of them entering the drawn set as the
            // other left would not move it - and hold two entries in the holder map.
            var systemKey = SystemKey.readKeyOf(system);

            visibility += MapVisibilityFingerprint.computeSystemContribution(
                systemKey,
                hasRevealedDecivilised);

            // The pass's one colony read per system, which the drawn-set answer above is composed
            // from too: membership asks it whether anybody lives here, the dominance rule ranks
            // the footprints it weighs out of it. A null economy (early load) reads as no colonies
            // rather than faulting.
            var systemColonies = pass.sectorIndex().readColoniesIn(system);

            var footprintByFactionId = KnownMarketFootprints.readByFaction(
                systemColonies,
                rules,
                pass.colonyKnowledge());

            // A decivilised-only system is drawn yet unowned, so it counts toward
            // visibility but is left out of the holder map - a system gaining or
            // losing a holder then reads as a diff against that absence.
            //
            // Barred under the same candidacy the fills are resolved under, though this walk only
            // fingerprints them: a colony founded under a heavier neutral station moves the fill to
            // that faction, and a holder read here without the bar would still say neutral both
            // before and after, leaving the map showing whatever it last built there.
            var dominantFactionId = SystemDominance.resolveDominantFactionId(
                footprintByFactionId,
                HolderRankingRules.createByLowestId(BlocCandidacy::isCandidateFaction));

            if (dominantFactionId != null) {
                ownerBySystemKey.put(systemKey, dominantFactionId);
            }
        }
        return new PoliticalMapSectorSnapshot(visibility, ownerBySystemKey);
    }
}
