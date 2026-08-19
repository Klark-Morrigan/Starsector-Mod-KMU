package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.base.visibility.MapVisibility;
import kmu.maplayers.base.visibility.MapVisibilityOverrides;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

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
 * <p>Both come from a single walk. Each system's colonies are selected once, and that
 * one set answers both outputs: whether anybody lives there, which decides membership,
 * and the footprints the dominance rule weighs. Inhabitation is asked of the set rather
 * than read off those footprints, since a footprint is weighed only for a colony the
 * economy lists - a system settled by an unregistered one alone lives, and would go
 * missing from the fingerprint that notices it appear. The concerns stay separated: the
 * visibility contribution is {@link MapVisibility}'s and the dominant holder is
 * {@link SystemDominance}'s; this coordinator only sequences the shared walk.
 */
public record PoliticalMapSectorSnapshot(
    int visibilityFingerprint,
    Map<String, String> ownerBySystemId) {

    /**
     * Walks the sector once under visibility overrides the caller has already read,
     * reading the dominance-weighting rules itself so the whole walk resolves every
     * system under one rule even if the player applies a settings change mid-scan. Lets
     * a caller that shares one toggle read across several walks (the staleness poll,
     * which drives both this scan and the motion walk from a single read) pass the
     * toggles in while leaving weighting - which only this scan needs - encapsulated
     * here.
     *
     * @param sector              the sector to scan; null yields an empty snapshot
     * @param visibilityOverrides the widenings in force for this pass - undiscovered markets
     *                            fold into dominance and inhabitation, hidden systems are
     *                            admitted to the drawn set
     * @return the visibility fingerprint and the dominant holder (by faction id) of each
     *         owned on-map system; a drawn-but-unowned system (a decivilised shell) is
     *         absent from the holder map
     */
    public static PoliticalMapSectorSnapshot scan(
            SectorAPI sector,
            MapVisibilityOverrides visibilityOverrides) {
        return scan(
            sector,
            DominanceRules.readFromLunaSettings(),
            visibilityOverrides);
    }

    /**
     * Walks the sector once under an explicit weighting rule and visibility overrides,
     * for a caller that resolves both itself rather than letting this class read the
     * live settings.
     *
     * @param sector              the sector to scan; null yields an empty snapshot
     * @param rules               the dominance-weighting rules for this pass - whether
     *                            stability scales each rating and whether an attached station
     *                            lifts it - before dominance is compared
     * @param visibilityOverrides the widenings in force for this pass - undiscovered markets
     *                            fold into dominance and inhabitation, hidden systems are
     *                            admitted to the drawn set
     * @return the visibility fingerprint and the dominant holder (by faction id) of
     *         each owned on-map system; a drawn-but-unowned system (a decivilised
     *         shell) is absent from the holder map
     */
    public static PoliticalMapSectorSnapshot scan(
            SectorAPI sector,
            DominanceRules rules,
            MapVisibilityOverrides visibilityOverrides) {

        if (sector == null) {
            return new PoliticalMapSectorSnapshot(0, Map.of());
        }

        // Scanned once for the whole walk so the per-system access check stays an
        // O(1) lookup rather than rescanning hyperspace each time.
        var visibleStars = VisibleStars.scan(sector);

        var visibility = 0;
        var ownerBySystemId = new LinkedHashMap<String, String>();

        // The scan's own colony walk, opened here and discarded with the scan: a snapshot has to
        // read the sector as it stands at this moment, and an index outliving one would answer the
        // next scan off the sector this one saw - which is precisely the change a scan exists to
        // notice.
        var colonies = new SystemColoniesIndex(sector);

        for (var system : sector.getStarSystems()) {

            // One colony read per system, shared by both concerns: membership asks it whether
            // anybody lives here, the dominance rule ranks the footprints it weighs out of it. A
            // null economy (early load) reads as no colonies rather than faulting.
            var systemColonies = colonies.readColoniesIn(system);

            // Read before inhabitation and handed to it, rather than left for that rule to
            // re-derive: it is wanted here anyway, to salt a drawn system's fingerprint, and it
            // walks every planet in the system.
            var hasRevealedDecivilised = DecivilisedMarkets.hasRevealedDecivilisedPlanet(system);

            // Asked through the shared rule rather than off the footprints below, which is the
            // narrower question: a footprint is only ever weighed for an economy-listed colony,
            // so a system settled by an unregistered one alone would read as empty here while the
            // drawn set - which asks the rule - draws it. The fingerprint would then never move
            // for it, and the map would go on showing whatever it last built there.
            var isInhabited = MapVisibility.isInhabited(
                systemColonies,
                hasRevealedDecivilised,
                visibilityOverrides);

            var footprintByFactionId = KnownMarketFootprints.readByFaction(
                systemColonies,
                rules,
                visibilityOverrides.shouldIncludeUndiscoveredMarkets());

            if (!MapVisibility.shouldAppearOnMap(
                    system,
                    visibleStars,
                    isInhabited,
                    visibilityOverrides)) {
                continue;
            }
            var systemId = system.getId();
            visibility += MapVisibility.computeVisibilityContribution(
                systemId,
                hasRevealedDecivilised);
                    
            // A decivilised-only system is drawn yet unowned, so it counts toward
            // visibility but is left out of the holder map - a system gaining or
            // losing an holder then reads as a diff against that absence.
            var dominantFactionId =
                SystemDominance.resolveDominantFactionId(footprintByFactionId);
                
            if (dominantFactionId != null) {
                ownerBySystemId.put(systemId, dominantFactionId);
            }
        }
        return new PoliticalMapSectorSnapshot(visibility, ownerBySystemId);
    }
}
