package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.profiling.RecordedCapture;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.colonies.RevelationGate;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.holding.DecivilisedColonyHabitation;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderGroupingFixture;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.holding.OwnerMapInhabitation;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.RibbonPlanInputs;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.RibbonSegmentLengths;
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

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;

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
        new SystemOwner(HEGEMONY, new OwnerPalette(Color.RED, Color.BLUE));

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

    private final StaticSeams seams = new StaticSeams();

    private MockedStatic<OwnerMapInhabitation> inhabitationMock;
    private MockedStatic<SpotlitBlocs> spotlitBlocsMock;

    // What the layer's rules were handed, in the order they were asked.
    private final List<HolderPass> providerPasses = new ArrayList<>();
    private final List<String> providerSpotlitIds = new ArrayList<>();
    private final List<RibbonPlanInputs> plannerInputs = new ArrayList<>();

    private final SystemHolderResolveSourceFake holderResolveSourceFake =
        new SystemHolderResolveSourceFake();

    @BeforeEach
    void openTheColonySeams() {

        // The layer's own habitation knob, read where a pass opens - LunaLib-backed.
        seams.openSeam(DecivilisedColonyHabitation.class)
            .when(DecivilisedColonyHabitation::readFromLunaSettings)
            .thenReturn(DecivilisedColonyHabitation.COUNTS_AS_POPULATED);

        inhabitationMock = seams.openSeam(OwnerMapInhabitation.class);
        inhabitationMock
            .when(() -> OwnerMapInhabitation.readInhabitedSystemKeys(any()))
            .thenReturn(Set.of(HELD_SYSTEM, UNHELD_SETTLED_SYSTEM));

        spotlitBlocsMock = seams.openSeam(SpotlitBlocs.class);
        spotlitBlocsMock
            .when(() -> SpotlitBlocs.findPresentSystemKeys(any(), any(), any()))
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

            assertThatThrownBy(() -> new HolderOwnerSource(
                    null,
                    HolderGrouping.identity(),
                    HolderProviderFake.createHoldingNothing(),
                    holderResolveSourceFake,
                    inputs -> system -> RibbonPlan.NONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("grouping");
        }

        @Test
        void refusesAMissingHoldingRule() {

            assertThatThrownBy(() -> new HolderOwnerSource(
                    HolderGrouping.identity(),
                    HolderGrouping.identity(),
                    null,
                    holderResolveSourceFake,
                    inputs -> system -> RibbonPlan.NONE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("holderProvider");
        }
    }

    @Nested
    class Grouping {

        @Test
        void answersTheGroupingItWasBuiltUnder() {

            var grouping = HolderGroupingFixture.buildGroupOf(HEGEMONY, PERSEAN);

            assertThat(buildSourceUnder(grouping).grouping())
                .isSameAs(grouping);
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

            assertThat(providerPasses)
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

            assertThat(providerPasses)
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

            assertThat(providerPasses.get(0).colonyKnowledge().rule())
                .isEqualTo(GATED_RULES.colonyVisibility());
        }

        @Test
        void handsTheLayersRuleTheSpotlitOwner() {

            buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), HEGEMONY);

            assertThat(providerSpotlitIds)
                .containsExactly(HEGEMONY);
        }

        @Test
        void answersWithTheLayersOwnersAndFillExceptions() {

            var owners = buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);

            assertThat(owners.ownerBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY_OWNER));
            assertThat(owners.contestedSystemKeys())
                .containsExactly(CONTESTED_SYSTEM);
            assertThat(owners.unfilledSystemKeys())
                .containsExactly(UNFILLED_SYSTEM);
        }

        @Test
        void readsInhabitationOffThePassTheHoldingWasResolvedOver() {
            // Off one pass, the holding and the inhabitation cannot disagree about a colony the
            // habitation rule admits.
            var owners = buildSourceUnder(HolderGrouping.identity())
                .resolveOwners(buildWalkUnder(MapVisibilityRules.BASE), null);

            inhabitationMock.verify(
                () -> OwnerMapInhabitation.readInhabitedSystemKeys(same(providerPasses.get(0))));

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

            spotlitBlocsMock.verify(() -> SpotlitBlocs.findPresentSystemKeys(
                same(providerPasses.get(0)),
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
                .isSameAs(providerPasses.get(0));
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
                .isNotSameAs(providerPasses.get(0))
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
                .isNotSameAs(providerPasses.get(0))
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
                .when(() -> OwnerMapInhabitation.isSystemInhabited(any(), same(system)))
                .thenReturn(true);

            var resolve = buildSourceUnder(HolderGrouping.identity())
                .openSystemResolve(buildWalkUnder(MapVisibilityRules.BASE), null);

            assertThat(resolve.isSystemInhabited(system))
                .isTrue();
            inhabitationMock.verify(() -> OwnerMapInhabitation.isSystemInhabited(
                same(holderResolveSourceFake.readPassesOpenedOver().get(0)),
                same(system)));
        }

        @Test
        void answersPresenceUnderTheSpotlitOwnerItWasOpenedUnder() {

            var system = buildSystem("hybrasil");

            spotlitBlocsMock
                .when(() -> SpotlitBlocs.isBlocPresentIn(any(), eq(HEGEMONY), same(system)))
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
                .isSameAs(providerPasses.get(0));
        }

        @Test
        void judgesContestsAgainstTheContestGroupingItWasBuiltUnder() {
            // Who stands together is a second reading beside the fold: allies painted apart still
            // band as allies, so the affiliation is built off the contest grouping rather than the
            // one the counts fold under.
            new HolderOwnerSource(
                    HolderGrouping.identity(),
                    HolderGroupingFixture.buildGroupOf(HEGEMONY, PERSEAN),
                    buildProvider(),
                    holderResolveSourceFake,
                    this::recordPlannerInputs)
                .resolveRibbonPlanner(buildWalkUnder(MapVisibilityRules.BASE), RULES);

            assertThat(plannerInputs.get(0).affiliation().areBlocsAllied(HEGEMONY, PERSEAN))
                .isTrue();
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
                    HolderGrouping.identity(),
                    buildProvider(),
                    holderResolveSourceFake,
                    inputs -> planner)
                .resolveRibbonPlanner(buildWalkUnder(MapVisibilityRules.BASE), RULES);

            assertThat(answered)
                .isSameAs(planner);
        }

        private SystemRibbonPlanner recordPlannerInputs(RibbonPlanInputs inputs) {
            plannerInputs.add(inputs);
            return system -> RibbonPlan.NONE;
        }
    }

    // A source under the given grouping over the recording rules, nobody standing together.
    private HolderOwnerSource buildSourceUnder(HolderGrouping grouping) {

        return new HolderOwnerSource(
            grouping,
            HolderGrouping.identity(),
            buildProvider(),
            holderResolveSourceFake,
            inputs -> {
                plannerInputs.add(inputs);
                return system -> RibbonPlan.NONE;
            });
    }

    // The layer's whole-sector rule: one held system, one contested and one unfilled, recording the
    // pass and the pick it was handed.
    private HolderProvider buildProvider() {

        return (pass, selectedBlocId) -> {
            providerPasses.add(pass);
            providerSpotlitIds.add(selectedBlocId);

            return new HolderResolution(
                Map.of(HELD_SYSTEM, HEGEMONY_OWNER),
                Set.of(CONTESTED_SYSTEM),
                Set.of(UNFILLED_SYSTEM));
        };
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
