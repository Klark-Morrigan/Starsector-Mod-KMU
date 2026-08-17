package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterLabelStylingSnapshot;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbon;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbonsBaker;
import kmu.maplayers.politicalmap.base.render.ribbon.RibbonSettingsFixtures;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.TerritoryBuilder;
import kmu.maplayers.politicalmap.dominance.factions.FactionsView;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.KmuPoliticalMapSettings;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the claim the whole incremental path rests on: a map brought up to date by a refresh is
 * the map a full rebuild would have produced.
 *
 * <p>Every fact a cell is drawn from - who holds its system, whether anything stands there, and
 * where a spotlit pick lives - has to be kept in step by a batch that never re-derives more than
 * the systems a colony event marked. What pins each of those separately is what the fold
 * <em>calls</em>; what nothing else pins is what comes out, which is the only thing a player
 * sees. So a fourth fact added to how a cell is drawn and left out of the refresh fails here
 * rather than in play.
 *
 * <p>An integration test because the comparison has to run the real builders. The unit suite
 * ({@link IncrementalPoliticsRefreshTest}) mocks {@code StyledCellBuilder} and
 * {@code FactionTerritoryBuilder} at their static seams - which is what lets it assert that a
 * neighbour was not re-shaped - and that leaves the produced cell unobservable, the seam it would
 * be observed through being the one that was neutralised. Here both legs shape, style and band for
 * real over a hand-built partition and a stubbed sector, so the only thing varying between them is
 * which path produced the map. The settings and theme reads are seamed, being the pass's inputs
 * rather than its subject.
 *
 * <p>The names are switched off for the comparison, and that is a decision rather than a
 * convenience. Cluster-name placements are the one derivation deliberately carried across a
 * rebuild, so a second rebuild handed no standing placements re-fits from scratch and may put a
 * name where the first did not - and a band keeps clear of the names, so the two legs would then
 * lay different bands over identical holdings. Off, the bands are a function of the map alone,
 * which is what this comparison is about; the carried-over fit has its own suite.
 *
 * <p>The uncontested cells are banded here, unlike in most band suites: every case moves a colony
 * in a system one bloc holds alone, so with the uncontested cells bare the band half of the
 * comparison would hold trivially for every one of them.
 */
final class IncrementalPoliticsRefreshIntegrationTest {

    // The three factions the fixture's colonies belong to, so one system can change hands between
    // two of them while a third arrives somewhere nothing stood.
    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PIRATES = "pirates";

    // The four systems, each drawing one square cell abutting the next in a row. Named for what
    // each case does to it: the core is the system nothing ever happens to, and every other one
    // moves in exactly one of the three ways a colony event can move a system.
    private static final String CORE_SYSTEM = "core";
    private static final String BORDER_SYSTEM = "border";
    private static final String DYING_SYSTEM = "dying";
    private static final String FRONTIER_SYSTEM = "frontier";

    private static final List<String> DRAWN_SYSTEM_IDS =
        List.of(CORE_SYSTEM, BORDER_SYSTEM, DYING_SYSTEM, FRONTIER_SYSTEM);

    // The side of each square cell. Wide enough to hold a band clear of its own inset border, so a
    // cell whose colonies moved reports that in its band rather than coming back bare either way.
    private static final double CELL_SIDE = 4000.0;

    // Distinct colony sizes, so no two blocs weigh the same anywhere and no case is settled by the
    // proximity tie-break - which would need orbit geometry this fixture's systems do not carry.
    private static final int CORE_COLONY_SIZE = 5;
    private static final int BORDER_COLONY_SIZE = 4;
    private static final int DYING_COLONY_SIZE = 3;
    private static final int FRONTIER_COLONY_SIZE = 2;

    // The cut these hand-built cells stand at. Any number will do - nothing here recuts them - but
    // both legs must name the same one, or the second would refuse to reuse the first's placements
    // for a reason that has nothing to do with the refresh.
    private static final int GEOMETRY_REVISION = 1;

    // The opacity one category paints at, stepped so each of the four is distinct. That is what
    // makes a category observable in the comparison at all: a cell records its resolved paints and
    // not the category behind them, so two categories sharing an opacity would leave a cell
    // classified into the wrong one reading identically to a correctly classified one.
    private static final double CATEGORY_OPACITY_STEP = 0.2;

