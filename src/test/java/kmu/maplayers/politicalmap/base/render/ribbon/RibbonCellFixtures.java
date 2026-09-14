package kmu.maplayers.politicalmap.base.render.ribbon;

import java.util.List;

/**
 * The cell outlines the band suites pose their cases over, and the site each is laid out from.
 *
 * <p>Shared because what these shapes mean is arithmetic against the sizes a suite bakes at - a
 * cell is "too narrow for the pad" only in relation to the pad plus the half width - so a suite
 * restating one is restating that arithmetic. They had drifted already: the same 500-unit cell was
 * {@code TINY_CELL} in one suite and {@code CELL_TOO_NARROW_FOR_THE_PAD} in another, and
 * {@code NARROW_CELL_SITE} named the site of a different cell in each, so a case moved between
 * suites would have compiled and posed the wrong shape.
 *
 * <p>Each site is named for its own cell rather than for how narrow that cell is, which is what
 * makes the pairing impossible to get wrong: a cell laid out from another cell's site traces from
 * an anchor outside itself and falls back to a corner, which reads as a band starting in the wrong
 * place rather than as the fixture mistake it is.
 *
 * <p>Package-private in the test tree, since only the band suites need these; the settings stubs
 * next door are public because a suite one package up reaches for them.
 */
final class RibbonCellFixtures {

    // A cell four thousand units across, the scale a real cell is cut at, laid out from its own
    // centre. Roomy enough for the band and its pad the whole way round, so a case posed on it is
    // about what it states rather than about the cell running out of room.
    static final List<double[]> SQUARE_CELL = List.of(
        new double[] {0.0, 0.0},
        new double[] {4000.0, 0.0},
        new double[] {4000.0, 4000.0},
        new double[] {0.0, 4000.0});

    static final double[] SQUARE_CELL_SITE = new double[] {2000.0, 2000.0};

    // The same cell with a tab hanging off its right side, too narrow to hold the band. The tab is
    // 400 across against a centreline inset of 400, so its two offset walls cross and what is left
    // of the mouth stands 283 off the cell's own border rather than 400 - a neck the band may not
    // run through, on a cell whose four sides have room several times over.
    //
    // Laid out from SQUARE_CELL_SITE, which is its own centre too: the tab adds room to one side
    // without moving where the cell is.
    static final List<double[]> NECKED_CELL = List.of(
        new double[] {0.0, 0.0},
        new double[] {4000.0, 0.0},
        new double[] {4000.0, 1800.0},
        new double[] {4600.0, 1800.0},
        new double[] {4600.0, 2200.0},
        new double[] {4000.0, 2200.0},
        new double[] {4000.0, 4000.0},
        new double[] {0.0, 4000.0});

    // A cell with no room for the pad and the half width together anywhere along it, but room to
    // spare for the half width on its own - the cell the fallback to the shallower inset exists
    // for. A cell merely narrowed in one place is not this cell and never takes that fall.
    static final List<double[]> CELL_TOO_NARROW_FOR_THE_PAD = List.of(
        new double[] {0.0, 0.0},
        new double[] {500.0, 0.0},
        new double[] {500.0, 500.0},
        new double[] {0.0, 500.0});

    static final double[] CELL_TOO_NARROW_FOR_THE_PAD_SITE = new double[] {250.0, 250.0};

    // A cell narrower than the band is wide. The pad given up entirely still leaves the half width
    // nowhere to go, so there is no shallower trace to fall back to - which is where forcing a band
    // onto a cell stops.
    static final List<double[]> CELL_NARROWER_THAN_THE_BAND = List.of(
        new double[] {0.0, 0.0},
        new double[] {300.0, 0.0},
        new double[] {300.0, 300.0},
        new double[] {0.0, 300.0});

    static final double[] CELL_NARROWER_THAN_THE_BAND_SITE = new double[] {150.0, 150.0};

    // Fixtures only; never instantiated.
    private RibbonCellFixtures() {
    }
}
