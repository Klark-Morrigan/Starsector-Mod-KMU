package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.visibility.ColonyVisibility;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;
import kmu.maplayers.politicalmap.base.politics.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.dominance.ribbon.HeldOrClaimedSystemRibbonPlanner;

import java.util.function.Predicate;

/**
 * A view whose territory the domination contest paints, and which therefore offers the domination
 * metrics as its picker. It answers {@link #resolveBlocPicker} once for every such view: run the
 * grouped dominance pass, assemble the options from it, and pair them with the vocabulary that reads
 * those numbers. What a view painted this way actually varies is one thing - which of the present
 * blocs it offers as spotlight targets - so that is the only thing it is left to declare.
 *
 * <p>This sits between the view seam and the concrete views rather than on the seam itself,
 * because the assembly it holds names the dominance aggregator and the dominance vocabulary. On
 * {@link PoliticalMapView} those names would reach every view including the ones the contest does
 * not paint; here they reach exactly the views whose blocs carry the numbers. A view painted by
 * another mechanic implements the seam directly and pairs its own list with its own vocabulary.
 */
public interface DominancePaintedView extends PoliticalMapView {

    /**
     * The band counting for a view the contest paints: held dominance where a bloc holds something
     * in a system, and the claim contest where only a claim paints the cell.
     *
     * <p>Both halves are here because both are how such a view paints. Its holder source extends
     * held territory with the systems a bloc claims but does not hold, so a cell of either kind can
     * carry a band, and each has to be counted by the mechanic that painted it - the composition
     * decides which per system off the very economy read the held count makes anyway.
     *
     * <p>Answered once for every such view for the same reason {@link #resolveBlocPicker} is: the
     * assembly names the dominance planner, and on {@link PoliticalMapView} that name would reach
     * the views the contest does not paint.
     *
     * @param inputs the bake's pass, palette source and laying rules
     * @return the planner counting each system by the mechanic that painted it
     */
    @Override
    default SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs) {
        return HeldOrClaimedSystemRibbonPlanner.createForPass(inputs);
    }

    /**
     * The picker for a view the domination contest paints: the blocs present under this view's own
     * grouping that its gate accepts, each carrying that bloc's whole-sector {@link DominanceStats},
     * paired with {@link DominanceSortMode}'s vocabulary.
     *
     * <p>The pairing is fixed rather than a per-view choice on purpose - these are the metrics such
     * a view's blocs carry, so offering any other vocabulary would rank rows by numbers they do not
     * hold.
     *
     * @param sector           the sector whose economy the visibility gate reads; null yields an
     *                         empty picker
     * @param rules            the dominance-weighting rules for this read, so selectable blocs are
     *                         gated under the same rule the map paints under
     * @param colonyVisibility what the player may be shown of a colony, so a bloc is offered on
     *                         the strength of the very colonies the map paints it for
     * @return this view's picker, its blocs in the order the economy walk surfaces them
     */
    @Override
    default ListPicker<RankedBloc<DominanceStats>> resolveBlocPicker(
            SectorAPI sector,
            DominanceRules rules,
            ColonyVisibility colonyVisibility) {

        // The grouping is resolved once and handed to both halves, so the numbers, the crest, and
        // the gate all read against the one snapshot rather than three live samples of a set that
        // moves (the alliances view samples Nexerelin).
        var grouping = resolveGrouping();
        var pass = DominancePass.over(sector, rules, colonyVisibility, grouping);

        return new ListPicker<>(
            buildSelectableBlocs(
                sector,
                grouping,
                DominanceStatsAggregator.aggregateDominanceStats(pass),
                resolveSelectableBlocGate(grouping)),
            DominanceSortMode.MODES);
    }

    /**
     * Which of the present blocs this view offers as spotlight targets - the one decision that
     * differs between views the contest paints, which is why it is the only thing this interface
     * leaves abstract. The presence gate itself is not asked for here: a bloc reaches the test only
     * when the shared stats read already found it holding a visible market somewhere.
     *
     * @param grouping the grouping the blocs were folded under, so a gate that asks what a bloc is
     *                 (an alliance, a lone faction) reads the same snapshot the numbers came from
     * @return the test a present bloc's id passes to be listed; always-true for a view that offers
     *         every present bloc
     */
    Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping);
}
