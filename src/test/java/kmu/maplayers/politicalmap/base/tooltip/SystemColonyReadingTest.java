package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.ColonyDiscoveryLookup;
import kmu.maplayers.base.visibility.ColonyKind;
import kmu.maplayers.base.visibility.ColonyKindLookup;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a box may say about a system's colonies when it has read nothing about them.
 *
 * <p>What each of the three halves answers is its own suite's ({@link ColonyKindLookupTest},
 * {@link ColonyDiscoveryLookupTest}, {@link ColonyObservationNotesTest}); what this one is about is
 * the gathering - that a reading with a half missing answers as the ordinary colony in plain sight
 * rather than throwing, since a box asking it holds a row it is already committed to drawing.
 *
 * <p>And the one thing the gathering does rather than answers: laying what it knows onto a colony's
 * line. Which words a finding comes to is {@link ColonyQualifierTest}'s; that both the finding and
 * the date reach the same line is pinned here, that being the whole reason the two are laid
 * together.
 */
final class SystemColonyReadingTest {

    private static final String DERELICT_ID = "sentinel_gantries";

    // A colony's line before anything has been said about it, which is what an unremarked reading
    // has to hand back unchanged.
    private static final CellTooltipEntryLine PLAIN_LINE =
        CellTooltipEntryLine.createLine(null, "Sentinel Gantries", "0");

    // What an account with no findings of its own hands in, which is every case here but the one
    // asserting that a finding and a remark reach the same line.
    private static final ColonyQualifierFacts NOTHING_WAS_FOUND = null;

    // The derelict in plain sight the pairing case is posed over: found, open, and off the
    // economy's listing, which is how a hulk reaches a box. Named for the reason the suite's other
    // absences are - the facts read as a row of unexplained booleans otherwise.
    private static final boolean HOLDS_NO_CLAIM = false;
    private static final boolean IS_FOUND = true;
    private static final boolean IS_OPEN = false;
    private static final boolean IS_UNLISTED = false;

    @BeforeEach
    void installStrings() {
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ReadColoniesIn {

        @Test
        void readColoniesInReadsNoWorldAtAllAsTheOrdinaryColonyInPlainSight() {
            // A box with no system to read still draws its rows, so the reading answers rather than
            // refuses: nothing is qualified and nothing is dated.
            var reading = SystemColonyReading.readColoniesIn(null, null, null, null);

            assertThat(reading.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.COLONY);
            assertThat(reading.isDiscoveredColony(DERELICT_ID))
                .isTrue();
            assertThat(reading.describeColony(PLAIN_LINE, DERELICT_ID, NOTHING_WAS_FOUND).noteText())
                .isNull();
        }
    }

    @Nested
    class ReadKindOf {

        @Test
        void readKindOfAnswersFromTheKindsItWasGatheredWith() {

            var reading = new SystemColonyReading(
                new ColonyKindLookup(Map.of(DERELICT_ID, ColonyKind.SPACE_DERELICT)),
                ColonyDiscoveryLookup.NONE,
                ColonyObservationNotes.NONE);

            assertThat(reading.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
        }

        @Test
        void readKindOfReadsAnUngatheredHalfAsTheOrdinaryColony() {
            // Part of a reading is still a reading. The kind errs towards the settled place
            // everywhere else it is resolved, so an absent lookup errs the same way rather than
            // differently.
            var reading = new SystemColonyReading(null, null, null);

            assertThat(reading.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.COLONY);
        }
    }

    @Nested
    class IsDiscoveredColony {

        @Test
        void isDiscoveredColonyAnswersFromTheDiscoveriesItWasGatheredWith() {

            var reading = new SystemColonyReading(
                ColonyKindLookup.NONE,
                new ColonyDiscoveryLookup(Set.of(DERELICT_ID)),
                ColonyObservationNotes.NONE);

            assertThat(reading.isDiscoveredColony(DERELICT_ID))
                .isFalse();
        }

        @Test
        void isDiscoveredColonyReadsAnUngatheredHalfAsFound() {
            // The direction that states no finding: a box cannot call a colony unfound on the
            // strength of a fold nobody made.
            var reading = new SystemColonyReading(null, null, null);

            assertThat(reading.isDiscoveredColony(DERELICT_ID))
                .isTrue();
        }
    }

    @Nested
    class DescribeColony {

        @Test
        void describeColonyLeavesALineAloneWhereNothingIsDue() {

            var reading = new SystemColonyReading(
                ColonyKindLookup.NONE,
                ColonyDiscoveryLookup.NONE,
                null);

            assertThat(reading.describeColony(PLAIN_LINE, DERELICT_ID, NOTHING_WAS_FOUND))
                .isSameAs(PLAIN_LINE);
        }

        @Test
        void describeColonyLaysBothTheFindingAndTheRemarkOnTheOneLine() {
            // The whole of what the seam exists for. An account reaching the two apart is one that
            // can lay a finding and forget the date, and a line missing its date reads exactly like
            // a colony somebody is standing over - so the pair is asserted together.
            var reading = SystemColonyReadingFixture.buildReadingRemarkingOn(DERELICT_ID);

            var line = reading.describeColony(PLAIN_LINE, DERELICT_ID, new ColonyQualifierFacts(
                ColonyKind.SPACE_DERELICT,
                HOLDS_NO_CLAIM,
                IS_FOUND,
                IS_OPEN,
                IS_UNLISTED));

            assertThat(line.qualifierText())
                .isEqualTo("abandoned");
            assertThat(line.noteText())
                .isEqualTo(SystemColonyReadingFixture.LAST_SEEN);
        }
    }
}
