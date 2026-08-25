package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.ColonyKind;
import kmu.maplayers.base.visibility.ColonyKindLookup;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a box may say about a system's colonies when it has read nothing about them.
 *
 * <p>What the two halves answer is each their own suite's ({@link ColonyKindLookupTest},
 * {@link ColonyObservationNotesTest}); what this one is about is the pairing - that a reading with
 * a half missing answers as the ordinary colony in plain sight rather than throwing, since a box
 * asking it holds a row it is already committed to drawing.
 */
final class SystemColonyReadingTest {

    private static final String DERELICT_ID = "sentinel_gantries";

    // A colony's line before anything has been said about it, which is what an unremarked reading
    // has to hand back unchanged.
    private static final CellTooltipEntryLine PLAIN_LINE =
        CellTooltipEntryLine.createLine(null, "Sentinel Gantries", "0");

    @Nested
    class ReadColoniesIn {

        @Test
        void readColoniesInReadsNoWorldAtAllAsTheOrdinaryColonyInPlainSight() {
            // A box with no system to read still draws its rows, so the reading answers rather than
            // refuses: nothing is qualified and nothing is dated.
            var reading = SystemColonyReading.readColoniesIn(null, null, null, null);

            assertThat(reading.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.COLONY);
            assertThat(reading.remarkOnColony(PLAIN_LINE, DERELICT_ID).noteText())
                .isNull();
        }
    }

    @Nested
    class ReadKindOf {

        @Test
        void readKindOfAnswersFromTheKindsItWasPairedWith() {

            var reading = new SystemColonyReading(
                new ColonyKindLookup(Map.of(DERELICT_ID, ColonyKind.SPACE_DERELICT)),
                ColonyObservationNotes.NONE);

            assertThat(reading.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
        }

        @Test
        void readKindOfReadsAnUnpairedHalfAsTheOrdinaryColony() {
            // Half a reading is still a reading. The kind errs towards the settled place everywhere
            // else it is resolved, so an absent lookup errs the same way rather than differently.
            var reading = new SystemColonyReading(null, null);

            assertThat(reading.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.COLONY);
        }
    }

    @Nested
    class RemarkOnColony {

        @Test
        void remarkOnColonyLeavesALineAloneWhereNothingIsDue() {
            
            var reading = new SystemColonyReading(ColonyKindLookup.NONE, null);

            assertThat(reading.remarkOnColony(PLAIN_LINE, DERELICT_ID))
                .isSameAs(PLAIN_LINE);
        }
    }
}
