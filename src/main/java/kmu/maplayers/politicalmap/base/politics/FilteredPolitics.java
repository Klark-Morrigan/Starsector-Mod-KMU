package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.PoliticalMapDevOverrides;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves who paints each star system while the filter spotlights one bloc, keeping the
 * selected bloc visible everywhere it owns a market rather than only where it wins.
 *
 * <p>The presence-aware sibling of {@link SectorPolitics}: the normal pass collapses each
 * system to its single dominant owner and discards the losers, which would erase the
 * spotlighted bloc from every system a rival holds. Filter mode instead keeps the selected
 * bloc wherever it is present - drawn solid where it dominates and "contested" (hatched
 * downstream) where it is present but dominated - while every other system keeps its real
 * dominant owner, flagged to recede so the spotlight reads against a muted background.
 *
 * <p>The selected bloc's two states are carried as synthetic group keys distinct from any
 * faction id, so the agnostic geometry ({@code CellShaper}, {@code SystemClusters}, and the
 * border trace) clusters the solid systems into one territory and the contested systems into
 * another with no awareness of the filter, and the render layer reads the selected bloc's own
 * palette straight off the {@link DominantOwner}. Both synthetic keys stay internal to this
 * class: callers ask {@link #isSpotlitBloc} and {@link #isContestedBloc} rather than matching
 * the raw strings, and everything not spotlit recedes.
 */
public final class FilteredPolitics {

    // The selected bloc's two synthetic group keys: one for the systems it dominates (drawn
    // solid) and one for the systems it is merely present in (drawn hatched downstream). The
    // "$" sentinel prefix cannot occur in a real faction or alliance id, so the geometry
    // clusters each set on its own without ever colliding with a rival's key. Internal to this
    // class - callers read the two is-* helpers rather than the raw strings.
    private static final String SPOTLIT_DOMINANT_KEY = "$kmu_filter_spotlit_dominant";
    private static final String SPOTLIT_CONTESTED_KEY = "$kmu_filter_spotlit_contested";

    private FilteredPolitics() {
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
     * Whether a group key is the spotlighted bloc - either of its two synthetic keys, the solid
     * and the contested clusters - which the render layer draws at full strength while every
     * other key recedes. The single question the filter branch asks to decide recede, so the
     * synthetic encoding stays private to this class.
     *
     * @param blocId the group key a resolved owner carries
     * @return true when the key is one of the spotlighted bloc's two synthetic keys
     */
    public static boolean isSpotlitBloc(String blocId) {
        return SPOTLIT_DOMINANT_KEY.equals(blocId) || SPOTLIT_CONTESTED_KEY.equals(blocId);
    }

    /**
     * Whether a group key is the spotlighted bloc's contested cluster - the systems it is
     * present in but does not dominate - which the render layer draws hatched rather than solid.
     *
     * @param blocId the group key a resolved owner carries
     * @return true when the key is the contested (present-but-dominated) synthetic key
     */
    public static boolean isContestedBloc(String blocId) {
        return SPOTLIT_CONTESTED_KEY.equals(blocId);
    }

    /**
     * Builds the presence-aware owner for every inhabited system under the player's live
     * settings - the entry the filter branch of the render pipeline calls in place of
     * {@link SectorPolitics#resolveDominantOwnerBySystemId} while a bloc is spotlighted.
     *
     * @param sector         the sector whose economy is read; null yields an empty map
     * @param grouping       the active view's grouping, sampled once for the whole pass
     * @param selectedBlocId the spotlighted bloc's id; null yields an empty map (no filter)
     * @return the presence-aware owner keyed by system id
     */
    public static Map<String, DominantOwner> resolveOwnerBySystemId(
            SectorAPI sector, OwnershipGrouping grouping, String selectedBlocId) {
        return resolveOwnerBySystemId(sector, DominanceRules.readFromLunaSettings(),
                PoliticalMapDevOverrides.readFromLunaSettings().isShowingAllFactions(),
                grouping, selectedBlocId);
    }

    /**
     * Builds the presence-aware owner for every inhabited system under an explicit weighting
     * rule and grouping, for a caller that has already read the player's toggles for the pass.
     *
     * <p>Mirrors {@link SectorPolitics#resolveDominantOwnerBySystemId} system for system: each
     * inhabited system resolves to one {@link DominantOwner} the geometry clusters by, but the
     * selected bloc's systems carry a synthetic key (and its palette) instead of the real
     * winner, so the bloc survives where it loses. A system with no owned markets is absent from
     * the map, exactly as in the normal pass.
     *
     * @param sector                       the sector whose economy is read; null yields an empty
     *                                     map
     * @param rules                        the dominance-weighting rules for this pass
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count (the dev
     *                                     reveal); false applies the normal known-to-player
     *                                     filter
     * @param grouping                     the active view's grouping that collapses factions into
     *                                     blocs before dominance is compared
     * @param selectedBlocId               the spotlighted bloc's id; null yields an empty map
     * @return the presence-aware owner keyed by system id; a system with no owned market is
     *         absent
     */
    public static Map<String, DominantOwner> resolveOwnerBySystemId(
            SectorAPI sector, DominanceRules rules, boolean shouldIncludeUndiscoveredMarkets,
            OwnershipGrouping grouping, String selectedBlocId) {
        var ownerBySystemId = new LinkedHashMap<String, DominantOwner>();
        if (sector == null || sector.getEconomy() == null || selectedBlocId == null) {
            return ownerBySystemId;
        }
        for (var system : sector.getStarSystems()) {
            var owner = resolveOwner(sector, system, rules,
                    shouldIncludeUndiscoveredMarkets, grouping, selectedBlocId);
            if (owner != null) {
                ownerBySystemId.put(system.getId(), owner);
            }
        }
        return ownerBySystemId;
    }

    // Resolves one system's presence-aware owner: the selected bloc under a synthetic key where
    // it is present (solid when it dominates, contested when it does not), otherwise the
    // system's real dominant owner unchanged (flagged to recede by the caller, which sees a
    // key isSpotlitBloc rejects). Reads and regroups the footprints once and shares them with
    // both the classification and the real-owner fallback.
    private static DominantOwner resolveOwner(SectorAPI sector, StarSystemAPI system,
            DominanceRules rules, boolean shouldIncludeUndiscoveredMarkets,
            OwnershipGrouping grouping, String selectedBlocId) {
        var footprintByBlocId = SectorPolitics.regroupByBloc(
                KnownMarketFootprints.readByFaction(sector, system, rules,
                        shouldIncludeUndiscoveredMarkets), grouping);
        var presence = classifySelectedBlocPresence(footprintByBlocId, selectedBlocId);
        if (presence == SelectedBlocPresence.ABSENT) {
            return resolveRealOwner(sector, grouping, footprintByBlocId);
        }
        var syntheticKey = presence == SelectedBlocPresence.DOMINATES
                ? SPOTLIT_DOMINANT_KEY
                : SPOTLIT_CONTESTED_KEY;
        var spotlit = resolveSpotlitOwner(sector, grouping, selectedBlocId, syntheticKey);
        // A selectable bloc's colour faction resolves; this fallback only guards the degenerate
        // case where it vanished mid-session, so the system still draws (as its real receded
        // owner) rather than dropping off the map.
        return spotlit != null ? spotlit : resolveRealOwner(sector, grouping, footprintByBlocId);
    }

    // The selected bloc under a synthetic key: the bloc's real palette (its own for a faction,
    // its dominant member's for an alliance) rekeyed onto the synthetic solid/contested key, so
    // the cell paints in the selected bloc's colours while clustering as one of the two spotlit
    // territories. Null when the bloc's colour faction does not resolve.
    private static DominantOwner resolveSpotlitOwner(SectorAPI sector, OwnershipGrouping grouping,
            String selectedBlocId, String syntheticKey) {
        var paletteOwner = SectorPolitics.resolveBlocOwner(sector, grouping, selectedBlocId);
        if (paletteOwner == null) {
            return null;
        }
        return new DominantOwner(syntheticKey,
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
