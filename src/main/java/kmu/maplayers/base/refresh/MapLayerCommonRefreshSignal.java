package kmu.maplayers.base.refresh;

/**
 * The coarse changes any painting map layer could raise, whatever it paints: its cells moved,
 * what it recedes changed, its filter moved, its styling moved. Declared in the framework rather
 * than beside one layer because none of them names anything a layer holds - a hazard overlay
 * would mean by each of these exactly what a political one does.
 *
 * <p>Two things a signal can be, and the three sidebar ones are only the second. {@link #GEOMETRY}
 * is folded into a consumer's staleness, so raising it is what makes a rebuild happen. The other
 * three are a record that a preference moved: a bake samples the preference <em>values</em> and
 * folds those, since a screen switch moves a value without moving any counter, so what these
 * raises leave is the board's line naming which one a player touched and when. That line is read
 * beside a capture, where it says which flip preceded a rebuild - which the sampled values, folded
 * into one number, cannot.
 */
public enum MapLayerCommonRefreshSignal implements MapLayerRefreshSignal {

    /**
     * The set of reachable systems changed - a gate activated, a jump point was established - so
     * the cell geometry must be rebuilt. The Voronoi partition is expensive but changes rarely,
     * which is why it is worth its own signal rather than riding on a styling one.
     *
     * <p>The one signal here a consumer folds into its staleness, so a raise rebuilds.
     */
    GEOMETRY,

    /**
     * A toggle governing how a receded cluster draws flipped. Sidebar-only per-save state rather
     * than a LunaLib field, so a flip moves no settings revision.
     *
     * <p>What repaints is the bake's sampling of the toggle's value; this is the record that a
     * player flipped it, which is what a reader has to read a rebuild against.
     */
    RECEDE_STYLE,

    /**
     * The filter's spotlight selection changed or was cleared. Sidebar-only per-save state like
     * the recede toggles, and raised at the pipeline level rather than by whatever the layer is
     * currently drawing, since the filter is a mode orthogonal to that choice - anything the
     * layer draws can be filtered.
     *
     * <p>A record of the change rather than what acts on it, on the same terms as
     * {@link #RECEDE_STYLE}.
     */
    FILTER,

    /**
     * A toggle restyling the whole map rather than one cluster flipped. Sidebar-only per-save
     * state again, and raised at the pipeline level for the same reason the filter is: the change
     * lands across everything drawn, whatever the layer is currently drawing.
     *
     * <p>A record of the change rather than what acts on it, on the same terms as
     * {@link #RECEDE_STYLE}.
     */
    MAP_STYLE;

    @Override
    public String getId() {
        // The constant is the name, so there is no second identity to keep in step with it.
        return name();
    }
}
