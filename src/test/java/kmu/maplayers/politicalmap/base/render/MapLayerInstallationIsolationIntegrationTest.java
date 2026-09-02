package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.refresh.MapLayerSectorWatcher;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapInstaller;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonySizeListener;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;
import kmu.settings.KmuPoliticalMapGeometrySettings;
import kmu.settings.KmuPoliticalMapRibbonSettings;
import kmu.starsector.listeners.RecordingListenerManager;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that two sectors with the map machinery really installed on both keep nothing in common: a
 * colony change in one marks only its own system stale, a rebuild cuts only its own cells, and the
 * other sector's draw lists, movers and hover are left exactly as they were - including where both
 * sectors hold a system under the same id, which is the case every part of the machinery keys on and
 * none of them can tell apart on its own.
 *
 * <p>No unit can make this claim. Isolation is a fact about the composition: each part, handed its
 * own installation, behaves correctly whether or not the parts share one underneath, so a shared
 * holder reintroduced below any of them passes every suite but this. So the installations, the
 * listeners, the watcher, the poll and the rebuild are all real here, and only what no test JVM
 * answers is stood in for - the logger and the live LunaLib reads the rebuild's stages are
 * configured by.
 *
 * <p>It carries the installers' own wiring besides, which no suite either side of them reaches.
 * Which installation an installer builds a listener or a watcher against is invisible to the
 * collaborator's suite, which builds its own collaborator, and to the installer's suite, which
 * cannot see where the collaborator staged anything without standing up the whole poll behind it.
 * Machinery installed on two sectors is what reads the argument back: a watcher handed the wrong
 * sector's installation observes one sector's positions into the other's tracker.
 *
 * <p>And it carries the load discard, which is what an entry point calls before installing on the
 * sector it loaded. Pinned here rather than over that entry point because covering the call there
 * means standing in for three installers and both feature switches to reach one line, while here it
 * is a sector installed on, a load discarding it, and the sector loaded next drawing its own cells
 * with nothing of the first one's reachable.
 *
 * <p>Sits in the render package because the cache holding the draw lists is that package's own; the
 * installations and the installers it drives beside them are reached from anywhere.
 */
final class MapLayerInstallationIsolationIntegrationTest {

    private static final String HEGEMONY_ID = "hegemony";
    private static final String TRITACHYON_ID = "tritachyon";

    // The system id both sectors hold, which is the whole point of the pairing: every holder below
    // keys on a bare system id and nothing forbids two sectors from generating one alike, so this is
    // the id under which a shared holder would have one sector answer for the other.
    private static final String SHARED_SYSTEM_ID = "alpha";

    // One system each sector holds alone, so a cut that strayed into the other sector shows up as a
    // key that has no business being there rather than as a count.
    private static final String FIRST_SECTOR_SYSTEM_ID = "beta";
    private static final String SECOND_SECTOR_SYSTEM_ID = "gamma";

    // The size every staged colony carries. Nothing here weighs a colony against another, so a case
    // varying this would vary nothing the machinery can see.
    private static final int HOLDING_COLONY_SIZE = 5;

    // What a resize event reports the colony was before it changed. It reaches only the log line;
    // what the listener acts on is the seat.
    private static final int PREVIOUS_COLONY_SIZE = 3;

    // The cells' seed knobs, wide enough that a cell holds clear of its own inset border.
    private static final int CELL_BOUND_SEGMENTS = 16;
    private static final double CELL_RADIUS = 4000.0;

    // Comfortably past the watcher's 4-5s poll interval, so each advance drives exactly one poll.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 6f;

    // Comfortably past the motion tracker's one-unit noise floor, so a staged drift is unambiguous
    // motion rather than something that could read as float jitter.
    private static final float CLEAR_OF_THE_NOISE_FLOOR = 500f;

    // A cursor resting on the cell both sectors have an id for - the one hover that could be
    // mistaken for the other sector's.
    private static final MapHover HOVERED_SHARED_CELL =
        new MapHover(SHARED_SYSTEM_ID, List.of(SHARED_SYSTEM_ID));

