package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.PoliticalMapDevOverrides;
import kmu.maplayers.politicalmap.base.politics.weighting.DominanceRules;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves who paints each star system while the filter spotlights one bloc, keeping the
 * selected bloc visible everywhere it owns a market rather than only where it wins.
 *
 * <p>The presence-aware sibling of {@link SectorPolitics}: the normal pass collapses each
 * system to its single dominant owner and discards the losers, which would erase the
 * spotlighted bloc from every system a rival holds. Filter mode instead keeps the selected
 * bloc wherever it is present - drawn solid where it dominates and hatched where it is present
 * but dominated - while every other system keeps its real dominant owner, flagged to recede so
 * the spotlight reads against a muted background.
 *
 * <p>The spotlighted bloc's whole footprint - the systems it dominates and the systems it is
 * merely present in - carries one synthetic group key distinct from any faction id, so the
 * agnostic geometry ({@code CellShaper}, {@code SystemClusters}, and the border trace) fuses
 * the whole footprint into a single territory outlined by one frontier, with no awareness of
 * the filter. Which of those systems are contested is reported apart from the key, in
 * {@link FilteredOwnership#contestedSystemIds()}, so the render layer can split the fill per
 * cell (solid where it dominates, hatched where contested) inside that one frontier rather than
 * fracturing the footprint into two separately-bordered clusters. The synthetic key stays
 * internal to this class: callers ask {@link #isSpotlitBloc} rather than matching the raw
 * string, and everything not spotlit recedes.
 */
public final class FilteredPolitics {

    // The spotlighted bloc's single synthetic group key, carried by every system it is present
    // in - dominated or not - so the geometry fuses its whole footprint into one bordered
    // territory. The "$" sentinel prefix cannot occur in a real faction or alliance id, so the
    // key never collides with a rival's. Internal to this class - callers read isSpotlitBloc
    // rather than the raw string; contested-vs-dominant is reported separately, not by key.
    private static final String SPOTLIT_KEY = "$kmu_filter_spotlit";

    private FilteredPolitics() {
    }

    /**
     * The presence-aware ownership one filter pass resolves: the owner keyed by system, plus the
     * subset of the spotlighted bloc's systems it is present in but does not dominate.
     *
     * <p>The whole spotlit footprint keys to one synthetic {@code SPOTLIT_KEY} in
     * {@code ownerBySystemId} so the geometry traces one frontier over it; {@code contestedSystemIds}
     * is how the render layer then splits that footprint's fill per cell - solid where the bloc
     * dominates, hatched where it is merely present - without the key having to fracture the
     * cluster. A contested id is always a spotlit id; a dominant spotlit id is simply absent from
     * the set. Off filter (no selection, or an empty sector) both are empty.
     *
     * @param ownerBySystemId    the presence-aware owner per owned system
     * @param contestedSystemIds the spotlit systems the bloc is present in but does not dominate
     */
    public record FilteredOwnership(Map<String, DominantOwner> ownerBySystemId,
            Set<String> contestedSystemIds) {
    }

    /**
     * How the spotlighted bloc stands in one star system: the three-way classification filter
     * mode keys every system's draw off. Decided purely from the system's per-bloc footprints,
     * free of economy and faction types, so the rule can be exercised on hand-built footprints.
     */
    public enum SelectedBlocPresence {
        /** The selected bloc holds the system outright: drawn solid, at full strength. */
        DOMINATES,
        /** The selected bloc owns a market but a rival wins: drawn contested (hatched). */
        PRESENT_BUT_DOMINATED,
        /** The selected bloc owns nothing here: the real dominant owner draws, receded. */
        ABSENT
    }

    /**
     * Classifies how the selected bloc stands in one system from its per-bloc footprints - the
     * pure rule the whole presence resolver turns on, testable on hand-built footprints.
     *
     * <p>Present means the selected bloc owns any market in the system (it has a footprint),
     * under the same known-to-player, dominance-rules, and dev-reveal inputs the normal pass
     * reads - so a bloc marks presence exactly where it could paint. A present bloc that also
     * wins the dominance comparison dominates; a present bloc a rival outranks is present but
     * dominated; a bloc with no footprint is absent. The comparison uses the same
     * {@link SystemDominance} rule the normal pass does, so "does the selected bloc dominate" is
     * judged against honest competition under the active grouping.
     *
     * @param footprintByBlocId each bloc's footprint in the system, already regrouped under the
     *                          active view's grouping; empty means no owned markets
     * @param selectedBlocId    the spotlighted bloc's id (a faction id, or an alliance id)
     * @return where the selected bloc stands in this system
     */
    public static SelectedBlocPresence classifySelectedBlocPresence(
            Map<String, MarketFootprint> footprintByBlocId, String selectedBlocId) {
        if (!footprintByBlocId.containsKey(selectedBlocId)) {
            return SelectedBlocPresence.ABSENT;
        }
        var dominantBlocId = SystemDominance.resolveDominantFactionId(footprintByBlocId);
        return selectedBlocId.equals(dominantBlocId)
                ? SelectedBlocPresence.DOMINATES
                : SelectedBlocPresence.PRESENT_BUT_DOMINATED;
    }

    /**
     * Whether a group key is the spotlighted bloc's synthetic key - carried by every system the
     * bloc is present in, which the render layer draws at full strength while every other key
     * recedes. The single question the filter branch asks to decide recede, so the synthetic
     * encoding stays private to this class.
     *
     * @param blocId the group key a resolved owner carries
     * @return true when the key is the spotlighted bloc's synthetic key
     */
    public static boolean isSpotlitBloc(String blocId) {
        return SPOTLIT_KEY.equals(blocId);
    }

    /**
     * Builds the presence-aware ownership for every inhabited system under the player's live
     * settings - the entry the filter branch of the render pipeline calls in place of
     * {@link SectorPolitics#resolveDominantOwnerBySystemId} while a bloc is spotlighted.
     *
     * @param sector         the sector whose economy is read; null yields empty ownership
     * @param grouping       the active view's grouping, sampled once for the whole pass
     * @param selectedBlocId the spotlighted bloc's id; null yields empty ownership (no filter)
     * @return the presence-aware ownership: the owner per system and the contested spotlit systems
     */
    public static FilteredOwnership resolveFilteredOwnership(
            SectorAPI sector, OwnershipGrouping grouping, String selectedBlocId) {
        return resolveFilteredOwnership(sector, DominanceRules.readFromLunaSettings(),
                PoliticalMapDevOverrides.readFromLunaSettings().isShowingAllFactions(),
                grouping, selectedBlocId);
    }

    /**
     * Builds the presence-aware ownership under an explicit weighting rule and grouping, for a
     * caller that has already read the player's toggles for the pass.
     *
     * <p>Mirrors {@link SectorPolitics#resolveDominantOwnerBySystemId} system for system: each
     * inhabited system resolves to one {@link DominantOwner} the geometry clusters by, but every
     * system the selected bloc is present in carries the one spotlit key (and the bloc's palette)
     * instead of the real winner, so the bloc survives where it loses and its whole footprint
     * fuses into one territory. A present-but-dominated system is additionally recorded in the
     * returned contested set, the only place the dominant/contested split lives now that both
     * share a key. A system with no owned markets is absent, exactly as in the normal pass.
     *
     * @param sector                       the sector whose economy is read; null yields empty
     *                                     ownership
     * @param rules                        the dominance-weighting rules for this pass
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count (the dev
     *                                     reveal); false applies the normal known-to-player
     *                                     filter
     * @param grouping                     the active view's grouping that collapses factions into
     *                                     blocs before dominance is compared
     * @param selectedBlocId               the spotlighted bloc's id; null yields empty ownership
     * @return the presence-aware ownership: the owner per system and the contested spotlit systems
     */
    public static FilteredOwnership resolveFilteredOwnership(
            SectorAPI sector, DominanceRules rules, boolean shouldIncludeUndiscoveredMarkets,
            OwnershipGrouping grouping, String selectedBlocId) {
        var ownerBySystemId = new LinkedHashMap<String, DominantOwner>();
        var contestedSystemIds = new LinkedHashSet<String>();
        if (sector == null || sector.getEconomy() == null || selectedBlocId == null) {
            return new FilteredOwnership(ownerBySystemId, contestedSystemIds);
        }
        for (var system : sector.getStarSystems()) {
            var owner = resolveOwner(sector, system, rules, shouldIncludeUndiscoveredMarkets,
                    grouping, selectedBlocId, contestedSystemIds);
            if (owner != null) {
                ownerBySystemId.put(system.getId(), owner);
            }
        }
        return new FilteredOwnership(ownerBySystemId, contestedSystemIds);
    }

    // Resolves one system's presence-aware owner: the selected bloc under the spotlit key where
    // it is present (recording the system as contested when it is present but dominated),
    // otherwise the system's real dominant owner unchanged (flagged to recede by the caller,
    // which sees a key isSpotlitBloc rejects). Reads and regroups the footprints once and shares
    // them with both the classification and the real-owner fallback.
    private static DominantOwner resolveOwner(SectorAPI sector, StarSystemAPI system,
            DominanceRules rules, boolean shouldIncludeUndiscoveredMarkets,
            OwnershipGrouping grouping, String selectedBlocId, Set<String> contestedSystemIds) {
        var footprintByBlocId = SectorPolitics.regroupByBloc(
                KnownMarketFootprints.readByFaction(sector, system, rules,
                        shouldIncludeUndiscoveredMarkets),
                grouping, MarketFootprint.EMPTY, MarketFootprint::merge);
        var presence = classifySelectedBlocPresence(footprintByBlocId, selectedBlocId);
        if (presence == SelectedBlocPresence.ABSENT) {
            return resolveRealOwner(sector, grouping, footprintByBlocId);
        }
        var spotlit = resolveSpotlitOwner(sector, grouping, selectedBlocId);
        // A selectable bloc's colour faction resolves; this fallback only guards the degenerate
        // case where it vanished mid-session, so the system still draws (as its real receded
        // owner) rather than dropping off the map - and a system that fell back is not spotlit,
        // so it is not recorded contested.
        if (spotlit == null) {
            return resolveRealOwner(sector, grouping, footprintByBlocId);
        }
        if (presence == SelectedBlocPresence.PRESENT_BUT_DOMINATED) {
            contestedSystemIds.add(system.getId());
        }
        return spotlit;
    }

    // The selected bloc under the spotlit key: the bloc's real palette (its own for a faction,
    // its dominant member's for an alliance) rekeyed onto the one synthetic key, so the cell
    // paints in the selected bloc's colours while clustering into the single spotlit territory.
    // Null when the bloc's colour faction does not resolve.
    private static DominantOwner resolveSpotlitOwner(SectorAPI sector, OwnershipGrouping grouping,
            String selectedBlocId) {
        var paletteOwner = SectorPolitics.resolveBlocOwner(sector, grouping, selectedBlocId);
        if (paletteOwner == null) {
            return null;
        }
        return new DominantOwner(SPOTLIT_KEY,
                paletteOwner.primaryColor(), paletteOwner.secondaryColor());
    }

    // The system's real dominant owner, unchanged from the normal pass, for a system the
    // selected bloc is absent from. Its real key (rejected by isSpotlitBloc) is how the caller
    // knows to recede it.
    private static DominantOwner resolveRealOwner(SectorAPI sector, OwnershipGrouping grouping,
            Map<String, MarketFootprint> footprintByBlocId) {
        var dominantBlocId = SystemDominance.resolveDominantFactionId(footprintByBlocId);
        if (dominantBlocId == null) {
            return null;
        }
        return SectorPolitics.resolveBlocOwner(sector, grouping, dominantBlocId);
    }
}
