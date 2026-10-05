package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.ribbon.RibbonPlanInputs;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;

/**
 * A view whose owners are holders: blocs that hold a system through its colonies, folded from the
 * colonies' owning factions by a grouping. It states the parts that are its own - the grouping, who
 * stands together in a contest, the holding rule for the whole sector and for one system, how a band
 * is counted, and how a bloc looks - and this assembles them into the two answers the core asks of
 * every view.
 *
 * <p>The assembly is the point of the type. A view painting holders samples its grouping once per
 * rebuild here, and the reading and the source are both built over that one sampling, so the owners
 * the source resolves and the names and shades the reading gives them describe one fold.
 *
 * <p>Every part is declared rather than defaulted: each is one layer's rule, and a default here would
 * put one layer's mechanic in front of every layer painting holders.
 */
public interface HolderPaintedView extends OwnerPaintedView {

    /**
     * The grouping this view folds factions into blocs under: the identity grouping for a view
     * painting every faction as its own bloc, named groups for a view folding several factions into
     * one. Sampled once per rebuild by {@link #resolveViewReading}.
     *
     * @return the grouping that collapses factions into blocs
     */
    HolderGrouping resolveGrouping();

    /**
     * Which blocs stand together in a contest this view's bands judge: two blocs this grouping folds
     * together share a system as allies rather than as rivals.
     *
     * <p>Apart from {@link #resolveGrouping}, which decides what a cell is painted as: blocs a view
     * paints apart can still stand together when a system is contested. A layer in which nobody does
     * answers with the identity grouping.
     *
     * @return the grouping a band judges its contest against
     */
    HolderGrouping resolveContestGrouping();

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
                resolveContestGrouping(),
                resolveHolderProvider(),
                resolveSystemHolderResolveSource(),
                this::resolveRibbonPlanner));
    }
}
