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

    // One cell across, which is the size a section of void is cut to: a piece of void the size
    // of a system's own cell is comparable to what surrounds it, and a longer one is a corridor
    // rather than a place.
    static final double SECTION_LENGTH = 2 * SectorGeometryParameters.DEFAULT_CELL_RADIUS;

    /**
     * What share of a section a cut may leave behind - the viewer's own setting, so a report
     * describes the division a reader would see there. The sweep at the end of the dump is
     * what that choice rests on.
     */
    static final double MIN_SECTION_SHARE = 0.4;

    /** How long a piece of void may be before it is cut into more than one. */
    static final VoidSections.SectionRules SECTION_RULES =
        new VoidSections.SectionRules(SECTION_LENGTH, MIN_SECTION_SHARE);

    /**
     * How far apart two cells may sit and still be bridged - read off the coast's own defaults
     * rather than restated, which is the only way the two stay equal.
     */
    static final double BRIDGE_REACH_MULTIPLE = Coastlines.DEFAULT_RULES.bridgeReachMultiple();

    private ShippedMap() {
    }
}
