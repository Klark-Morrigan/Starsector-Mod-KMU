package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.starsector.listeners.RecordingListenerManager;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerSectorWatcher;
import kmu.maplayers.politicalmap.base.PoliticalMapInstaller;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonySizeListener;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that two sectors with the map machinery really installed on both keep nothing in common: a
 * colony change in one marks only its own system stale, rebuilds only its own cells, and leaves the
 * other's draw lists, movers and hover exactly as they were - including where both sectors hold a
 * system under the same id, which is the case every part of the machinery keys on and none of them
 * can tell apart on its own.
 *
 * <p>No unit can make this claim. Isolation is a fact about the composition: each part, handed its
 * own machinery, behaves correctly whether or not the parts share one underneath, so a shared
 * holder reintroduced below any of them passes every suite but this. So the machinery, the
 * listeners, the watcher, the poll and the rebuild are all real here, and only what no test JVM
 * answers is stood in for.
 *
 * <p>It carries the installers' own wiring besides, which no suite either side of them reaches.
 * Which machinery an installer builds a listener or a watcher against is invisible to the
 * collaborator's suite, which builds its own collaborator, and to the installer's suite, which
 * cannot see where the collaborator staged anything without standing up the whole poll behind it.
 * Machinery installed on two sectors is what reads the argument back: a watcher handed the wrong
 * sector's machinery observes one sector's positions into the other's tracker.
 *
 * <p>And it carries the load discard, which is what an entry point calls before installing on the
 * sector it loaded. Pinned here rather than over that entry point because covering the call there
 * means standing in for three installers and both feature switches to reach one line, while here it
 * is a sector installed on, a load discarding it, and the sector loaded next drawing its own cells
 * with nothing of the first one's reachable.
 *
 * <p>Sits in the render package because the cache holding the draw lists is that package's own; the
 * machinery and the installers it drives beside them are reached from anywhere.
 */
final class SectorMapMachineryIsolationIntegrationTest {

    private static final String HEGEMONY_ID = "hegemony";
    private static final String TRITACHYON_ID = "tritachyon";

    // A third faction, for the one case that hands a system over between two rebuilds: the sector
    // that changes has to end up holding somebody neither sector started with, or a holder read back
    // off the other sector could not be told from the one it always had.
    private static final String PERSEAN_ID = "persean";

    // The system id both sectors hold, which is the whole point of the pairing: every holder below
    // keys on the sector's own naming of a system - the bare id, or the key read off it - and
    // nothing forbids two sectors from generating one alike, so this is the id under which a shared
    // holder would have one sector answer for the other.
    private static final String SHARED_SYSTEM_ID = "alpha";

    // The key that system carries in either sector: the id alone, a staged system stating no
    // centre and no anchor. Written out rather than read off the system, an expectation taken
    // from the code under test being no expectation at all.
    private static final SystemKey SHARED_SYSTEM_KEY = new SystemKey("alpha", "", "");

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

    // Comfortably past the watcher's 4-5s poll interval, so each advance drives exactly one poll.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 6f;

    // Comfortably past the motion tracker's one-unit noise floor, so a staged drift is unambiguous
    // motion rather than something that could read as float jitter.
    private static final float CLEAR_OF_THE_NOISE_FLOOR = 500f;

    // The screen the rebuilds here are driven for. A stand-in rather than one of the two live screens:
    // what these cases separate is two sectors, and both are drawn for the same panel throughout.
    private static final ScreenMemoryScope SCREEN = ScreenMemoryScopes.createStandInScreen();

    // A cursor resting on the cell both sectors have an id for - the one hover that could be
    // mistaken for the other sector's.
    private static final MapHover HOVERED_SHARED_CELL =
        new MapHover(SHARED_SYSTEM_ID, List.of(SHARED_SYSTEM_ID));

    private PoliticalMapRebuildSeams seams;

    @BeforeEach
    void discardEveryMachineryAndOpenSeams() {

        // The index is process-wide, so a sector another suite installed on would still be indexed
        // here - and a resolution by location would walk it. Cleared before the seams open because
        // the index holds a logger taken from Global at class load.
        SectorMapMachineryIndex.disposeAllMachinery();

        seams = PoliticalMapRebuildSeams.openEverySeamARebuildNeeds();

        // A sector neither machinery was made over, staged as the one the game is running. Any
        // stage that asked the running game which sector it was working on would then find a sector
        // with nothing in it, rather than quietly agreeing with whichever of the two happened to be
        // loaded last.
        seams.resolveGlobalSeam()
            .when(Global::getSector)
            .thenReturn(mock(SectorAPI.class));
    }

