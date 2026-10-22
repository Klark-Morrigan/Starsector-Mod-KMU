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
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.colonies.RevelationGate;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;
import kmu.maplayers.ownermap.ribbon.UncontestedRibbonRuns;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private static final RibbonPlanRules RULES = new RibbonPlanRules(
        new RibbonSegmentLengths(3, 1),
        new UncontestedRibbonRuns(false));

    private MockedStatic<DecivilisedColonyHabitation> habitationSettingMock;

    // The layer's whole-sector rule: one held system, one contested and one unfilled.
    private final HolderProviderFake holderProviderFake = HolderProviderFake.createAnswering(
        new HolderResolution(
            Map.of(HELD_SYSTEM, HEGEMONY_OWNER),
            SystemFillStates.createOf(Map.of(
                CONTESTED_SYSTEM,
                FillState.HATCHED,
                UNFILLED_SYSTEM,
                FillState.UNFILLED))));

    private final SystemHolderResolveSourceFake holderResolveSourceFake =
        new SystemHolderResolveSourceFake();

    private MockedStatic<HolderInhabitation> inhabitationMock;

    // What the layer's band planner was handed, in the order it was asked.
    private final List<RibbonPlanInputs> plannerInputs = new ArrayList<>();

    private final StaticSeams seams = new StaticSeams();

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

            var viewMock = stubViewOver(OwnerAffiliation.NONE, inputs -> system -> RibbonPlan.NONE);

            assertThatThrownBy(() -> new HolderOwnerSource(null, viewMock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("grouping");
        }

        @Test
        void refusesAMissingHoldingRule() {

            var viewMock = stubViewOver(OwnerAffiliation.NONE, inputs -> system -> RibbonPlan.NONE);

            when(viewMock.resolveHolderProvider())
                .thenReturn(null);

            assertThatThrownBy(() -> new HolderOwnerSource(HolderGrouping.identity(), viewMock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("holderProvider");
        }
    }

    @Nested
    class ResolveOwners {

        @Test
        void handsTheLayersRuleAPassOverTheWalksOwnIndex() {
            // The walk the rebuild opened is the one every stage shares, so the pass the source
            // opens has to stand on its index rather than a traversal of its own.
            var walk = buildWalkUnder(MapVisibilityRules.BASE);

            buildSourceUnder(HolderGrouping.identity()).resolveOwners(walk, null);

            assertThat(holderProviderFake.readResolvedPasses())
                .singleElement()
                .extracting(HolderPass::sectorIndex)
                .isSameAs(walk.sectorIndex());
        }

        @Test
        void foldsUnderTheGroupingItWasBuiltUnder() {
            // One sampling of the grouping per rebuild, taken where the view built the source - a
            // pass folded under a fresh sampling could resolve a fold the reading never named.
            var grouping = HolderGroupingFixture.buildGroupOf(HEGEMONY, PERSEAN);

            buildSourceUnder(grouping).resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);

            assertThat(holderProviderFake.readResolvedPasses())
                .singleElement()
                .extracting(HolderPass::grouping)
                .isSameAs(grouping);
        }

        @Test
        void readsColoniesUnderTheRuleTheCellsWereCutUnder() {
            // The walk carries the visibility rule the cut decided which systems got cells by, so
            // the owners are resolved for the very sector the standing geometry was cut for.
            buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(GATED_RULES), null);

            assertThat(readFirstResolvedPass().colonyKnowledge().rule())
                .isEqualTo(GATED_RULES.colonyVisibility());
        }

        @Test
        void readsDecivilisedWorldsUnderTheLayersOwnHabitationKnob() {
            // The other half of the colony rule is the layer's own setting rather than the walk's.
            // Every other case leaves it at the shipped reading, which a pass hard-coding that
            // reading would match; posed the other way here, the world is nobody's home.
            habitationSettingMock
                .when(DecivilisedColonyHabitation::readFromLunaSettings)
                .thenReturn(DecivilisedColonyHabitation.COUNTS_AS_UNPOPULATED);

            var sector = SectorOwnershipFixtures.buildSectorWith("corvus");
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(new SectorWalk(new SectorPassIndex(sector), MapVisibilityRules.BASE), null);

            var habitation = readFirstResolvedPass().readHabitationIn(system);

            assertThat(habitation.blocIds())
                .isEmpty();
            assertThat(habitation.hasInhabitingColony())
                .isFalse();
        }

        @Test
        void handsTheLayersRuleTheSpotlitOwner() {

            buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY);

            assertThat(holderProviderFake.readSelectedBlocIds())
                .containsExactly(HEGEMONY);
        }

        @Test
        void answersWithTheLayersOwnersAndFillExceptions() {

            var owners = buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);

            assertThat(owners.ownerBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY_OWNER));
            assertThat(owners.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(CONTESTED_SYSTEM, FillState.HATCHED);
            assertThat(owners.fillStates().getNonSolidFillStateBySystemKey())
                .containsEntry(UNFILLED_SYSTEM, FillState.UNFILLED);
        }

        @Test
        void readsInhabitationOffThePassTheHoldingWasResolvedOver() {
            // Off one pass, the holding and the inhabitation cannot disagree about a colony the
            // habitation rule admits.
            var owners = buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);

            inhabitationMock.verify(
                () -> HolderInhabitation.readInhabitedSystemKeys(same(readFirstResolvedPass())));

            assertThat(owners.inhabitedSystemKeys())
                .containsExactlyInAnyOrder(HELD_SYSTEM, UNHELD_SETTLED_SYSTEM);
        }

        @Test
        void asksPresenceOnlyOfTheInhabitedSystemsNobodyHolds() {
            // Presence spares a cell nobody holds, so a system somebody holds is never asked about:
            // the read walks the sector to find its candidates, and an answer that decides nothing
            // is a walk paid for nothing.
            var owners = buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY);

            inhabitationMock.verify(() -> HolderInhabitation.findSpotlitPresenceSystemKeys(
                same(readFirstResolvedPass()),
                eq(HEGEMONY),
                eq(Set.of(UNHELD_SETTLED_SYSTEM))));

            assertThat(owners.spotlitPresenceSystemKeys())
                .containsExactly(UNHELD_SETTLED_SYSTEM);
        }

        @Test
        void namesWhatTheHoldingResolveFound() {
            // The counts the resolve found, reported where the profiler sees them: none is a volume
            // of work its duration divides by, and the row is read against what the readers beneath
            // it walked.
            var capture = RecordedCapture.recordWhile(() -> buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null));

            assertThat(capture.findNode(RESOLVE_HOLDERS_SECTION).getWorstCall().getTag())
                .isEqualTo("owned=1 filtering=false contested=1 unfilled=1");
        }

        @Test
        void namesTheHoldingResolveAsFilteringUnderASpotlight() {
            // A filtered resolve walks the spotlit bloc's presence as well, so its duration is read
            // against a different volume of work and the row has to say which kind it was.
            var capture = RecordedCapture.recordWhile(() -> buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY));

            assertThat(capture.findNode(RESOLVE_HOLDERS_SECTION).getWorstCall().getTag())
                .isEqualTo("owned=1 filtering=true contested=1 unfilled=1");
        }

        @Test
        void namesWhatEachSystemScanSelected() {
            // Both scans report identically, which is what one shared helper is for.
            var capture = RecordedCapture.recordWhile(() -> buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY));

            assertThat(capture.findNode(FIND_INHABITED_SECTION).getWorstCall().getTag())
                .isEqualTo("systems=2");
            assertThat(capture.findNode(FIND_SPOTLIT_PRESENCE_SECTION).getWorstCall().getTag())
                .isEqualTo("systems=1");
        }
    }

    @Nested
    class OpenSystemResolve {

        @Test
        void opensTheLayersResolveOverThePassTheWholeSectorAnswerUsed() {
            // One walk, one pass: a resolve asked after the whole-sector answer over the same walk
            // re-derives off the very pass that answer read.
            var source = buildSourceUnder(HolderGrouping.identity());
            var walk = buildWalkUnder(MapVisibilityRules.BASE);

            source.resolveOwners(walk, null);
            source.openSystemResolve(walk, null);

            assertThat(holderResolveSourceFake.readPassesOpenedOver())
                .singleElement()
                .isSameAs(readFirstResolvedPass());
        }

        @Test
        void opensAFreshPassForAnotherWalk() {
            // A batch's walk is a later reading of the sector, so the pass the rebuild read must not
            // answer for it.
            var source = buildSourceUnder(HolderGrouping.identity());
            var batchWalk = buildWalkUnder(MapVisibilityRules.BASE);

            source.resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);
            source.openSystemResolve(batchWalk, null);

            assertThat(holderResolveSourceFake.readPassesOpenedOver())
                .singleElement()
                .isNotSameAs(readFirstResolvedPass())
                .extracting(HolderPass::sectorIndex)
                .isSameAs(batchWalk.sectorIndex());
        }

        @Test
        void opensAPassOfItsOwnBesideAnotherSourceOverTheSameWalk() {
            // The walk keeps one pass per source, so a second source - another sampling of the
            // grouping - never folds under the first one's.
            var walk = buildWalkUnder(MapVisibilityRules.BASE);
            var otherGrouping = HolderGroupingFixture.buildGroupOf(HEGEMONY, PERSEAN);

            buildSourceUnder(HolderGrouping.identity()).resolveOwners(walk, null);
            buildSourceUnder(otherGrouping).openSystemResolve(walk, null);

            assertThat(holderResolveSourceFake.readPassesOpenedOver())
                .singleElement()
                .isNotSameAs(readFirstResolvedPass())
                .extracting(HolderPass::grouping)
                .isSameAs(otherGrouping);
        }

        @Test
        void resolvesTheOwnerThroughTheLayersOwnResolve() {

            holderResolveSourceFake.recordHolderOf("corvus", HEGEMONY_OWNER);

            var resolve = buildSourceUnder(HolderGrouping.identity())
                .openSystemResolve(buildWalkUnder(MapVisibilityRules.BASE), null);

            assertThat(resolve.resolveOwnerOf(buildSystem("corvus")))
                .isEqualTo(HEGEMONY_OWNER);
        }

        @Test
        void answersInhabitationOffTheBatchesPass() {
            // The same projection the whole-sector scan classified by, over the batch's own pass.
            var system = buildSystem("corvus");

            inhabitationMock
                .when(() -> HolderInhabitation.isSystemInhabited(any(), same(system)))
                .thenReturn(true);

            var resolve = buildSourceUnder(HolderGrouping.identity())
                .openSystemResolve(buildWalkUnder(MapVisibilityRules.BASE), null);

            assertThat(resolve.isSystemInhabited(system))
                .isTrue();
            inhabitationMock.verify(() -> HolderInhabitation.isSystemInhabited(
                same(holderResolveSourceFake.readPassesOpenedOver().get(0)),
                same(system)));
        }

        @Test
        void answersPresenceUnderTheSpotlitOwnerItWasOpenedUnder() {

            var system = buildSystem("hybrasil");

            inhabitationMock
                .when(() -> HolderInhabitation.isBlocPresentIn(any(), eq(HEGEMONY), same(system)))
                .thenReturn(true);

            var resolve = buildSourceUnder(HolderGrouping.identity())
                .openSystemResolve(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY);

            assertThat(resolve.isSpotlitOwnerPresentIn(system))
                .isTrue();
        }
    }

    @Nested
    class ResolveRibbonPlanner {

        @Test
        void countsOffThePassTheOwnersWereResolvedOver() {
            // A bake in the same frame as the build counts off that build's pass rather than paying
            // a second habitation fold over the walk it already shares.
            var source = buildSourceUnder(HolderGrouping.identity());
            var walk = buildWalkUnder(MapVisibilityRules.BASE);

            source.resolveOwners(walk, null);
            source.resolveRibbonPlanner(walk, RULES);

            assertThat(plannerInputs)
                .singleElement()
                .extracting(RibbonPlanInputs::pass)
                .isSameAs(readFirstResolvedPass());
        }

        @Test
        void judgesContestsAgainstTheAffiliationItWasBuiltUnder() {
            // Who stands together is a second reading beside the fold: allies painted apart still
            // band as allies, so the bake judges by the affiliation the source was built with rather
            // than the grouping the counts fold under.
            OwnerAffiliation allied = (first, second) -> !first.equals(second);

            buildSourceUnder(HolderGrouping.identity(), allied)
                .resolveRibbonPlanner(buildWalkUnder(MapVisibilityRules.BASE), RULES);

            assertThat(plannerInputs.get(0).affiliation())
                .isSameAs(allied);
            assertThat(plannerInputs.get(0).grouping())
                .isSameAs(HolderGrouping.identity());
        }

        @Test
        void handsTheLayersPlannerTheRulesTheBakeSampled() {

            buildSourceUnder(HolderGrouping.identity())
                .resolveRibbonPlanner(buildWalkUnder(MapVisibilityRules.BASE), RULES);

            assertThat(plannerInputs.get(0).rules())
                .isSameAs(RULES);
        }

        @Test
        void answersWithThePlannerTheLayerBuilt() {

            var planner = (SystemRibbonPlanner) system -> RibbonPlan.NONE;

            var answered = new HolderOwnerSource(
                    HolderGrouping.identity(),
                    stubViewOver(OwnerAffiliation.NONE, inputs -> planner))
                .resolveRibbonPlanner(buildWalkUnder(MapVisibilityRules.BASE), RULES);

            assertThat(answered)
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

    // The first pass the layer's whole-sector rule was handed, which every other read in a case is
    // compared against.
    private HolderPass readFirstResolvedPass() {
        return holderProviderFake.readResolvedPasses().get(0);
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
        when(viewMock.resolveHolderProvider())
            .thenReturn(holderProviderFake);
        when(viewMock.resolveSystemHolderResolveSource())
            .thenReturn(holderResolveSourceFake);
        when(viewMock.resolveRibbonPlanner(any()))
            .thenAnswer(invocation -> plannerSource.apply(invocation.getArgument(0)));

        return viewMock;
    }

    // A walk over no sector: every read beneath the source is stood in for, so only which walk and
    // which rule reach it matter.
    private static SectorWalk buildWalkUnder(MapVisibilityRules rules) {
        return new SectorWalk(new SectorPassIndex(null), rules);
    }

    private static StarSystemAPI buildSystem(String systemId) {
        return StarSystemFixture.buildSystemAt(systemId, 0, 0);
    }
}
