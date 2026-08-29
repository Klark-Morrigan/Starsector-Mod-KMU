package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.entities.EntityNameplate;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.SectorScenarioFixtures;
import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.visibility.ColonyKind;
import kmu.maplayers.base.visibility.ColonySightings;
import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.PresenceOnlyFactionStanding;
import kmu.maplayers.politicalmap.base.dominance.UnweighedColony;
import kmu.maplayers.politicalmap.base.dominance.WeighedFactionStanding;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.settings.HiddenMarketScalingChoice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static kmu.maplayers.politicalmap.base.tooltip.StandingsTooltipSeamsFake.VIEW_GROUPING;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the detail mode adds: every faction the box lists opened up into the colonies it holds the
 * system with, and each colony into the factors its weight was summed from.
 *
 * <p>The two facts this box alone decides are that a faction is accounted for by <em>its own</em>
 * colonies - the one thing a bloc's line above cannot say, since a bloc's score is the sum over its
 * members' - and that the economy behind all of them is read once for the box rather than once per
 * faction.
 *
 * <p>A third holds for the colonies the economy does not list: they are read apart from the weighed
 * ones and paired to their faction the same way, so they reach the account without ever reaching the
 * pass. That they weigh nothing is {@link MarketWeightRowResolverTest}'s; that they are the faction's
 * own is here.
 *
 * <p>Where those colonies then hang is the shared resolution's ({@link StandingRowResolverTest}), and
 * the ranking, the status line, the decree, the two headings and the lines naming the blocs belong to
 * the shape both boxes share ({@link SystemStandingsTooltipTest}).
 */