    private static final double BORDER_WIDTH = 2.0;
    private static final double SEAM_WIDTH = 1.0;

    @Nested
    class ApplyStalePoliticsUpdates {

        // Closed in reverse on the way out, so a seam opened over another is never left standing
        // when the inner one is already gone.
        private final List<MockedStatic<?>> openStaticSeams = new ArrayList<>();

        // The systems the fixture sector lists, kept by id so a case can re-stub the colonies of
        // the one it moves.
        private final Map<String, StarSystemAPI> systemMocksById = new LinkedHashMap<>();

        // The four cells, hand-built rather than partitioned from the sector: the comparison is
        // about which path produced the map, so the partition has to be the one thing that cannot
        // differ between the legs.
        private final CellGeometryCache cellsMock = buildRowOfAbuttingCells();

        private FactionAPI hegemonyMock;
        private FactionAPI tritachyonMock;
        private FactionAPI piratesMock;

        private EconomyAPI economyMock;
        private SectorAPI sectorMock;

        @BeforeEach
        void openSeamsAndSettleTheSector() {

            var globalMock = openSeam(Global.class);

            // The builders below log through static fields initialised on first touch, which
            // happens inside this block; without this the logger would come back null and the
            // first debug line would fault before any comparison was reached.
            globalMock
                .when(() -> Global.getLogger(any(Class.class)))
                .thenReturn(Logger.getLogger(IncrementalPoliticsRefreshIntegrationTest.class));

            hegemonyMock = SectorPoliticsFixtures.buildFaction(
                HEGEMONY,
                SectorPoliticsFixtures.HEGEMONY_BRIGHT);
            tritachyonMock = SectorPoliticsFixtures.buildFaction(
                TRITACHYON,
                SectorPoliticsFixtures.TRITACHYON_BRIGHT);
            piratesMock = SectorPoliticsFixtures.buildFaction(
                PIRATES,
                SectorPoliticsFixtures.PERSEAN_BRIGHT);

            sectorMock = buildSectorOverTheDrawnSystems();

            // The refresh reads the sector globally, where a rebuild is handed one, so both legs
            // have to be answered with the same object or they would be comparing two sectors.
            globalMock
                .when(Global::getSector)
                .thenReturn(sectorMock);

            // The state both legs start from: two hegemony systems, one tritachyon system, and a
            // frontier nothing stands in.
            placeColoniesIn(CORE_SYSTEM, buildColonyOf(hegemonyMock, CORE_COLONY_SIZE));
            placeColoniesIn(BORDER_SYSTEM, buildColonyOf(hegemonyMock, BORDER_COLONY_SIZE));
            placeColoniesIn(DYING_SYSTEM, buildColonyOf(tritachyonMock, DYING_COLONY_SIZE));
            placeColoniesIn(FRONTIER_SYSTEM);

            // The dev reveal and the map-anchor tuning, both LunaLib-backed: no case turns on
            // either, so the seam's own answers stand for them.
            openSeam(KmuMapLayerSettings.class);

            var settingsMock = openSeam(KmuPoliticalMapSettings.class);
            RibbonSettingsFixtures.stubBandsOnAtSizesThatDraw(settingsMock);

            // Every system here is held by one bloc alone, so without this no cell would band and
            // the bands would agree between the legs by drawing nothing.
            settingsMock
                .when(KmuPoliticalMapSettings::shouldDrawPoliticalMapUncontestedRibbons)
                .thenReturn(true);

            // The weighting rule the fills and the bands are both resolved under. Read live off
            // LunaLib in production, so it is answered here rather than left to the settings seam,
            // whose zeroes would weigh every colony at nothing and leave the sector unheld.
            var rulesMock = openSeam(DominanceRules.class);
            rulesMock
                .when(DominanceRules::readFromLunaSettings)
                .thenReturn(SectorPoliticsFixtures.buildStabilityWeightedRules());

            var renderStyleMock = openSeam(RenderStyleReader.class);
            renderStyleMock
                .when(RenderStyleReader::readRenderStyle)
                .thenReturn(buildThemePaintingEachCategoryApart());

            // The names off, which is what leaves the bands a function of the map alone; the
            // choice is sector-memory state no test JVM has, so it comes off a seam.
            var nameFormatMock = openSeam(NameFormatPreference.class);
            nameFormatMock
                .when(NameFormatPreference::getSelectedNameFormat)
                .thenReturn(FactionNameFormatChoice.NONE);

            // No bloc spotlighted, which the seam's own null answers - the pick is sector-memory
            // state as well.
            openSeam(FilterSelection.class);

            // The stale set is static and shared, so a residue from another suite would read here
            // as a system this one never marked.
            MapLayerRefresh.drainStaleGroupingSystemIds();
        }

