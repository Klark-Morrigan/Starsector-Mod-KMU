package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves who paints each star system while the filter spotlights one bloc, keeping the
 * selected bloc visible everywhere it owns a market rather than only where it wins.
 *
 * <p>The presence-aware sibling of {@link SectorPolitics}: the normal pass collapses each
 * system to its single dominant holder and discards the losers, which would erase the
 * spotlighted bloc from every system a rival holds. Filter mode instead keeps the selected
 * bloc wherever it is present - drawn solid where it dominates and hatched where it is present
 * but dominated - while every other system keeps its real dominant holder, flagged to recede so
 * the spotlight reads against a muted background.
 *
 * <p>The spotlighted bloc's whole footprint - the systems it dominates and the systems it is
 * merely present in - carries one synthetic group key distinct from any faction id, so the
 * agnostic geometry ({@code CellShaper}, {@code SystemClusters}, and the border trace) fuses
 * the whole footprint into a single territory outlined by one frontier, with no awareness of
 * the filter. Which of those systems are contested is reported apart from the key, in
 * {@link FilteredHolder#contestedSystemIds()}, so the render layer can split the fill per
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
     * The presence-aware holders one filter pass resolves: the holder keyed by system, plus the
     * subset of the spotlighted bloc's systems it is present in but does not dominate.
     *
     * <p>The whole spotlit footprint keys to one synthetic {@code SPOTLIT_KEY} in
     * {@code ownerBySystemId} so the geometry traces one frontier over it; {@code contestedSystemIds}
     * is how the render layer then splits that footprint's fill per cell - solid where the bloc
     * dominates, hatched where it is merely present - without the key having to fracture the
     * cluster. A contested id is always a spotlit id; a dominant spotlit id is simply absent from
     * the set. Off filter (no selection, or an empty sector) both are empty.
     *
     * @param ownerBySystemId    the presence-aware holder per owned system
     * @param contestedSystemIds the spotlit systems the bloc is present in but does not dominate
     */
    public record FilteredHolder(Map<String, DominantHolder> ownerBySystemId,
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
        /** The selected bloc owns nothing here: the real dominant holder draws, receded. */
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
     * {@link SystemDominance} rule the normal pass does, breaking a tie by lowest id, so "does
     * the selected bloc dominate" is judged against honest competition under the active
     * grouping.
     *
     * @param footprintByBlocId each bloc's footprint in the system, already regrouped under the
     *                          active view's grouping; empty means no owned markets
     * @param selectedBlocId    the spotlighted bloc's id (a faction id, or an alliance id)
     * @return where the selected bloc stands in this system
     */
    public static SelectedBlocPresence classifySelectedBlocPresence(
            Map<String, MarketFootprint> footprintByBlocId, String selectedBlocId) {
        return classifySelectedBlocPresence(
                footprintByBlocId, selectedBlocId, Comparator.naturalOrder());
    }

    /**
     * Classifies how the selected bloc stands in one system, breaking a dominance tie with the
     * supplied comparator so the spotlight's "dominates" matches the holder the normal pass would
     * paint - the live filter branch hands in the market-proximity tie-break the normal pass
     * uses, so a tied system is drawn solid or hatched consistently with the base layers.
     *
     * @param footprintByBlocId each bloc's footprint in the system, already regrouped under the
     *                          active view's grouping; empty means no owned markets
     * @param selectedBlocId    the spotlighted bloc's id (a faction id, or an alliance id)
     * @param tieBreak          consulted only when the selected bloc ties a rival on every
     *                          weight level; the id it orders first dominates the system
     * @return where the selected bloc stands in this system
     */
    public static SelectedBlocPresence classifySelectedBlocPresence(
            Map<String, MarketFootprint> footprintByBlocId,
            String selectedBlocId,
            Comparator<String> tieBreak) {
        if (!footprintByBlocId.containsKey(selectedBlocId)) {
            return SelectedBlocPresence.ABSENT;
        }
        var dominantBlocId = SystemDominance.resolveDominantFactionId(footprintByBlocId, tieBreak);
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
     * @param blocId the group key a resolved holder carries
     * @return true when the key is the spotlighted bloc's synthetic key
     */
    public static boolean isSpotlitBloc(String blocId) {
        return SPOTLIT_KEY.equals(blocId);
    }

    /**
     * Builds the presence-aware holders for every inhabited system under the player's live
     * settings - the entry the filter branch of the render pipeline calls in place of
     * {@link SectorPolitics#resolveDominantHolderBySystemId} while a bloc is spotlighted.
     *
     * @param sector         the sector whose economy is read; null yields empty holding
     * @param grouping       the active view's grouping, sampled once for the whole pass
     * @param selectedBlocId the spotlighted bloc's id; null yields empty holding (no filter)
     * @return the presence-aware holders: the holder per system and the contested spotlit systems
     */
    public static FilteredHolder resolveFilteredHolder(
            SectorAPI sector, HolderGrouping grouping, String selectedBlocId) {
        return resolveFilteredHolder(
                sector, DominancePass.readFromLunaSettings(grouping), selectedBlocId);
    }

    /**
     * Builds the presence-aware holders under an explicit dominance pass, for a caller that has
     * already sampled the player's settings.
     *
     * <p>Mirrors {@link SectorPolitics#resolveDominantHolderBySystemId} system for system: each
     * inhabited system resolves to one {@link DominantHolder} the geometry clusters by, but every
     * system the selected bloc is present in carries the one spotlit key (and the bloc's palette)
     * instead of the real winner, so the bloc survives where it loses and its whole footprint
     * fuses into one territory. A present-but-dominated system is additionally recorded in the
     * returned contested set, the only place the dominant/contested split lives now that both
     * share a key. A system with no owned markets is absent, exactly as in the normal pass.
     *
     * @param sector         the sector whose economy is read; null yields empty holding
     * @param pass           the rule, dev reveal, and grouping this pass resolves under
     * @param selectedBlocId the spotlighted bloc's id; null yields empty holding
     * @return the presence-aware holders: the holder per system and the contested spotlit systems
     */
    public static FilteredHolder resolveFilteredHolder(
            SectorAPI sector, DominancePass pass, String selectedBlocId) {
        var ownerBySystemId = new LinkedHashMap<String, DominantHolder>();
        var contestedSystemIds = new LinkedHashSet<String>();
        if (sector == null || sector.getEconomy() == null || selectedBlocId == null) {
            return new FilteredHolder(ownerBySystemId, contestedSystemIds);
        }
        for (var system : sector.getStarSystems()) {
            var holder = resolveHolder(sector, system, pass, selectedBlocId, contestedSystemIds);
            if (holder != null) {
                ownerBySystemId.put(system.getId(), holder);
            }
        }
        return new FilteredHolder(ownerBySystemId, contestedSystemIds);
    }

    /**
     * The holder that draws a system as part of the selected bloc's single spotlight territory: the
     * bloc's palette - its own for a faction, its dominant member's for an alliance - carried on the
     * one synthetic spotlight key, so a cell holding it paints at the bloc's full strength and
     * clusters into that one bordered frontier rather than a territory of its own.
     *
     * <p>Keying any system this way fuses it into the spotlight, whether the bloc reaches it through
     * a market its presence pass reads or through a tie that pass never sees - so a system the
     * normal resolve would leave out can still be drawn under the spotlight, keyed exactly as a
     * present one. The synthetic key stays this class's secret: a caller holds the returned holder
     * opaquely and never has to name the key.
     *
     * @param sector         the sector whose faction palette is read
     * @param grouping       the grouping naming the bloc's colour faction
     * @param selectedBlocId the spotlighted bloc to draw the system under
     * @return the spotlight holder, or null when the bloc's colour faction does not resolve
     */
    public static DominantHolder resolveSpotlitHolder(
            SectorAPI sector,
            HolderGrouping grouping,
            String selectedBlocId) {
        var paletteHolder = SectorPolitics.resolveBlocHolder(sector, grouping, selectedBlocId);
        if (paletteHolder == null) {
            return null;
        }
        return new DominantHolder(SPOTLIT_KEY,
                paletteHolder.primaryColor(), paletteHolder.secondaryColor());
    }

    // Resolves one system's presence-aware holder: the selected bloc under the spotlit key where
    // it is present (recording the system as contested when it is present but dominated),
    // otherwise the system's real dominant holder unchanged (flagged to recede by the caller,
    // which sees a key isSpotlitBloc rejects). Reads and regroups the footprints once and shares
    // them with both the classification and the real-holder fallback.
    private static DominantHolder resolveHolder(
            SectorAPI sector,
            StarSystemAPI system,
            DominancePass pass,
            String selectedBlocId,
            Set<String> contestedSystemIds) {
        var footprintByBlocId = pass.readBlocFootprints(sector, system);
        // The proximity tie-break the normal pass uses, so both the "does the selected bloc
        // dominate" call and the receded real-holder fallback settle a tie the same way the base
        // layers do; lazy, so it reads no geometry unless this system actually ties.
        var tieBreak = pass.tieBreakFor(sector, system);
        var grouping = pass.grouping();
        var presence = classifySelectedBlocPresence(footprintByBlocId, selectedBlocId, tieBreak);
        if (presence == SelectedBlocPresence.ABSENT) {
            return resolveRealHolder(sector, grouping, footprintByBlocId, tieBreak);
        }
        var spotlit = resolveSpotlitHolder(sector, grouping, selectedBlocId);
        // A selectable bloc's colour faction resolves; this fallback only guards the degenerate
        // case where it vanished mid-session, so the system still draws (as its real receded
        // holder) rather than dropping off the map - and a system that fell back is not spotlit,
        // so it is not recorded contested.
        if (spotlit == null) {
            return resolveRealHolder(sector, grouping, footprintByBlocId, tieBreak);
        }
        if (presence == SelectedBlocPresence.PRESENT_BUT_DOMINATED) {
            contestedSystemIds.add(system.getId());
        }
        return spotlit;
    }

    // The system's real dominant holder, unchanged from the normal pass, for a system the
    // selected bloc is absent from. Its real key (rejected by isSpotlitBloc) is how the caller
    // knows to recede it. Takes the same proximity tie-break the normal pass uses so a tied
    // receded system draws the same holder it would off filter.
    private static DominantHolder resolveRealHolder(
            SectorAPI sector,
            HolderGrouping grouping,
            Map<String, MarketFootprint> footprintByBlocId,
            Comparator<String> tieBreak) {
        var dominantBlocId = SystemDominance.resolveDominantFactionId(footprintByBlocId, tieBreak);
        if (dominantBlocId == null) {
            return null;
        }
        return SectorPolitics.resolveBlocHolder(sector, grouping, dominantBlocId);
    }
}
