package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.DecivilisedMarkets;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.render.ribbon.RibbonSettingsFixtures;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.KmuPoliticalMapSettings;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
 * update, the territory build and the bake are all real here, and the count is taken off
 * {@code getAllEntities} - what the unregistered-market half of a colony selection reaches for,
 * and so what a second reading shows up as.
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

    // The cells' seed knobs, wide enough that a cell holds a band clear of its own inset border.
    private static final int CELL_BOUND_SEGMENTS = 16;
    private static final double CELL_RADIUS = 4000.0;

    // The dev reveal lifted, for the case that flips a rule between two rebuilds: it admits an
    // undiscovered colony, which moves the drawn set, the fills and the counts together.
    private static final MapVisibilityRules UNDISCOVERED_REVEALED = new MapVisibilityRules(
        new ColonyVisibility(true, DecivilisedMarkets.DEFAULT_SURVEY_LEVEL, Set.of()),
        false);

    // Comfortably past the motion tracker's one-unit noise floor, so a staged drift is unambiguous
    // motion rather than something that could read as float jitter.
    private static final float CLEAR_OF_THE_NOISE_FLOOR = 500f;

    // Closed in reverse on the way out, so a seam opened over another is never left standing when
    // the inner one is already gone.
    private final List<MockedStatic<?>> openStaticSeams = new ArrayList<>();

    // A second sector's machinery, standing beside the one the cache under test is built against.
    // Its own sector reaches nothing here - what a case wants of it is its tracker, so that a claim
    // about whose movers a cut consults has somebody else's to be made against.
    private final MapLayerInstallation otherInstallation =
        new MapLayerInstallation(mock(SectorAPI.class));

    // The machinery the cache under test is built against, made over the sector a case stages -
    // which is the sector its rebuild reads, and so has to exist before the installation does.
    private MapLayerInstallation installation;

    private MockedStatic<Global> globalMock;
    private MockedStatic<MapVisibilityRules> visibilityRulesMock;

    @BeforeEach
    void openSeams() {

        globalMock = openSeam(Global.class);
        globalMock
            .when(() -> Global.getLogger(any(Class.class)))
            .thenReturn(Logger.getLogger(PoliticalMapRebuildWalkIntegrationTest.class));

        // The dev reveal and the anchor tuning, both LunaLib-backed: no case turns on either, so
        // the seam's own answers stand for them.
        openSeam(KmuMapLayerSettings.class);
        openSeam(KmuLunaSettings.class);

        // No bloc spotlighted, which the seam's own null answers - the pick is sector-memory state
        // no test JVM has.
        openSeam(FilterSelection.class);

        var settingsMock = openSeam(KmuPoliticalMapSettings.class);
        RibbonSettingsFixtures.stubBandsOnAtSizesThatDraw(settingsMock);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapCellBoundSegments)
            .thenReturn(CELL_BOUND_SEGMENTS);
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapCellRadius)
            .thenReturn(CELL_RADIUS);

        visibilityRulesMock = openSeam(MapVisibilityRules.class);
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);

        // The weighting rule the fills and the bands are both resolved under, read live off
        // LunaLib in production - left to the settings seam it would weigh every colony at
        // nothing and leave the sector unheld.
        var rulesMock = openSeam(DominanceRules.class);
        rulesMock
            .when(DominanceRules::readFromLunaSettings)
            .thenReturn(SectorPoliticsFixtures.buildStabilityWeightedRules());

        var renderStyleMock = openSeam(RenderStyleReader.class);
        renderStyleMock
            .when(RenderStyleReader::readRenderStyle)
            .thenReturn(PoliticalMapTerritoryFixtures.createRenderStyleForEveryCategory(
                buildInertCategoryStyle()));

        // The names off, which keeps the label mint and the anchor fit off a rebuild that has no
        // font to measure with; the choice is sector-memory state as well.
        var nameFormatMock = openSeam(NameFormatPreference.class);
        nameFormatMock
            .when(NameFormatPreference::getSelectedNameFormat)
            .thenReturn(FactionNameFormatChoice.NONE);

        // The stale set is static and shared, so a residue from another suite would read here as a
        // system this one never marked.
        MapLayerRefresh.drainStaleGroupingSystemIds();
    }

    @AfterEach
    void closeSeams() {

        for (var index = openStaticSeams.size() - 1; index >= 0; index--) {
            openStaticSeams.get(index).close();
        }
        openStaticSeams.clear();
    }

    @Nested
    class Refresh {

        @Test
        void refreshSelectsEachSystemsColoniesOnceForTheWholeRebuild() {
            // The step's own claim. Every stage asks the same question of the same systems, so the
            // rebuild opens one reading of the sector and hands it down; three stages each opening
            // a reading of their own is what this stops.
            var sector = buildContestedSectorWithAnEmptyNeighbour();

            runOneRebuild();

            for (var system : sector.getStarSystems()) {
                verify(system, times(1)).getAllEntities();
            }
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

            cache.refresh(FactionsView.INSTANCE);
            cache.refresh(FactionsView.INSTANCE);

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

            visibilityRulesMock.verify(MapVisibilityRules::readFromLunaSettings, times(1));
        }

        @Test
        void refreshReadsTheSectorAsItStandsOnEachRebuildRatherThanOffTheLastOnes() {
            // The other half of holding one reading per rebuild: it has to be this rebuild's. An
            // index kept between rebuilds would draw the second off the sector the first saw,
            // which is precisely the change a rebuild exists to show.
            var sector = buildContestedSectorWithAnEmptyNeighbour();
            var cache = new PoliticalMapCache(installation);

            cache.refresh(FactionsView.INSTANCE);
            settleTheEmptyNeighbour(sector);
            requestTheNextRebuild();
            cache.refresh(FactionsView.INSTANCE);

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

            cache.refresh(FactionsView.INSTANCE);
            visibilityRulesMock
                .when(MapVisibilityRules::readFromLunaSettings)
                .thenReturn(UNDISCOVERED_REVEALED);
            requestTheNextRebuild();
            cache.refresh(FactionsView.INSTANCE);

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

            cache.refresh(FactionsView.INSTANCE);

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

            cache.refresh(FactionsView.INSTANCE);

            assertThat(cache.getTerritories().getStyledCellByCellId())
                .containsKeys(ALPHA_ID, BETA_ID);
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

            cache.refresh(FactionsView.INSTANCE);

            assertThat(cache.getTerritories().getStyledCellByCellId())
                .containsKeys(ALPHA_ID, BETA_ID)
                .doesNotContainKey(GAMMA_ID);
        }
    }

    // One rebuild of the real cache over whatever sector the global lookup was staged with - what
    // a case asserting on what the rebuild read, rather than on what it drew, wants.
    private void runOneRebuild() {
        new PoliticalMapCache(installation).refresh(FactionsView.INSTANCE);
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

    // Marks the shared geometry signal so the next refresh finds both halves stale and rebuilds
    // them, which is how a second rebuild is posed at all: nothing else in this suite moves a
    // revision, and a cache whose inputs stand still folds the frame into the cheap path.
    private static void requestTheNextRebuild() {
        MapLayerRefresh.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);
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

        globalMock
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
        globalMock
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

    // One style bundle for every category: nothing here turns on how a cell paints, only on what
    // the rebuild read before it painted anything.
    private static CategoryStyle buildInertCategoryStyle() {

        var element = new ElementStyle(FactionPaletteSlot.PRIMARY, 1.0);
        return new CategoryStyle(element, element, 1.0, element, 1.0);
    }

    // Opens a static seam and registers it for closing, so a case names what it needs rather than
    // repeating the open-and-remember pair for each.
    private <T> MockedStatic<T> openSeam(Class<T> seamedClass) {

        var seamMock = mockStatic(seamedClass);
        openStaticSeams.add(seamMock);

        return seamMock;
    }
}
