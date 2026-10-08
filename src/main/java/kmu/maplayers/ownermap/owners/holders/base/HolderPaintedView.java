package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.holders.base.picker.BlocPickerAssembly;
import kmu.maplayers.ownermap.owners.holders.base.picker.BlocStandingSortMode;
import kmu.maplayers.ownermap.picker.OwnerMetrics;
import kmu.maplayers.ownermap.picker.OwnerPickerRead;
import kmu.maplayers.ownermap.picker.OwnerStatsRead;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;

import java.util.function.Predicate;

/**
 * A view whose owners are holders: blocs that hold a system through its colonies, folded from the
 * colonies' owning factions by a grouping. A view states its own parts, and this assembles them into
 * the two answers the core asks of every view, both built over one sampling of the grouping per
 * rebuild. It assembles the picker too, off the sector's colonies under stated colony rules.
 */
public interface HolderPaintedView
        extends OwnerPaintedView {

    /**
     * The grouping this view folds factions into blocs under: the identity grouping for a view
     * painting every faction as its own bloc, named groups for a view folding several factions into
     * one. Sampled once per rebuild by {@link #resolveViewReading}.
     *
     * @return the grouping that collapses factions into blocs
     */
    HolderGrouping resolveGrouping();

    /**
     * Which blocs stand together in a contest this view's bands judge.
     *
     * <p>Apart from {@link #resolveGrouping}, which decides what a cell is painted as: blocs a view
     * paints apart can still stand together when a system is contested. A layer in which nobody does
     * answers {@link OwnerAffiliation#NONE}.
     *
     * @return who a band judges its contest against, sampled once per rebuild
     */
    OwnerAffiliation resolveContestAffiliation();

    /**
     * This view's whole-sector holding rule.
     *
     * @return the provider resolving each system's holder for one rebuild
     */
    HolderProvider resolveHolderProvider();

    /**
     * Where this view's per-system holding rule is opened from, for an incremental batch. It has to
     * land the holder {@link #resolveHolderProvider} would, or a refreshed cell parts from the
     * rebuild around it.
     *
     * @return the source a batch opens its holder resolve from
     */
    SystemHolderResolveSource resolveSystemHolderResolveSource();

    /**
     * The mechanic this view's cells are counted by for their presence bands.
     *
     * <p>Per view for the same reason the hover box is: a band explains the fill it sits inside, so
     * counting it by a mechanic other than the one the cell was painted by would lead the band on a
     * bloc the cell is not painted for.
     *
     * @param inputs everything one bake's bands are settled from, sampled once by the bake - the
     *               grouping among it, so a band folds factions into blocs exactly as the fill did
     * @return the planner this view's bands are counted through
     */
    SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs);

    /**
     * The answers about this view's blocs - each one's shades, name, crest, recede and category -
     * over one sampling of the grouping.
     *
     * @param sector   the sector a bloc's colour faction, name and crest are read from
     * @param grouping the grouping the holding was folded under, so the reading answers about the
     *                 blocs the holding produced
     * @return this view's reading of its blocs
     */
    OwnerReading resolveOwnerReading(SectorAPI sector, HolderGrouping grouping);

    /**
     * The reading and the holder source over one sampling of the grouping, as the core asks for
     * them.
     */
    @Override
    default ViewReading resolveViewReading(SectorAPI sector) {

        // Sampled once and handed to both halves, so the source folds and the reading names one
        // grouping - a live set of groups can move between two reads of it.
        var grouping = resolveGrouping();

        return new ViewReading(
            this,
            resolveOwnerReading(sector, grouping),
            new HolderOwnerSource(
                grouping,
                resolveContestAffiliation(),
                resolveHolderProvider(),
                resolveSystemHolderResolveSource(),
                this::resolveRibbonPlanner));
    }

    /**
     * This view's picker under the player's live colony rules ({@link
     * ColonyReadRules#readFromLunaSettings}) - what the core's sidebar and stale-selection heal ask
     * for, neither holding a running pass's rules.
     */
    @Override
    default OwnerPickerRead<?> resolveOwnerPickerRead(SectorAPI sector) {
        return resolveBlocPickerRead(sector, ColonyReadRules.readFromLunaSettings());
    }

    /**
     * This view's picker under stated colony rules, so a bloc is offered on the strength of the very
     * colonies the map paints it for.
     *
     * <p>What it does <em>not</em> take is the rule any one mechanic weighs by. Who a picker lists
     * is settled by the sector's colonies, and how a listed bloc's numbers are arrived at is the
     * painting layer's own business - so a seam naming a weighting rule would hand every view a
     * knob only some of them spend. A layer that weighs takes its rule beside this.
     *
     * <p>Defaults to an empty read, so a holder view with no spotlight inherits one rather than
     * overriding with two arguments it would ignore.
     *
     * @param sector          the sector whose colonies decide who is listed; null yields an empty
     *                        read
     * @param colonyReadRules what the player may be shown of a colony and what a decivilised world
     *                        counts as
     * @return this view's picker - its blocs in the order the source walk surfaces them, and the
     *         vocabulary ranking them - beside where that walk found each bloc; empty when no bloc
     *         qualifies
     */
    default OwnerPickerRead<?> resolveBlocPickerRead(
            SectorAPI sector,
            ColonyReadRules colonyReadRules) {

        return OwnerPickerRead.empty();
    }

    /**
     * Turns one bloc walk's read into the picker read a view offers, so an overriding view declares
     * only what distinguishes it - which of the present blocs are targets, and which vocabulary
     * ranks them - rather than repeating the crest, name, and option assembly every view resolves
     * identically.
     *
     * <p>It takes the walk's read whole rather than its totals and its presence apart. The two are
     * one reading of the sector ({@link OwnerStatsRead}), so passing them separately would let a view
     * hand over a map from one walk and an index from another, which is precisely the disagreement
     * the paired read exists to make impossible.
     *
     * <p>The crest and the label are the handed owner reading's, which the calling view resolves
     * over the walk's own grouping, so the rows name and badge each bloc exactly as the map does. A
     * bloc with no crest keeps its option and simply draws its name alone. The label is always in
     * the short form: the picker labels a bloc by its short name regardless of the map's name-format
     * setting, so a long-form map label never widens the sidebar's option rows.
     *
     * <p>The vocabulary it bundles is the caller's own modes with {@link BlocStandingSortMode} behind
     * them, which is why the sector and the grouping are read here beside the reading. Where a
     * bloc stands with the player is one fact read off the sector rather than a number any
     * mechanic's fold computes, so it is offered from the single point every view's read passes
     * through instead of being declared into each vocabulary.
     *
     * @param <S>             the calling view's own metrics type, ranked by that view's vocabulary
     * @param sector          the sector a bloc's standing with the player is read from
     * @param grouping        the grouping the walk folded under, so the gate and a bloc's
     *                        membership resolve against the same snapshot the numbers came from
     * @param reading         the view's reading of its owners over that same grouping, which names
     *                        and badges each row; handed in rather than resolved here so the rows
     *                        and the numbers come off one sampling
     * @param statsRead       the walk's totals and the systems behind them, in the order it surfaced
     *                        them, which the returned rows preserve
     * @param vocabularyModes the calling layer's own modes - the numbers its rows carry - bundled
     *                        with those rows so a row can only ever be sorted by numbers it carries
     * @return the rows this view offers paired with the vocabulary that ranks them, beside the whole
     *         walk's presence
     */
    default <S extends OwnerMetrics> OwnerPickerRead<RankedOwner<S>> buildBlocPickerRead(
            SectorAPI sector,
            HolderGrouping grouping,
            OwnerReading reading,
            OwnerStatsRead<S> statsRead,
            ListSortModes<RankedOwner<S>> vocabularyModes) {

        return BlocPickerAssembly.buildBlocPickerRead(
            this, sector, grouping, reading, statsRead, vocabularyModes);
    }

    /**
     * Which of the present blocs this view offers as spotlight targets. A bloc reaches the test only
     * when the walk already found it, so this decides what is offered among those, never who is
     * present.
     *
     * <p>Defaults to offering every one of them, which is the answer for a view whose blocs are all
     * of a kind. A view whose walk surfaces blocs it does not paint - a view painting groups, where
     * a lone faction is present but is not a group - narrows it here.
     *
     * @param grouping the grouping the blocs were folded under, so a gate that asks what a bloc is
     *                 (a group, a lone faction) reads the same snapshot the numbers came from
     * @return the test a present bloc's ID passes to be listed
     */
    default Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {

        return blocId -> true;
    }
}
