package kmu.maplayers.politicalmap.dominance.tooltip;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.memory.observations.RecordedObservations;

import kmu.maplayers.SectorScenarioFixtures;
import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.maplayers.base.visibility.colonies.ColonyObservation;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.standings.PresenceOnlyFactionStanding;
import kmu.maplayers.politicalmap.dominance.standings.WeighedFactionStanding;
import kmu.maplayers.politicalmap.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;
import kmu.maplayers.politicalmap.dominance.weighting.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.UnweighedColony;
import kmu.maplayers.politicalmap.tooltip.ColonyObservationNotes;
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

import static kmu.maplayers.base.tooltip.content.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.SYSTEM_COMPOSITION;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_DEV_REVEAL;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.politicalmap.dominance.tooltip.MarketWeightBreakdownFixtures.buildBaseSizeBreakdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * Pins what a running game's domination box hangs beneath each faction it lists: the colonies that
 * faction holds the system with, opened down to the factors each weight was summed from.
 *
 * <p>A faction is accounted for by <em>its own</em> colonies, the one thing a bloc's line above cannot
 * say, since a bloc's score is the sum over its members' - read once for the whole box rather than
 * once per faction. The colonies the economy does not list are read apart from the weighed ones and
 * paired to their faction the same way, so they reach the account without ever reaching the pass.
 * That they weigh nothing is {@link MarketWeightRowResolverTests}'s; that they are the faction's own is
 * here.
 */
final class PassFactionAccountSourceTests {

    private static final String SYSTEM_ID = "askonia";

    // Concealed or not, which no case here turns on.
    private static final boolean VISIBLE_COLONY = false;

    // The one colony the economy does not list, marked with no glyph: every case about it is about
    // which faction it is listed under rather than about what its line leads with.
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
    // about is which set the account hands on and under which knobs, not what a walk would have found.
    private static final DominancePass ANY_PASS =
        DominancePass.createOver(null, ANY_RULES, UNDER_THE_FOG, HolderGrouping.identity());

    // The same pass with the dev reveal on, so a case can tell a read that carries the pass's own
    // reveal from one that hardcodes the ordinary answer - which every other case would agree with.
    private static final DominancePass REVEALED_PASS =
        DominancePass.createOver(null, ANY_RULES, UNDER_THE_DEV_REVEAL, HolderGrouping.identity());

    // A pass over a sector that has actually observed something, so its register is a real read of
    // that sector's memory rather than the empty one. The distinction is the whole of what the case
    // using it can see: a sector with nothing recorded answers the same empty register a read that
    // opened one for itself would find, so only a written register tells the pass's own from any
    // other.
    private static final DominancePass OBSERVED_PASS = buildPassOverAnObservedSector();

    // Two factions of one bloc, so a case can tell "the faction's own colonies" from "the bloc's".
    private static final WeighedFactionStanding LEAD_MEMBER =
        new WeighedFactionStanding("hegemony", 6000);

    private static final WeighedFactionStanding OTHER_MEMBER =
        new WeighedFactionStanding("tritachyon", 3000);

    // A system the ranking weighed nobody in, for the cases about the colonies the economy does not
    // list.
    private static final Map<String, List<MarketWeightBreakdown>> NO_BREAKDOWNS = Map.of();

    private final PassFactionAccountSource accountSource = new PassFactionAccountSource();

    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    // The economy read behind the account, which no unit test can drive live. CALLS_REAL_METHODS keeps
    // the weight arithmetic beside it live, since the numbers on a factor line are read through the
    // very same class.
    private MockedStatic<KnownMarketFootprints> footprintsMock;

    @BeforeEach
    void installColoursAndTheEconomyRead() {

        CellTooltipPaletteFake.installPalette();

        footprintsMock = Mockito.mockStatic(KnownMarketFootprints.class, Mockito.CALLS_REAL_METHODS);
    }

