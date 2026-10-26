package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.profiling.RecordedCapture;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.DecivilisedPlanetFixtures;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.colonies.RevelationGate;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.listSystemMarkets;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.STANDARD_RULES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the owner source of a layer painting holders adds to the layer's own rules: one pass per
 * walk, opened over the walk's own index under the walk's own visibility rule and the grouping the
 * source was built under, and every question the tier asks answered off that one pass - so the
 * holding, the inhabitation and the band count read each system once between them and describe one
 * moment of the sector.
 *
 * <p>The layer's rules are stood in for by fakes, since what they answer is each layer's own; the
 * two colony scans the source runs beside them are stood in for at their static seams, each pinned
 * by its own suite. What belongs here is which pass reaches each of them, and what is done with the
 * answers.
 */
final class HolderOwnerSourceTests {

    private static final String HEGEMONY = "hegemony";
    private static final String PERSEAN = "persean";

    private static final SystemKey HELD_SYSTEM = buildCellKey("corvus");
    private static final SystemKey UNHELD_SETTLED_SYSTEM = buildCellKey("hybrasil");
    private static final SystemKey CONTESTED_SYSTEM = buildCellKey("askonia");
    private static final SystemKey UNFILLED_SYSTEM = buildCellKey("eos");

    private static final SystemOwner HEGEMONY_OWNER =
        new SystemOwner(HEGEMONY, new ColourPair(Color.RED, Color.BLUE));

    // A colony rule that is plainly not the fog alone, so a pass opened under a rule of its own fails
    // the case about which rule it reads under rather than passing it by coincidence.
    private static final MapVisibilityRules GATED_RULES = new MapVisibilityRules(
        new ColonyVisibility(
            false,
            DecivilisedMarkets.DEFAULT_SURVEY_LEVEL,
            Set.of(RevelationGate.SPACE_DERELICTS)),
        false);

    // The rows the three scans land on.
    private static final String RESOLVE_HOLDERS_SECTION = "ownerMap.resolveHolders";
    private static final String FIND_INHABITED_SECTION = "ownerMap.findInhabited";
    private static final String FIND_SPOTLIT_PRESENCE_SECTION = "ownerMap.findSpotlitPresence";

    private static final RibbonPlanRules RULES = STANDARD_RULES;

    private MockedStatic<DecivilisedColonyHabitation> habitationSettingMock;

    private final SystemHolderResolverSourceFake holderResolverSourceFake =
        new SystemHolderResolverSourceFake();

    private MockedStatic<HolderInhabitation> inhabitationMock;

    // What the layer's band planner was handed, in the order it was asked.
    private final List<RibbonPlanInputs> plannerInputs = new ArrayList<>();

    private final StaticSeams seams = new StaticSeams();

    // The layer's holding rule: one held system, one contested and one unfilled, every other system
    // the walk lists nobody's.
    @BeforeEach
    void stateTheLayersHolding() {

        holderResolverSourceFake.recordHolderOf(
            HELD_SYSTEM.systemId(),
            ResolvedSystemOwner.createSolidlyFilledBy(HEGEMONY_OWNER));
        holderResolverSourceFake.recordHolderOf(
            CONTESTED_SYSTEM.systemId(),
            new ResolvedSystemOwner(HEGEMONY_OWNER, FillState.HATCHED));
        holderResolverSourceFake.recordHolderOf(
            UNFILLED_SYSTEM.systemId(),
            new ResolvedSystemOwner(HEGEMONY_OWNER, FillState.UNFILLED));
    }

    @BeforeEach
    void openTheColonySeams() {

        // The layer's own habitation knob, read where a pass opens - LunaLib-backed.
        habitationSettingMock = seams.openSeam(DecivilisedColonyHabitation.class);
        habitationSettingMock
            .when(DecivilisedColonyHabitation::readFromLunaSettings)
            .thenReturn(DecivilisedColonyHabitation.COUNTS_AS_POPULATED);

        inhabitationMock = seams.openSeam(HolderInhabitation.class);
        inhabitationMock
            .when(() -> HolderInhabitation.readInhabitedSystemKeys(any()))
            .thenReturn(Set.of(HELD_SYSTEM, UNHELD_SETTLED_SYSTEM));
        inhabitationMock
            .when(() -> HolderInhabitation.findSpotlitPresenceSystemKeys(any(), any(), any()))
            .thenReturn(Set.of(UNHELD_SETTLED_SYSTEM));
    }

    @AfterEach
    void closeSeams() {
        seams.closeEverySeam();
    }

    @Nested
    class Constructor {

