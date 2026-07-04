package kmu.politicalmap.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.politicalmap.domain.politics.DominanceWeighting;
import kmu.politicalmap.domain.politics.FactionFootprint;
import kmu.politicalmap.domain.politics.KnownMarketFootprints;
import kmu.politicalmap.domain.politics.SystemDominance;
import kmu.politicalmap.domain.visibility.DecivilisedPresence;
import kmu.politicalmap.domain.visibility.MapVisibleStars;
import kmu.politicalmap.domain.visibility.PoliticalMapVisibility;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A snapshot of the political map's two refresh inputs, taken in one sector walk:
 * a scalar fingerprint of which systems are drawn, and a per-system map of who
 * holds each of them. The sector watcher scans and diffs it; it lives beside the
 * watcher rather than in the domain layer because it is a refresh input, not a
 * rule of the map's model.
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
public record PoliticalMapSectorSnapshot(int visibilityFingerprint,
        Map<String, String> ownerBySystemId) {

    /**
     * Walks the sector once and records both refresh inputs for every on-map
     * system.
     *
     * <p>Reads the player's dominance-weighting rules once up front, so the whole
     * walk resolves every system under the same rule even if the player applies a
     * settings change mid-scan.
     *
     * @param sector the sector to scan; null yields an empty snapshot
     * @return the visibility fingerprint and the dominant owner (by faction id) of
     *         each owned on-map system; a drawn-but-unowned system (a decivilised
     *         shell) is absent from the owner map
     */
    public static PoliticalMapSectorSnapshot scan(SectorAPI sector) {
        return scan(sector, DominanceWeighting.readFromSettings());
    }

    /**
     * Walks the sector once under an explicit weighting rule, for a caller that
     * has already read the player's toggle for the surrounding pass.
     *
     * @param sector    the sector to scan; null yields an empty snapshot
     * @param weighting the dominance-weighting rules for this pass - whether
     *                  stability scales each rating and whether an attached station
     *                  lifts it - before dominance is compared
     * @return the visibility fingerprint and the dominant owner (by faction id) of
     *         each owned on-map system; a drawn-but-unowned system (a decivilised
     *         shell) is absent from the owner map
     */
    public static PoliticalMapSectorSnapshot scan(SectorAPI sector,
            DominanceWeighting weighting) {
        if (sector == null) {
            return new PoliticalMapSectorSnapshot(0, Map.of());
        }
        // Scanned once for the whole walk so the per-system access check stays an
        // O(1) lookup rather than rescanning hyperspace each time.
        var visibleStars = MapVisibleStars.scan(sector);
        var hasEconomy = sector.getEconomy() != null;
        var visibility = 0;
        var ownerBySystemId = new LinkedHashMap<String, String>();
        for (var system : sector.getStarSystems()) {
            // One economy read per system, shared by both concerns: its emptiness
            // is the inhabitation flag membership needs, and its footprints are
            // what the dominance rule ranks. A null economy (early load) reads as
            // no markets rather than faulting.
            Map<String, FactionFootprint> footprintByFactionId = hasEconomy
                    ? KnownMarketFootprints.readByFaction(sector, system, weighting)
                    : Map.of();
            var hasRevealedDecivilised = DecivilisedPresence.hasRevealedDecivilisedPlanet(system);
            var isInhabited = !footprintByFactionId.isEmpty() || hasRevealedDecivilised;
            if (!PoliticalMapVisibility.shouldAppearOnMap(system, visibleStars, isInhabited)) {
                continue;
            }
            var systemId = system.getId();
            visibility += PoliticalMapVisibility.computeVisibilityContribution(systemId,
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
