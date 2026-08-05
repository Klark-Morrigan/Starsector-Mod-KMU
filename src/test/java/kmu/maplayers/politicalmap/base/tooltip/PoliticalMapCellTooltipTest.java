package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what naming the claim in a single place buys the layer: every one of its boxes runs on that one
 * read, and every one of them heads with the decree holding the hovered system. Both are asserted over
 * the boxes as a set rather than on either of them, since the point is not that two boxes agree today
 * but that agreeing is not each box's decision to make.
 *
 * <p>The line itself has its own suite ({@link CoreTerritoryRowTest}) and the contest below it belongs
 * to each box, so what is left here is where the heading comes from and what it costs to ask for.
 */
final class PoliticalMapCellTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    private static final String CORE_FACTION = "hegemony";
    private static final String CORE_FACTION_CREST = "graphics/hegemony_crest.png";

    // The one line heading a decreed box, and the run of it naming the faction the decree hands the
    // system to - the crest travels ahead of that run as part of the same line.
    private static final int BANNER_ROW = 0;
    private static final int BANNER_FACTION_NAME_RUN = 1;

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    @BeforeEach
    void installColoursAndTheHoveredSystem() {
        CellTooltipPaletteFake.installPalette();

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        stubFaction(sectorMock, CORE_FACTION, "The Hegemony", CORE_FACTION_CREST);
    }

    @AfterEach
    void clearColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class VanillaClaimBreakdownReaderBinding {

        @Test
        void vanillaClaimBreakdownReaderBindingIsTheOneEveryViewsBoxRunsOn() {
            // The regression this guards: a view minting a reader of its own reads the same mechanic
            // through a second computation, which nothing on screen would show the player disagreeing.
            assertThat(SystemDominationTooltip.INSTANCE.claimBreakdownReader)
                .isSameAs(PoliticalMapCellTooltip.VANILLA_CLAIM_BREAKDOWN_READER);

            assertThat(SystemClaimTooltip.INSTANCE.claimBreakdownReader)
                .isSameAs(PoliticalMapCellTooltip.VANILLA_CLAIM_BREAKDOWN_READER);
        }
    }

    @Nested
    class BuildTitleRows {

        @ParameterizedTest(name = "{0}")
        @MethodSource("kmu.maplayers.politicalmap.base.tooltip.PoliticalMapCellTooltipTest"
            + "#provideEveryPoliticalMapBox")
        void buildTitleRowsHeadsTheBoxWithTheFactionHoldingTheSystemByDecree(
                String viewName,
                Function<ClaimBreakdownReader, PoliticalMapCellTooltip> buildBox) {

            // A decree settles the system outright, so it is read off the system name rather than
            // found among the findings below - and it heads every box alike, so a player crossing
            // between the tabs meets one fact one way instead of a heading on one tab and a note on a
            // line on the next.
            stubCoreFaction(CORE_FACTION);

            var titleRows = buildBox.apply(claimBreakdownReaderFake)
                .buildTitleRows(sectorMock, systemMock);

            assertThat(titleRows)
                .as("%s heads with the decree", viewName)
                .hasSize(1);
            assertThat(titleRows.get(BANNER_ROW))
                .isInstanceOf(TooltipRow.CentredRow.class);
            assertThat(readLabelRun(titleRows.get(BANNER_ROW), BANNER_FACTION_NAME_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kmu.maplayers.politicalmap.base.tooltip.PoliticalMapCellTooltipTest"
            + "#provideEveryPoliticalMapBox")
        void buildTitleRowsHeadsTheBoxWithTheSystemNameAloneForASystemUnderNoDecree(
                String viewName,
                Function<ClaimBreakdownReader, PoliticalMapCellTooltip> buildBox) {

            // The ordinary case: nothing was imposed on the system, so nothing heads the box. A banner
            // here would name a core the map never painted.
            assertThat(buildBox.apply(claimBreakdownReaderFake).buildTitleRows(sectorMock, systemMock))
                .as("%s heads with the system name alone", viewName)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kmu.maplayers.politicalmap.base.tooltip.PoliticalMapCellTooltipTest"
            + "#provideEveryPoliticalMapBox")
        void buildTitleRowsAsksOnlyForTheDecreeRatherThanScoringEveryMarket(
                String viewName,
                Function<ClaimBreakdownReader, PoliticalMapCellTooltip> buildBox) {

            // Why the port carries two reads at all: a heading only has to know whether a decree holds
            // the system, and answering that through the full breakdown would charge the scoring of
            // every market in it to every hover. The fake derives one read from the other, so only a
            // case watching the calls can hold this.
            var claimBreakdownReaderMock = mock(ClaimBreakdownReader.class);

            buildBox.apply(claimBreakdownReaderMock)
                .buildTitleRows(sectorMock, systemMock);

            verify(claimBreakdownReaderMock, description(viewName + " reads the decree on its own"))
                .readCoreFactionId(systemMock);
            verify(claimBreakdownReaderMock, never())
                .readBreakdown(any());
        }
    }

    // Every box this layer draws, named for the views that inject it. Taken as a factory rather than as
    // the shared instance because a case has to drive the box on a reader of its own, while each
    // instance is bound to the live vanilla one.
    private static Stream<Arguments> provideEveryPoliticalMapBox() {
        return Stream.of(
            describeBox("the faction and alliance views' box", SystemDominationTooltip::new),
            describeBox("the claims view's box", SystemClaimTooltip::new));
    }

    // Names one box for the cases above. A method rather than an inline pair so the factory infers its
    // type from a parameter instead of being cast at every entry.
    private static Arguments describeBox(
            String viewName,
            Function<ClaimBreakdownReader, PoliticalMapCellTooltip> buildBox) {

        return Arguments.of(viewName, buildBox);
    }

    // Names the box in a call verification's failure, which otherwise reports the mock alone and leaves
    // it to the reader to work out which of the two boxes stopped asking.
    private static org.mockito.verification.VerificationMode verificationsFor(String viewName) {
        return org.mockito.internal.verification.VerificationModeFactory.times(1);
    }

    // Puts the system under a faction's decree, through the same single read the heading makes - the
    // fake derives it from the breakdown, so the two reads can never be set to disagree.
    private void stubCoreFaction(String factionId) {
        claimBreakdownReaderFake.setBreakdown(
            SYSTEM_ID,
            new SystemClaimBreakdown(factionId, factionId, List.of()));
    }
}