        @Test
        void refusesAMissingGrouping() {

            // ARRANGE
            var viewMock = stubViewOver(OwnerAffiliation.NONE, inputs -> system -> RibbonPlan.NONE);

            // ACT
            var actualThrown = catchThrowable(() -> new HolderOwnerSource(null, viewMock));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("grouping");
        }

        @Test
        void refusesAMissingHoldingRule() {

            // ARRANGE
            var viewMock = stubViewOver(OwnerAffiliation.NONE, inputs -> system -> RibbonPlan.NONE);

            when(viewMock.resolveSystemHolderResolverSource())
                .thenReturn(null);

            // ACT
            var actualThrown = catchThrowable(() -> new HolderOwnerSource(HolderGrouping.identity(), viewMock));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("holderResolverSource");
        }
    }

    @Nested
    class ResolveOwners {

        // The walk the rebuild opened is the one every stage shares, so the pass the source opens has
        // to stand on its index rather than a traversal of its own.
        @Test
        void handsTheLayersRuleAPassOverTheWalksOwnIndex() {

            // ARRANGE
            var walk = buildWalkUnder(MapVisibilityRules.BASE);
            var source = buildSourceUnder(HolderGrouping.identity());

            // ACT
            source.resolveOwners(walk, null);

            // ASSERT
            assertThat(holderResolverSourceFake.readPassesOpenedOver())
                .singleElement()
                .extracting(HolderPass::sectorIndex)
                .isSameAs(walk.sectorIndex());
        }

        // One sampling of the grouping per rebuild, taken where the view built the source - a pass
        // folded under a fresh sampling could resolve a fold the reading never named.
        @Test
        void foldsUnderTheGroupingItWasBuiltUnder() {

            // ARRANGE
            var grouping = HolderGroupingFixture.buildGroupOf(HEGEMONY, PERSEAN);
            var source = buildSourceUnder(grouping);

            // ACT
            source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);

            // ASSERT
            assertThat(holderResolverSourceFake.readPassesOpenedOver())
                .singleElement()
                .extracting(HolderPass::grouping)
                .isSameAs(grouping);
        }

