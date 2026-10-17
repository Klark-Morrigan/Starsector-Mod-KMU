package kmu.maplayers.politicalmap.tooltip;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipQualifier;
import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.util.KmuStringsSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a reading of a system lays onto one colony's line: which of its own answers reach the
 * line, which facts it carries as the account handed them, and that the finding and the date land on
 * the same line.
 *
 * <p>What each fold of the system answers is that fold's own suite's, and which words the gathered
 * facts come to is ColonyQualifierTests'. What is pinned here is the gathering: every case varies one
 * fact and reads which word it brings, so a fact dropped or transposed on its way to the line is a
 * failing assertion rather than a line calling out the wrong word.
 */
final class SystemColonyReadingTests {

    private static final String DERELICT_ID = "sentinel_gantries";

    // A colony's line before anything has been said about it, which is what a reading with nothing
    // to say has to hand back unchanged.
    private static final CellTooltipEntryLine PLAIN_LINE =
        CellTooltipEntryLine.createLine(null, "Sentinel Gantries", "0");

    // Whether the account's own carrier says the colony conceals itself - the one fact of the three
    // ways out of view no reading of a system answers.
    private static final boolean IS_CONCEALED = true;
    private static final boolean IS_OPEN = false;

    // A finding the painting layer leads a line with, already worded - handed in and carried as it
    // stands, no reading of a system being able to supply one.
    private static final String LEADING_FINDING = "strongest here";

    // The ordinary colony an account hands over: held in the open and listed by the economy, so a
    // line names it and nothing else unless the reading has something to add.
    private static final AccountedColony ORDINARY_COLONY =
        AccountedColony.createEconomyListedColony(DERELICT_ID, IS_OPEN);

    @BeforeEach
    void installStrings() {
        KmuStringsSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        KmuStringsSettingsFake.clearSettings();
    }

    @Nested
    class DescribeColony {

        @Test
        void leavesALineAloneWhereNothingIsDue() {
            assertThat(SystemColonyReading.NONE.describeColony(PLAIN_LINE, ORDINARY_COLONY))
                .isSameAs(PLAIN_LINE);
        }

        @Test
        void callsOutTheKindItReadForTheColony() {

            var reading = SystemColonyReadingFixture.buildReadingWithKind(
                DERELICT_ID,
                ColonyKind.SPACE_DERELICT);

            assertThat(reading.describeColony(PLAIN_LINE, ORDINARY_COLONY).qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("abandoned"));
        }

        @Test
        void statesTheKindItIsHandedRatherThanTheOneItRead() {
            // An account that met the colony through its own selection already holds its kind, and
            // the kind stated has to be about the colony the line names rather than whatever this
            // reading gathered under the same ID.
            var reading = SystemColonyReadingFixture.buildReadingWithKind(
                DERELICT_ID,
                ColonyKind.SPACE_DERELICT);

            var line = reading.describeColony(
                PLAIN_LINE,
                ORDINARY_COLONY,
                ColonyKind.UNGOVERNED_COLONY);

            assertThat(line.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("decivilised"));
        }

        @Test
        void callsOutAColonyItReadAsUndiscovered() {

            var reading = SystemColonyReadingFixture.buildReadingWithUndiscovered(DERELICT_ID);

            assertThat(reading.describeColony(PLAIN_LINE, ORDINARY_COLONY).qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("undiscovered"));
        }

        @Test
        void callsOutTheConcealmentItIsHanded() {
            // The one way out of view no reading of a system answers: it travels on the account's
            // own carrier, so a colony this reading knows nothing else about still states it.
            var colony = AccountedColony.createEconomyListedColony(DERELICT_ID, IS_CONCEALED);

            assertThat(SystemColonyReading.NONE.describeColony(PLAIN_LINE, colony).qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("hidden"));
        }

        @Test
        void excusesAConcealmentItReadAsOpenlyKnown() {
            // The concealment handed in meets the landmark answer this reading holds, and the
            // economy lists the colony, so nothing is left to call out.
            var reading = SystemColonyReadingFixture.buildReadingWithOpenlyKnown(DERELICT_ID);
            var colony = AccountedColony.createEconomyListedColony(DERELICT_ID, IS_CONCEALED);

            assertThat(reading.describeColony(PLAIN_LINE, colony))
                .isSameAs(PLAIN_LINE);
        }

        @Test
        void callsOutAColonyHandedAsOffTheEconomy() {

            var colony = AccountedColony.createOffEconomyColony(DERELICT_ID, IS_OPEN);

            assertThat(SystemColonyReading.NONE.describeColony(PLAIN_LINE, colony).qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("unlisted"));
        }

        @Test
        void leadsWithTheFindingItIsHanded() {
            var line = SystemColonyReading.NONE.describeColony(PLAIN_LINE, ORDINARY_COLONY, LEADING_FINDING);

            assertThat(line.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding(LEADING_FINDING));
        }

        @Test
        void laysBothTheFindingAndTheRemarkOnTheOneLine() {
            // The whole of what the seam exists for. An account reaching the two apart is one that
            // can lay a finding and forget the date, and a line missing its date reads exactly like
            // a colony somebody is standing over - so the pair is asserted together.
            var reading = SystemColonyReadingFixture.buildReadingRemarkingOn(DERELICT_ID);

            var line = reading.describeColony(
                PLAIN_LINE,
                AccountedColony.createOffEconomyColony(DERELICT_ID, IS_OPEN),
                ColonyKind.SPACE_DERELICT);

            assertThat(line.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("abandoned"));
            assertThat(line.noteText())
                .isEqualTo(SystemColonyReadingFixture.LAST_SEEN);
        }
    }
}