        @AfterEach
        void closeSeams() {
            for (var index = openStaticSeams.size() - 1; index >= 0; index--) {
                openStaticSeams.get(index).close();
            }
            openStaticSeams.clear();
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldWhenASystemTakesItsFirstColony() {
            // The system no holder ever accounted for: nobody held it before and somebody holds it
            // now, so the cell goes from empty backdrop to a bloc's territory and owes a band it
            // never had.
            var standingMap = buildMapByFullRebuild();

            placeColoniesIn(FRONTIER_SYSTEM, buildColonyOf(piratesMock, FRONTIER_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, FRONTIER_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldWhenASystemLosesItsLastColony() {
            // The other end of the same move: the holder entry goes, the system leaves the
            // inhabited set, and the cell falls back to the empty backdrop - a different category,
            // a different paint, and no band at all.
            var standingMap = buildMapByFullRebuild();

            placeColoniesIn(DYING_SYSTEM);

            assertRefreshDrawsWhatARebuildWould(standingMap, DYING_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldWhenASystemChangesHands() {
            // The flip, which is the one move that reaches past the marked system's own cell: the
            // edge it shares with the core turns from a same-bloc seam into a national border, and
            // the edge it shares with the dying system turns the other way.
            var standingMap = buildMapByFullRebuild();

            placeColoniesIn(BORDER_SYSTEM, buildColonyOf(tritachyonMock, BORDER_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, BORDER_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldWhenOneBatchMovesAllThree() {
            // All three moves in one drain, which is what a fleet action or an economy tick
            // actually delivers. The flip's re-shape ring reaches a system whose own colonies
            // moved in the same batch, so the two redraws have to compose rather than each
            // arriving at the map the other has already left behind.
            var standingMap = buildMapByFullRebuild();

            placeColoniesIn(BORDER_SYSTEM, buildColonyOf(tritachyonMock, BORDER_COLONY_SIZE));
            placeColoniesIn(DYING_SYSTEM);
            placeColoniesIn(FRONTIER_SYSTEM, buildColonyOf(piratesMock, FRONTIER_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(
                standingMap,
                BORDER_SYSTEM,
                DYING_SYSTEM,
                FRONTIER_SYSTEM);
        }

        // Marks the named systems, folds the batch into the standing map, and compares what that
        // leaves against a full rebuild over the very same sector.
        //
        // The refresh runs first because it is what drains the stale set: a rebuild taken ahead of
        // it would leave the marks standing, and the batch would then be folded into a map that
        // had already accounted for them.
        private void assertRefreshDrawsWhatARebuildWould(
                StandingPoliticalMap standingMap,
                String... markedSystemIds) {

            for (var systemId : markedSystemIds) {
                MapLayerRefresh.markSystemGroupingStale(systemId);
            }
            IncrementalPoliticsRefresh.applyStalePoliticsUpdates(
                standingMap.territories(),
                standingMap.standingAnchors(),
                standingMap.factionLabels(),
                standingMap.cellGeometry());

            // Compared structurally rather than by equality: a cell's draw record and a band's
            // runs are baked geometry, held as float arrays, which compare by identity under
            // equals and so would pass for any two maps whatever.
            //
            // The two occupancy sets are compared as the sets they are, since their iteration
            // order is not a fact about the map: a rebuild's scan fills a hash set, while a
            // refresh folds each marked system into the standing one, so the same membership
            // comes back in two orders. Named rather than taken over the whole comparison,
            // because a band's runs are a draw order and two bands laying the same runs in
            // different orders are two different bands.
            assertThat(readDrawnMap(standingMap.territories()))
                .usingRecursiveComparison()
                .ignoringCollectionOrderInFields("inhabitedSystemIds", "spotlitPresenceSystemIds")
                .isEqualTo(readDrawnMap(buildMapByFullRebuild().territories()));
        }

        // One full rebuild of the whole map over the sector as it currently stands, in the order
        // the plugin's cache drives it: the territories, then the names fitted over them, then the
        // bands laid around those names, then the labels minted from them.
        private StandingPoliticalMap buildMapByFullRebuild() {

            var territories = TerritoryBuilder.buildTerritories(
                cellsMock,
                sectorMock,
                FactionsView.INSTANCE);

            var standingAnchors = new StandingClusterAnchors();
            var factionLabels = new ArrayList<Label>();
            var cellGeometry = new RevisedCellGeometry(cellsMock, GEOMETRY_REVISION);

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                ClusterLabelStylingSnapshot.resolveFrom(territories));

            CellRibbonsBaker
                .createForPass(
                    territories,
                    cellsMock,
                    sectorMock,
                    standingAnchors.getAnchors())
                .bakeAllCellRibbons();

            LabelsBuilder.rebuildLabels(
                factionLabels,
                standingAnchors.getAnchors(),
                NameFormatPreference.getSelectedNameFormat().areNamesDrawn());

            return new StandingPoliticalMap(
                territories,
                standingAnchors,
                factionLabels,
                cellGeometry);
        }

        // Re-stubs one system's economy listing, which is how a case moves a colony: everything
        // the two legs disagree about is downstream of what the economy answers for a system.
        private void placeColoniesIn(String systemId, MarketAPI... marketMocks) {
            when(economyMock.getMarkets(systemMocksById.get(systemId)))
                .thenReturn(List.of(marketMocks));
        }

        // The fixture sector: the four drawn systems, one economy, and the three factions
        // resolvable by id so a holder can be coloured.
        private SectorAPI buildSectorOverTheDrawnSystems() {

            var systemMocks = new ArrayList<StarSystemAPI>(DRAWN_SYSTEM_IDS.size());

            for (var systemId : DRAWN_SYSTEM_IDS) {
                var systemMock = mock(StarSystemAPI.class);

                when(systemMock.getId())
                    .thenReturn(systemId);

                systemMocksById.put(systemId, systemMock);
                systemMocks.add(systemMock);
            }
            economyMock = mock(EconomyAPI.class);

            var builtSectorMock = mock(SectorAPI.class);

            when(builtSectorMock.getStarSystems())
                .thenReturn(systemMocks);
            when(builtSectorMock.getEconomy())
                .thenReturn(economyMock);

            for (var factionMock : List.of(hegemonyMock, tritachyonMock, piratesMock)) {

                when(builtSectorMock.getFaction(factionMock.getId()))
                    .thenReturn(factionMock);
            }
            return builtSectorMock;
        }

        // Opens a static seam and registers it for closing, so a case names what it needs rather
        // than repeating the open-and-remember pair for each.
        private <T> MockedStatic<T> openSeam(Class<T> seamType) {
            MockedStatic<T> staticMock = mockStatic(seamType);
            openStaticSeams.add(staticMock);
            return staticMock;
        }
    }

    // What the two legs are compared on: everything a cell is drawn from, and everything drawn
    // from it. Copied out of the built map rather than read through it, so the values compared are
    // the ones each leg finished with even though the refresh edits its map in place.
    //
    // The occupancy's three facts are here beside the cells because a difference in them is the
    // failure this suite exists to catch, and a difference that has not yet reached a cell is the
    // one a later change to how a cell is styled would turn into a visible one.
    private record DrawnMap(
        Map<String, DominantHolder> holderBySystemId,
        Set<String> inhabitedSystemIds,
        Set<String> spotlitPresenceSystemIds,
        Map<String, StyledCell> styledCellByCellId,
        Map<String, CellRibbon> ribbonByCellId) {
    }

    private static DrawnMap readDrawnMap(PoliticalMapTerritories territories) {
        return new DrawnMap(
            new LinkedHashMap<>(territories.getHolderBySystemId()),
            new LinkedHashSet<>(territories.getInhabitedSystemIds()),
            new LinkedHashSet<>(territories.getSpotlitPresenceSystemIds()),
            new LinkedHashMap<>(territories.getStyledCellByCellId()),
            new LinkedHashMap<>(territories.getRibbonByCellId()));
    }

    // A visible colony of the given size, the only kind of market this fixture stands up: what the
    // suite varies is which system holds one and whose it is, never what sort of market it is.
    private static MarketAPI buildColonyOf(FactionAPI factionMock, int size) {
        return SectorPoliticsFixtures.buildVisibleMarket(factionMock, size);
    }

    // A theme whose four categories each paint at their own opacity, over the inert global tier.
    // Every element draws, so a cell records a fill, an outline and a seam rather than dropping
    // the ones a hidden paint would let it skip.
    private static RenderStyle buildThemePaintingEachCategoryApart() {

        var categoryStyles = new LinkedHashMap<MapStyleCategory, CategoryStyle>();
        var categories = PoliticalMapCategory.values();

        for (var index = 0; index < categories.length; index++) {

            var opacity = CATEGORY_OPACITY_STEP * (index + 1);

            categoryStyles.put(categories[index], new CategoryStyle(
                new ElementStyle(FactionPaletteSlot.PRIMARY, opacity),
                new ElementStyle(FactionPaletteSlot.PRIMARY, opacity),
                BORDER_WIDTH,
                new ElementStyle(FactionPaletteSlot.SECONDARY, opacity),
                SEAM_WIDTH));
        }
        return new RenderStyle(ThemeFixtures.createInertGlobalStyle(), categoryStyles);
    }

    // The partition every case is posed over: four square cells in a row, each abutting the next,
    // each drawing as its own star. Mocked rather than cut from the sector because only its three
    // lookups are read, and a real partition would make each case depend on the Voronoi build as
    // well as on the two paths under comparison.
    private static CellGeometryCache buildRowOfAbuttingCells() {

        var cellEdgesByCellId = new LinkedHashMap<String, List<CellEdge>>();
        var systemIdByCellId = new LinkedHashMap<String, String>();
        var siteBySystemId = new LinkedHashMap<String, double[]>();

        for (var index = 0; index < DRAWN_SYSTEM_IDS.size(); index++) {

            var systemId = DRAWN_SYSTEM_IDS.get(index);
            var leftEdgeX = index * CELL_SIDE;

            cellEdgesByCellId.put(systemId, buildSquareCellBetween(
                leftEdgeX,
                index == 0 ? null : DRAWN_SYSTEM_IDS.get(index - 1),
                index == DRAWN_SYSTEM_IDS.size() - 1
                    ? null
                    : DRAWN_SYSTEM_IDS.get(index + 1)));

            systemIdByCellId.put(systemId, systemId);
            siteBySystemId.put(
                systemId,
                new double[] {leftEdgeX + CELL_SIDE / 2, CELL_SIDE / 2});
        }
        var geometryCacheMock = mock(CellGeometryCache.class);

        when(geometryCacheMock.getCellEdgesByCellId())
            .thenReturn(cellEdgesByCellId);
        when(geometryCacheMock.getSystemIdByCellId())
            .thenReturn(systemIdByCellId);
        when(geometryCacheMock.getSiteBySystemId())
            .thenReturn(siteBySystemId);

        return geometryCacheMock;
    }

    // One closed square cell: its left and right edges face its neighbours in the row (or open
    // space at the ends), its top and bottom face open space throughout. Real coordinates, since
    // both paths inset these edges for real and lay a band inside what that leaves.
    private static List<CellEdge> buildSquareCellBetween(
            double leftEdgeX,
            String leftSystemId,
            String rightSystemId) {

        var rightEdgeX = leftEdgeX + CELL_SIDE;
        var frontier = new EdgeTarget.NoSystem("frontier");

        return List.of(
            new CellEdge(leftEdgeX, 0, rightEdgeX, 0, frontier),
            new CellEdge(rightEdgeX, 0, rightEdgeX, CELL_SIDE, resolveEdgeTarget(rightSystemId)),
            new CellEdge(rightEdgeX, CELL_SIDE, leftEdgeX, CELL_SIDE, frontier),
            new CellEdge(leftEdgeX, CELL_SIDE, leftEdgeX, 0, resolveEdgeTarget(leftSystemId)));
    }

    // What lies across one edge: the neighbouring system, or open space at the ends of the row.
    private static EdgeTarget resolveEdgeTarget(String neighbourSystemId) {
        return neighbourSystemId == null
            ? new EdgeTarget.NoSystem("frontier")
            : new EdgeTarget.AcrossSystem(neighbourSystemId);
    }
}