        // The walk carries the visibility rule the cut decided which systems got cells by, so the
        // owners are resolved for the very sector the standing geometry was cut for.
        @Test
        void readsColoniesUnderTheRuleTheCellsWereCutUnder() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());

            // ACT
            source.resolveOwners(buildWalkUnder(GATED_RULES), null);

            // ASSERT
            assertThat(readFirstResolvedPass().colonyKnowledge().rule())
                .isEqualTo(GATED_RULES.colonyVisibility());
        }

        // The other half of the colony rule is the layer's own setting rather than the walk's. Every
        // other case leaves it at the shipped reading, which a pass hard-coding that reading would
        // match; posed the other way here, the world is nobody's home.
        @Test
        void readsDecivilisedWorldsUnderTheLayersOwnHabitationKnob() {

            // ARRANGE
            habitationSettingMock
                .when(DecivilisedColonyHabitation::readFromLunaSettings)
                .thenReturn(DecivilisedColonyHabitation.COUNTS_AS_UNPOPULATED);

            var sector = SectorOwnershipFixtures.buildSectorWith("corvus");
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            var source = buildSourceUnder(HolderGrouping.identity());

            source.resolveOwners(new SectorWalk(new SectorPassIndex(sector), MapVisibilityRules.BASE), null);

            // ACT
            var actualHabitation = readFirstResolvedPass().readHabitationIn(system);

            // ASSERT
            assertThat(actualHabitation.blocIds())
                .isEmpty();
            assertThat(actualHabitation.hasInhabitingColony())
                .isFalse();
        }

        @Test
        void handsTheLayersRuleTheSpotlitOwner() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());

            // ACT
            source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY);

            // ASSERT
            assertThat(holderResolverSourceFake.readSpotlitBlocIdsOpenedUnder())
                .containsExactly(HEGEMONY);
        }

        // The holding is the layer's per-system rule asked of every system the walk lists, so a
        // rebuild draws each system as a batch re-deriving it would.
        @Test
        void answersWithTheLayersOwnersAndFillExceptions() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());
            var expectedFillStateBySystemKey = Map.of(
                CONTESTED_SYSTEM,
                FillState.HATCHED,
                UNFILLED_SYSTEM,
                FillState.UNFILLED);

            // ACT
            var actualOwners = source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);

            // ASSERT
            assertThat(actualOwners.ownerBySystemKey())
                .containsOnly(
                    Map.entry(HELD_SYSTEM, HEGEMONY_OWNER),
                    Map.entry(CONTESTED_SYSTEM, HEGEMONY_OWNER),
                    Map.entry(UNFILLED_SYSTEM, HEGEMONY_OWNER));
            assertThat(actualOwners.fillStates().getNonSolidFillStateBySystemKey())
                .isEqualTo(expectedFillStateBySystemKey);
        }

        // Off one pass, the holding and the inhabitation cannot disagree about a colony the
        // habitation rule admits.
        @Test
        void readsInhabitationOffThePassTheHoldingWasResolvedOver() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());

            // ACT
            var actualOwners = source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);

            // ASSERT
            inhabitationMock.verify(
                () -> HolderInhabitation.readInhabitedSystemKeys(same(readFirstResolvedPass())));

            assertThat(actualOwners.inhabitedSystemKeys())
                .containsExactlyInAnyOrder(HELD_SYSTEM, UNHELD_SETTLED_SYSTEM);
        }

        // Presence spares a cell nobody holds, so a system somebody holds is never asked about: the
        // read walks the sector to find its candidates, and an answer that decides nothing is a walk
        // paid for nothing.
        @Test
        void asksPresenceOnlyOfTheInhabitedSystemsNobodyHolds() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());

            // ACT
            var actualOwners = source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY);

            // ASSERT
            inhabitationMock.verify(() -> HolderInhabitation.findSpotlitPresenceSystemKeys(
                same(readFirstResolvedPass()),
                eq(HEGEMONY),
                eq(Set.of(UNHELD_SETTLED_SYSTEM))));

            assertThat(actualOwners.spotlitPresenceSystemKeys())
                .containsExactly(UNHELD_SETTLED_SYSTEM);
        }

        // The counts the resolve found, reported where the profiler sees them: none is a volume of
        // work its duration divides by, and the row is read against what the readers beneath it
        // walked.
        @Test
        void namesWhatTheHoldingResolveFound() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());
            var expectedTag = "owned=3 filtering=false contested=1 unfilled=1";

            // ACT
            var capture = RecordedCapture.recordWhile(
                () -> source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null));
            var actualTag = capture.findNode(RESOLVE_HOLDERS_SECTION).getWorstCall().getTag();

            // ASSERT
            assertThat(actualTag)
                .isEqualTo(expectedTag);
        }

        // A filtered resolve walks the spotlit bloc's presence as well, so its duration is read
        // against a different volume of work and the row has to say which kind it was.
        @Test
        void namesTheHoldingResolveAsFilteringUnderASpotlight() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());
            var expectedTag = "owned=3 filtering=true contested=1 unfilled=1";

            // ACT
            var capture = RecordedCapture.recordWhile(
                () -> source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY));
            var actualTag = capture.findNode(RESOLVE_HOLDERS_SECTION).getWorstCall().getTag();

            // ASSERT
            assertThat(actualTag)
                .isEqualTo(expectedTag);
        }

        // Both scans report identically, which is what one shared helper is for.
        @Test
        void namesWhatEachSystemScanSelected() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());
            var expectedInhabitedTag = "systems=2";
            var expectedPresenceTag = "systems=1";

            // ACT
            var capture = RecordedCapture.recordWhile(
                () -> source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY));
            var actualInhabitedTag = capture.findNode(FIND_INHABITED_SECTION).getWorstCall().getTag();
            var actualPresenceTag = capture.findNode(FIND_SPOTLIT_PRESENCE_SECTION).getWorstCall().getTag();

            // ASSERT
            assertThat(actualInhabitedTag)
                .isEqualTo(expectedInhabitedTag);
            assertThat(actualPresenceTag)
                .isEqualTo(expectedPresenceTag);
        }
    }

    @Nested
    class OpenSystemResolver {

        // One walk, one pass: a resolver asked after the whole-sector answer over the same walk
        // re-derives off the very pass that answer read.
        @Test
        void opensTheLayersResolverOverThePassTheWholeSectorAnswerUsed() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());
            var walk = buildWalkUnder(MapVisibilityRules.BASE);

            source.resolveOwners(walk, null);

            // ACT
            source.openSystemResolver(walk, null);

            // ASSERT
            assertThat(readLastOpenedPass())
                .isSameAs(readFirstResolvedPass());
        }

        // A batch's walk is a later reading of the sector, so the pass the rebuild read must not
        // answer for it.
        @Test
        void opensAFreshPassForAnotherWalk() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());
            var batchWalk = buildWalkUnder(MapVisibilityRules.BASE);

            source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);

            // ACT
            source.openSystemResolver(batchWalk, null);

            // ASSERT
            assertThat(readLastOpenedPass())
                .isNotSameAs(readFirstResolvedPass())
                .extracting(HolderPass::sectorIndex)
                .isSameAs(batchWalk.sectorIndex());
        }

        // The walk keeps one pass per source, so a second source - another sampling of the grouping -
        // never folds under the first one's.
        @Test
        void opensAPassOfItsOwnBesideAnotherSourceOverTheSameWalk() {

            // ARRANGE
            var walk = buildWalkUnder(MapVisibilityRules.BASE);
            var otherGrouping = HolderGroupingFixture.buildGroupOf(HEGEMONY, PERSEAN);
            var otherSource = buildSourceUnder(otherGrouping);

            buildSourceUnder(HolderGrouping.identity()).resolveOwners(walk, null);

            // ACT
            otherSource.openSystemResolver(walk, null);

            // ASSERT
            assertThat(readLastOpenedPass())
                .isNotSameAs(readFirstResolvedPass())
                .extracting(HolderPass::grouping)
                .isSameAs(otherGrouping);
        }

        // The layer's rule decides how a held system fills in the same breath as who holds it, so the
        // batch's resolver hands its answer on whole. One that rebuilt the answer from the holder alone
        // would fold a contested or claimed system solid, which a solid answer could not catch.
        @Test
        void answersTheLayersOwnHolderWithItsFillStateIntact() {

            // ARRANGE
            var expectedResolved = new ResolvedSystemOwner(HEGEMONY_OWNER, FillState.UNFILLED);

            holderResolverSourceFake.recordHolderOf(
                "corvus",
                new ResolvedSystemOwner(HEGEMONY_OWNER, FillState.UNFILLED));

            var resolver = buildSourceUnder(HolderGrouping.identity())
                .openSystemResolver(buildWalkUnder(MapVisibilityRules.BASE), null);

            // ACT
            var actualResolved = resolver.resolveOwnerOf(buildSystem("corvus"));

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        // The batch re-derives a marked system as the standing build painted it, so the spotlight
        // that build was painted under reaches the layer's own per-system rule.
        @Test
        void opensTheLayersResolverUnderTheSpotlitOwnerItWasOpenedUnder() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());

            // ACT
            source.openSystemResolver(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY);

            // ASSERT
            assertThat(holderResolverSourceFake.readSpotlitBlocIdsOpenedUnder())
                .containsExactly(HEGEMONY);
        }

        // The same projection the whole-sector scan classified by, over the batch's own pass.
        @Test
        void answersInhabitationOffTheBatchesPass() {

            // ARRANGE
            var system = buildSystem("corvus");

            inhabitationMock
                .when(() -> HolderInhabitation.isSystemInhabited(any(), same(system)))
                .thenReturn(true);

            var resolver = buildSourceUnder(HolderGrouping.identity())
                .openSystemResolver(buildWalkUnder(MapVisibilityRules.BASE), null);

            // ACT
            var actualIsInhabited = resolver.isSystemInhabited(system);

            // ASSERT
            assertThat(actualIsInhabited)
                .isTrue();

            inhabitationMock.verify(() -> HolderInhabitation.isSystemInhabited(
                same(holderResolverSourceFake.readPassesOpenedOver().get(0)),
                same(system)));
        }

        @Test
        void answersPresenceUnderTheSpotlitOwnerItWasOpenedUnder() {

            // ARRANGE
            var system = buildSystem("hybrasil");

            inhabitationMock
                .when(() -> HolderInhabitation.isBlocPresentIn(any(), eq(HEGEMONY), same(system)))
                .thenReturn(true);

            var resolver = buildSourceUnder(HolderGrouping.identity())
                .openSystemResolver(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY);

            // ACT
            var actualIsPresent = resolver.isSpotlitOwnerPresentIn(system);

            // ASSERT
            assertThat(actualIsPresent)
                .isTrue();
        }
    }

    @Nested
    class ResolveRibbonPlanner {

        // A bake in the same frame as the build counts off that build's pass rather than paying a
        // second habitation fold over the walk it already shares.
        @Test
        void countsOffThePassTheOwnersWereResolvedOver() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());
            var walk = buildWalkUnder(MapVisibilityRules.BASE);

            source.resolveOwners(walk, null);

            // ACT
            source.resolveRibbonPlanner(walk, RULES);

            // ASSERT
            assertThat(plannerInputs)
                .singleElement()
                .extracting(RibbonPlanInputs::pass)
                .isSameAs(readFirstResolvedPass());
        }

        // Who stands together is a second reading beside the fold: allies painted apart still band
        // as allies, so the bake judges by the affiliation the source was built with rather than the
        // grouping the counts fold under.
        @Test
        void judgesContestsAgainstTheAffiliationItWasBuiltUnder() {

            // ARRANGE
            OwnerAffiliation allied = (first, second) -> !first.equals(second);

            var source = buildSourceUnder(HolderGrouping.identity(), allied);

            // ACT
            source.resolveRibbonPlanner(buildWalkUnder(MapVisibilityRules.BASE), RULES);

            // ASSERT
            assertThat(plannerInputs.get(0).affiliation())
                .isSameAs(allied);
            assertThat(plannerInputs.get(0).grouping())
                .isSameAs(HolderGrouping.identity());
        }

        @Test
        void handsTheLayersPlannerTheRulesTheBakeSampled() {

            // ARRANGE
            var source = buildSourceUnder(HolderGrouping.identity());

            // ACT
            source.resolveRibbonPlanner(buildWalkUnder(MapVisibilityRules.BASE), RULES);

            // ASSERT
            assertThat(plannerInputs.get(0).rules())
                .isSameAs(RULES);
        }

        @Test
        void answersWithThePlannerTheLayerBuilt() {

            // ARRANGE
            var planner = (SystemRibbonPlanner) system -> RibbonPlan.NONE;
            var source = new HolderOwnerSource(
                HolderGrouping.identity(),
                stubViewOver(OwnerAffiliation.NONE, inputs -> planner));

            // ACT
            var actualPlanner = source.resolveRibbonPlanner(buildWalkUnder(MapVisibilityRules.BASE), RULES);

            // ASSERT
            assertThat(actualPlanner)
                .isSameAs(planner);
        }
    }

    // A source under the given grouping over the recording rules, nobody standing together.
    private HolderOwnerSource buildSourceUnder(HolderGrouping grouping) {
        return buildSourceUnder(grouping, OwnerAffiliation.NONE);
    }

    // A source under the given grouping and affiliation over the recording rules.
    private HolderOwnerSource buildSourceUnder(HolderGrouping grouping, OwnerAffiliation affiliation) {
        return new HolderOwnerSource(grouping, stubViewOver(affiliation, this::recordPlannerInputs));
    }

    // The first pass the layer's rule was opened over, which every other read in a case is compared
    // against.
    private HolderPass readFirstResolvedPass() {
        return holderResolverSourceFake.readPassesOpenedOver().get(0);
    }

    // The pass the layer's rule was last opened over: a batch's, where a case opens one after the
    // rebuild.
    private HolderPass readLastOpenedPass() {

        var openedPasses = holderResolverSourceFake.readPassesOpenedOver();

        return openedPasses.get(openedPasses.size() - 1);
    }

    // The layer's band planner: records what it was handed, and counts nothing.
    private SystemRibbonPlanner recordPlannerInputs(RibbonPlanInputs inputs) {

        plannerInputs.add(inputs);
        return system -> RibbonPlan.NONE;
    }

    // A view answering the recording rules, the stated affiliation and the stated band planner.
    private HolderPaintedView stubViewOver(
            OwnerAffiliation affiliation,
            Function<RibbonPlanInputs, SystemRibbonPlanner> plannerSource) {

        var viewMock = mock(HolderPaintedView.class);

        when(viewMock.resolveContestAffiliation())
            .thenReturn(affiliation);
        when(viewMock.resolveSystemHolderResolverSource())
            .thenReturn(holderResolverSourceFake);
        when(viewMock.resolveRibbonPlanner(any()))
            .thenAnswer(invocation -> plannerSource.apply(invocation.getArgument(0)));

        return viewMock;
    }

    // A walk over a sector listing the four systems the cases name, with nothing in them: the
    // holding is the layer's rule and the colony scans are stood in for, so only which systems,
    // which walk and which rule reach them matter.
    private static SectorWalk buildWalkUnder(MapVisibilityRules rules) {

        var sector = SectorOwnershipFixtures.buildSectorWithSystems(
            List.of(),
            listSystemMarkets(HELD_SYSTEM.systemId()),
            listSystemMarkets(UNHELD_SETTLED_SYSTEM.systemId()),
            listSystemMarkets(CONTESTED_SYSTEM.systemId()),
            listSystemMarkets(UNFILLED_SYSTEM.systemId()));

        return new SectorWalk(new SectorPassIndex(sector), rules);
    }

    private static StarSystemAPI buildSystem(String systemId) {
        return StarSystemFixture.buildSystemAt(systemId, 0, 0);
    }
}
