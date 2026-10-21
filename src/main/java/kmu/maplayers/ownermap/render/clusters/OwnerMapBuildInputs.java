package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.render.style.MapPaints;
import kmu.maplayers.ownermap.render.style.MapStyling;

import java.util.Set;

/**
 * What one build was baked under, retained whole so a cell rebuilt after it is resolved against
 * exactly the inputs the full build used: the paint scheme, the view and the owner reading it was
 * styled by, the sidebar picks it sampled, and the two sets its owner source derived about the fill -
 * which owned systems paint no fill, and which spotlit systems the pick contests.
 *
 * <p>Fixed once the build ends, which is what parts it from the {@link SystemOccupancy} beside it:
 * the occupancy is folded per marked system as what stands there comes and goes, while nothing
 * here moves until the next rebuild samples afresh. A reader takes these inputs and names which
 * snapshot it reads, so a pass cannot compose a cell out of one build's theme and another's
 * reading without the type saying so.
 *
 * <p>The three snapshots stay three records rather than being flattened here, since each is one
 * sampling: the scheme is read once off the theme, the reading once off the view, the picks once
 * off the sidebar. What this adds is the two derived sets, which belong to no one of them - they
 * come off the owner source, and a fill split reads them beside the paint.
 *
 * <p>It also holds the one {@link MapPaints} resolved over those snapshots, which every cell and
 * cluster group of the build paints from. Held here rather than built by each stage, because a
 * stage building its own would resolve each owner's paint again, and the full build and a later
 * batch would each keep a memo of their own.
 */
public final class OwnerMapBuildInputs {

    private final Set<SystemKey> contestedSystemKeys;
    private final ContentInputs contentInputs;
    private final MapPaints paints;
    private final MapStyling styling;
    private final Set<SystemKey> unfilledSystemKeys;
    private final ViewReading viewReading;

    /**
     * @param styling             the resolved paint scheme every cell and cluster group is styled
     *                            from
     * @param viewReading         the view painted, the owner reading it was styled by, and the
     *                            source its owners came from, which a batch re-derives a marked
     *                            system through
     * @param contentInputs       the sidebar picks the build sampled, as its one reading of them
     * @param unfilledSystemKeys  the owned systems drawn with no fill: held by their owner for
     *                            border and label but painting nothing inside its one frontier, so
     *                            the boundary between what an owner holds and what its view extends
     *                            it with reads as a seam where the fill stops
     * @param contestedSystemKeys the spotlit systems the owner is present in but does not win, so
     *                            their cells hatch inside the one spotlit frontier while the ones it
     *                            wins fill solid; empty off filter
     */
    public OwnerMapBuildInputs(
            MapStyling styling,
            ViewReading viewReading,
            ContentInputs contentInputs,
            Set<SystemKey> unfilledSystemKeys,
            Set<SystemKey> contestedSystemKeys) {

        this.contestedSystemKeys = contestedSystemKeys;
        this.contentInputs = contentInputs;
        this.styling = styling;
        this.unfilledSystemKeys = unfilledSystemKeys;
        this.viewReading = viewReading;

        paints = new MapPaints(styling, viewReading.reading(), contentInputs);
    }

    /**
     * The inert inputs a build that never ran carries - what the empty placeholder the render path
     * falls back on after a failed first build is built under.
     *
     * <p>Each snapshot's own placeholder, named by the record that owns it, plus no reading, no
     * source and two empty sets. It carries the active view rather than naming a concrete one,
     * keeping the model view-agnostic. The null reading and source are the honest record of a build
     * that never resolved either, and {@link #wasBuilt()} is what every reader that would reach them
     * asks first.
     *
     * @param view the view being drawn when the build failed
     * @return the placeholder inputs
     */
    public static OwnerMapBuildInputs createEmpty(OwnerPaintedView view) {

        return new OwnerMapBuildInputs(
            MapStyling.createEmpty(),
            new ViewReading(view, null, null),
            ContentInputs.createEmpty(),
            Set.of(),
            Set.of());
    }

    /** @return the spotlit systems the owner is present in but does not win; empty off filter */
    public Set<SystemKey> contestedSystemKeys() {
        return contestedSystemKeys;
    }

    /** @return the sidebar picks the build sampled */
    public ContentInputs contentInputs() {
        return contentInputs;
    }

    /** @return the paints every cell and cluster group of this build draws in, over its own snapshots */
    public MapPaints paints() {
        return paints;
    }

    /** @return the resolved paint scheme every cell and cluster group is styled from */
    public MapStyling styling() {
        return styling;
    }

    /** @return the owned systems drawn with no fill */
    public Set<SystemKey> unfilledSystemKeys() {
        return unfilledSystemKeys;
    }

    /** @return the view painted, with the owner reading and source the build was styled by */
    public ViewReading viewReading() {
        return viewReading;
    }

    /**
     * Whether a build actually ran under these inputs, rather than these being the placeholder a
     * failed first build stands behind - which resolved no theme, no categories and no reading, so
     * nothing may be styled, folded or coloured against it.
     *
     * <p>Asked of the reading rather than of any one drawn part, because a real build can draw
     * nothing at all and still be one a later change may fold into.
     *
     * @return true for the inputs of a build that ran
     */
    public boolean wasBuilt() {

        return viewReading.reading() != null;
    }
}