    @AfterEach
    void clearColoursAndTheEconomyRead() {

        footprintsMock.close();

        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class CreateFactionAccountResolver {

        // The point of the deeper levels, and the one thing a bloc's line cannot state: a faction's
        // score is the sum over the colonies it holds here, so those are what its account lists - and
        // a sibling's colonies are the sibling's account, never this one's.
        @Test
        void accountsForAFactionWithTheColoniesItHolds() {

            // ARRANGE
            var breakdownsByFactionId = Map.of(
                "hegemony", List.of(buildBaseSizeBreakdown("Culann", 3.0)),
                "tritachyon", List.of(buildBaseSizeBreakdown("Eventide", 5.0)));

            // ACT
            var accountResolver = accountSource.createFactionAccountResolver(
                systemMock,
                ANY_PASS,
                breakdownsByFactionId,
                PATROL_DETAILS);
            var actualLeadEntries = accountResolver.resolveAccountEntries(LEAD_MEMBER);
            var actualOtherEntries = accountResolver.resolveAccountEntries(OTHER_MEMBER);

            // ASSERT
            assertThat(readLabelTexts(actualLeadEntries))
                .containsExactly("Culann");
            assertThat(readLabelTexts(actualOtherEntries))
                .containsExactly("Eventide");
        }

        // A colony's own line is a sum too, so the account goes one level further: the factors that
        // moved its weight hang beneath it rather than the number being left to be taken on trust.
        @Test
        void breaksEachColonyDownIntoItsFactors() {

            // ARRANGE
            var breakdownsByFactionId = Map.of("hegemony", List.of(buildBaseSizeBreakdown("Jangala", 6.0)));

            // ACT
            var actualColonyEntries = accountSource
                .createFactionAccountResolver(systemMock, ANY_PASS, breakdownsByFactionId, PATROL_DETAILS)
                .resolveAccountEntries(LEAD_MEMBER);

            // ASSERT
            assertThat(readLabelTexts(actualColonyEntries.get(0).children()))
                .containsExactly("Size");
        }

        // The level travels on into the account rather than stopping at the decision to build one:
        // the composition level names the colonies and works out none of the arithmetic beneath
        // them. Left to the cut, the box would have worded every factor of every colony first and
        // then dropped the lot.
        @Test
        void breaksAColonyIntoNothingWhereTheLevelStopsAtTheColonies() {

            // ARRANGE
            var breakdownsByFactionId = Map.of("hegemony", List.of(buildBaseSizeBreakdown("Jangala", 6.0)));

            // ACT
            var actualColonyEntries = accountSource
                .createFactionAccountResolver(systemMock, ANY_PASS, breakdownsByFactionId, SYSTEM_COMPOSITION)
                .resolveAccountEntries(LEAD_MEMBER);

            // ASSERT
            assertThat(readLabelTexts(actualColonyEntries))
                .containsExactly("Jangala");
            assertThat(actualColonyEntries.get(0).children())
                .isEmpty();
        }

        // Nothing to account for reads as the faction listed by its line alone, which is exactly what
        // an empty answer means to the box's shape - not a heading over an empty account.
        @Test
        void accountsForNothingWhereAFactionHoldsNoColonyHere() {

            // ARRANGE
            var breakdownsByFactionId = Map.of("tritachyon", List.of(buildBaseSizeBreakdown("Eventide", 5.0)));

            // ACT
            var actualEntries = accountSource
                .createFactionAccountResolver(systemMock, ANY_PASS, breakdownsByFactionId, PATROL_DETAILS)
                .resolveAccountEntries(LEAD_MEMBER);

            // ASSERT
            assertThat(actualEntries)
                .isEmpty();
        }

        // The one colony no score above accounts for: vanilla builds it and never registers it, so
        // the weighed read cannot see it and the player is left looking at a station in a faction's
        // colours that the box says nothing about.
        @Test
        void accountsForTheColoniesTheEconomyDoesNotList() {

            // ARRANGE
            stubUnweighedColonies(Map.of(
                "hegemony",
                List.of(UNLISTED_COLONY)));

            var breakdownsByFactionId = Map.of("hegemony", List.of(buildBaseSizeBreakdown("Culann", 3.0)));

            // ACT
            var actualEntries = accountSource
                .createFactionAccountResolver(systemMock, ANY_PASS, breakdownsByFactionId, PATROL_DETAILS)
                .resolveAccountEntries(LEAD_MEMBER);

            // ASSERT
            assertThat(readLabelTexts(actualEntries))
                .containsExactly("Culann", "Galatia Academy");
        }

        // The two reads are keyed the same way and paired the same way, so an unlisted colony can no
        // more be listed under a sibling's name than a weighed one can.
        @Test
        void keepsAnUnlistedColonyToTheFactionHoldingIt() {

            // ARRANGE
            stubUnweighedColonies(Map.of(
                "tritachyon",
                List.of(UNLISTED_COLONY)));

            // ACT
            var accountResolver = accountSource.createFactionAccountResolver(
                systemMock,
                ANY_PASS,
                NO_BREAKDOWNS,
                PATROL_DETAILS);
            var actualLeadEntries = accountResolver.resolveAccountEntries(LEAD_MEMBER);
            var actualOtherEntries = accountResolver.resolveAccountEntries(OTHER_MEMBER);

            // ASSERT
            assertThat(actualLeadEntries)
                .isEmpty();
            assertThat(readLabelTexts(actualOtherEntries))
                .containsExactly("Galatia Academy");
        }

        // A faction the weighing never reached: its every colony here is one the economy does not
        // list, so its whole account is the unlisted read. The colonies were always carried - what
        // they lacked was a line to hang from, which the ranking gives them.
        @Test
        void accountsForAFactionPresentThroughUnlistedColoniesAlone() {

            // ARRANGE
            stubUnweighedColonies(Map.of(
                "tritachyon",
                List.of(UNLISTED_COLONY)));

            // ACT
            var actualEntries = accountSource
                .createFactionAccountResolver(systemMock, ANY_PASS, NO_BREAKDOWNS, PATROL_DETAILS)
                .resolveAccountEntries(new PresenceOnlyFactionStanding("tritachyon"));

            // ASSERT
            assertThat(readLabelTexts(actualEntries))
                .containsExactly("Galatia Academy");
        }

        // The weights are the ranking's, handed over whole: an account weighing the system again
        // would weigh every market twice for one hover, and could explain a score with arithmetic
        // other than the arithmetic that ranked it.
        @Test
        void weighsNothingOfItsOwn() {

            // ARRANGE
            stubUnweighedColonies(Map.of());

            var breakdownsByFactionId = Map.of("hegemony", List.of(buildBaseSizeBreakdown("Jangala", 6.0)));

            // ACT
            accountSource
                .createFactionAccountResolver(systemMock, ANY_PASS, breakdownsByFactionId, PATROL_DETAILS)
                .resolveAccountEntries(LEAD_MEMBER);

            // ASSERT
            footprintsMock.verify(
                () -> KnownMarketFootprints.readBreakdownByFaction(any(), any(), any()),
                Mockito.never());
        }

        // The reveal decides what the box may name, and a colony the pass could not weigh is no
        // exception. Read at a reveal of its own it would withhold an undiscovered station while
        // naming the weighed colonies beside it - or name one the player has not found.
        @Test
        void readsTheUnlistedColoniesUnderTheRankingsOwnReveal() {

            // ARRANGE
            stubUnweighedColonies(Map.of());

            // ACT
            accountSource.createFactionAccountResolver(
                systemMock,
                REVEALED_PASS,
                NO_BREAKDOWNS,
                PATROL_DETAILS);

            // ASSERT
            footprintsMock.verify(
                () -> KnownMarketFootprints.readUnweighedColoniesByFaction(
                    Colonies.NONE,
                    REVEALED_PASS.colonyKnowledge()));
        }

        // The unlisted colonies are a second selection over the same set, made once per paint rather
        // than once per faction listed - the same rule the weighed read is held to, so neither can end
        // up describing a system the other did not.
        @Test
        void readsTheUnlistedColoniesOnceForTheWholeBox() {

            // ARRANGE
            stubUnweighedColonies(Map.of(
                "hegemony",
                List.of(UNLISTED_COLONY)));

            // ACT
            var accountResolver = accountSource.createFactionAccountResolver(
                systemMock,
                ANY_PASS,
                NO_BREAKDOWNS,
                PATROL_DETAILS);

            accountResolver.resolveAccountEntries(LEAD_MEMBER);
            accountResolver.resolveAccountEntries(OTHER_MEMBER);

            // ASSERT
            // Counted on the real arguments rather than on matchers, since registering the stub is
            // itself an invocation and an any()-matched count would take it for a second read.
            footprintsMock.verify(
                () -> KnownMarketFootprints.readUnweighedColoniesByFaction(
                    Colonies.NONE,
                    ANY_PASS.colonyKnowledge()),
                Mockito.times(1));
        }

        // The whole reason the register is handed down rather than opened here: the dates stated
        // have to come off the very observations the pass resolved its projection against. Read from
        // the sector afresh, a box could date a colony against a register the fills beside it were
        // never drawn under - and nothing on screen would say which.
        @Test
        void datesTheColoniesAgainstThePassesOwnRegister() {

            // ARRANGE
            stubUnweighedColonies(Map.of());

            ArgumentCaptor<RecordedObservations<ColonyObservation>> sightings = ArgumentCaptor.captor();

            // The case is worth nothing unless the pass carries a register that is not the empty one,
            // that being what a read opening its own would also find - so the premise is asserted
            // rather than assumed.
            assertThat(OBSERVED_PASS.colonyKnowledge().sightings())
                .isNotSameAs(RecordedObservations.createEmptyRegister());

            var expectedSightings = OBSERVED_PASS.colonyKnowledge().sightings();

            // ACT
            // Real methods behind the spy, because the reading takes the remark off the notes it is
            // handed and a stubbed read would hand it none.
            try (var notesMock = Mockito.mockStatic(
                    ColonyObservationNotes.class,
                    Mockito.CALLS_REAL_METHODS)) {

                accountSource.createFactionAccountResolver(
                    systemMock,
                    OBSERVED_PASS,
                    NO_BREAKDOWNS,
                    PATROL_DETAILS);

                notesMock.verify(() -> ColonyObservationNotes.readNotesFor(
                    any(),
                    any(),
                    any(),
                    sightings.capture()));
            }

            // ASSERT
            assertThat(sightings.getValue())
                .isSameAs(expectedSightings);
        }
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

        SectorOwnershipFixtures.markSystemAsVisitedByPlayer(
            sector,
            SectorOwnershipFixtures.buildOnlySystem(sector));

        return DominancePass.createOver(sector, ANY_RULES, UNDER_THE_FOG, HolderGrouping.identity());
    }
}
