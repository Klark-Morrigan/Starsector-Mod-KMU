package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.politicalmap.base.UninhabitedOutlinePreference;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what a political-map rebuild is owed, which after the preferences moved into the bake's own
 * sampling is a question about values rather than about counters.
 *
 * <p>A sidebar pick is stored per save rather than as a LunaLib field, so none of them moves the
 * settings revision; each flip instead raises a coarse counter on the refresh board. Read off those
 * counters, "is the map stale" answers yes to any click anywhere - and, once a pick is per screen,
 * no to a screen switch that changes every one of them. Read off the sampled values, it answers the
 * only question worth asking: would this build come out the same.
 *
 * <p>No unit can make the claim. Whether a rebuild happened is visible only as the map being built
 * afresh, so the cache, the geometry cut, the territory build and the bake are all real here and the
 * answer is taken off the identity of the built map: a rebuild replaces it wholesale, and a frame
 * that owes nothing leaves the one already in hand.
 *
 * <p>Two of the five picks are moved here - the spotlight and the uninhabited outline - and that is
 * what the claim needs: the revision is a fold of the whole sampled reading, so a pick that moves it
 * shows that the reading is what the decision is taken on. Which readings count as different is
 * {@link ContentInputsTest}'s, and it separates all five. The other three are left where they sit
 * because moving them costs this suite the game: the name format turns the labels on, which reaches
 * the font loader and the anchor search's live tuning, and the two recede sets are written through
 * sector memory the rebuild fixture's own sector stands in for.
 *
 * <p>What is stubbed is what no test JVM answers - the logger and the live LunaLib reads - plus the
 * spotlight pick a case moves between frames.
 */
final class PoliticalMapRebuildStalenessIntegrationTest {

    private static final String ALPHA_ID = "alpha";
    private static final String BETA_ID = "beta";
    private static final String HEGEMONY_ID = "hegemony";

    // One colony, sized so it holds its system: the map has to build something for a second frame
    // to be able to leave it standing.
    private static final int COLONY_SIZE = 5;

    private PoliticalMapRebuildSeams seams;

    // Not one of the seams the rebuild fixture owns, because only a case about the outline needs it
    // and the read it stands in for is per-save state rather than a LunaLib knob.
    private MockedStatic<UninhabitedOutlinePreference> outlinePreferenceMock;

    private MapLayerInstallation installation;
    private PoliticalMapCache cache;

    @BeforeEach
    void openSeamsAndBuildTheFirstMap() {

        seams = PoliticalMapRebuildSeams.openEverySeamARebuildNeeds();
        outlinePreferenceMock = mockStatic(UninhabitedOutlinePreference.class);

        stageASettledSector();

        cache = new PoliticalMapCache(installation);
        cache.refresh(FactionsView.INSTANCE);
    }

    @AfterEach
    void closeSeams() {
        outlinePreferenceMock.close();
        seams.closeEverySeam();
    }

    @Nested
    class Refresh {

        @Test
        void refreshRebuildsNothingWhileEverySampledPickStandsStill() {
            // The frame the map spends nearly all of its life on. Stated first because every case
            // below is read against it: a rebuild only means something if standing still does not
            // produce one.
            var standingMap = cache.getTerritories();

            cache.refresh(FactionsView.INSTANCE);

            assertThat(cache.getTerritories())
                .isSameAs(standingMap);
        }

        @Test
        void refreshRebuildsNothingWhenTheRefreshCountersBumpWithEveryPickUnmoved() {
            // The counters a pick's flip raises still stand, and the sidebar and the bloc cache still
            // repaint on them - they simply no longer decide whether the map is stale. Folded in, a
            // click that put a pick back where it was would cost a full rebuild, and so would every
            // click made on the other screen's panel.
            var standingMap = cache.getTerritories();
            var board = installation.resolveRefreshBoard();

            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
            board.requestRefresh(MapLayerCommonRefreshSignal.RECEDE_STYLE);
            board.requestRefresh(MapLayerCommonRefreshSignal.MAP_STYLE);
            cache.refresh(FactionsView.INSTANCE);

            assertThat(cache.getTerritories())
                .isSameAs(standingMap);
        }

        @Test
        void refreshRebuildsOnceWhenTheSpotlitBlocMoves() {
            // The spotlight decides who holds a cell rather than only how it is coloured, so a pick
            // is a different map. The second refresh after it must find nothing further owed, or the
            // pick would be rebuilding the map on every frame it stayed made.
            var standingMap = cache.getTerritories();

            seams.resolveFilterSelectionSeam()
                .when(() -> FilterSelection.getSelectedIdOf(any()))
                .thenReturn(HEGEMONY_ID);
            cache.refresh(FactionsView.INSTANCE);

            var rebuiltMap = cache.getTerritories();

            assertThat(rebuiltMap)
                .isNotSameAs(standingMap);

            cache.refresh(FactionsView.INSTANCE);

            assertThat(cache.getTerritories())
                .isSameAs(rebuiltMap);
        }

        @Test
        void refreshRebuildsOnceWhenTheUninhabitedOutlineFlips() {
            // Whether never-settled space strokes an outline is baked into the theme the cells are
            // styled from, so the flip cannot show without a rebuild.
            var standingMap = cache.getTerritories();

            outlinePreferenceMock
                .when(() -> UninhabitedOutlinePreference.isOutlineDrawn(any()))
                .thenReturn(true);
            cache.refresh(FactionsView.INSTANCE);

            var rebuiltMap = cache.getTerritories();

            assertThat(rebuiltMap)
                .isNotSameAs(standingMap);

            cache.refresh(FactionsView.INSTANCE);

            assertThat(cache.getTerritories())
                .isSameAs(rebuiltMap);
        }
    }

    // One settled system and one empty neighbour, both in hyperspace so each seeds a cell: enough
    // for the map to build something a later frame can be asked to leave alone.
    //
    // Installed on, since that is where a rebuild's sector comes from, and staged as the running one
    // besides, which the per-save picks are read through.
    private void stageASettledSector() {

        // Coloured, because a case that draws the names has their shade resolved off the holder's
        // own palette - a palette-less faction leaves the fit with no colour to fade.
        var hegemony = SectorPoliticsFixtures.buildFaction(HEGEMONY_ID, Color.RED);

        SectorAPI sector = SectorPoliticsFixtures.buildSectorWithSystems(
            List.of(hegemony),
            listSystemMarkets(
                ALPHA_ID,
                SectorPoliticsFixtures.buildVisibleMarket(hegemony, COLONY_SIZE)),
            listSystemMarkets(BETA_ID));

        SectorPoliticsFixtures.placeEverySystemInHyperspace(sector);
        installation = new MapLayerInstallation(sector);

        seams.resolveGlobalSeam()
            .when(Global::getSector)
            .thenReturn(sector);
    }
}
