package kmu.maplayers.base.refresh;

/**
 * The coarse changes any painting map layer could raise, whatever it paints: its ground moved,
 * its background receded, its filter moved, its styling moved. Declared in the framework rather
 * than beside one layer because none of them names anything a layer holds - a hazard overlay
 * would mean by each of these exactly what a political one does.
 */
public enum MapLayerCommonRefreshSignal implements MapLayerRefreshSignal {

    /**
     * The set of reachable systems changed - a gate activated, a jump point was established - so
     * the cell geometry must be rebuilt. The Voronoi partition is expensive but changes rarely,
     * which is why it is worth its own signal rather than riding on a styling one.
     */
    GEOMETRY,

    /**
     * A recede toggle (Mute or Desaturate) flipped, so every view that recedes ground rebuilds.
     * The toggles are sidebar-only per-save state rather than LunaLib fields, so a flip moves no
     * settings revision and this is the seam that repaints the overlay live instead. A view that
     * draws no receded ground does not read it, exactly as it ignores any change it does not
     * render.
     */
    RECEDE_STYLE,

    /**
     * The filter's spotlight selection changed or was cleared. Sidebar-only per-save state like
     * the recede toggles, and read at the pipeline level rather than by any single view, since
     * the filter is a mode orthogonal to the active view - either view can be filtered - so a
     * raise repaints under whichever view is up without a view naming the filter.
     */
    FILTER,

    /**
     * A shared appearance toggle flipped - whether uninhabited systems draw their outline,
     * whether a cluster label spells its owner's full or short name. Sidebar-only per-save state
     * again, and read at the pipeline level for the same reason the filter is: both restyle the
     * whole map under whichever view is up.
     */
    MAP_STYLE;

    @Override
    public String getId() {
        // The constant is the name, so there is no second identity to keep in step with it.
        return name();
    }
}
