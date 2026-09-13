package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.dominance.HolderRankingRules;
import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;

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
 * merely present in - carries one synthetic group key distinct from any faction ID, so the
 * agnostic geometry ({@code CellShaper}, {@code SystemClusters}, and the border trace) fuses
 * the whole footprint into a single territory outlined by one frontier, with no awareness of
 * the filter. Which of those systems are contested is reported apart from the key, in
 * {@link FilteredHolder#contestedSystemKeys()}, so the render layer can split the fill per
 * cell (solid where it dominates, hatched where contested) inside that one frontier rather than
 * fracturing the footprint into two separately-bordered clusters. The synthetic key stays
 * internal to this class: callers ask {@link #isSpotlitBloc} rather than matching the raw
 * string, and everything not spotlit recedes.
 */
public final class FilteredPolitics {

    // The spotlighted bloc's single synthetic group key, carried by every system it is present
    // in - dominated or not - so the geometry fuses its whole footprint into one bordered
    // territory. The "$" sentinel prefix cannot occur in a real faction or alliance ID, so the
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
     * {@code ownerBySystemKey} so the geometry traces one frontier over it;
     * {@code contestedSystemKeys} is how the render layer then splits that footprint's fill per
     * cell - solid where the bloc dominates, hatched where it is merely present - without the key
     * having to fracture the cluster. A contested system is always a spotlit one; a dominant
     * spotlit system is simply absent from the set. Off filter (no selection, or an empty sector)
     * both are empty.
     *
     * @param ownerBySystemKey    the presence-aware holder per owned system
     * @param contestedSystemKeys the spotlit systems the bloc is present in but does not dominate
     */
    public record FilteredHolder(
        Map<SystemKey, DominantHolder> ownerBySystemKey,
        Set<SystemKey> contestedSystemKeys) {
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
     * footprints - the pure rule the whole presence resolver turns on, settled from what it is
     * handed and nothing else.
     *
     * <p>Presence and dominance are two questions, and this takes an answer to each rather than
     * inferring the first from the second. A footprint is economy-fed in every term, so a bloc whose
     * only colony here is one the economy does not list raises none - and a spotlight that read
     * presence off the footprints would dim the very system the player picked that bloc to see,
     * while the band inside the same cell drew its run. A present bloc that also wins the dominance
     * comparison dominates; a present bloc that does not - outranked, or holding nothing anybody
     * weighed - is present but dominated; a bloc absent from the presence set is absent. The
     * comparison uses the same {@link SystemDominance} rule the normal pass does, under the rules
     * the caller hands in, so "does the selected bloc dominate" is judged against honest
     * competition under the active grouping.
     *
     * <p>Presence is living in the system rather than merely being nameable in it, which is what
     * keeps a spared cell and a painted cell the same cell: a bloc whose only market here is a
     * derelict nobody was ever aboard is absent, so the classification that calls the system empty
     * space and the spotlight that would have kept a fill over it answer alike.
     *
     * @param footprintByBlocId each bloc's footprint in the system, already regrouped under the
     *                          active view's grouping; empty means nothing here was weighed
     * @param presentBlocIds    the blocs somebody the player knows of lives in the system under,
     *                          which the footprints are a subset of
     * @param selectedBlocId    the spotlighted bloc's ID (a faction ID, or an alliance ID)
     * @param rankingRules      how the system's holder is settled - who may win it, and who takes
     *                          a dead heat - so the spotlight's "dominates" is judged against the
     *                          same field the fills were
     * @return where the selected bloc stands in this system
     */
    public static SelectedBlocPresence classifySelectedBlocPresence(
            Map<String, MarketFootprint> footprintByBlocId,
            Set<String> presentBlocIds,
            String selectedBlocId,
            HolderRankingRules rankingRules) {

        if (!presentBlocIds.contains(selectedBlocId)) {
            return SelectedBlocPresence.ABSENT;
        }
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
            footprintByBlocId,
            rankingRules);
        return selectedBlocId.equals(dominantBlocId)
            ? SelectedBlocPresence.DOMINATES
            : SelectedBlocPresence.PRESENT_BUT_DOMINATED;
    }

    /**
     * Which of {@code candidateSystemKeys} the spotlighted bloc is present in - the presence read
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
     * <p>Presence is the set {@link #classifySelectedBlocPresence}'s absent arm reads - the blocs
     * the system's habitation folds into - so a bloc counts as living in a system here exactly
     * where the filter's own resolve would have kept it visible, including where it holds only
     * colonies no mechanic weighed. It is asked directly rather than through that classification,
     * because the classification's other two arms rank the system's footprints to tell dominant
     * from contested, and a holderless system has no contest for the bloc to win or lose: the
     * ranking would be a weighing of every candidate's colonies for a verdict this read discards.
     *
     * <p>Reading habitation is what makes the sparing agree with the drawing. The cells this
     * spares are the ones the map classified as empty backdrop, and that classification asks the
     * emptiness of this very set - so a bloc present here is a bloc the cell was never going to be
     * called empty for.
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
     * @param selectedBlocId      the spotlighted bloc's ID; null yields an empty set (no filter)
     * @param candidateSystemKeys the systems to test - those this pass resolved no holder for
     * @return the candidates the spotlighted bloc holds a colony somebody lives on in
     */
    public static Set<SystemKey> findPresentSystemKeys(
            HolderPass pass,
            String selectedBlocId,
            Set<SystemKey> candidateSystemKeys) {

        var presentSystemKeys = new LinkedHashSet<SystemKey>();

        // A read that can decide nothing returns before the walk: off filter there is no pick to
        // look for, and with no candidates every system the walk reached would be discarded.
        if (!pass.canReadEconomy() || selectedBlocId == null || candidateSystemKeys.isEmpty()) {
            return presentSystemKeys;
        }
        for (var system : pass.readSystems()) {

            // Membership is tested before the colony read, so a system outside the candidate
            // set costs a set probe rather than a read of its colonies.
            var systemKey = SystemKey.readKeyOf(system);
            if (!candidateSystemKeys.contains(systemKey)) {
                continue;
            }
            if (pass.readHabitationIn(system).blocIds().contains(selectedBlocId)) {
                presentSystemKeys.add(systemKey);
            }
        }
        return presentSystemKeys;
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
     * place of {@link SectorPolitics#resolveDominantHolderBySystemKey} while a bloc is
     * spotlighted, the rule being the one knob the pass it was handed does not carry.
     *
     * @param pass           the rebuild's reading of the sector, whose walk of each system this
     *                       resolve shares; a pass over no sector yields empty holding
     * @param selectedBlocId the spotlighted bloc's ID; null yields empty holding (no filter)
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
     * <p>Mirrors {@link SectorPolitics#resolveDominantHolderBySystemKey} system for system: each
     * inhabited system resolves to one {@link DominantHolder} the geometry clusters by, but every
     * system the selected bloc is present in carries the one spotlit key (and the bloc's palette)
     * instead of the real winner, so the bloc survives where it loses and its whole footprint
     * fuses into one territory. A present-but-dominated system is additionally recorded in the
     * returned contested set, the only place the dominant/contested split lives now that both
     * share a key. A system with no owned markets is absent, exactly as in the normal pass.
     *
     * @param pass           the weighting rule, colony rule, grouping, and sector walk this pass
     *                       resolves under; a pass over no sector yields empty holding
     * @param selectedBlocId the spotlighted bloc's ID; null yields empty holding
     * @return the presence-aware holders: the holder per system and the contested spotlit systems
     */
    public static FilteredHolder resolveFilteredHolder(
            DominancePass pass,
            String selectedBlocId) {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, DominantHolder>();
        var contestedSystemKeys = new LinkedHashSet<SystemKey>();

        if (!pass.canReadEconomy() || selectedBlocId == null) {
            return new FilteredHolder(ownerBySystemKey, contestedSystemKeys);
        }
        for (var system : pass.readSystems()) {
            var systemKey = SystemKey.readKeyOf(system);
            var holder = resolveHolder(system, systemKey, pass, selectedBlocId, contestedSystemKeys);
            if (holder != null) {
                ownerBySystemKey.put(systemKey, holder);
            }
        }
        return new FilteredHolder(ownerBySystemKey, contestedSystemKeys);
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
    //
    // Taken off the system's habitation, which is the same value the cell classification asks the
    // emptiness of, so a bloc kept visible here is never one whose cell the map has meanwhile
    // called empty space.
    private static DominantHolder resolveHolder(
            StarSystemAPI system,
            SystemKey systemKey,
            DominancePass pass,
            String selectedBlocId,
            Set<SystemKey> contestedSystemKeys) {

        var footprintByBlocId = pass.readBlocFootprints(system);
        var presentBlocIds = pass.readHabitationIn(system).blocIds();

        // The pass's own settling rules, so both the "does the selected bloc dominate" call and the
        // receded real-holder fallback answer as the base layers do: a spotlight must never move a
        // fill, and judging "dominates" under rules the fills were not resolved by would do exactly
        // that. The tie-break inside is lazy, reading no geometry unless this system actually ties.
        var rankingRules = pass.resolveRankingRulesFor(system);
        var sector = pass.sector();
        var grouping = pass.grouping();
        var presence = classifySelectedBlocPresence(
            footprintByBlocId,
            presentBlocIds,
            selectedBlocId,
            rankingRules);

        if (presence == SelectedBlocPresence.ABSENT) {
            return resolveRealHolder(sector, grouping, footprintByBlocId, rankingRules);
        }
        var spotlit = resolveSpotlitHolder(sector, grouping, selectedBlocId);

        // A selectable bloc's colour faction resolves; this fallback only guards the degenerate
        // case where it vanished mid-session, so the system still draws (as its real receded
        // holder) rather than dropping off the map - and a system that fell back is not spotlit,
        // so it is not recorded contested.
        if (spotlit == null) {
            return resolveRealHolder(sector, grouping, footprintByBlocId, rankingRules);
        }
        if (presence == SelectedBlocPresence.PRESENT_BUT_DOMINATED) {
            contestedSystemKeys.add(systemKey);
        }
        return spotlit;
    }

    // The system's real dominant holder, unchanged from the normal pass, for a system the
    // selected bloc is absent from. Its real key (rejected by isSpotlitBloc) is how the caller
    // knows to recede it. Settled by the same rules the normal pass uses, so a receded system draws
    // the holder it would off filter.
    private static DominantHolder resolveRealHolder(
            SectorAPI sector,
            HolderGrouping grouping,
            Map<String, MarketFootprint> footprintByBlocId,
            HolderRankingRules rankingRules) {

        var dominantBlocId = SystemDominance.resolveDominantFactionId(
            footprintByBlocId,
            rankingRules);
        if (dominantBlocId == null) {
            return null;
        }
        return SectorPolitics.resolveBlocHolder(sector, grouping, dominantBlocId);
    }
}
