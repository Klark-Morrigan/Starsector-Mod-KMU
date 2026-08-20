package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colony;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The one rule that counts a cell's band, whichever mechanic painted the cell: every colony the
 * player may be shown in the system, folded into a count per bloc.
 *
 * <p>Each mechanic used to count for itself - the held side off the footprints its weights were
 * summed from, the claim side off the contest's standings - and the two answered differently about
 * one system. A colony the economy does not list reached neither band though both boxes named it; a
 * concealed colony fed a weight on one layer and only a sibling term on the other; and the dev
 * reveal reached the held count while the claim count went on hiding what the fill was drawing. A
 * band reports how a system splits, and that is a fact about the system rather than about the
 * mechanic reading it, so one count now serves both.
 *
 * <p>What stays each mechanic's own is the painter and the ranking, which are the two things that
 * genuinely differ: whose fill the band sits inside, and the order the fill decided. A bloc neither
 * mechanic ranked - one present through colonies no score was ever computed from - falls to the end
 * in id order, so it draws its run without a place it never earned.
 *
 * <p>The counts come off the pass's habitation projection, so a band never counts out a colony the
 * pass's colony rule is keeping back and never omits one it is showing. What that rule admits is
 * settled there and not here - a concealed colony reaches this count where the player has seen it
 * and not otherwise - so nothing about a colony is asked twice.
 *
 * <p>Habitation rather than the wider listing of what may be named, which is the one place a band
 * and a hover box over the same cell say different things. A run stands for somebody holding
 * something in the system, and nobody has ever been aboard a derelict, so a seen hulk is named in
 * the box and raises no run - the same reading that keeps the cell beneath from painting as settled.
 *
 * <p>Past the projection, nothing at all is asked: a colony the economy never listed and one it
 * weighed at full size both count as the one colony each of them is.
 *
 * <p>Pure over the pass it is handed, with the two live reads - the walk of a system and a bloc's
 * shades - reached through {@link RibbonPlanInputs}, so every case is posed on hand-built sets.
 */
public final class ColonyCellRibbons {

    // What one colony adds to its bloc's count. A band counts holdings rather than weighing them,
    // so every colony behind a run is worth exactly one segment whatever its size or standing.
    private static final int ONE_COLONY = 1;

    // The count a bloc's first colony is added to, which is the fold's identity.
    private static final int NO_COLONIES = 0;

    private ColonyCellRibbons() {
    }

    /**
     * Plans one cell's band from the colonies in its system.
     *
     * @param paintingBlocId the bloc the cell's fill was painted for, which the band's gate is
     *                       stated against; empty where no bloc's fill covers the cell, which the
     *                       gate reads as its own case rather than as a painter every bloc differs
     *                       from
     * @param system         the system the cell draws as, counted through the pass's own walk of it
     * @param rankedBlocIds  the order the painting mechanic ranked its blocs in, best placed first;
     *                       a bloc absent from it draws behind those that are, ordered by id
     * @param inputs         the pass the colonies are read from, where a bloc's shades come from,
     *                       and how far its runs go
     * @return the cell's runs in draw order, or {@link RibbonPlan#NONE} where nothing the rule
     *         counts is present in the cell
     */
    public static RibbonPlan planCellRibbon(
            Optional<String> paintingBlocId,
            StarSystemAPI system,
            List<String> rankedBlocIds,
            RibbonPlanInputs inputs) {

        return RibbonPlan.planCellRibbon(
            paintingBlocId,
            BlocPresence.collectColouredPresences(
                orderCountsForBand(
                    countColoniesByBloc(
                        inputs.readInhabitingColoniesIn(system),
                        inputs.grouping()),
                    rankedBlocIds),
                inputs.palettes()),
            inputs.rules());
    }

    // How many colonies each bloc holds in the system: counted per owner, then folded under the
    // pass's grouping so two allies' colonies arrive as one bloc's count rather than as two runs
    // side by side.
    //
    // Folded through the grouping's own regroup rather than by resolving each colony's bloc here,
    // so what happens to a colony whose owner belongs to no nameable bloc is decided once, where
    // every other per-bloc fold on the map decides it.
    //
    // Unordered on purpose: the ranking is the mechanic's to state, and a count that came out in
    // the walk's order would look ranked without being it.
    private static Map<String, Integer> countColoniesByBloc(
            List<Colony> inhabitingColonies,
            HolderGrouping grouping) {

        var countByFactionId = new HashMap<String, Integer>();

        for (var colony : inhabitingColonies) {
            countByFactionId.merge(
                colony.market().getFaction().getId(),
                ONE_COLONY,
                Integer::sum);
        }
        return grouping.regroupByBloc(countByFactionId, NO_COLONIES, Integer::sum);
    }

    // The counts in the order their runs are drawn: the mechanic's ranking first, then whatever it
    // did not rank, by id.
    //
    // The tail is what the widening made possible. A mechanic ranks the blocs it scored, and the
    // shared set now hands back blocs no score was computed for - a faction present through a
    // concealed base or an unlisted station alone. Ordering those by id is arbitrary and says so:
    // there is no place among the ranked blocs that would not be a claim about a contest they took
    // no part in.
    //
    // A ranking naming one bloc twice - which an alliance's two members produce - keeps the place
    // its first mention took, since a map insertion does not move an existing key.
    private static Map<String, Integer> orderCountsForBand(
            Map<String, Integer> countByBlocId,
            List<String> rankedBlocIds) {

        var orderedCountByBlocId = new LinkedHashMap<String, Integer>();

        for (var blocId : rankedBlocIds) {
            var blocCount = countByBlocId.get(blocId);

            if (blocCount != null) {
                orderedCountByBlocId.put(blocId, blocCount);
            }
        }
        for (var blocId : sortUnrankedBlocIds(countByBlocId, orderedCountByBlocId.keySet())) {
            orderedCountByBlocId.put(blocId, countByBlocId.get(blocId));
        }
        return orderedCountByBlocId;
    }

    // The counted blocs the mechanic's ranking never named, in id order.
    private static List<String> sortUnrankedBlocIds(
            Map<String, Integer> countByBlocId,
            Set<String> rankedBlocIds) {

        var unrankedBlocIds = new ArrayList<String>();

        for (var blocId : countByBlocId.keySet()) {
            if (!rankedBlocIds.contains(blocId)) {
                unrankedBlocIds.add(blocId);
            }
        }
        unrankedBlocIds.sort(String::compareTo);

        return unrankedBlocIds;
    }
}
