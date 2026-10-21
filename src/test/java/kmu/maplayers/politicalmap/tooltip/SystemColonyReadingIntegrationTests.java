package kmu.maplayers.politicalmap.tooltip;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.testfixtures.starsector.markets.colonies.ColonyMarketFixture;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipQualifier;
import kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture;
import kmu.util.KmuStringsSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins that a reading folded off a walk of a system carries each of the walk's real folds through
 * to a colony's line.
 *
 * <p>Integration rather than unit because the folds are the real lookups over a real colony set:
 * what is pinned is that the factory hands each of them on, so a fold swapped for its empty answer
 * shows on the line rather than going unnoticed.
 */
final class SystemColonyReadingIntegrationTests {

    private static final String DERELICT_ID = "sentinel_gantries";
    private static final String ACADEMY_ID = "galatia_academy";

    // A colony's line before anything has been said about it, which is what an unremarked reading
    // has to hand back unchanged.
    private static final CellTooltipEntryLine PLAIN_LINE =
        CellTooltipEntryLine.createLine(null, "Sentinel Gantries", "0");

    private static final CellTooltipEntryLine ACADEMY_LINE =
        CellTooltipEntryLine.createLine(null, "Galatia Academy", "0");

    // Whether the account's own carrier says the colony conceals itself.
    private static final boolean IS_CONCEALED = true;
    private static final boolean IS_OPEN = false;

    // The academy as vanilla builds it: a colony the economy never registers.
    private static final boolean IS_OFF_ECONOMY = false;

    @BeforeEach
    void installStrings() {
        KmuStringsSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        KmuStringsSettingsFake.clearSettings();
    }

    @AfterEach
    void clearOpenlyKnownColonies() {
        OpenlyKnownColonyFixture.clearRegistrations();
    }

    @Nested
    class ReadColonyReadingOf {

        @Test
        void readsNoWorldAtAllAsTheOrdinaryColonyInPlainSight() {
            // A box with no system to read still draws its rows, so the reading answers rather than
            // refuses: the ordinary kind, found, undated - and a concealment it is handed reads as a
            // secret, nothing having vouched for it.
            var reading = SystemColonyReading.readColonyReadingOf(null, null, null, null);

            var openLine = reading.describeColony(
                PLAIN_LINE,
                AccountedColony.createEconomyListedColony(DERELICT_ID, IS_OPEN));

            var concealedLine = reading.describeColony(
                PLAIN_LINE,
                AccountedColony.createEconomyListedColony(DERELICT_ID, IS_CONCEALED));

            assertThat(openLine)
                .isSameAs(PLAIN_LINE);
            assertThat(concealedLine.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("hidden"));
        }

        @Test
        void foldsTheLandmarksOfTheWalkItWasHandedRatherThanNone() {
            // The factory's four folds are one reading, and only this case says the landmark fold is
            // among them: every other suite hands a reading built from its parts, so the line that
            // folds this one off the walk could be dropped for the empty answer and nothing else
            // would notice. Dropped, the concealed academy would read as hidden rather than falling
            // through to the off-economy word.
            var academy = OpenlyKnownColonyFixture.standOnEntity(
                ColonyMarketFixture.buildFoundConcealedColony("independent"),
                OpenlyKnownColonyFixture.ACADEMY_ENTITY_ID);

            when(academy.getId())
                .thenReturn(ACADEMY_ID);

            OpenlyKnownColonyFixture.registerTheAcademy();

            var reading = SystemColonyReading.readColonyReadingOf(
                null,
                null,
                new Colonies(List.of(new Colony(academy, IS_OFF_ECONOMY))),
                null);

            var line = reading.describeColony(
                ACADEMY_LINE,
                AccountedColony.createOffEconomyColony(ACADEMY_ID, IS_CONCEALED));

            assertThat(line.qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("unlisted"));
        }
    }
}
