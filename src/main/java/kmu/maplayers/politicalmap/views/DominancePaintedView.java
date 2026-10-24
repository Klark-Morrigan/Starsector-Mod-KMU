package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReaderSource;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;

import kmu.maplayers.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.ownermap.owners.holders.base.ColonyReadRules;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPaintedView;
import kmu.maplayers.ownermap.owners.holders.base.HolderProvider;
import kmu.maplayers.ownermap.owners.holders.base.RibbonPlanInputs;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolveSource;
import kmu.maplayers.ownermap.picker.OwnerPickerRead;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.DominanceSortModes;
import kmu.maplayers.politicalmap.dominance.DominanceStats;
import kmu.maplayers.politicalmap.dominance.DominanceStatsAggregator;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.holders.ClaimAugmentedHolderProvider;
import kmu.maplayers.politicalmap.holders.DominanceSystemHolderResolve;
import kmu.maplayers.politicalmap.ribbon.HeldOrClaimedSystemRibbonPlanner;

import java.util.function.Predicate;

/**
 * A view whose territory the domination contest paints, and which therefore offers the domination
 * metrics as its picker. It answers {@link #resolveBlocPickerRead} once for every such view: run the
 * grouped dominance pass, then hand its whole read to the shared assembly paired with the vocabulary
 * that can read those numbers. What a view painted this way actually varies is one thing - which of
 * the present blocs it offers as spotlight targets - so that is the only thing it is left to declare.
 *
 * <p>This sits between the layer's views and the concrete ones rather than on
 * {@link PoliticalHolderView}, because the assembly it holds names the dominance aggregator and the
 * dominance vocabulary. There those names would reach the claims view, which the contest does not
 * paint; here they reach exactly the views whose blocs carry the numbers. A view painted by another
 * mechanic implements {@link PoliticalHolderView} directly and pairs its own list with its own
 * vocabulary.
 */
