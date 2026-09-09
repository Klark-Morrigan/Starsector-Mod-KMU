package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.profiling.ProfileCounter;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.profiling.snapshot.BudgetBreach;
import kmlib.profiling.snapshot.ProfileNode;
import kmlib.starsector.SectorWalkCounters;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.render.MapFrameSections;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.render.ribbon.RibbonSettingsFixtures;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how many times a political-map rebuild reads the sector: once per system, for the whole
 * rebuild, however many stages run.
 *
 * <p>Three of them ask each system who lives there - the cell cut deciding which systems are
 * drawn, the fills resolving who holds each, and the band bake counting how each splits - and each
 * is handed the rebuild's own reading rather than the sector it was opened over. What that costs,
 * if it is ever undone, is a walk of every entity in every system in the sector, twice over, on
 * every rebuild; and a rebuild runs whenever the drawn set moves, a toggle flips, a view switches
 * or a colony changes hands.
 *
 * <p>The rules are pinned here for the same reason. Each stage used to sample the player's
 * visibility settings for itself, so a gate flipped mid-rebuild left cells cut under one rule,
 * fills painted under a second and bands counted under a third, with nothing on screen reporting
 * which half was which.
 *
 * <p>No unit can make either claim. How many times a system was walked, and how many times a knob
 * was read, are facts about the composition rather than about any stage of it: each stage, handed
 * a pass, reads precisely what it is given and passes either way. So the cache, the geometry
 * update, the territory build and the bake are all real here, and the count is read off the
 * profiler's own walk counters, which the library's shared reads add as they traverse - the same
 * witness a reader of a capture has in play, rather than however many times one vanilla method
 * happened to be called.
 *
 * <p>Those counts are read off {@link MapFrameSections#REFRESH}, which is the row the frame opens
 * around exactly this call and the row carrying the framework's bound on what one refresh may
 * traverse. The one case still counting a vanilla call is the idle frame's, whose claim is that
 * the sector was not read at all rather than how often it was.
 *
 * <p>What is stubbed is what no test JVM answers: the logger and the live LunaLib reads the
 * rebuild's stages are configured by, plus the sector lookup - which the rebuild itself no longer
 * makes, taking its sector off the installation it belongs to, and which is staged only for the
 * incremental path a frame with nothing stale falls through to. Nothing standing in for a
 * collaborator.
 */
final class PoliticalMapRebuildWalkIntegrationTest {

    private static final String ALPHA_ID = "alpha";
    private static final String BETA_ID = "beta";

    // The only system of the sector staged as the one the game is running. Named apart from the two
    // above so a cell cut from the running sector rather than the installed one is visible as a key
    // that has no business being there, rather than as a count.
    private static final String GAMMA_ID = "gamma";

    private static final String HEGEMONY_ID = "hegemony";
    private static final String TRITACHYON_ID = "tritachyon";

    // Two colonies of unequal size in one system, so the system has a settled winner and still
    // splits between two blocs - which is what gives the bake a band with runs to lay and so a
    // reason to ask that system who lives there at all.
    private static final int HOLDING_COLONY_SIZE = 5;
    private static final int RIVAL_COLONY_SIZE = 3;

    // The dev reveal lifted, for the case that flips a rule between two rebuilds: it admits an
    // undiscovered colony, which moves the drawn set, the fills and the counts together.
    private static final MapVisibilityRules UNDISCOVERED_REVEALED = new MapVisibilityRules(
        new ColonyVisibility(true, DecivilisedMarkets.DEFAULT_SURVEY_LEVEL, Set.of()),
        false);

    // Comfortably past the motion tracker's one-unit noise floor, so a staged drift is unambiguous
    // motion rather than something that could read as float jitter.
    private static final float CLEAR_OF_THE_NOISE_FLOOR = 500f;

    // Nothing this suite claims is a duration, so one reading answers every clock read the capture
    // makes - and a rebuild that took no time is still a rebuild that traversed what it traversed.
    private static final long FIXED_CLOCK_NANOS = 0L;

    // The staged sector's two rival colonies, each selected once for the whole rebuild. A stage
    // opening a selection of its own reads both again, so the count doubles rather than moving by
    // one.
    private static final long TWO_COLONIES_SELECTED_ONCE = 2L;

    // Two rebuilds, each opening a traversal of its own: what the row holds after a refresh signal
    // has forced the second.
    private static final long ONE_SECTOR_WALK_PER_REBUILD = 2L;

    // The screen each rebuild here is driven for. A stand-in rather than one of the two live screens:
    // how many times a rebuild walks the sector is the same on either panel, and what a switch between
    // them costs is PoliticalMapRebuildStalenessIntegrationTest's.
    private static final ScreenMemoryScope SCREEN = ScreenMemoryScopes.createStandInScreen();

    // A second sector's machinery, standing beside the one the cache under test is built against.
    // Its own sector reaches nothing here - what a case wants of it is its tracker, so that a claim
    // about whose movers a cut consults has somebody else's to be made against.
    private final MapLayerInstallation otherInstallation =
        new MapLayerInstallation(mock(SectorAPI.class));

    // The machinery the cache under test is built against, made over the sector a case stages -
    // which is the sector its rebuild reads, and so has to exist before the installation does.
    private MapLayerInstallation installation;

    private PoliticalMapRebuildSeams seams;

    @BeforeEach
    void openSeams() {

        seams = PoliticalMapRebuildSeams.openEverySeamARebuildNeeds();

        // The one seam this suite wants answered differently: the bands on at sizes that draw, so a
        // contested system's cell carries runs and the bake has a reason to ask who lives there.
        RibbonSettingsFixtures.stubBandsOnAtSizesThatDraw(seams.resolveRibbonSettingsSeam());
    }

    @AfterEach
    void closeSeams() {
        seams.closeEverySeam();
    }

    @Nested
    class Refresh {

        @Test
        void refreshSelectsEachSystemsColoniesOnceForTheWholeRebuild() {
            // The step's own claim. Every stage asks the same question of the same systems, so the
            // rebuild opens one reading of the sector and hands it down; three stages each opening
            // a reading of their own is what this stops, and each would show here as another
            // reading of every colony in the sector.
            buildContestedSectorWithAnEmptyNeighbour();

            var refresh = captureOneRebuild();

            assertThat(readCount(refresh, SectorWalkCounters.COLONIES_READ))
                .isEqualTo(TWO_COLONIES_SELECTED_ONCE);
        }

        @Test
        void refreshKeepsTheRebuildInsideTheFrameworksWalkBudget() {
            // What the framework promises the frame it draws in, read off the row the frame opens
            // around this very call: one traversal of the sector per refresh, however many stages
            // run. A stage going looking for the system list on its own breaks it, and the breach
            // is the finding a reader of a capture is handed rather than a number to compare.
            buildContestedSectorWithAnEmptyNeighbour();

            assertThat(captureOneRebuild().getBudgetBreach())
                .isSameAs(BudgetBreach.NO_BREACH);
        }

        @Test
        void refreshKeepsTheRebuildInsideTheWalkBudgetOnARefreshSignalToo() {
            // The second rebuild, which reads the sector afresh rather than off the first one's
            // reading. Each refresh is judged on its own call, so a second rebuild that traversed
            // twice would be the breach even though the first stayed inside the bound.
            buildContestedSectorWithAnEmptyNeighbour();

            assertThat(captureRebuildsAcrossARefreshSignal().getBudgetBreach())
                .isSameAs(BudgetBreach.NO_BREACH);
        }

        @Test
        void refreshWalksTheSectorAgainOnASecondRebuild() {
            // The bound's other half: one walk per refresh rather than one walk ever. A reading
            // kept between rebuilds would draw the second off the sector the first saw, which is
            // the change a rebuild exists to show - and would show here as the walk that never
            // happened.
            buildContestedSectorWithAnEmptyNeighbour();

            var refresh = captureRebuildsAcrossARefreshSignal();

            assertThat(readCount(refresh, SectorWalkCounters.SECTOR_WALKS))
                .isEqualTo(ONE_SECTOR_WALK_PER_REBUILD);
        }

        @Test
        void refreshReadsNoSectorOnAFrameWithNothingStaleToRebuild() {
            // The frame this cache spends nearly all of its life on: the map is open, refresh runs,
            // and no input has moved. Both staleness questions are settled before a reading of the
            // sector is opened for exactly this reason - a reading opened first would put a walk of
            // every system into every frame of an idle map, which is worse than the three walks the
            // step set out to remove.
            var sector = buildContestedSectorWithAnEmptyNeighbour();
            var cache = new PoliticalMapCache(installation);

            cache.refresh(FactionsView.INSTANCE, SCREEN);
            cache.refresh(FactionsView.INSTANCE, SCREEN);

            for (var system : sector.getStarSystems()) {
                verify(system, times(1)).getAllEntities();
            }
        }

        @Test
        void refreshSamplesTheVisibilityRulesOnceForTheWholeRebuild() {
            // The other half of one reading: one sampling of the rules it is taken under. Three
            // samplings let a gate flipped mid-rebuild cut the cells under one rule and paint the
            // fills under another, which nothing on screen would report.
            buildContestedSectorWithAnEmptyNeighbour();

            runOneRebuild();

            seams.resolveVisibilityRulesSeam().verify(MapVisibilityRules::readFromLunaSettings, times(1));
        }

        @Test
        void refreshReadsTheSectorAsItStandsOnEachRebuildRatherThanOffTheLastOnes() {
            // The other half of holding one reading per rebuild: it has to be this rebuild's. An
            // index kept between rebuilds would draw the second off the sector the first saw,
            // which is precisely the change a rebuild exists to show.
            var sector = buildContestedSectorWithAnEmptyNeighbour();
            var cache = new PoliticalMapCache(installation);

            cache.refresh(FactionsView.INSTANCE, SCREEN);
            settleTheEmptyNeighbour(sector);
            requestTheNextRebuild();
            cache.refresh(FactionsView.INSTANCE, SCREEN);

            assertThat(cache.getTerritories().getInhabitedSystemIds())
                .containsExactlyInAnyOrder(ALPHA_ID, BETA_ID);
        }

        @Test
        void refreshResolvesEveryStageOfARebuildUnderTheRulesInForceForThatRebuild() {
            // A gate flipped between two rebuilds, posed on a colony nobody has discovered: under
            // the shipped rule the system is unsettled, and under the reveal it is settled and
            // contested. So the second rebuild's cut, fills and bands each have to move, and a
            // stage sampling the rules for itself would be the one that did not.
            buildUndiscoveredSectorWithAnEmptyNeighbour();
            var cache = new PoliticalMapCache(installation);

            cache.refresh(FactionsView.INSTANCE, SCREEN);
            seams.resolveVisibilityRulesSeam()
                .when(MapVisibilityRules::readFromLunaSettings)
                .thenReturn(UNDISCOVERED_REVEALED);
            requestTheNextRebuild();
            cache.refresh(FactionsView.INSTANCE, SCREEN);

            var territories = cache.getTerritories();

            // The cut: the revealed system is drawn, so it has a cell to paint at all.
            assertThat(territories.getStyledCellByCellId())
                .containsKey(ALPHA_ID);
            // The fills: the same system reads as settled rather than as empty backdrop.
            assertThat(territories.getInhabitedSystemIds())
                .containsExactly(ALPHA_ID);
            // The bands: its two revealed blocs split the system, so its cell carries runs.
            assertThat(territories.getRibbonByCellId().get(ALPHA_ID).isEmpty())
                .isFalse();
        }

        @Test
        void refreshLeavesOutTheSystemsItsOwnInstallationSawMoving() {
            // The cut consults the movers so a system drifting across hyperspace seeds no cell and
            // clips no neighbour, its borders having nowhere stable to sit. Posed here rather than
            // in a unit because the moving set is published by a real tracker off real observations
            // and read by a real cut - neither end of which a stand-in could stage.
            var sector = buildContestedSectorWithASettledNeighbour();

            observeSystemMovingInto(installation.resolveMovingSystems(), sector, ALPHA_ID);
            var cache = new PoliticalMapCache(installation);

            cache.refresh(FactionsView.INSTANCE, SCREEN);

            assertThat(cache.getTerritories().getStyledCellByCellId())
                .containsKey(BETA_ID)
                .doesNotContainKey(ALPHA_ID);
        }

        @Test
        void refreshCutsASystemAnotherInstallationSawMoving() {
            // The half a cache holding its own installation is for. A tracker is keyed by bare
            // system id and nothing forbids two sectors from generating a system under the same
            // one, so a cache reading the running game's movers would drop this sector's system
            // for a drift the other sector's made.
            var sector = buildContestedSectorWithASettledNeighbour();

            observeSystemMovingInto(otherInstallation.resolveMovingSystems(), sector, ALPHA_ID);
            var cache = new PoliticalMapCache(installation);

            cache.refresh(FactionsView.INSTANCE, SCREEN);

            assertThat(cache.getTerritories().getStyledCellByCellId())
                .containsKeys(ALPHA_ID, BETA_ID);
        }

        @Test
        void refreshDrainsItsOwnInstallationsStaleSystemsAndLeavesAnothersStanding() {
            // The board's half of the same claim. A full rebuild re-derives every system, so it
            // drains the marks it has just accounted for - and the marks are bare system ids, so a
            // rebuild draining a shared board would swallow another sector's pending re-shape and
            // leave that sector drawing a holder that has already moved, with nothing on either map
            // to say a mark went missing.
            buildContestedSectorWithASettledNeighbour();

            installation.resolveRefreshBoard().markSystemGroupingStale(ALPHA_ID);
            otherInstallation.resolveRefreshBoard().markSystemGroupingStale(ALPHA_ID);

            new PoliticalMapCache(installation).refresh(FactionsView.INSTANCE, SCREEN);

            assertThat(installation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .isEmpty();
            assertThat(otherInstallation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .containsExactly(ALPHA_ID);
        }

        @Test
        void refreshCutsTheSectorItsInstallationWasMadeForRatherThanTheRunningOne() {
            // The question this whole rework was for. Vanilla's map hook names no sector, so a
            // rebuild used to ask the running game which one it was drawing - which is right only
            // while the sector it holds cells for and the sector that is loaded are the same. Posed
            // with them apart: the cells have to come from the sector the machinery was installed
            // on, and the running one has to reach nothing.
            buildContestedSectorWithASettledNeighbour();
            stageADifferentSectorAsTheRunningOne();

            var cache = new PoliticalMapCache(installation);

            cache.refresh(FactionsView.INSTANCE, SCREEN);

            assertThat(cache.getTerritories().getStyledCellByCellId())
                .containsKeys(ALPHA_ID, BETA_ID)
                .doesNotContainKey(GAMMA_ID);
        }
    }

    // One rebuild of the real cache over the staged sector, unmeasured - what a case asserting on
    // what the rebuild sampled, rather than on what it traversed, wants.
    private void runOneRebuild() {
        new PoliticalMapCache(installation).refresh(FactionsView.INSTANCE, SCREEN);
    }

    // One rebuild of the real cache over the staged sector, measured the way a frame measures it:
    // inside the beat the renderer opens around this very call, which is the row carrying what one
    // refresh is allowed to traverse.
    private ProfileNode captureOneRebuild() {

        var cache = new PoliticalMapCache(installation);

        return captureRefreshesOf(profiler -> {
            try (var refresh = profiler.open(MapFrameSections.REFRESH)) {
                cache.refresh(FactionsView.INSTANCE, SCREEN);
            }
        });
    }

    // Two rebuilds of one cache with a geometry signal between them, which is how a second full
    // rebuild is posed at all: a cache whose inputs stand still folds the next frame into the cheap
    // path. Both land on the one row, each as a call of its own.
    private ProfileNode captureRebuildsAcrossARefreshSignal() {

        var cache = new PoliticalMapCache(installation);

        return captureRefreshesOf(profiler -> {
            try (var refresh = profiler.open(MapFrameSections.REFRESH)) {
                cache.refresh(FactionsView.INSTANCE, SCREEN);
            }
            requestTheNextRebuild();

            try (var refresh = profiler.open(MapFrameSections.REFRESH)) {
                cache.refresh(FactionsView.INSTANCE, SCREEN);
            }
        });
    }

    // The capture both forms are: the refreshes run against the profiler they were bound to, and
    // the beat's row read back off what that profiler kept. The profiler is bound and taken back by
    // the capture, the holder being process state that would otherwise follow this suite into
    // whatever runs next.
    private static ProfileNode captureRefreshesOf(Consumer<RecordingProfiler> refreshes) {

        var profiler = new RecordingProfiler(() -> FIXED_CLOCK_NANOS);

        return RecordedCapture
            .recordWhile(profiler, () -> refreshes.accept(profiler))
            .findNode(MapFrameSections.REFRESH.getName());
    }

    // What one row counted of one counter. A counter nothing touched is absent from the row rather
    // than present at nought, and reading it as nought is what turns "never counted" into the
    // number a case names - which is the failure a case about a walk that stopped happening wants.
    private static long readCount(ProfileNode row, ProfileCounter counter) {

        var count = row.findCount(counter);

        return count == null ? 0L : count.getTotals().getTotal();
    }

    // Drifts one system far enough for two observations either side of the move to read it as
    // moving, and stages those observations into the given tracker - which is the state the cut
    // consults when it decides what to leave out of the partition.
    //
    // Staged through real observations rather than by writing a set, since the moving set is
    // published by the tracker and there is no other way in.
    private static void observeSystemMovingInto(
            MovingSystems movingSystems,
            SectorAPI sector,
            String systemId) {

        movingSystems.updateMovingSystems(MapVisibilityPass.over(sector, MapVisibilityRules.BASE));

        SectorPoliticsFixtures.findSystemIn(sector, systemId).getLocation().x
            += CLEAR_OF_THE_NOISE_FLOOR;

        movingSystems.updateMovingSystems(MapVisibilityPass.over(sector, MapVisibilityRules.BASE));
    }

    // Raises the geometry signal on the cache's own board so the next refresh finds both halves
    // stale and rebuilds them, which is how a second rebuild is posed at all: nothing else in this
    // suite moves a revision, and a cache whose inputs stand still folds the frame into the cheap
    // path.
    private void requestTheNextRebuild() {
        installation
            .resolveRefreshBoard()
            .requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);
    }

    // Two star systems: one settled by two rival colonies, and one empty. The rivalry is what
    // gives the bake a band to lay, and the empty neighbour is what a later rebuild can settle.
    private SectorAPI buildContestedSectorWithAnEmptyNeighbour() {
        return buildContestedSectorStagedBy(SectorPoliticsFixtures::buildVisibleMarket);
    }

    // The same two systems with the neighbour settled from the start, so both are drawn and a case
    // about one of them dropping out has the other left standing to say so against.
    private SectorAPI buildContestedSectorWithASettledNeighbour() {

        var sector = buildContestedSectorWithAnEmptyNeighbour();
        settleTheEmptyNeighbour(sector);

        return sector;
    }

    // The same shape with neither colony discovered, so the shipped rule leaves the system
    // unsettled and the dev reveal admits both at once.
    private SectorAPI buildUndiscoveredSectorWithAnEmptyNeighbour() {
        return buildContestedSectorStagedBy(SectorPoliticsFixtures::buildUndiscoveredOpenMarket);
    }

    // The sector both shapes above are, differing only in how their two colonies are staged - so
    // the reveal case and the plain one cannot drift apart on anything else, which is what makes
    // the rule the only thing between them.
    //
    // Installed on, since that is where a rebuild's sector comes from, and staged as the running one
    // besides, which the incremental path still reaches for. Every system is given a site to seed a
    // cell at: without one neither seeds a cell and the rebuild would draw nothing for the count to
    // be taken over.
    private SectorAPI buildContestedSectorStagedBy(ColonyStaging stageColony) {

        var hegemony = SectorPoliticsFixtures.buildFaction(HEGEMONY_ID);
        var tritachyon = SectorPoliticsFixtures.buildFaction(TRITACHYON_ID);

        var sector = SectorPoliticsFixtures.buildSectorWithSystems(
            List.of(hegemony, tritachyon),
            listSystemMarkets(
                ALPHA_ID,
                stageColony.stageColony(hegemony, HOLDING_COLONY_SIZE),
                stageColony.stageColony(tritachyon, RIVAL_COLONY_SIZE)),
            listSystemMarkets(BETA_ID));

        SectorPoliticsFixtures.placeEverySystemInHyperspace(sector);
        installation = new MapLayerInstallation(sector);

        seams.resolveGlobalSeam()
            .when(Global::getSector)
            .thenReturn(sector);

        return sector;
    }

    // Puts a sector of one settled system under the global lookup, leaving the installed one where
    // it is - so the two disagree, and a rebuild that asked the running game would cut a cell for
    // this system and none for the installed sector's.
    private void stageADifferentSectorAsTheRunningOne() {

        var tritachyon = SectorPoliticsFixtures.buildFaction(TRITACHYON_ID);

        var runningSector = SectorPoliticsFixtures.buildSectorWithSystems(
            List.of(tritachyon),
            listSystemMarkets(
                GAMMA_ID,
                SectorPoliticsFixtures.buildVisibleMarket(tritachyon, HOLDING_COLONY_SIZE)));

        SectorPoliticsFixtures.placeEverySystemInHyperspace(runningSector);
        seams.resolveGlobalSeam()
            .when(Global::getSector)
            .thenReturn(runningSector);
    }

    // Settles the empty neighbour, which puts it on the drawn set and into the settled set - the
    // change a stale reading of the sector could not report.
    private static void settleTheEmptyNeighbour(SectorAPI sector) {

        var beta = SectorPoliticsFixtures.findSystemIn(sector, BETA_ID);
        var colony = SectorPoliticsFixtures.buildVisibleMarket(
            SectorPoliticsFixtures.buildFaction(TRITACHYON_ID),
            HOLDING_COLONY_SIZE);

        when(sector.getEconomy().getMarkets(beta))
            .thenReturn(List.of(colony));
    }

    // How one of the sector's colonies is staged - a plain visible market, or one nobody has
    // discovered. Named rather than taken as a bare lambda type so the two sector shapes read as
    // one arrangement under two colony kinds.
    @FunctionalInterface
    private interface ColonyStaging {
        MarketAPI stageColony(FactionAPI faction, int size);
    }
}
