package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.OwnerStyleResolver;
import kmu.maplayers.ownermap.render.style.OwnerStyling;
import kmu.maplayers.ownermap.render.style.ResolvedBlocPaint;

import java.util.Set;

/**
 * What one build was baked under, retained whole so a cell rebuilt after it is resolved against
 * exactly the inputs the full build used: the paint scheme, the view and the owner reading it was
 * styled by, the sidebar picks it sampled, and the two sets its holding derived about the fill -
 * which owned systems paint no fill, and which spotlit systems the pick contests.
 *
 * <p>Fixed once the build ends, which is what parts it from the {@link SystemOccupancy} beside it:
 * the occupancy is folded per marked system as colonies come and go, while nothing here moves
 * until the next rebuild samples afresh. A reader takes this record and names which snapshot it
 * reads, so a pass cannot compose a cell out of one build's theme and another's reading without
 * the type saying so.
 *
 * <p>The three snapshots stay three records rather than being flattened here, since each is one
 * sampling: the scheme is read once off the theme, the reading once off the view, the picks once
 * off the sidebar. What this adds is the two derived sets, which belong to no one of them - they
 * come off the holding resolve, and a fill split reads them beside the paint.
 *
 * @param styling             the resolved paint scheme every cell and cluster group is styled from
 * @param viewReading         the view painted and the owner reading it was styled by
 * @param contentInputs       the sidebar picks the build sampled, as its one reading of them
 * @param unfilledSystemKeys  the owned systems drawn with no fill: held by their owner for border
 *                            and label but painting nothing inside its one frontier, so the
 *                            boundary between what an owner holds and what its view extends it
 *                            with reads as a seam where the fill stops
 * @param contestedSystemKeys the spotlit systems the owner is present in but does not win, so their
 *                            cells hatch inside the one spotlit frontier while the ones it wins
 *                            fill solid; empty off filter
 */
public record OwnerMapBuildInputs(
    MapStyling styling,
    ViewReading viewReading,
    ContentInputs contentInputs,
    Set<SystemKey> unfilledSystemKeys,
    Set<SystemKey> contestedSystemKeys) {

    /**
     * The inert inputs a build that never ran carries - what the empty placeholder the render path
     * falls back on after a failed first build is built under.
     *
     * <p>Each snapshot's own placeholder, named by the record that owns it, plus no reading, the
     * identity grouping and two empty sets. It carries the active view rather than naming a
     * concrete one, keeping the model view-agnostic. The null reading is the honest record of a
     * build that never resolved one, and {@link #wasBuilt()} is what every reader that would reach
     * it asks first.
     *
     * @param view the view being drawn when the build failed
     * @return the placeholder inputs
     */
    public static OwnerMapBuildInputs createEmpty(OwnerPaintedView view) {
        return new OwnerMapBuildInputs(
            MapStyling.createEmpty(),
            new ViewReading(view, null, HolderGrouping.identity()),
            ContentInputs.createEmpty(),
            Set.of(),
            Set.of());
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

    /**
     * Everything one owner's elements paint from under this build, cascading the retained reading,
     * filter state and theme in one step and carrying the result down to the shades.
     *
     * <p>Asked of the inputs rather than assembled by each builder from the three snapshots: every
     * input is this build's own retained reading, so an owner's fill, its cluster border, and its
     * cells' interior seams all resolve from the same read and cannot diverge.
     *
     * @param ownerId the owner to style - an owner ID, or one of the filter's synthetic keys
     * @param owner   whose shades it paints in where nothing desaturates it
     * @return the bundle, the adjustment over it, and the shades its elements pick from
     */
    public ResolvedBlocPaint resolveBlocPaintOf(String ownerId, SystemOwner owner) {
        return ResolvedBlocPaint.resolveFrom(
            resolveBlocStyling(ownerId),
            owner,
            styling.desaturationPalette());
    }

    // The bundle and adjustment behind that paint: the layer's own per-owner decision mapped onto
    // this build's theme. Private because a caller wanting one is a caller about to paint an
    // element with it, and resolving the shades separately is how a fill comes to be muted while
    // the border beside it is not.
    private OwnerStyling resolveBlocStyling(String ownerId) {
        return OwnerStyling.resolveFrom(
            styling.renderStyle(),
            styling.categories(),
            OwnerStyleResolver.resolveBlocStyleDecision(
                ownerId,
                viewReading.reading(),
                styling.categories(),
                contentInputs));
    }
}