    // Closed in reverse on the way out, so a seam opened over another is never left standing when
    // the inner one is already gone.
    private final List<MockedStatic<?>> openStaticSeams = new ArrayList<>();

    @BeforeEach
    void discardEveryInstallationAndOpenSeams() {

        // The index is process-wide, so a sector another suite installed on would still be indexed
        // here - and a resolution by location would walk it. Cleared before the seams open because
        // the index holds a logger taken from Global at class load.
        MapLayerInstallations.disposeEveryInstallation();

        var globalMock = openSeam(Global.class);
        globalMock
            .when(() -> Global.getLogger(any(Class.class)))
            .thenReturn(Logger.getLogger(MapLayerInstallationIsolationIntegrationTest.class));

        // A sector neither installation was made over, staged as the one the game is running. Any
        // stage that asked the running game which sector it was working on would then find a sector
        // with nothing in it, rather than quietly agreeing with whichever of the two happened to be
        // loaded last.
        globalMock
            .when(Global::getSector)
            .thenReturn(mock(SectorAPI.class));

        // The dev reveal and the anchor tuning, both LunaLib-backed: no case turns on either, so the
        // seam's own answers stand for them. The ribbon and diagnostics knobs likewise, whose
        // defaults leave the bands and the debug overlay off.
        openSeam(KmuMapLayerSettings.class);
        openSeam(KmuLunaSettings.class);
        openSeam(KmuPoliticalMapRibbonSettings.class);
        openSeam(KmuPoliticalMapDiagnosticsSettings.class);

        // No bloc spotlighted, which the seam's own null answers - the pick is sector-memory state
        // no test JVM has.
        openSeam(FilterSelection.class);

        var geometrySettingsMock = openSeam(KmuPoliticalMapGeometrySettings.class);
        geometrySettingsMock
            .when(KmuPoliticalMapGeometrySettings::getPoliticalMapCellBoundSegments)
            .thenReturn(CELL_BOUND_SEGMENTS);
        geometrySettingsMock
            .when(KmuPoliticalMapGeometrySettings::getPoliticalMapCellRadius)
            .thenReturn(CELL_RADIUS);

        var visibilityRulesMock = openSeam(MapVisibilityRules.class);
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);