public interface DominancePaintedView
        extends PoliticalHolderView {

    /**
     * Where these views read the claims that extend their held territory, for both the fills and
     * the bands - so a claimed cell's band ranks the contest the cell was painted by.
     *
     * <p>Stated once between the two views the contest paints, which read claims alike.
     *
     * @return the source every claim of the view is read through
     */
    default ClaimBreakdownReaderSource resolveClaimReaderSource() {
        return VanillaClaimBreakdownReader::new;
    }

    /**
     * The source the contest-painted views resolve their per-system holder from: each inhabited
     * system's dominant holder, extended with each system a bloc claims but does not hold, drawn
     * as an unfilled part of that bloc's territory.
     *
     * <p>Answered here rather than on {@link HolderPaintedView}, for the reason the ribbon planner
     * is: a default there would name this mechanic's resolver in front of every view, including
     * the ones it does not paint. Both views the contest paints resolve identically, so it is
     * stated once between them instead of twice beside them.
     *
     * @return the provider that folds claims onto held dominance
     */
    @Override
    default HolderProvider resolveHolderProvider() {
        return ClaimAugmentedHolderProvider.createReadingThrough(resolveClaimReaderSource());
    }

    /**
     * The per-system half of the contest: a marked system re-derived by the market contest alone,
     * under one weighting rule sampled per batch.
     *
     * @return the source a batch opens its dominance resolve from
     */
    @Override
    default SystemHolderResolveSource resolveSystemHolderResolveSource() {
        return DominanceSystemHolderResolve::openResolveOver;
    }

    /**
     * The band counting for a view the contest paints: held dominance where a bloc holds something
     * in a system, and the claim contest where only a claim paints the cell.
     *
     * <p>Both halves are here because both are how such a view paints. Its holder source extends
     * held territory with the systems a bloc claims but does not hold, so a cell of either kind can
     * carry a band, and each has to be counted by the mechanic that painted it - the composition
     * decides which per system off the very economy read the held count makes anyway.
     *
     * <p>Answered once for every such view for the same reason {@link #resolveBlocPickerRead} is: the
     * assembly names the dominance planner, and on {@link HolderPaintedView} that name would reach
     * the views the contest does not paint.
     *
     * @param inputs everything one bake's bands are settled from, sampled once by the bake
     * @return the planner counting each system by the mechanic that painted it
     */
    @Override
    default SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs) {
        return HeldOrClaimedSystemRibbonPlanner.createForPass(inputs, resolveClaimReaderSource());
    }

    /**
     * The picker for a view the domination contest paints, under the player's live weighting rule -
     * what the shared seam resolves to for every such view, the rule being the one input the seam
     * itself does not carry.
     *
     * @param sector          the sector whose colonies decide who is listed; null yields an empty
     *                        read
     * @param colonyReadRules what the player may be shown of a colony and what a decivilised
     *                        world counts as, so a bloc is offered on the strength of the very
     *                        colonies the map paints it for
     * @return this view's picker and presence under the live weighting rule
     */
    @Override
    default OwnerPickerRead<RankedOwner<DominanceStats>> resolveBlocPickerRead(
            SectorAPI sector,
            ColonyReadRules colonyReadRules) {

        return resolveBlocPickerRead(sector, DominanceRules.readFromLunaSettings(), colonyReadRules);
    }

    /**
     * That same picker under a stated weighting rule: the blocs present under this view's own
     * grouping that its gate accepts, each carrying that bloc's whole-sector
     * {@link DominanceStats}, ranked by the {@link DominanceSortModes} vocabulary and whatever the
     * shared assembly offers behind it.
     *
     * <p>The pairing is fixed rather than a per-view choice on purpose - these are the metrics such
     * a view's blocs carry, so offering any other mechanic's vocabulary would rank rows by numbers
     * they do not hold.
     *
     * <p>Beside the live entry above: a caller that has already sampled the rule states it, and one
     * that has not reads the player's. The rule is named here and not on the shared seam because it
     * is this mechanic's, so only the views it paints have an entry taking it.
     *
     * @param sector          the sector whose colonies decide who is listed; null yields an empty
     *                        read
     * @param rules           the dominance-weighting rules for this read, so a listed bloc's
     *                        score and dominations are the numbers the map paints by; who is
     *                        listed at all is decided by the colonies, not by this
     * @param colonyReadRules what the player may be shown of a colony and what a decivilised
     *                        world counts as, so a bloc is offered on the strength of the very
     *                        colonies the map paints it for
     * @return this view's picker, its blocs in the order the sector walk surfaces them, beside the
     *         systems that walk found each bloc living in
     */
    default OwnerPickerRead<RankedOwner<DominanceStats>> resolveBlocPickerRead(
            SectorAPI sector,
            DominanceRules rules,
            ColonyReadRules colonyReadRules) {

        // The aggregation is handed over whole rather than opened here: the rows come off its totals
        // and the presence off the very entries counted into them, so both halves of the picker read
        // are one reading of the sector rather than two walks that could disagree.
        return readPickerUnderOneGrouping(
            sector,
            grouping -> DominanceStatsAggregator.aggregateDominanceStats(
                DominancePass.createOver(sector, rules, colonyReadRules, grouping)),
            DominanceSortModes.MODES);
    }

    /**
     * Which of the present blocs this view offers as spotlight targets - the one decision that
     * differs between views the contest paints, which is why it is the only thing this interface
     * leaves abstract.
     *
     * <p>Re-abstracted from {@link HolderPaintedView#resolveSelectableBlocGate}, whose default offers
     * every present bloc. That default is right for a view whose walk surfaces only blocs it paints;
     * here the grouping can surface a lone faction beside the alliances, so a view that inherited the
     * default silently would list blocs it does not paint rather than failing to compile.
     *
     * @param grouping the grouping the blocs were folded under, so a gate that asks what a bloc is
     *                 (an alliance, a lone faction) reads the same snapshot the numbers came from
     * @return the test a present bloc's ID passes to be listed; always-true for a view that offers
     *         every present bloc
     */
    @Override
    Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping);
}
