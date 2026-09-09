package kmu.maplayers.base.geometry;

/**
 * The map as it is shipped, as the one set of knobs every report measures against.
 *
 * <p>Each report here describes the map a reader would open, so all of them have to describe
 * the SAME one. Derived per report, the knobs are a set of values that happen to agree today
 * and can come apart with one edit, leaving two numbers in one dump that are about different
 * geometry with nothing saying so.
 *
 * <p>Only what is genuinely shipped. A knob a single report chooses for itself - how finely to
 * sample, which percentiles to name - is a property of that report and lives with it.
 *
 * <p>The drawn widths a span is judged against are stated here rather than read off the
 * drawing, because this package may not reach into it: how wide a line is stroked is the
 * window's business, and a report that imported the window to learn it would point the
 * geometry at the thing it is supposed to be independent of.
 */
public final class ShippedMap {

    /** The cell knobs the viewer and every drawing of the map open on. */
    public static final SectorGeometryParameters KNOBS = SectorGeometryParameters.createDefaults();

    /** How the coasts are traced, taken from the one place that declares it. */
    public static final Coastlines.CoastRules COAST_RULES = Coastlines.DEFAULT_RULES;

    // Whether spans sharing an anchor are thinned, which is how the map lays them: the thinned
    // set is the proposal, and an unthinned one is a different laying.
    private static final boolean SHOULD_THIN_FORMATIONS = true;

    // How far off a wall already down a span may run and still count as doubling it. The width
    // a span is drawn at: two lines closer than that overlap on screen, which is the state a
    // reader calls doubled.
    private static final double COAST_SLACK = 120;

    // How close two span feet may stand before one of them moves. The same width, which
    // separates feet that are coincident and leaves the rest where the search put them.
    private static final double ANCHOR_SEPARATION = 120;

    /**
     * How every span is offered and judged, at the reach the coast rules carry so the two sets
     * of knobs cannot come apart.
     */
    public static final ContinentBridges.BridgeRules SPAN_RULES = new ContinentBridges.BridgeRules(
        COAST_RULES.bridgeReachMultiple(),
        COAST_SLACK,
        SHOULD_THIN_FORMATIONS,
        ANCHOR_SEPARATION);

    private ShippedMap() {
    }
}
