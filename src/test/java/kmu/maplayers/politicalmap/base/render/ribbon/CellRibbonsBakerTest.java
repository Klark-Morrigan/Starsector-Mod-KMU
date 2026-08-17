package kmu.maplayers.politicalmap.base.render.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.SQUARE_CELL;
import static kmu.maplayers.politicalmap.base.render.ribbon.RibbonCellFixtures.SQUARE_CELL_SITE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins what the pass does that no single cell can be asked about: which cells it walks over, and
 * where the rings it walks are kept.
 *
 * <p>Which cells is really the question of which of the territories' two sets the bake hands the
 * source - what the sector holds, or what this layer painted - and the two are equal on an
 * ordinary map, so only a settled system no bloc holds can tell them apart. One case poses that
 * deliberately; every other here would pass either way.
 *
 * <p>The store is the point, and it is the one thing here that cannot be seen one level down. A
 * band's own suite bakes through one source and would pass just as well if every pass kept its
 * rings to itself - the cost of that shows only as a rebuild that got slower, never as a wrong
 * map. So the two cases that matter are stated against the cells' own store: a pass writes the
 * rings it walks into the store the cells hold, and a later pass over those same cells lays its
 * bands along the very same rings rather than walking them again.
 *
 * <p>Identity, not equality, is what says a ring was not walked again: a fresh walk of the same
 * outline produces an equal path, so only the same instance coming back distinguishes a pass that
 * read the store from one that quietly re-derived what it found there.
 *
 * <p>A pass per bake rather than one reused, because that is how a rebuild drives it - each bake
 * samples the settings, the names and the holding afresh - so a case that reused a pass would be
 * posing an arrangement production never has.
 */
final class CellRibbonsBakerTest {

    private static final String BANDED_CELL = "corvus";
    private static final String OTHER_BANDED_CELL = "askonia";

    // A settled system this layer's holding gives to nobody - the unclaimed pirate haven, on the
    // claims layer. Held apart from the two above because it is the one cell whose band depends on
    // which of the pass's two sets the bake gates on.
    private static final String UNHELD_SETTLED_CELL = "hybrasil";

    // A cell the territories were never told about, which is what an incremental re-bake names
    // when a colony change takes the last thing standing in a system.
    private static final String UNDRAWN_CELL = "vanished";

    private static final Color BAND_COLOUR = new Color(140, 160, 220);

    // A plan with a run in it, so a cell that reaches the geometry comes back carrying a band and
    // one the pass never reached is told apart by drawing none.
    private static final RibbonPlan ANY_PLAN =
        new RibbonPlan(List.of(new RibbonSegment(BAND_COLOUR, 1)));

    // The player's knobs stand in for the whole settings class, so a bake reads its sizes from a
    // stub rather than from a LunaLib the test JVM has no game to load.
    private MockedStatic<KmuPoliticalMapSettings> settingsMock;

    // The map-layer knobs stand in for the same reason, a bake opening a pass that samples the dev
    // reveal off them. No case here turns on the reveal, so the stub's own false is the answer.
    private MockedStatic<KmuMapLayerSettings> mapLayerSettingsMock;

    @BeforeEach
    void stubBandSettings() {

        settingsMock = mockStatic(KmuPoliticalMapSettings.class);
        mapLayerSettingsMock = mockStatic(KmuMapLayerSettings.class);

        RibbonSettingsFixtures.stubBandsOnAtSizesThatDraw(settingsMock);
    }

    @AfterEach
    void releaseBandSettings() {
        mapLayerSettingsMock.close();
        settingsMock.close();
    }

    @Nested
    class BakeAllCellRibbons {

