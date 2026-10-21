package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.owners.SpotlitOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;

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
 * {@link HolderResolution#contestedSystemKeys()}, so the render layer can split the fill per
 * cell (solid where it dominates, hatched where contested) inside that one frontier rather than
 * fracturing the footprint into two separately-bordered clusters. The key itself is
 * {@link SpotlitOwners}'s - asked for here and recognised there - so a resolve that stamps a
 * footprint and a render side that reads one can never disagree about what a spotlit key is.
 */
public final class FilteredPolitics {

    private FilteredPolitics() {
    }

    /**
     * How the spotlighted bloc stands in one star system: the three-way classification filter
     * mode keys every system's draw off. Decided purely from the system's per-bloc footprints,
     * free of economy and faction types.
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
     * inferring the first from the second. A bloc whose only colony here is one the economy does not
     * list raises no footprint - and a spotlight that read
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

        var dominantBlocId = SystemDominance.resolveDominantBlocId(
            footprintByBlocId,
            rankingRules);

        return selectedBlocId.equals(dominantBlocId)
            ? SelectedBlocPresence.DOMINATES
            : SelectedBlocPresence.PRESENT_BUT_DOMINATED;
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
    public static HolderResolution resolveFilteredHolder(
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
     * inhabited system resolves to one {@link SystemOwner} the geometry clusters by, but every
     * system the selected bloc is present in carries the one spotlit key (and the bloc's palette)
     * instead of the real winner, so the bloc survives where it loses and its whole footprint
     * fuses into one territory. A present-but-dominated system is additionally recorded in the
     * returned contested set, the only place the dominant/contested split lives since both share
     * a key. A system with no owned markets is absent, exactly as in the normal pass.
     *
     * <p>A contested system is always a spotlit one; a dominant spotlit system is simply absent from
     * the set. The filter leaves every owned system filled, so the unfilled set is always empty.
     *
     * @param pass           the weighting rule, colony rule, grouping, and sector walk this pass
     *                       resolves under; a pass over no sector yields empty holding
     * @param selectedBlocId the spotlighted bloc's ID; null yields empty holding
     * @return the presence-aware holders: the holder per system and the contested spotlit systems
     */
    public static HolderResolution resolveFilteredHolder(
            DominancePass pass,
            String selectedBlocId) {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();
        var contestedSystemKeys = new LinkedHashSet<SystemKey>();

        if (!pass.canReadEconomy() || selectedBlocId == null) {
            return new HolderResolution(ownerBySystemKey, contestedSystemKeys, Set.of());
        }

        // Resolved once for the whole walk: every system the bloc is present in joins the same
        // spotlight territory, so they all carry the identical holder. The rebuild pass's own
        // palettes, so the spotlight paints the bloc in the colours its bands take.
        var spotlight = new Spotlight(
            selectedBlocId,
            SpotlitOwners.createSpotlitOwner(pass.palettes().readBlocPalette(selectedBlocId)));

        for (var system : pass.readSystems()) {

            var systemKey = SystemKey.readKeyOf(system);
            var holder = resolveHolder(system, systemKey, pass, spotlight, contestedSystemKeys);

            if (holder != null) {
                ownerBySystemKey.put(systemKey, holder);
            }
        }

        return new HolderResolution(ownerBySystemKey, contestedSystemKeys, Set.of());
    }

    // Resolves one system's presence-aware holder: the selected bloc under the spotlit key where
    // it is present (recording the system as contested when it is present but dominated),
    // otherwise the system's real dominant holder unchanged (flagged to recede by the caller,
    // which sees a key isSpotlitBloc rejects). Reads and regroups the footprints once and shares
    // them with both the classification and the real-holder fallback.
    //
    // Presence is read beside them rather than taken off them: the footprints answer who wins the
    // system, and a bloc whose only colony here is unregistered wins nothing and is still living in
    // the system. Widening the footprints instead
    // would put such a bloc into the map the dominant-holder rank is taken over, where an
    // all-weightless system would hand it the system outright - a spotlight moving a fill, which
    // it must never do.
    //
    // Taken off the system's habitation, which is the same value the cell classification asks the
    // emptiness of, so a bloc kept visible here is never one whose cell the map has meanwhile
    // called empty space.
    private static SystemOwner resolveHolder(
            StarSystemAPI system,
            SystemKey systemKey,
            DominancePass pass,
            Spotlight spotlight,
            Set<SystemKey> contestedSystemKeys) {

        var footprintByBlocId = pass.readBlocFootprints(system);
        var presentBlocIds = pass.readHabitationIn(system).blocIds();

        // Judged under the pass's own settling rules, which the receded real-holder fallback settles
        // by too, so both answer as the base layers do: a spotlight must never move a fill, and
        // judging "dominates" under rules the fills were not resolved by would do exactly that. The
        // tie-break inside is lazy, reading no geometry unless this system actually ties.
        var presence = classifySelectedBlocPresence(
            footprintByBlocId,
            presentBlocIds,
            spotlight.blocId(),
            pass.resolveRankingRulesFor(system));

        // A selectable bloc's colour faction resolves; a missing holder only guards the degenerate
        // case where it vanished mid-session, so the system still draws (as its real receded
        // holder) rather than dropping off the map - and a system that fell back is not spotlit,
        // so it is not recorded contested.
        if (presence == SelectedBlocPresence.ABSENT || spotlight.holder() == null) {
            return resolveRealHolder(system, pass, footprintByBlocId);
        }

        if (presence == SelectedBlocPresence.PRESENT_BUT_DOMINATED) {
            contestedSystemKeys.add(systemKey);
        }

        return spotlight.holder();
    }

    // The system's real dominant holder, unchanged from the normal pass, for a system the
    // selected bloc is absent from. Its real key (rejected by isSpotlitBloc) is how the caller
    // knows to recede it. Taken through the normal pass's own resolve, so a receded system draws
    // the holder it would off filter.
    private static SystemOwner resolveRealHolder(
            StarSystemAPI system,
            DominancePass pass,
            Map<String, MarketFootprint> footprintByBlocId) {

        return SectorPolitics.resolveDominantHolder(system, pass, footprintByBlocId);
    }

    // The spotlighted bloc and the one holder every system it is present in draws as, resolved
    // together once per walk so no system can pair the bloc with a holder resolved apart from it.
    // The holder is null in the degenerate case of a colour faction gone mid-session.
    private record Spotlight(
        String blocId,
        SystemOwner holder) {
    }
}