        // The weighting rule the fills are resolved under, read live off LunaLib in production -
        // left to the settings seam it would weigh every colony at nothing and leave both sectors
        // unheld, which is the one state that would make every holder assertion below vacuous.
        var dominanceRulesMock = openSeam(DominanceRules.class);
        dominanceRulesMock
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
    }

    @AfterEach
    void closeSeamsAndDiscardEveryInstallation() {

        for (var index = openStaticSeams.size() - 1; index >= 0; index--) {
            openStaticSeams.get(index).close();
        }
        openStaticSeams.clear();

        MapLayerInstallations.disposeEveryInstallation();
    }

    @Nested
    class InstallMachineryOn {

        @Test
        void marksOnlyTheChangedSectorsSystemStaleWhenAColonyResizesThere() {
            // The mark is a bare system id on a board, so this is where two sectors collide most
            // quietly: a shared board would have one sector's resize reshape the other's cell, with
            // nothing on either map to say where the mark came from. Driven through the listener the
            // installer actually registered, which is also what says that listener was built against
            // the sector it was installed on.
            var firstSector = installMachineryOnASectorHeldBy(
                HEGEMONY_ID, SHARED_SYSTEM_ID, FIRST_SECTOR_SYSTEM_ID);
            var secondSector = installMachineryOnASectorHeldBy(
                TRITACHYON_ID, SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);

            findInstalledListenerOn(firstSector, PoliticalMapColonySizeListener.class)
                .reportColonySizeChanged(
                    mockMarketInSystem(SHARED_SYSTEM_ID),
                    PREVIOUS_COLONY_SIZE);

            assertThat(readRefreshBoardOf(firstSector).drainStaleGroupingSystemIds())
                .containsExactly(SHARED_SYSTEM_ID);
            assertThat(readRefreshBoardOf(secondSector).drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void cutsEachSectorsOwnCellsWhereBothHoldASystemUnderOneId() {
            // The drawing itself. The geometry cache reconciles by system id, so a cache serving two
            // sectors would not overwrite one sector's cell with the other's - it would keep the
            // first cut and leave the second sector drawing a cell around a place it does not hold.
            var firstSector = installMachineryOnASectorHeldBy(
                HEGEMONY_ID, SHARED_SYSTEM_ID, FIRST_SECTOR_SYSTEM_ID);
            var secondSector = installMachineryOnASectorHeldBy(
                TRITACHYON_ID, SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);

            var firstTerritories = rebuildTerritoriesOf(firstSector);
            var secondTerritories = rebuildTerritoriesOf(secondSector);

            assertThat(firstTerritories.getStyledCellByCellId())
                .containsKeys(SHARED_SYSTEM_ID, FIRST_SECTOR_SYSTEM_ID)
                .doesNotContainKey(SECOND_SECTOR_SYSTEM_ID);
            assertThat(secondTerritories.getStyledCellByCellId())
                .containsKeys(SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID)
                .doesNotContainKey(FIRST_SECTOR_SYSTEM_ID);

            // The shared id is held by a different faction in each, so the cell they both have an id
            // for still paints each sector's own holder.
            assertThat(firstTerritories.getHolderBySystemId().get(SHARED_SYSTEM_ID).factionId())
                .isEqualTo(HEGEMONY_ID);
            assertThat(secondTerritories.getHolderBySystemId().get(SHARED_SYSTEM_ID).factionId())
                .isEqualTo(TRITACHYON_ID);
        }

        @Test
        void observesEachSectorsPositionsIntoTheTrackerOfTheInstallationItsWatcherWasBuiltWith() {
            // The installer's own wiring, which nothing else reads back. The watcher is built with
            // the installation resolved for the sector it is added to, and that argument is
            // invisible either side of the install - so a watcher handed the other sector's
            // installation would observe these positions into that sector's tracker, and the cut
            // over there would drop a system for a drift it never made.
            var firstSector = installMachineryOnASectorHeldBy(
                HEGEMONY_ID, SHARED_SYSTEM_ID, FIRST_SECTOR_SYSTEM_ID);
            var secondSector = installMachineryOnASectorHeldBy(
                TRITACHYON_ID, SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);

            driftTheSharedSystemPastTwoPollsOf(firstSector);

            assertThat(readMovingSystemsOf(firstSector).getMovingSystemIds())
                .containsExactly(SHARED_SYSTEM_ID);
            assertThat(readMovingSystemsOf(secondSector).getMovingSystemIds())
                .isEmpty();
        }

        @Test
        void leavesTheOtherSectorsHoverParkedWhenACursorReadLandsOnOne() {
            // A hover names a cell by bare system id too, so a shared holder would light a cell on
            // the other sector's map and name that system in its box. Both ends go through the index
            // rather than through the handle the install returned, since what a render pass has is a
            // sector to resolve from.
            var firstSector = installMachineryOnASectorHeldBy(
                HEGEMONY_ID, SHARED_SYSTEM_ID, FIRST_SECTOR_SYSTEM_ID);
            var secondSector = installMachineryOnASectorHeldBy(
                TRITACHYON_ID, SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);

            resolveInstallationOf(firstSector).resolveHoverState().publishHover(HOVERED_SHARED_CELL);

            assertThat(resolveInstallationOf(firstSector).resolveHoverState().getHover())
                .isSameAs(HOVERED_SHARED_CELL);
            assertThat(resolveInstallationOf(secondSector).resolveHoverState().getHover())
                .isSameAs(MapHover.NONE);
        }
    }

    @Nested
    class UninstallMachineryFrom {

        @Test
        void leavesTheOtherSectorDrawingAndPutsTheRemovedOnesMachineryBeyondReach() {
            // A sector removed mid-session - the player switching the layers off on one while
            // another is still installed. What must survive is the other sector's drawing; what must
            // not is any route back to the removed sector's holders, which would otherwise go on
            // answering for a sector nothing draws.
            var firstSector = installMachineryOnASectorHeldBy(
                HEGEMONY_ID, SHARED_SYSTEM_ID, FIRST_SECTOR_SYSTEM_ID);
            var secondSector = installMachineryOnASectorHeldBy(
                TRITACHYON_ID, SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);

            var removedInstallation = resolveInstallationOf(firstSector);

            removedInstallation.resolveRefreshBoard().markSystemGroupingStale(SHARED_SYSTEM_ID);
            removedInstallation.resolveHoverState().publishHover(HOVERED_SHARED_CELL);

            MapLayerInstallations.uninstallMachineryFrom(firstSector);

            assertThat(rebuildTerritoriesOf(secondSector).getStyledCellByCellId())
                .containsKeys(SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);

            assertThat(removedInstallation.isDisposed())
                .isTrue();
            assertThat(resolveInstallationOf(firstSector))
                .isNotSameAs(removedInstallation);
        }
    }

    @Nested
    class DisposeEveryInstallation {

        @Test
        void leavesTheSectorLoadedNextNothingOfThePreviousOnes() {
            // The load discard, which is why nothing has to notice that a sector went away. Every
            // holder the previous sector filled is staged first - a stale mark, a drift, a hover -
            // so a discard that missed one shows up as that sector's state answering for the loaded
            // one, and the drift shows up twice over: a tracker carried across would drop the shared
            // system from the loaded sector's cut for a move the previous sector made.
            var previousSector = installMachineryOnASectorHeldBy(
                HEGEMONY_ID, SHARED_SYSTEM_ID, FIRST_SECTOR_SYSTEM_ID);
            var previousInstallation = resolveInstallationOf(previousSector);

            previousInstallation.resolveRefreshBoard().markSystemGroupingStale(SHARED_SYSTEM_ID);
            previousInstallation.resolveHoverState().publishHover(HOVERED_SHARED_CELL);
            driftTheSharedSystemPastTwoPollsOf(previousSector);

            MapLayerInstallations.disposeEveryInstallation();

            var loadedSector = installMachineryOnASectorHeldBy(
                TRITACHYON_ID, SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);
            var loadedInstallation = resolveInstallationOf(loadedSector);

            assertThat(rebuildTerritoriesOf(loadedSector).getStyledCellByCellId())
                .containsKeys(SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID)
                .doesNotContainKey(FIRST_SECTOR_SYSTEM_ID);

            assertThat(loadedInstallation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .isEmpty();
            assertThat(loadedInstallation.resolveMovingSystems().getMovingSystemIds())
                .isEmpty();
            assertThat(loadedInstallation.resolveHoverState().getHover())
                .isSameAs(MapHover.NONE);
            assertThat(previousInstallation.isDisposed())
                .isTrue();
        }
    }

    // A sector whose systems are each settled by one faction, with the map machinery installed on it
    // and the political map's own listeners and watcher registered against it - which is what makes
    // this real machinery rather than a pair of hand-built holders.
    //
    // The holder differs per sector so the cell both sectors have an id for is still told apart by
    // what it paints, and every system is placed in hyperspace: an unplaced one is skipped before the
    // drawn-set rule is ever asked about it, so it would seed no cell and the cut would have nothing
    // to be wrong about.
    private static SectorAPI installMachineryOnASectorHeldBy(
            String holderFactionId,
            String... systemIds) {

        var holder = SectorPoliticsFixtures.buildFaction(holderFactionId);
        var systems = new SectorPoliticsFixtures.SystemMarkets[systemIds.length];

        for (var index = 0; index < systemIds.length; index++) {
            systems[index] = listSystemMarkets(
                systemIds[index],
                SectorPoliticsFixtures.buildVisibleMarket(holder, HOLDING_COLONY_SIZE));
        }
        var sector = SectorPoliticsFixtures.buildSectorWithSystems(List.of(holder), systems);

        SectorPoliticsFixtures.placeEverySystemInHyperspace(sector);

        // The seat the installers register their listeners through; a sector without one registers
        // none, which would leave every listener case below with nothing to drive.
        when(sector.getListenerManager())
            .thenReturn(new RecordingListenerManager());

        MapLayerInstallations.installMachineryOn(sector);
        PoliticalMapInstaller.installAll(sector);

        return sector;
    }

    // One rebuild of this sector's own cache, over the installation the index hands back for it.
    private static PoliticalMapTerritories rebuildTerritoriesOf(SectorAPI sector) {

        var cache = new PoliticalMapCache(resolveInstallationOf(sector));

        cache.refresh(FactionsView.INSTANCE);

        return cache.getTerritories();
    }

    // Drives the sector's own installed watcher twice with a drift between, which is the only way a
    // system reads as moving: the tracker publishes a mover off two observations that disagree, and
    // the first poll only establishes where everything was.
    //
    // Through the installed watcher rather than through a tracker call, since which tracker the poll
    // stages into is the whole of what the wiring case is about.
    private static void driftTheSharedSystemPastTwoPollsOf(SectorAPI sector) {

        var watcher = findInstalledWatcherOn(sector);

        watcher.advance(ADVANCE_PAST_POLL_INTERVAL);

        SectorPoliticsFixtures.findSystemIn(sector, SHARED_SYSTEM_ID).getLocation().x
            += CLEAR_OF_THE_NOISE_FLOOR;

        watcher.advance(ADVANCE_PAST_POLL_INTERVAL);
    }

    // The watcher the installer added to this sector, taken back off the sector it was added to.
    // Filtered by type rather than taken as the only script, so a second transient script added
    // beside it later does not turn this into a cast failure in an unrelated case.
    private static MapLayerSectorWatcher findInstalledWatcherOn(SectorAPI sector) {

        var addedScript = ArgumentCaptor.forClass(EveryFrameScript.class);

        verify(sector, atLeastOnce())
            .addTransientScript(addedScript.capture());

        return addedScript.getAllValues().stream()
            .filter(MapLayerSectorWatcher.class::isInstance)
            .map(MapLayerSectorWatcher.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No map layer sector watcher was installed on the sector"));
    }

    // The listener of one kind the installer registered with this sector's manager, which is the
    // only handle on the sector each listener was built against.
    private static <T> T findInstalledListenerOn(SectorAPI sector, Class<T> listenerClass) {

        var listenerManager = (RecordingListenerManager) sector.getListenerManager();

        return listenerManager.getAddedListeners().stream()
            .filter(listenerClass::isInstance)
            .map(listenerClass::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No " + listenerClass.getSimpleName() + " was installed on the sector"));
    }

    // Every read of a holder goes back through the index rather than through what an install
    // returned, because that is what a seam handed a sector actually does - and it is the index
    // handing one sector another's holder that this whole suite is posed against.
    private static MapLayerInstallation resolveInstallationOf(SectorAPI sector) {
        return MapLayerInstallations.resolveInstallationFor(sector);
    }

    private static MapLayerRefreshBoard readRefreshBoardOf(SectorAPI sector) {
        return resolveInstallationOf(sector).resolveRefreshBoard();
    }

    private static MovingSystems readMovingSystemsOf(SectorAPI sector) {
        return resolveInstallationOf(sector).resolveMovingSystems();
    }

    // One style bundle for every category: nothing here turns on how a cell paints, only on which
    // sector's cells were cut and who each is painted for.
    private static CategoryStyle buildInertCategoryStyle() {

        var element = new ElementStyle(FactionPaletteSlot.PRIMARY, 1.0);
        return new CategoryStyle(element, element, 1.0, element, 1.0);
    }

    // Opens a static seam and registers it for closing, so the arrangement names what it needs
    // rather than repeating the open-and-remember pair for each.
    private <T> MockedStatic<T> openSeam(Class<T> seamedClass) {

        var seamMock = mockStatic(seamedClass);
        openStaticSeams.add(seamMock);

        return seamMock;
    }
}
