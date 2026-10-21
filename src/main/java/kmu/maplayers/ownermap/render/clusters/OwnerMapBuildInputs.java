package kmu.maplayers.ownermap.render.clusters;

import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.render.style.MapPaints;
import kmu.maplayers.ownermap.render.style.MapStyling;

/**
 * What one build was baked under, retained whole so a cell rebuilt after it is resolved against
 * exactly the inputs the full build used: the paint scheme, the view and the owner reading it was
 * styled by, the sidebar picks it sampled, and the fill state its owner source decided for each
 * system - which owned systems hatch, and which paint no fill.
 *
 * <p>Fixed once the build ends, which is what parts it from the {@link SystemOccupancy} beside it:
 * the occupancy is folded per marked system as what stands there comes and goes, while nothing
 * here moves until the next rebuild samples afresh. A reader takes these inputs and names which
 * snapshot it reads, so a pass cannot compose a cell out of one build's theme and another's
 * reading without the type saying so.
 *
 * <p>The scheme, the reading and the picks are held once, inside the build's one {@link MapPaints},
 * which every cell and cluster group of the build paints from. Held here rather than built by each
 * stage, because a stage building its own would resolve each owner's paint again, and the full
 * build and a later batch would each keep a memo of their own. What this adds beside the paints is
 * the view and its source, which a batch re-derives through, and the fill states, which come off
 * the owner source rather than off any one of the three samplings.
 */
public final class OwnerMapBuildInputs {

    private final SystemFillStates fillStates;
    private final MapPaints paints;
    private final ViewReading viewReading;

    /**
     * @param styling       the resolved paint scheme every cell and cluster group is styled from
     * @param viewReading   the view painted, the owner reading it was styled by, and the source its
     *                      owners came from, which a batch re-derives a marked system through
     * @param contentInputs the sidebar picks the build sampled, as its one reading of them
     * @param fillStates    the state each system fills in: hatched where a spotlit owner is present
     *                      in a system it does not win, unfilled where an owner holds a system for
     *                      border and label alone, solid everywhere else
     */
    public OwnerMapBuildInputs(
            MapStyling styling,
            ViewReading viewReading,
            ContentInputs contentInputs,
            SystemFillStates fillStates) {

        this.fillStates = fillStates;
        this.viewReading = viewReading;

        paints = new MapPaints(styling, viewReading.reading(), contentInputs);
    }

    /**
     * The inert inputs a build that never ran carries - what the empty placeholder the render path
     * falls back on after a failed first build is built under.
     *
     * <p>Each snapshot's own placeholder, named by the record that owns it, plus no reading, no
     * source and every system solid. It carries the active view rather than naming a concrete one,
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
            SystemFillStates.ALL_SOLID);
    }

    /** @return the sidebar picks the build sampled, as its paints hold them */
    public ContentInputs getContentInputs() {
        return paints.getContentInputs();
    }

    /** @return the state each system fills in; solid for any system not named */
    public SystemFillStates getFillStates() {
        return fillStates;
    }

    /** @return the paints every cell and cluster group of this build draws in, over its own snapshots */
    public MapPaints getPaints() {
        return paints;
    }

    /** @return the resolved paint scheme every cell and cluster group is styled from, as its paints hold it */
    public MapStyling getStyling() {
        return paints.getStyling();
    }

    /** @return the view painted, with the owner reading and source the build was styled by */
    public ViewReading getViewReading() {
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