final class ExpandedSystemDominationTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    // Where a colony sits and whether it is concealed, neither of which any case here turns on.
    // Named so the pair of booleans a colony's parts open with can be read rather than counted off
    // against the record's own order.
    private static final boolean VISIBLE_COLONY = false;
    private static final boolean PLANET_COLONY = false;

    // The one colony the economy does not list, marked with no glyph: every case here is about which
    // faction a colony is listed under rather than about what its line leads with.
    private static final UnweighedColony UNLISTED_COLONY = new UnweighedColony(
        "galatia_academy",
        ColonyKind.COLONY,
        VISIBLE_COLONY,
        EntityNameplate.createUnmarkedNameplate("Galatia Academy"));

    // Stability is left unweighed throughout, so a colony breaks down into the one factor each case is
    // about rather than into a stability line every assertion would have to step over.
    private static final DominanceRules ANY_RULES = new DominanceRules(
        false,
        new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.NORMAL, 2.5, 1.0),
        new StationWeighting(false, 3.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    // Opened over no sector, so every pass here answers the empty colony set: what these cases are
    // about is which set the box hands on and under which knobs, not what a walk would have found.
    private static final DominancePass ANY_PASS =
        DominancePass.over(null, ANY_RULES, BASE_FOG, VIEW_GROUPING);

    // The same pass with the dev reveal on, so a case can tell a read that carries the pass's own
    // reveal from one that hardcodes the ordinary answer - which every other case would agree with.
    private static final DominancePass REVEALED_PASS =
        DominancePass.over(null, ANY_RULES, UNDER_THE_REVEAL, VIEW_GROUPING);

    // A pass over a sector that has actually observed something, so its register is a real read of
    // that sector's memory rather than the empty one. The distinction is the whole of what the case
    // using it can see: a sector with nothing recorded answers the same empty register a read that
    // opened one for itself would find, so only a written register tells the pass's own from any
    // other.
    //
    // Built here rather than in the case, because the seam fixture stands a static in front of
    // DominancePass for the duration of each one.
    private static final DominancePass OBSERVED_PASS = buildPassOverAnObservedSector();

    // Two factions of one bloc, so a case can tell "the faction's own colonies" from "the bloc's".
    private static final WeighedFactionStanding LEAD_MEMBER =
        new WeighedFactionStanding("hegemony", 6000);
    private static final WeighedFactionStanding OTHER_MEMBER =
        new WeighedFactionStanding("tritachyon", 3000);

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();
    private final ExpandedSystemDominationTooltip tooltip =
        new ExpandedSystemDominationTooltip(claimBreakdownReaderFake, HolderGrouping::identity);

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    // The one seam this box reaches that the ordinary box does not, so it is stood up here rather than
    // in the shared fixture. CALLS_REAL_METHODS keeps the weight arithmetic beside it live, since the
    // numbers on a factor line are read through the very same class.
    private MockedStatic<KnownMarketFootprints> footprintsMock;

    @BeforeEach
    void installColoursAndTheRankingSeams() {

        CellTooltipPaletteFake.installPalette();
        StandingsTooltipSeamsFake.installSeams(ANY_PASS);

        footprintsMock = Mockito.mockStatic(KnownMarketFootprints.class, Mockito.CALLS_REAL_METHODS);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);
    }

    @AfterEach
    void clearColoursAndTheRankingSeams() {
        footprintsMock.close();
        StandingsTooltipSeamsFake.clearSeams();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class CreateFactionAccountResolver {

        @Test
        void createFactionAccountResolverAccountsForAFactionWithTheColoniesItHolds() {
            // The point of the mode, and the one thing a bloc's line cannot state: a faction's score is
            // the sum over the colonies it holds here, so those are what its account lists - and a
            // sibling's colonies are the sibling's account, never this one's.
            stubBreakdowns(Map.of(
                "hegemony", List.of(buildBreakdown("Culann", 3.0)),
                "tritachyon", List.of(buildBreakdown("Eventide", 5.0))));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS);

            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(LEAD_MEMBER)))
                .containsExactly("Culann");
            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(OTHER_MEMBER)))
                .containsExactly("Eventide");
        }

        @Test
        void createFactionAccountResolverBreaksEachColonyDownIntoItsFactors() {
            // A colony's own line is a sum too, so the account goes one level further: the factors that
            // moved its weight hang beneath it rather than the number being left to be taken on trust.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            var colonyEntries = tooltip
                .createFactionAccountResolver(systemMock, ANY_PASS)
                .resolveAccountEntries(LEAD_MEMBER);

            assertThat(readLabelTexts(colonyEntries.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void createFactionAccountResolverAccountsForNothingWhereAFactionHoldsNoColonyHere() {
            // Nothing to account for reads as the faction listed by its line alone, which is exactly
            // what an empty answer means to the shape above - not a heading over an empty account.
            stubBreakdowns(Map.of("tritachyon", List.of(buildBreakdown("Eventide", 5.0))));

            assertThat(tooltip
                    .createFactionAccountResolver(systemMock, ANY_PASS)
                    .resolveAccountEntries(LEAD_MEMBER))
                .isEmpty();
        }

        @Test
        void createFactionAccountResolverAccountsForTheColoniesTheEconomyDoesNotList() {
            // The one colony no score above accounts for: vanilla builds it and never registers it,
            // so the weighed read cannot see it and the player is left looking at a station in a
            // faction's colours that the box says nothing about.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Culann", 3.0))));
            stubUnweighedColonies(Map.of(
                "hegemony",
                List.of(UNLISTED_COLONY)));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS);

            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(LEAD_MEMBER)))
                .containsExactly("Culann", "Galatia Academy");
        }

        @Test
        void createFactionAccountResolverKeepsAnUnlistedColonyToTheFactionHoldingIt() {
            // The two reads are keyed the same way and paired the same way, so an unlisted colony
            // can no more be listed under a sibling's name than a weighed one can.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of(
                "tritachyon",
                List.of(UNLISTED_COLONY)));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS);

            assertThat(accountResolver.resolveAccountEntries(LEAD_MEMBER))
                .isEmpty();
            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(OTHER_MEMBER)))
                .containsExactly("Galatia Academy");
        }

        @Test
        void createFactionAccountResolverAccountsForAFactionPresentThroughUnlistedColoniesAlone() {
            // A faction the weighing never reached: its every colony here is one the economy does
            // not list, so its whole account is the unlisted read. The colonies were always carried
            // - what they lacked was a line to hang from, which the ranking now gives them.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of(
                "tritachyon",
                List.of(UNLISTED_COLONY)));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS);

            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(
                    new PresenceOnlyFactionStanding("tritachyon"))))
                .containsExactly("Galatia Academy");
        }

        @Test
        void createFactionAccountResolverReadsTheColoniesUnderTheRankingsOwnPass() {
            // The parts have to be read under the rule and reveal the scores above them were ranked
            // through, or the box would explain a number with arithmetic that did not produce it.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            tooltip.createFactionAccountResolver(systemMock, ANY_PASS);

            footprintsMock.verify(
                () -> KnownMarketFootprints.readBreakdownByFaction(
                    Colonies.NONE,
                    ANY_RULES,
                    ANY_PASS.colonyKnowledge()));
        }

        @Test
        void createFactionAccountResolverReadsTheWeighedColoniesOnceForTheWholeBox() {
            // Every faction's colonies come out of one read of the pass's colony set: read per
            // faction, two of them could be explained from different selections over it, and the
            // walk behind that set is the most expensive thing a hover does.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS);

            accountResolver.resolveAccountEntries(LEAD_MEMBER);
            accountResolver.resolveAccountEntries(OTHER_MEMBER);

            // Counted on the real arguments rather than on matchers, since registering the stub is
            // itself an invocation and an any()-matched count would take it for a second read.
            footprintsMock.verify(
                () -> KnownMarketFootprints.readBreakdownByFaction(
                    Colonies.NONE,
                    ANY_RULES,
                    ANY_PASS.colonyKnowledge()),
                Mockito.times(1));
        }

        @Test
        void createFactionAccountResolverReadsTheUnlistedColoniesUnderTheRankingsOwnReveal() {
            // The reveal decides what the box may name, and a colony the pass could not weigh is no
            // exception. Read at a reveal of its own it would withhold an undiscovered station while
            // naming the weighed colonies beside it - or name one the player has not found.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of());

            tooltip.createFactionAccountResolver(systemMock, REVEALED_PASS);

            footprintsMock.verify(
                () -> KnownMarketFootprints.readUnweighedColoniesByFaction(
                    Colonies.NONE,
                    REVEALED_PASS.colonyKnowledge()));
        }

        @Test
        void createFactionAccountResolverReadsTheUnlistedColoniesOnceForTheWholeBox() {
            // The unlisted colonies are a second selection over the same set, made once per paint
            // rather than once per faction listed - the same rule the weighed read is held to, so
            // neither can end up describing a system the other did not.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of(
                "hegemony",
                List.of(UNLISTED_COLONY)));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS);

            accountResolver.resolveAccountEntries(LEAD_MEMBER);
            accountResolver.resolveAccountEntries(OTHER_MEMBER);

            // Counted on the real arguments rather than on matchers, since registering the stub is
            // itself an invocation and an any()-matched count would take it for a second read.
            footprintsMock.verify(
                () -> KnownMarketFootprints.readUnweighedColoniesByFaction(
                    Colonies.NONE,
                    ANY_PASS.colonyKnowledge()),
                Mockito.times(1));
        }

        @Test
        void createFactionAccountResolverDatesTheColoniesAgainstThePassesOwnRegister() {
            // The whole reason the register is handed down rather than opened here: the dates
            // stated have to come off the very observations the pass resolved its projection
            // against. Read from the sector afresh, a box could date a colony against a register
            // the fills beside it were never drawn under - and nothing on screen would say which.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of());

            // The case is worth nothing unless the pass carries a register that is not the empty
            // one, that being what a read opening its own would also find - so the premise is
            // asserted rather than assumed.
            assertThat(OBSERVED_PASS.colonyKnowledge().sightings())
                .isNotSameAs(ColonySightings.NONE);

            var sightings = ArgumentCaptor.forClass(ColonySightings.class);

            try (var notesMock = Mockito.mockStatic(ColonyObservationNotes.class)) {

                tooltip.createFactionAccountResolver(systemMock, OBSERVED_PASS);

                notesMock.verify(() -> ColonyObservationNotes.readNotesFor(
                    any(),
                    any(),
                    any(),
                    sightings.capture()));
            }
            assertThat(sightings.getValue())
                .isSameAs(OBSERVED_PASS.colonyKnowledge().sightings());
        }
    }

    @Nested
    class BuildBodySections {

        @Test
        void buildBodySectionsFallsBackToTheSystemStatusWhenNothingRanks() {
            // An empty system says the same thing in either mode: there is no more detail to be had
            // about a system nobody holds.
            var statusRow = StandingsTooltipSeamsFake.stubStatusRow("Unpopulated");

            StandingsTooltipSeamsFake.stubGroupEntries();
            stubBreakdowns(Map.of());

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).readRowsInOrder())
                .containsExactly(statusRow);
        }
    }

    // Stands the economy read in as the colonies each faction holds in the system, so no case needs a
    // live economy to produce parts for the box to open up.
    private void stubBreakdowns(Map<String, List<MarketWeightBreakdown>> breakdownsByFactionId) {
        footprintsMock
            .when(() -> KnownMarketFootprints.readBreakdownByFaction(
                any(),
                any(),
                any()))
            .thenReturn(breakdownsByFactionId);
    }

    // Stands in the second read, the colonies present that the economy does not list. Stubbed apart
    // from the weighed ones because that is how the box reads them: two selections over the one set,
    // so nothing an unlisted colony says can reach the pass.
    private void stubUnweighedColonies(Map<String, List<UnweighedColony>> coloniesByFactionId) {
        footprintsMock
            .when(() -> KnownMarketFootprints.readUnweighedColoniesByFaction(
                any(),
                any()))
            .thenReturn(coloniesByFactionId);
    }

    // The sector behind OBSERVED_PASS: one derelict, met by the player, so a register entry really
    // lands and the read comes back as something other than the empty answer.
    private static DominancePass buildPassOverAnObservedSector() {

        // The staged pair is a derelict and a concealed base, which is exactly what the register
        // records - only a gated colony ever reaches it, and those are the two gated shapes.
        var sector = SectorScenarioFixtures.buildUnvisitedSectorHoldingGatedPair(SYSTEM_ID);

        SectorPoliticsFixtures.markSystemAsVisitedByPlayer(
            sector,
            SectorPoliticsFixtures.buildOnlySystem(sector));

        return DominancePass.over(sector, ANY_RULES, BASE_FOG, VIEW_GROUPING);
    }

    // One colony worth the given size points on its base size alone, so a case states a colony by the
    // one number it is ranked against its siblings by.
    private static MarketWeightBreakdown buildBreakdown(String marketName, double contribution) {
        return new MarketWeightBreakdown(
            marketName,
            EntityNameplate.createUnmarkedNameplate(marketName),
            VISIBLE_COLONY,
            PLANET_COLONY,
            FULL_STABILITY,
            new BaseSizeFactor(4, 4.0, contribution, 0.0),
            Optional.empty(),
            Optional.empty());
    }
}
