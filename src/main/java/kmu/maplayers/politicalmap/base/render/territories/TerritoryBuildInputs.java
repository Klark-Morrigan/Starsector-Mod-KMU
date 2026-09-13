package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.style.BlocStyleResolver;
import kmu.maplayers.politicalmap.base.render.style.BlocStyling;

import java.util.Set;

/**
 * What one build was baked under, retained whole so a cell rebuilt after it is resolved against
 * exactly the inputs the full build used: the paint scheme, the view and the grouping its holding
 * was resolved under, the sidebar picks it sampled, and the two sets its holding derived about the
 * fill - which owned systems paint no fill, and which spotlit systems the pick contests.
 *
 * <p>Fixed once the build ends, which is what parts it from the {@link SystemOccupancy} beside it:
 * the occupancy is folded per marked system as colonies come and go, while nothing here moves
 * until the next rebuild samples afresh. A reader takes this record and names which snapshot it
 * reads, so a pass cannot compose a cell out of one build's theme and another's grouping without
 * the type saying so.
 *
 * <p>The three snapshots stay three records rather than being flattened here, since each is one
 * sampling: the scheme is read once off the theme, the grouping once off the view, the picks once
 * off the sidebar. What this adds is the two derived sets, which belong to no one of them - they
 * come off the holding resolve, and a fill split reads them beside the paint.
 *
 * @param styling             the resolved paint scheme every cell and territory is styled from
 * @param viewGrouping        the view painted and the grouping snapshot holding was resolved under
 * @param contentInputs       the sidebar picks the build sampled, as its one reading of them
 * @param unfilledSystemKeys  the owned systems drawn with no fill: held by their bloc for border
 *                            and label but painting nothing inside its one frontier, so a
 *                            held/claimed boundary reads as a seam where the fill stops
 * @param contestedSystemKeys the spotlit systems the bloc is present in but does not dominate, so
 *                            their cells hatch inside the one spotlit frontier while the dominated
 *                            cells fill solid; empty off filter
 */
public record TerritoryBuildInputs(
    MapStyling styling,
    ViewGrouping viewGrouping,
    ContentInputs contentInputs,
    Set<SystemKey> unfilledSystemKeys,
    Set<SystemKey> contestedSystemKeys) {

    /**
     * The inert inputs a build that never ran carries - what the empty placeholder the render path
     * falls back on after a failed first build is built under.
     *
     * <p>Each snapshot's own placeholder, named by the record that owns it, plus the identity
     * grouping and two empty sets. It carries the active view rather than naming a concrete one,
     * keeping the model view-agnostic; nothing here is read, since the render path skips an empty
     * overlay before it would reach any of it.
     *
     * @param view the view being drawn when the build failed
     * @return the placeholder inputs
     */
    public static TerritoryBuildInputs createEmpty(PoliticalMapView view) {
        return new TerritoryBuildInputs(
            MapStyling.createEmpty(),
            new ViewGrouping(view, HolderGrouping.identity()),
            ContentInputs.createEmpty(),
            Set.of(),
            Set.of());
    }

    /**
     * The concrete style and adjustment one bloc draws under this build, cascading the retained
     * view, grouping, filter state, and theme in one step.
     *
     * <p>Asked of the inputs rather than assembled by each builder from the three snapshots: every
     * input is this build's own retained reading, so a bloc's fill, its national border, and its
     * cells' interior seams all resolve from the same read and cannot diverge.
     *
     * @param blocId the bloc to style - a faction ID, or one of the filter's synthetic keys
     * @return the category bundle and the adjustment applied over it
     */
    public BlocStyling resolveBlocStyling(String blocId) {
        return BlocStyling.resolveFrom(
            styling.renderStyle(),
            BlocStyleResolver.resolveBlocStyleDecision(
                blocId,
                viewGrouping.view(),
                viewGrouping.grouping(),
                contentInputs));
    }
}
