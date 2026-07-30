package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;

import kmu.maplayers.base.visibility.PoliticalMapVisibility;
import kmu.maplayers.politicalmap.base.PoliticalMapDevOverrides;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
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
 * Ownership drives a per-system re-derive: the political overlay reshapes just the
 * systems whose owner changed and their neighbours, so a per-system owner map lets
 * the watcher name exactly which systems went stale rather than forcing a
 * whole-map re-colour. Keeping ownership system-scoped is what lets the watcher
 * feed the same targeted stale set the event listeners do, so a change a listener
 * already marked and the watcher's own diff dedupe to one reshape.
 *
 * <p>Both come from a single walk. Each system is read from the economy once - the
 * same footprint read sizes its dominance and tells whether it is inhabited - and
 * that one read feeds both outputs. The concerns stay separated: the visibility
 * contribution is {@link PoliticalMapVisibility}'s and the dominant owner is
 * {@link SystemDominance}'s; this coordinator only sequences the shared walk.
 */
public record PoliticalMapSectorSnapshot(
        int visibilityFingerprint,
        Map<String, String> ownerBySystemId) {

    /**
     * Walks the sector once under dev reveal overrides the caller has already read,
     * reading the dominance-weighting rules itself so the whole walk resolves every
     * system under one rule even if the player applies a settings change mid-scan. Lets
     * a caller that shares one override read across several walks (the staleness poll,
     * which drives both this scan and the motion walk from a single toggle read) pass
     * the overrides in while leaving weighting - which only this scan needs -
     * encapsulated here.
     *
     * @param sector    the sector to scan; null yields an empty snapshot
     * @param overrides the dev reveal overrides for this pass - show-all-factions folds
     *                  undiscovered colonies into dominance and inhabitation,
     *                  force-all-systems admits every system to the drawn set
     * @return the visibility fingerprint and the dominant owner (by faction id) of each
     *         owned on-map system; a drawn-but-unowned system (a decivilised shell) is
     *         absent from the owner map
     */
    public static PoliticalMapSectorSnapshot scan(
            SectorAPI sector,
            PoliticalMapDevOverrides overrides) {
        return scan(
                sector,
                DominanceRules.readFromLunaSettings(),
                overrides);
    }

    /**
     * Walks the sector once under an explicit weighting rule and the normal reveal
     * gates.
     *
     * @param sector the sector to scan; null yields an empty snapshot
     * @param rules  the dominance-weighting rules for this pass
     * @return the visibility fingerprint and the dominant owner (by faction id) of
     *         each owned on-map system
     */
    public static PoliticalMapSectorSnapshot scan(
            SectorAPI sector,
            DominanceRules rules) {
        return scan(
                sector,
                rules,
                PoliticalMapDevOverrides.NONE);
    }

    /**
     * Walks the sector once under an explicit weighting rule and dev reveal overrides,
     * for a caller that has already read the player's toggles for the surrounding pass.
     *
     * @param sector    the sector to scan; null yields an empty snapshot
     * @param rules     the dominance-weighting rules for this pass - whether
     *                  stability scales each rating and whether an attached station
     *                  lifts it - before dominance is compared
     * @param overrides the dev reveal overrides for this pass - show-all-factions folds
     *                  undiscovered colonies into dominance and inhabitation,
     *                  force-all-systems admits every system to the drawn set
     * @return the visibility fingerprint and the dominant owner (by faction id) of
     *         each owned on-map system; a drawn-but-unowned system (a decivilised
     *         shell) is absent from the owner map
     */
    public static PoliticalMapSectorSnapshot scan(
            SectorAPI sector,
            DominanceRules rules,
            PoliticalMapDevOverrides overrides) {

        if (sector == null) {
            return new PoliticalMapSectorSnapshot(0, Map.of());
        }

        // Scanned once for the whole walk so the per-system access check stays an
        // O(1) lookup rather than rescanning hyperspace each time.
        var visibleStars = VisibleStars.scan(sector);
        var hasEconomy = sector.getEconomy() != null;
        var visibility = 0;
        var ownerBySystemId = new LinkedHashMap<String, String>();

        for (var system : sector.getStarSystems()) {
            // One economy read per system, shared by both concerns: its emptiness
            // is the inhabitation flag membership needs, and its footprints are
            // what the dominance rule ranks. A null economy (early load) reads as
            // no markets rather than faulting.
            Map<String, MarketFootprint> footprintByFactionId = hasEconomy
                    ? KnownMarketFootprints.readByFaction(
                            sector,
                            system,
                            rules,
                            overrides.isShowingAllFactions())
                    : Map.of();

            var hasRevealedDecivilised = DecivilisedMarkets.hasRevealedDecivilisedPlanet(system);
            var isInhabited = !footprintByFactionId.isEmpty() || hasRevealedDecivilised;

            if (!PoliticalMapVisibility.shouldAppearOnMap(
                    system,
                    visibleStars,
                    isInhabited,
                    overrides.isForcingAllSystemsOnMap())) {
                continue;
            }
            var systemId = system.getId();
            visibility += PoliticalMapVisibility.computeVisibilityContribution(
                    systemId,
                    hasRevealedDecivilised);
                    
            // A decivilised-only system is drawn yet unowned, so it counts toward
            // visibility but is left out of the owner map - a system gaining or
            // losing an owner then reads as a diff against that absence.
            var dominantFactionId =
                    SystemDominance.resolveDominantFactionId(footprintByFactionId);
            if (dominantFactionId != null) {
                ownerBySystemId.put(systemId, dominantFactionId);
            }
        }
        return new PoliticalMapSectorSnapshot(visibility, ownerBySystemId);
    }
}
