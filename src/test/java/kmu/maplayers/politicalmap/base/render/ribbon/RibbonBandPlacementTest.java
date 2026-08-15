package kmu.maplayers.politicalmap.base.render.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where a band sits on the stretch it was given, which is the whole of what one clamp has to
 * answer: a band shorter than its stretch can sit anywhere along it, and only one of those places
 * reads as the same landmark on every cell.
 *
 * <p>The cases are the four behaviours the clamp produces plus the two the arithmetic has to be
 * pinned against - a stretch the band exactly fills, where there is one position and both readings
 * of the rule must give it, and a stretch lying opposite the anchor, where the two candidates are
 * equally far and the tie has to break the same way every rebuild.
 *
 * <p>Stated on literals rather than through a cell, because the rule is arithmetic over three
 * numbers and posing it through a ring would decide the interesting cases by whichever name box
 * happened to land where.
 */
final class RibbonBandPlacementTest {

    // A ring twelve thousand units round, the scale a real cell's traced path comes out at.
    private static final double PERIMETER = 12000.0;

    // The band's own reach, short enough against the stretches below that where it sits is a
    // decision rather than the only place it fits - which is the question this rule exists for.
    private static final double BAND_LENGTH = 1000.0;

    @Nested
    class PlaceBandStart {

        @Test
        void startsTheBandOnTheTopCentreWhereTheStretchHasRoomAfterIt() {
            // The common cell: the anchor sits on the stretch with room clockwise of it, so the
            // band opens exactly on the landmark. The stretch straddles the path's start and so
            // arrives fused, which is how a cell whose name sits anywhere but its top centre
            // states its longest stretch - the anchor is the 12000 within it, not the zero the
            // stretch would measure back to.
            assertThat(RibbonBandPlacement.placeBandStart(9000.0, 14000.0, BAND_LENGTH, PERIMETER))
                .isEqualTo(12000.0);
        }

        @Test
        void backsTheBandUpWhereANameBeginsTooSoonAfterTheTopCentre() {
            // The anchor is on the stretch, but the stretch closes 500 after it and the band
            // reaches 1000. The start backs up to the latest the stretch allows, so the band still
            // covers the anchor and only which part of it lands there has moved.
            assertThat(RibbonBandPlacement.placeBandStart(9000.0, 12500.0, BAND_LENGTH, PERIMETER))
                .isEqualTo(11500.0);
        }

        @Test
        void opensTheBandWhereTheStretchDoesWhereANameCoversTheTopCentre() {
            // A name over the anchor and 200 of ring clockwise of it. The band begins as near the
            // anchor as the name allows rather than being thrown to the stretch's far end, which
            // is what makes this one clamp rather than a preference with a fallback: aligning to
            // the end here would have moved the reading's opening most of the way round the cell
            // for the sake of that 200.
            assertThat(RibbonBandPlacement.placeBandStart(200.0, 9000.0, BAND_LENGTH, PERIMETER))
                .isEqualTo(200.0);
        }

        @Test
        void endsTheBandNearTheTopCentreWhereTheStretchClosesJustBehindIt() {
            // The stretch closes 200 short of the anchor and opens 3000 the other side of it, so
            // the band's start is nearer at the closing end: it sits at 10800 and runs up to 11800,
            // finishing just behind the landmark.
            assertThat(RibbonBandPlacement.placeBandStart(3000.0, 11800.0, BAND_LENGTH, PERIMETER))
                .isEqualTo(10800.0);
        }

        @Test
        void placesTheBandOnTheOnlyPositionAnExactFitStretchAllows() {
            // A stretch the band exactly fills has one position, and the clamp has to reach it
            // however far off the anchor lies - the compression that sized the band against the
            // stretch is what guarantees the range is never empty.
            assertThat(RibbonBandPlacement.placeBandStart(4000.0, 5000.0, BAND_LENGTH, PERIMETER))
                .isEqualTo(4000.0);
        }

        @Test
        void takesTheStretchsOwnStartWhereBothItsEndsAreEquallyFarFromTheTopCentre() {
            // A stretch lying opposite the anchor: 3000 of ring from the anchor round to where it
            // opens, and 3000 from the latest start it allows back to the anchor. The tie takes the
            // start, so such a cell places the same way every rebuild instead of on the last of the
            // rounding.
            assertThat(RibbonBandPlacement.placeBandStart(3000.0, 10000.0, BAND_LENGTH, PERIMETER))
                .isEqualTo(3000.0);
        }
    }
}