        @Test
        void bandsEveryCellTheMapDrew() {

            var territories = buildTwoDrawnCells();

            bakeEveryCellThrough(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(BANDED_CELL, OTHER_BANDED_CELL);
        }

        @Test
        void bandsASettledCellThisLayersHoldingGivesToNobody() {
            // The wiring the whole step turns on, and the one case that can fail if the bake reads
            // the wrong set: the pass gates on what the sector holds, not on what this layer
            // painted, so a haven no bloc holds is offered a band like any settled cell. Handed
            // the holder map instead, this cell would be skipped and every other case here would
            // still pass.
            var territories = buildHeldAndUnheldDrawnCells();

            bakeEveryCellThrough(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(BANDED_CELL, UNHELD_SETTLED_CELL);
        }

        @Test
        void keepsEachWalkedRingInTheStoreTheCellsThemselvesHold() {
            // The wiring this suite exists for. A pass handed a store of its own would bake an
            // identical map and throw every ring away at the end of it, which nothing about the
            // bands can show - so what is asserted is that the rings landed where the cells keep
            // them, which is also where the write that re-shapes a cell will drop them.
            var territories = buildTwoDrawnCells();

            bakeEveryCellThrough(territories);

            assertThat(territories.getRingPathCache().findRingPathOf(BANDED_CELL))
                .isNotNull();
            assertThat(territories.getRingPathCache().findRingPathOf(OTHER_BANDED_CELL))
                .isNotNull();
        }

        @Test
        void laysALaterPassesBandsAlongTheRingsTheFirstPassWalked() {
            // What the store is for. A bake runs whenever a cluster name may have moved, which is
            // every colony flip, while a cell's ring moves only when the cell is cut again - so
            // the second pass over an untouched cell must walk nothing and lay its band along the
            // ring already standing.
            var territories = buildTwoDrawnCells();

            bakeEveryCellThrough(territories);

            var ringAfterFirstPass = territories.getRingPathCache().findRingPathOf(BANDED_CELL);

            // Stated before the identity below rather than left to the case above: two passes that
            // both kept their rings to themselves leave null standing either side of the second
            // bake, and "the same nothing" would read as a ring carried over.
            assertThat(ringAfterFirstPass)
                .isNotNull();

            bakeEveryCellThrough(territories);

            assertThat(territories.getRingPathCache().findRingPathOf(BANDED_CELL))
                .isSameAs(ringAfterFirstPass);
        }

        @Test
        void walksACellsRingAgainOnceItHasBeenReshaped() {
            // The other half of that, and why the store needs no key: the write that records a
            // cell's new shape drops the ring walked inside its old one, so the next pass walks a
            // ring for the shape the cell holds now rather than serving one traced round a cell
            // that is no longer there.
            var territories = buildTwoDrawnCells();

            bakeEveryCellThrough(territories);

            var ringBeforeReshape = territories.getRingPathCache().findRingPathOf(BANDED_CELL);

            territories.putStyledCell(
                BANDED_CELL,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                SQUARE_CELL);

            bakeEveryCellThrough(territories);

            assertThat(territories.getRingPathCache().findRingPathOf(BANDED_CELL))
                .isNotSameAs(ringBeforeReshape);
        }
    }

    @Nested
    class BakeCellRibbonsOf {

        @Test
        void bandsOnlyTheCellsItWasNamed() {
            // The incremental path: a colony change disturbs a handful of cells, and the rest of
            // the map keeps the bands it already carries rather than being re-baked around them.
            var territories = buildTwoDrawnCells();

            bakeThrough(territories).bakeCellRibbonsOf(List.of(BANDED_CELL));

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(BANDED_CELL);
        }

        @Test
        void passesOverACellTheMapNeverDrew() {
            // A caller names the cells something happened to, and one of them losing its last
            // colony is one of the things that can have happened - so a cell with no shape to bake
            // inside is skipped rather than being an error the refresh has to guard against.
            var territories = buildTwoDrawnCells();

            assertThatCode(() -> bakeThrough(territories).bakeCellRibbonsOf(List.of(UNDRAWN_CELL)))
                .doesNotThrowAnyException();

            assertThat(territories.getRibbonByCellId())
                .isEmpty();
        }
    }

    // Two settled, placed, band-sized cells, each drawing as a system of its own - the ordinary
    // arrangement a pass runs over, and the smallest one that can show a bake reaching every cell
    // rather than only the first.
    private static PoliticalMapTerritories buildTwoDrawnCells() {

        return drawCells(
            PoliticalMapTerritoryFixtures.createTerritoriesOwnedBy(Map.of(
                BANDED_CELL, buildHolder(),
                OTHER_BANDED_CELL, buildHolder())),
            BANDED_CELL,
            OTHER_BANDED_CELL);
    }

    // One held cell beside one the holding does not account for, which is the only arrangement
    // that tells the pass's two sets apart: under createTerritoriesOwnedBy they are equal, so a
    // bake gating on the holding would answer every other case here correctly.
    private static PoliticalMapTerritories buildHeldAndUnheldDrawnCells() {

        return drawCells(
            PoliticalMapTerritoryFixtures.createTerritoriesSettledIn(
                Map.of(BANDED_CELL, buildHolder()),
                Set.of(BANDED_CELL, UNHELD_SETTLED_CELL)),
            BANDED_CELL,
            UNHELD_SETTLED_CELL);
    }

    // Records each named cell as drawn, at the one band-sized shape this suite poses, and answers
    // the view with the planner every case counts through.
    private static PoliticalMapTerritories drawCells(
            PoliticalMapTerritories territories,
            String... cellIds) {

        for (var cellId : cellIds) {
            territories.putStyledCell(
                cellId,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                SQUARE_CELL);
        }

        // The mechanic the pass counts by, answered off the view the territories already carry -
        // which is where a bake reads it from, so a stub anywhere else would leave the pass
        // counting through whatever the fixture's mock returns by default.
        when(territories.getView().resolveRibbonPlanner(any()))
            .thenReturn(system -> ANY_PLAN);

        return territories;
    }

    private static void bakeEveryCellThrough(PoliticalMapTerritories territories) {
        bakeThrough(territories).bakeAllCellRibbons();
    }

    // A fresh pass over the given territories, as a rebuild mints one per bake.
    //
    // The geometry and the sector are derived from the cells the territories actually drew rather
    // than named here, so a case adding a cell gets it placed and listed without a second fixture
    // to keep in step - which is what let the settled-but-unheld case below be posed at all.
    private static CellRibbonsBaker bakeThrough(PoliticalMapTerritories territories) {

        var drawnCellIds = territories.getFillPolygonByCellId().keySet();

        return CellRibbonsBaker.createForPass(
            territories,
            buildGeometryPlacing(drawnCellIds),
            buildSectorOf(drawnCellIds),
            // No names placed, since where a name falls is pinned by the builder that lays a band
            // inside one cell rather than by which cells a pass reaches.
            List.of());
    }

    // Each cell drawing as the system of its own name, each system placed at the same site: the
    // cells are told apart by their ids here, never by where they sit.
    private static CellGeometryCache buildGeometryPlacing(Set<String> cellIds) {

        var systemIdByCellId = new LinkedHashMap<String, String>();
        var siteBySystemId = new LinkedHashMap<String, double[]>();

        for (var cellId : cellIds) {
            systemIdByCellId.put(cellId, cellId);
            siteBySystemId.put(cellId, SQUARE_CELL_SITE);
        }
        var geometryCacheMock = mock(CellGeometryCache.class);

        when(geometryCacheMock.getSystemIdByCellId())
            .thenReturn(systemIdByCellId);
        when(geometryCacheMock.getSiteBySystemId())
            .thenReturn(siteBySystemId);

        return geometryCacheMock;
    }

    private static SectorAPI buildSectorOf(Set<String> systemIds) {

        // The systems are built before the stubbing rather than inside it: each is itself a mock,
        // and building one while another stubbing is open reads to Mockito as an unfinished stub.
        var systems = new ArrayList<StarSystemAPI>(systemIds.size());

        for (var systemId : systemIds) {
            systems.add(buildSystem(systemId));
        }
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(systems);

        return sectorMock;
    }

    private static StarSystemAPI buildSystem(String systemId) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(systemId);

        return systemMock;
    }

    // Any holder, which the fixture also counts as making its system inhabited - the set the band
    // pass gates on. Which bloc holds it decides nothing here.
    private static DominantHolder buildHolder() {
        return new DominantHolder("hegemony", Color.WHITE, Color.GRAY);
    }
}
