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
     * How long a section of void may be, in cell radii.
     *
     * <p>One cell across: a piece of void the size of a system's own cell is comparable to
     * what surrounds it, and a longer one is a corridor rather than a place.
     *
     * <p>In radii rather than units because that is what it means, and because the viewer's
     * slider is set in them. Held here in the form both readers want it, so the window and the
     * report cannot come to describe two different divisions.
     */
    static final double SECTION_LENGTH_IN_RADII = 2;

    /** The same length in world units, which is what the division itself is measured in. */
    static final double SECTION_LENGTH =
        SECTION_LENGTH_IN_RADII * SectorGeometryParameters.DEFAULT_CELL_RADIUS;

    /**
     * What share of a section a cut may leave behind.
     *
     * <p>Settled by eye against the sweep at the end of the void regions dump. Above it the
     * only crossings leaving that much on both sides are chords over the open middle, which
     * read as thrown across a pocket rather than dividing it; below it the tips come back into
     * range, win on being narrowest, and leave one long piece uncut behind them.
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
