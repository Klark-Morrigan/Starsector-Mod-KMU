package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves who paints each star system while the filter spotlights one bloc, keeping the
 * selected bloc visible everywhere it holds a colony rather than only where it wins.
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
    public record FilteredHolder(
        Map<String, DominantHolder> ownerBySystemId,
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
        /** The selected bloc lives here without winning the system: drawn contested (hatched). */
        PRESENT_BUT_DOMINATED,
        /** The selected bloc owns nothing here: the real dominant holder draws, receded. */
        ABSENT
    }

    /**
     * Classifies how the selected bloc stands in one system from the blocs present in it and their
     * footprints - the pure rule the whole presence resolver turns on, testable on hand-built sets.
     *
     * <p>Presence and dominance are two questions, and this takes an answer to each rather than
     * inferring the first from the second. A footprint is economy-fed in every term, so a bloc whose
     * only colony here is one the economy does not list raises none - and a spotlight that read
     * presence off the footprints would dim the very system the player picked that bloc to see,
     * while the band inside the same cell drew its run. A present bloc that also wins the dominance
     * comparison dominates; a present bloc that does not - outranked, or holding nothing anybody
     * weighed - is present but dominated; a bloc absent from the presence set is absent. The
     * comparison uses the same {@link SystemDominance} rule the normal pass does, breaking a tie by
     * lowest id, so "does the selected bloc dominate" is judged against honest competition under the
     * active grouping.
     *
     * @param footprintByBlocId each bloc's footprint in the system, already regrouped under the
     *                          active view's grouping; empty means nothing here was weighed
     * @param presentBlocIds    the blocs holding a colony the player may be shown, which the
     *                          footprints are a subset of
     * @param selectedBlocId    the spotlighted bloc's id (a faction id, or an alliance id)
     * @return where the selected bloc stands in this system
     */
    public static SelectedBlocPresence classifySelectedBlocPresence(
            Map<String, MarketFootprint> footprintByBlocId,
            Set<String> presentBlocIds,
            String selectedBlocId) {
        return classifySelectedBlocPresence(
            footprintByBlocId,
            presentBlocIds,
            selectedBlocId,
            Comparator.naturalOrder());
    }

    /**
     * Classifies how the selected bloc stands in one system, breaking a dominance tie with the
     * supplied comparator so the spotlight's "dominates" matches the holder the normal pass would
     * paint - the live filter branch hands in the market-proximity tie-break the normal pass
     * uses, so a tied system is drawn solid or hatched consistently with the base layers.
     *
     * @param footprintByBlocId each bloc's footprint in the system, already regrouped under the
     *                          active view's grouping; empty means nothing here was weighed
     * @param presentBlocIds    the blocs holding a colony the player may be shown, which the
     *                          footprints are a subset of
     * @param selectedBlocId    the spotlighted bloc's id (a faction id, or an alliance id)
     * @param tieBreak          consulted only when the selected bloc ties a rival on every
     *                          weight level; the id it orders first dominates the system
     * @return where the selected bloc stands in this system
     */
    public static SelectedBlocPresence classifySelectedBlocPresence(
            Map<String, MarketFootprint> footprintByBlocId,
            Set<String> presentBlocIds,
            String selectedBlocId,
            Comparator<String> tieBreak) {

        if (!presentBlocIds.contains(selectedBlocId)) {
            return SelectedBlocPresence.ABSENT;
        }
        var dominantBlocId = SystemDominance.resolveDominantFactionId(footprintByBlocId, tieBreak);
        return selectedBlocId.equals(dominantBlocId)
            ? SelectedBlocPresence.DOMINATES
            : SelectedBlocPresence.PRESENT_BUT_DOMINATED;
    }

    /**
     * Which of {@code candidateSystemIds} the spotlighted bloc is present in - the presence read
     * for systems this pass resolved <em>no</em> holder for.
     *
     * <p>The counterpart to {@link #resolveFilteredHolder} for cells the holder map never
     * reaches. That resolve keeps the bloc visible wherever it is present by rekeying the
     * system, which only works on a system somebody holds; a view whose holding rule admits
     * only some factions leaves settled systems with no holder at all, and the spotlit bloc can
     * be living in one of them. On the claims views it routinely is, vanilla leaving a settled
     * system unclaimed for several reasons - see
     * {@link kmu.maplayers.politicalmap.base.politics.holders} - so a bloc's own unclaimed
     * colonies land here. A view that resolves its holding through the resolve above has already
     * kept every system the bloc is present in, so what reaches this read is what it was absent
     * from and the answer comes back empty.
     *
     * <p>Presence is the set {@link #classifySelectedBlocPresence}'s absent arm reads, so a bloc
     * counts as living in a system here exactly where the filter's own resolve would have kept it
     * visible - including where it holds only colonies no mechanic weighed. It is asked directly
     * rather than through that classification, because the classification's other two arms rank
     * the system's footprints to tell dominant from contested, and a holderless system has no
     * contest for the bloc to win or lose: the ranking would be a weighing of every candidate's
     * colonies for a verdict this read discards.
     *
     * <p>So it takes the layer-generic reading of the sector rather than a dominance pass. Nothing
     * here weighs a market, which means no weighting rule has to be sampled to answer it.
     *
     * <p>Answered over a caller-supplied candidate set rather than the whole sector, because the
     * only systems it can change anything for are the handful the holder map left out. Walking
     * every system would re-read the economy the holding resolve just walked, for an answer
     * discarded at all but a few of them.
     *
     * @param pass              the rebuild's reading of the sector, whose walk of each system this
     *                          read shares; a pass over no sector yields an empty set
     * @param selectedBlocId    the spotlighted bloc's id; null yields an empty set (no filter)
     * @param candidateSystemIds the systems to test - those this pass resolved no holder for
     * @return the candidates the spotlighted bloc holds a colony the player may be shown in
     */
    public static Set<String> findPresentSystemIds(
            HolderPass pass,
            String selectedBlocId,
            Set<String> candidateSystemIds) {

        var presentSystemIds = new LinkedHashSet<String>();

        // A read that can decide nothing returns before the walk: off filter there is no pick to
        // look for, and with no candidates every system the walk reached would be discarded.
        if (!pass.canReadEconomy() || selectedBlocId == null || candidateSystemIds.isEmpty()) {
            return presentSystemIds;
        }
        for (var system : pass.readSystems()) {

            // Membership is tested before the colony read, so a system outside the candidate
            // set costs a set probe rather than a read of its colonies.
            if (!candidateSystemIds.contains(system.getId())) {
                continue;
            }
            if (pass.readKnownColonyBlocIds(system).contains(selectedBlocId)) {
                presentSystemIds.add(system.getId());
            }
        }
        return presentSystemIds;
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
     * Builds the presence-aware holders for every inhabited system over a rebuild's own reading
     * of the sector, reading the weighting rule live - the entry a holding provider calls in
     * place of {@link SectorPolitics#resolveDominantHolderBySystemId} while a bloc is
     * spotlighted, the rule being the one knob the pass it was handed does not carry.
     *
     * @param pass           the rebuild's reading of the sector, whose walk of each system this
     *                       resolve shares; a pass over no sector yields empty holding
     * @param selectedBlocId the spotlighted bloc's id; null yields empty holding (no filter)
     * @return the presence-aware holders: the holder per system and the contested spotlit systems
     */
    public static FilteredHolder resolveFilteredHolder(
            HolderPass pass,
            String selectedBlocId) {
        return resolveFilteredHolder(
            DominancePass.readRulesFromLunaSettings(pass),
            selectedBlocId);
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
     * @param pass           the weighting rule, colony rule, grouping, and sector walk this pass
     *                       resolves under; a pass over no sector yields empty holding
     * @param selectedBlocId the spotlighted bloc's id; null yields empty holding
     * @return the presence-aware holders: the holder per system and the contested spotlit systems
     */
    public static FilteredHolder resolveFilteredHolder(
            DominancePass pass,
            String selectedBlocId) {

        var ownerBySystemId = new LinkedHashMap<String, DominantHolder>();
        var contestedSystemIds = new LinkedHashSet<String>();

        if (!pass.canReadEconomy() || selectedBlocId == null) {
            return new FilteredHolder(ownerBySystemId, contestedSystemIds);
        }
        for (var system : pass.readSystems()) {
            var holder = resolveHolder(system, pass, selectedBlocId, contestedSystemIds);
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
        return new DominantHolder(
            SPOTLIT_KEY,
            paletteHolder.primaryColour(),
            paletteHolder.secondaryColour());
    }

    // Resolves one system's presence-aware holder: the selected bloc under the spotlit key where
    // it is present (recording the system as contested when it is present but dominated),
    // otherwise the system's real dominant holder unchanged (flagged to recede by the caller,
    // which sees a key isSpotlitBloc rejects). Reads and regroups the footprints once and shares
    // them with both the classification and the real-holder fallback.
    //
    // Presence is read beside them rather than taken off them: the footprints answer who wins the
    // system, and every term of that arithmetic is economy-fed, so a bloc whose only colony here is
    // unregistered wins nothing and is still living in the system. Widening the footprints instead
    // would put such a bloc into the map the dominant-holder rank is taken over, where an
    // all-weightless system would hand it the system outright - a spotlight moving a fill, which
    // it must never do.
    private static DominantHolder resolveHolder(
            StarSystemAPI system,
            DominancePass pass,
            String selectedBlocId,
            Set<String> contestedSystemIds) {

        var footprintByBlocId = pass.readBlocFootprints(system);
        var presentBlocIds = pass.readKnownColonyBlocIds(system);

        // The proximity tie-break the normal pass uses, so both the "does the selected bloc
        // dominate" call and the receded real-holder fallback settle a tie the same way the base
        // layers do; lazy, so it reads no geometry unless this system actually ties.
        var tieBreak = pass.tieBreakFor(system);
        var sector = pass.sector();
        var grouping = pass.grouping();
        var presence = classifySelectedBlocPresence(
            footprintByBlocId,
            presentBlocIds,
            selectedBlocId,
            tieBreak);

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