    @AfterEach
    void closeSeamsAndDiscardEveryMachinery() {

        seams.closeEverySeam();

        SectorMapMachineryIndex.disposeAllMachinery();
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
            var firstSector = installMachineryOnAFirstSector();
            var secondSector = installMachineryOnASecondSector();

            findInstalledListenerOn(firstSector, PoliticalMapColonySizeListener.class)
                .reportColonySizeChanged(
                    mockMarketInSystem(SHARED_SYSTEM_ID),
                    PREVIOUS_COLONY_SIZE);

            assertThat(resolveMachineryOf(firstSector)
                    .resolveRefreshBoard()
                    .drainStaleGroupingSystemIds())
                .containsExactly(SHARED_SYSTEM_ID);
            assertThat(resolveMachineryOf(secondSector)
                    .resolveRefreshBoard()
                    .drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void cutsEachSectorsOwnCellsWhereBothHoldASystemUnderOneId() {
            // The drawing itself. The geometry cache reconciles by system id, so a cache serving two
            // sectors would not overwrite one sector's cell with the other's - it would keep the
            // first cut and leave the second sector drawing a cell around a place it does not hold.
            var firstSector = installMachineryOnAFirstSector();
            var secondSector = installMachineryOnASecondSector();

            var firstTerritories = new PoliticalMapCacheDriver(firstSector).rebuild();
            var secondTerritories = new PoliticalMapCacheDriver(secondSector).rebuild();

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
        void leavesTheOtherSectorsDrawnHoldersStandingWhenAChangeRebuildsOne() {
            // The three halves joined, which is what neither of the two cases above does on its own:
            // a change marks one sector, that sector re-derives the system it marked, and the sector
            // nobody touched still draws what it drew. The re-derive is the targeted path rather
            // than a whole rebuild, which is the one that used to ask the running game which sector
            // it was folding - so a leak here reads as the other sector's cell changing hands to a
            // faction it never held.
            var firstSector = installMachineryOnAFirstSector();
            var secondSector = installMachineryOnASecondSector();

            var firstMap = new PoliticalMapCacheDriver(firstSector);
            var secondMap = new PoliticalMapCacheDriver(secondSector);

            firstMap.rebuild();
            secondMap.rebuild();

            handTheSharedSystemOf(firstSector, PERSEAN_ID);
            findInstalledListenerOn(firstSector, PoliticalMapColonySizeListener.class)
                .reportColonySizeChanged(
                    mockMarketInSystem(SHARED_SYSTEM_ID),
                    PREVIOUS_COLONY_SIZE);

            assertThat(firstMap.rebuild().getHolderBySystemId().get(SHARED_SYSTEM_ID).factionId())
                .isEqualTo(PERSEAN_ID);

            var untouchedTerritories = secondMap.readTerritories();

            assertThat(untouchedTerritories.getHolderBySystemId().get(SHARED_SYSTEM_ID).factionId())
                .isEqualTo(TRITACHYON_ID);
            assertThat(untouchedTerritories.getStyledCellByCellId())
                .containsOnlyKeys(SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);
        }

        @Test
        void observesEachSectorsPositionsIntoTheTrackerOfTheMachineryItsWatcherWasBuiltWith() {
            // The installer's own wiring, which nothing else reads back. The watcher is built with
            // the machinery resolved for the sector it is added to, and that argument is
            // invisible either side of the install - so a watcher handed the other sector's
            // machinery would observe these positions into that sector's tracker, and the cut
            // over there would drop a system for a drift it never made.
            var firstSector = installMachineryOnAFirstSector();
            var secondSector = installMachineryOnASecondSector();

            driftTheSharedSystemPastTwoPollsOf(firstSector);

            assertThat(resolveMachineryOf(firstSector)
                    .resolveMovingSystems()
                    .getMovingSystemKeys())
                .containsExactly(SHARED_SYSTEM_KEY);
            assertThat(resolveMachineryOf(secondSector)
                    .resolveMovingSystems()
                    .getMovingSystemKeys())
                .isEmpty();
        }

        @Test
        void leavesTheOtherSectorsHoverParkedWhenACursorReadLandsOnOne() {
            // A hover names a cell by bare system id too, so a shared holder would light a cell on
            // the other sector's map and name that system in its box. Both ends go through the index
            // rather than through the handle the install returned, since what a render pass has is a
            // sector to resolve from.
            var firstSector = installMachineryOnAFirstSector();
            var secondSector = installMachineryOnASecondSector();

            resolveMachineryOf(firstSector).resolveHoverState().publishHover(HOVERED_SHARED_CELL);

            assertThat(resolveMachineryOf(firstSector).resolveHoverState().getHover())
                .isSameAs(HOVERED_SHARED_CELL);
            assertThat(resolveMachineryOf(secondSector).resolveHoverState().getHover())
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
            var firstSector = installMachineryOnAFirstSector();
            var secondSector = installMachineryOnASecondSector();

            var removedMachinery = resolveMachineryOf(firstSector);

            removedMachinery.resolveRefreshBoard().markSystemGroupingStale(SHARED_SYSTEM_ID);
            removedMachinery.resolveHoverState().publishHover(HOVERED_SHARED_CELL);

            SectorMapMachineryIndex.uninstallMachineryFrom(firstSector);

            assertThat(new PoliticalMapCacheDriver(secondSector).rebuild().getStyledCellByCellId())
                .containsOnlyKeys(SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);

            assertThat(removedMachinery.isDisposed())
                .isTrue();
            assertThat(resolveMachineryOf(firstSector))
                .isNotSameAs(removedMachinery);
        }
    }

    @Nested
    class DisposeAllMachinery {

        @Test
        void leavesTheSectorLoadedNextNothingOfThePreviousOnes() {
            // The load discard, which is why nothing has to notice that a sector went away. Every
            // holder the previous sector filled is staged first - a stale mark, a drift, a hover -
            // so a discard that missed one shows up as that sector's state answering for the loaded
            // one, and the drift shows up twice over: a tracker carried across would drop the shared
            // system from the loaded sector's cut for a move the previous sector made.
            var previousSector = installMachineryOnAFirstSector();
            var previousMachinery = resolveMachineryOf(previousSector);

            previousMachinery.resolveRefreshBoard().markSystemGroupingStale(SHARED_SYSTEM_ID);
            previousMachinery.resolveHoverState().publishHover(HOVERED_SHARED_CELL);
            driftTheSharedSystemPastTwoPollsOf(previousSector);

            SectorMapMachineryIndex.disposeAllMachinery();

            var loadedSector = installMachineryOnASecondSector();
            var loadedMachinery = resolveMachineryOf(loadedSector);

            assertThat(new PoliticalMapCacheDriver(loadedSector).rebuild().getStyledCellByCellId())
                .containsOnlyKeys(SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);

            assertThat(loadedMachinery.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .isEmpty();
            assertThat(loadedMachinery.resolveMovingSystems().getMovingSystemKeys())
                .isEmpty();
            assertThat(loadedMachinery.resolveHoverState().getHover())
                .isSameAs(MapHover.NONE);
            assertThat(previousMachinery.isDisposed())
                .isTrue();
        }
    }

    // The two sectors every case pairs, named rather than parameterised at the call so a case reads
    // as "one sector and another" instead of restating which faction holds which system - facts that
    // only have to differ, and differ the same way in every case.
    private static SectorAPI installMachineryOnAFirstSector() {
        return installMachineryOnASectorHeldBy(
            HEGEMONY_ID, SHARED_SYSTEM_ID, FIRST_SECTOR_SYSTEM_ID);
    }

    private static SectorAPI installMachineryOnASecondSector() {
        return installMachineryOnASectorHeldBy(
            TRITACHYON_ID, SHARED_SYSTEM_ID, SECOND_SECTOR_SYSTEM_ID);
    }

    // A sector whose systems are each settled by one faction, with the map machinery installed on it
    // and the political map's own listeners and watcher registered against it - which is what makes
    // this real machinery rather than a pair of hand-built holders.
    //
    // Every system is placed in hyperspace: an unplaced one is skipped before the drawn-set rule is
    // ever asked about it, so it would seed no cell and the cut would have nothing to be wrong about.
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

        SectorMapMachineryIndex.installMachineryOn(sector);
        PoliticalMapInstaller.installAll(sector);

        return sector;
    }

    // Hands the system both sectors have an id for to another faction, by re-listing its economy
    // under a colony that faction holds - the change a resize event announces, staged as the
    // economy would answer it on the next read.
    private static void handTheSharedSystemOf(SectorAPI sector, String factionId) {

        // Both are built out fully before either stubbing opens: each stubs internally, so nesting
        // one inside when(...) would trip Mockito's unfinished-stubbing guard.
        var faction = SectorPoliticsFixtures.buildFaction(factionId);
        var colony = SectorPoliticsFixtures.buildVisibleMarket(faction, HOLDING_COLONY_SIZE);
        var system = SectorPoliticsFixtures.findSystemIn(sector, SHARED_SYSTEM_ID);

        when(sector.getEconomy().getMarkets(system))
            .thenReturn(List.of(colony));

        // Resolvable by id besides, which is how the fills reach the new holder's palette; a faction
        // the sector cannot name paints as nobody.
        when(sector.getFaction(factionId))
            .thenReturn(faction);
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
    private static SectorMapMachinery resolveMachineryOf(SectorAPI sector) {
        return SectorMapMachineryIndex.resolveMachineryFor(sector);
    }

    // One sector's cache, kept across the rebuilds a case drives so the second reads the state the
    // first left. A case holding only the territories could not tell a cache that rebuilt from one
    // that was never asked again, the draw lists being replaced wholesale by a rebuild.
    private static final class PoliticalMapCacheDriver {

        private final PoliticalMapCache cache;

        private PoliticalMapCacheDriver(SectorAPI sector) {
            cache = new PoliticalMapCache(resolveMachineryOf(sector));
        }

        // What the cache last drew, without asking it to draw again - for a case claiming a sector
        // nobody touched still holds what it held.
        private PoliticalMapTerritories readTerritories() {
            return cache.getTerritories();
        }

        private PoliticalMapTerritories rebuild() {

            cache.refresh(FactionsView.INSTANCE, SCREEN);

            return cache.getTerritories();
        }
    }
}
