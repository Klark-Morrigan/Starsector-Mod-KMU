package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.ParameterDeclarations;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readLabelRun;
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
 * read, and each heads with the decree holding the hovered system unless its own body states that
 * decree. Both are asserted over the boxes as a set rather than on either of them, since the point is
 * not that two boxes agree today but that agreeing is not each box's decision to make.
 *
 * <p>The line itself has its own suite ({@link CoreTerritoryHeadingTest}) and the contest below it
 * belongs to each box, so what is left here is where the heading comes from, which boxes take it, and
 * what it costs to ask for.
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
        @ArgumentsSource(StandingsBoxProvider.class)
        void buildTitleRowsHeadsTheBoxWithTheFactionHoldingTheSystemByDecree(
                String viewName,
                Function<ClaimBreakdownReader, PoliticalMapCellTooltip> buildBox) {

            // A decree settles the system outright, so it is read off the system name rather than
            // found among the findings below. These are the boxes whose subject is a standing rather
            // than the claim, so the decree would go unsaid entirely if the heading did not say it.
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
        @ArgumentsSource(ClaimBoxProvider.class)
        void buildTitleRowsHeadsTheBoxWithTheSystemNameAloneWhereTheBodyStatesTheDecree(
                String viewName,
                Function<ClaimBreakdownReader, PoliticalMapCellTooltip> buildBox) {

            // The claim boxes name the claimant and mark the decreed hold on that very line, so a
            // banner above would put the same fact twice in one hover - which reads as two findings
            // about the system rather than as one stated once.
            stubCoreFaction(CORE_FACTION);

            assertThat(buildBox.apply(claimBreakdownReaderFake).buildTitleRows(sectorMock, systemMock))
                .as("%s leaves the decree to its body", viewName)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(EveryPoliticalMapBoxProvider.class)
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
        @ArgumentsSource(EveryPoliticalMapBoxProvider.class)
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

    /**
     * Every box this layer draws, named for the views that inject it. Taken as a factory rather than
     * as the shared instance because a case has to drive the box on a reader of its own, while each
     * instance is bound to the live vanilla one.
     *
     * <p>A class rather than a factory method so the cases above name it by class literal: a method
     * is reached by a fully-qualified string that no rename ever follows, which leaves the suite
     * compiling and failing at run time instead.
     */
    static final class EveryPoliticalMapBoxProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return Stream.concat(
                new StandingsBoxProvider().provideArguments(parameters, context),
                new ClaimBoxProvider().provideArguments(parameters, context));
        }
    }

    /**
     * The boxes whose subject is a faction's standing rather than the claim, so nothing in their bodies
     * names a decree and the heading is the only place it can be said. A provider rather than the one
     * box it holds today, since what the cases behind it are about is the category.
     */
    static final class StandingsBoxProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            // The alliance seam is stood in as the identity grouping and the wording as the contested
            // one: these cases are about the heading over the box, which neither reaches.
            return Stream.of(
                describeBox(
                    "the faction and alliance views' box",
                    reader -> new SystemDominationTooltip(
                        reader,
                        HolderGrouping::identity,
                        ContestWordingFixtures.CONTESTED_WORDING)));
        }
    }

    /**
     * The boxes built on the claim contest, whose claim block states the decree itself - so what they
     * are pinned on is heading with the system name alone. A provider rather than the one box it holds
     * today, since what the cases behind it are about is the category.
     */
    static final class ClaimBoxProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            // The alliance seam is stood in as the identity grouping and the wording as the contested
            // one: these cases are about the heading over the box, which neither reaches.
            return Stream.of(
                describeBox(
                    "the claims view's box",
                    reader -> new SystemClaimTooltip(
                        reader,
                        HolderGrouping::identity,
                        ContestWordingFixtures.CONTESTED_WORDING)));
        }
    }

    // Names one box for the cases above. A method rather than an inline pair so the factory infers its
    // type from a parameter instead of being cast at every entry.
    private static Arguments describeBox(
            String viewName,
            Function<ClaimBreakdownReader, PoliticalMapCellTooltip> buildBox) {

        return Arguments.of(viewName, buildBox);
    }

    // Puts the system under a faction's decree, through the same single read the heading makes - the
    // fake derives it from the breakdown, so the two reads can never be set to disagree.
    private void stubCoreFaction(String factionId) {
        claimBreakdownReaderFake.setBreakdown(
            SYSTEM_ID,
            new SystemClaimBreakdown(factionId, factionId, List.of()));
    }
}
