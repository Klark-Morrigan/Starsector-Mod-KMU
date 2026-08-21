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
 */
final class ShippedMap {

    /** The cell knobs the viewer and every drawing of the map open on. */
    static final SectorGeometryParameters KNOBS = SectorGeometryParameters.createDefaults();

    /**
     * How far apart two cells may sit and still be bridged - read off the coast's own defaults
     * rather than restated, which is the only way the two stay equal.
     */
    static final double BRIDGE_REACH_MULTIPLE = Coastlines.DEFAULT_RULES.bridgeReachMultiple();

    private ShippedMap() {
    }
}
