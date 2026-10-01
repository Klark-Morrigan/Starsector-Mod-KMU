package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.installed.LazyFontCache;
import kmlib.starsector.ui.label.BandFitSpecification;
import kmlib.starsector.ui.label.NameFitSpecification;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellSeedRule;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.LabelFonts;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.labels.anchor.specifications.AnchorDiagnostics;
import kmu.maplayers.base.labels.anchor.specifications.AnchorSearch;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.base.labels.anchor.specifications.LeanScoring;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.clusters.ClusterBorderTrace;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.base.visibility.systems.MapSectorFixture;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwnerResolve;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.preferences.NameFormatPreference;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferences;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferencesFixtures;
import kmu.maplayers.ownermap.render.OwnerMapCache;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuilder;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.labels.ClusterAnchorsBuilder;
import kmu.maplayers.ownermap.render.labels.ClusterLabelStylingSnapshot;
import kmu.maplayers.ownermap.render.style.FactionPaletteSlot;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.render.style.RenderStyleReader;
import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuMapLabelSettings;
import kmu.settings.KmuMapVisibilitySettings;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;
import kmu.settings.KmuOwnerMapGeometrySettings;
import kmu.settings.KmuOwnerMapRibbonSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.lazylib.ui.LazyFont;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeFacing;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A layer the owner-map tier has never heard of, keyed by a two-valued rule with nothing to do with
 * factions - whether a system carries a comm relay - built and labelled through the tier over a
 * sector that answers no faction at all.
 *
 * <p>What it pins is what the owner reading and the declared categories exist for: everything the
 * tier paints for an owner - its fill, its border, its seams, its name and the category each draws
 * in - and everything it paints for a cell nobody owns comes off the layer's own answers. The
 * layer's categories are its own set, not the holder layers' four, and the sector is never asked for
 * a faction, so a tier that still worked any of it out from factions would fail here rather than
 * quietly colour this layer by whatever faction an owner ID happened to name.
 *
 * <p>The build runs for real over hand-built 2000-unit square cells; what is stood in for is only
 * what no JVM outside the game answers - the settings behind the border trace, the global theme
 * tier and the label tuning, and the label font.
 *
 * <p>The rebuild cases go one stage further out: the cache cuts its own cells over a staged sector
 * and asks the layer's own source for its owners, so what they pin is that the core reads no colony
 * for a layer that never asked it to, and cuts cells by the layer's seed rule rather than another
 * layer's.
 */
final class NonFactionOwnerLayerIntegrationTests {

    // The two owner values, and each one's shades - the relay-bearing systems bright, the rest in
    // a second pair, both unlike anything a faction would carry.
    private static final String RELAY = "carries_relay";
    private static final String NO_RELAY = "lacks_relay";
    private static final OwnerPalette RELAY_PALETTE = new OwnerPalette(Color.CYAN, Color.BLUE);
    private static final OwnerPalette NO_RELAY_PALETTE = new OwnerPalette(Color.ORANGE, Color.RED);

    // The shade the layer paints a cell nobody owns in.
    private static final Color UNOWNED_COLOUR = new Color(90, 90, 90);

    // Two relay systems sharing an edge, a system without one, a settled system the rule gives
    // nobody, and an empty one - each a 2000-unit square, well apart except the relay pair.
    private static final String RELAY_WEST = "relay_west";
    private static final String RELAY_EAST = "relay_east";
    private static final String DARK_SYSTEM = "dark";
    private static final String SETTLED_SYSTEM = "settled";
    private static final String EMPTY_SYSTEM = "empty";

    private static final double WELD_TOLERANCE = 1.0;
    private static final double MITER_LIMIT = 4.0;

    // The cells' seed knobs for a rebuild cutting its own cells, wide enough that each cell holds
    // clear of its own inset border.
    private static final int CELL_BOUND_SEGMENTS = 16;
    private static final double CELL_RADIUS = 4000.0;

    private static final LabelAnchorSpecification ANCHOR_SPECIFICATION =
        new LabelAnchorSpecification(
            new AnchorSearch(new ClusterBorderTrace(WELD_TOLERANCE, MITER_LIMIT), 3, 1),
            new LeanScoring(0.0, 2.0, 0.0),
            new AnchorDiagnostics(false, false),
            new BandFitSpecification(0.0, 0.0, 0.01),
            new NameFitSpecification(0.0, 2000.0, 1, 1.0));

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final CellGeometryCache geometryCacheMock = mock(CellGeometryCache.class);
    private final OwnerPaintedView viewMock = mock(OwnerPaintedView.class);

    private MockedStatic<KmuMapLabelSettings> labelSettingsMock;
    private MockedStatic<RenderStyleReader> styleReaderMock;
    private MockedStatic<KmuOwnerMapDiagnosticsSettings> diagnosticsSettingsMock;
    private MockedStatic<LabelAnchorSpecification> specificationMock;
    private MockedStatic<LazyFontCache> fontsMock;

    /** The categories a relay layer divides its cells into - none of them a holder category. */
    private enum RelayCategory implements MapStyleCategory {
        CARRIES_RELAY,
        LACKS_RELAY,
        SETTLED,
        EMPTY
    }

    @BeforeEach
    void openTheSettingsAndFontSeams() {

        labelSettingsMock = mockStatic(KmuMapLabelSettings.class);
        labelSettingsMock
            .when(KmuMapLabelSettings::getMapBorderWeldTolerance)
            .thenReturn(WELD_TOLERANCE);
        labelSettingsMock
            .when(KmuMapLabelSettings::getMapBorderMiterLimit)
            .thenReturn(MITER_LIMIT);

        // The theme reader runs for real, so the categories it reads are the layer's own; only the
        // global tier behind it reads settings no JVM outside the game answers.
        styleReaderMock = mockStatic(RenderStyleReader.class, CALLS_REAL_METHODS);
        styleReaderMock
            .when(RenderStyleReader::readGlobalStyle)
            .thenReturn(ThemeFixtures.createInertGlobalStyle());

        diagnosticsSettingsMock = mockStatic(KmuOwnerMapDiagnosticsSettings.class);
        specificationMock = mockStatic(LabelAnchorSpecification.class);
        specificationMock
            .when(LabelAnchorSpecification::readFromLunaSettings)
            .thenReturn(ANCHOR_SPECIFICATION);

        // A font measuring every character at half its size, so a name is fitted against real
        // widths and the fitted placement carries the name it was fitted for.
        var fontMock = mock(LazyFont.class);
        when(fontMock.calcWidth(anyString(), anyFloat()))
            .thenAnswer(invocation -> invocation.<String>getArgument(0).length()
                * invocation.<Float>getArgument(1) * 0.5f);
        fontsMock = mockStatic(LazyFontCache.class);
        fontsMock
            .when(() -> LazyFontCache.loadByFace(any()))
            .thenReturn(fontMock);

        when(viewMock.resolveCategories())
            .thenReturn(new RelayCategoriesFake());

        var edges = buildKeyedValues(listOrderedEdges());
        when(geometryCacheMock.getCellEdgesByCellKey())
            .thenReturn(edges);
        when(geometryCacheMock.getSystemKeyByCellKey())
            .thenReturn(listIdentityCellsFor(edges.keySet()));
        when(geometryCacheMock.getSiteBySystemKey())
            .thenReturn(buildKeyedValues(Map.of(
                RELAY_WEST, new double[] {1000, 1000},
                RELAY_EAST, new double[] {3000, 1000},
                DARK_SYSTEM, new double[] {21000, 1000})));
    }

    @AfterEach
    void closeTheSettingsAndFontSeams() {
        fontsMock.close();
        specificationMock.close();
        diagnosticsSettingsMock.close();
        styleReaderMock.close();
        labelSettingsMock.close();
    }

    @Nested
    class BuildClusters {

        @Test
        void fillsAndBordersEachOwnerInItsOwnShadesByItsOwnCategory() {
            // The relay category fills from the primary slot and the other from the secondary, so a
            // fill in the right owner's right slot says both the shades and the category were the
            // layer's.
            var clusters = buildRelayMap();

            var relayGroup = clusters.getStyledClusterGroupByOwnerId().get(RELAY);
            var darkGroup = clusters.getStyledClusterGroupByOwnerId().get(NO_RELAY);

            assertThat(relayGroup.fill().colour())
                .isEqualTo(Color.CYAN);
            assertThat(relayGroup.border().colour())
                .isEqualTo(Color.BLUE);
            assertThat(darkGroup.fill().colour())
                .isEqualTo(Color.RED);
        }

        @Test
        void fusesTheRelayPairIntoOneBodyUnderItsOwnerKey() {

            var clusters = buildRelayMap();

            assertThat(clusters.getStyledClusterGroupByOwnerId().get(RELAY).clusters())
                .hasSize(1);
        }

        @Test
        void strokesTheSeamBetweenTheRelayPairInTheRelayShade() {
            // A fused cell's seam paints from the same reading as its body's fill and border.
            var clusters = buildRelayMap();

            var seam = (StyledCell.FusedCell) clusters.getStyledCellByCellKey()
                .get(buildCellKey(RELAY_WEST));

            assertThat(seam.seamPaint().colour())
                .isEqualTo(Color.CYAN);
        }

        @Test
        void paintsAnUnownedCellInTheLayersUnownedShadeByItsOwnCategories() {
            // The settled category fills and the empty one does not, so the two unowned cells prove
            // which of the layer's categories each fell to - and both paint the layer's own shade.
            var clusters = buildRelayMap();

            var settledCell = (StyledCell.LoneCell) clusters.getStyledCellByCellKey()
                .get(buildCellKey(SETTLED_SYSTEM));
            var emptyCell = (StyledCell.LoneCell) clusters.getStyledCellByCellKey()
                .get(buildCellKey(EMPTY_SYSTEM));

            assertThat(settledCell.fillPaint().colour())
                .isEqualTo(UNOWNED_COLOUR);
            assertThat(emptyCell.fillPaint().colour())
                .isNull();
            assertThat(emptyCell.outlinePaint().colour())
                .isEqualTo(UNOWNED_COLOUR);
        }

        @Test
        void neverAsksTheSectorForAFaction() {

            buildRelayMap();

            verify(sectorMock, never()).getFaction(any());
        }
    }

    @Nested
    class RebuildClusterAnchors {

        @Test
        void namesEachOwnerByTheLayersNameInItsCategorysNameShade() {
            // The relay category's names draw in the primary slot and the other's in the secondary,
            // so each placement's shade names its owner's category as well as its shades.
            var anchors = labelRelayMap();

            assertThat(anchors)
                .extracting(ClusterAnchor::nameLines, ClusterAnchor::colour)
                .containsExactly(
                    tuple(List.of("Relay Net"), Color.CYAN),
                    tuple(List.of("Dark Space"), Color.RED));
        }

        @Test
        void neverAsksTheSectorForAFaction() {

            labelRelayMap();

            verify(sectorMock, never()).getFaction(any());
        }
    }

    @Nested
    class RefreshDrawLists {

        // A full rebuild runs the cut, the source, the build and the label pass for real, so what
        // it reaches that no test JVM answers is stood in for here, beside what the suite opens.
        private final StaticSeams seams = new StaticSeams();

        private final OwnerPaintedView relayViewMock = mock(OwnerPaintedView.class);
        private final RelaySourceFake relaySourceFake = new RelaySourceFake();

        // Three reachable systems far enough apart that each is cut a cell of its own: two carrying
        // a relay, one dark.
        private SectorAPI relaySectorMock;

        @BeforeEach
        void openTheRebuildSeams() {

            seams.holdSeam(StubbedGlobalLogger.openGlobalAnsweringLoggers());
            seams.openSeam(KmuLunaSettings.class);
            seams.openSeam(KmuMapVisibilitySettings.class);
            seams.openSeam(FilterSelection.class);
            seams.openSeam(LabelFonts.class);

            // Opened unanswered, so the bands are off: no case here is about them.
            seams.openSeam(KmuOwnerMapRibbonSettings.class);

            var geometrySettingsMock = seams.openSeam(KmuOwnerMapGeometrySettings.class);
            geometrySettingsMock
                .when(KmuOwnerMapGeometrySettings::getOwnerMapCellBoundSegments)
                .thenReturn(CELL_BOUND_SEGMENTS);
            geometrySettingsMock
                .when(KmuOwnerMapGeometrySettings::getOwnerMapCellRadius)
                .thenReturn(CELL_RADIUS);

            seams.openSeam(MapVisibilityRules.class)
                .when(MapVisibilityRules::readFromLunaSettings)
                .thenReturn(MapVisibilityRules.BASE);

            relaySectorMock = MapSectorFixture.buildStarAnchoredSectorOf(
                buildReachableSystem(RELAY_WEST, 0f),
                buildReachableSystem(DARK_SYSTEM, 30000f),
                buildReachableSystem(RELAY_EAST, 60000f));

            when(relayViewMock.getId())
                .thenReturn("relays");
            when(relayViewMock.resolveViewRecedeAdjustment(any()))
                .thenReturn(ElementStyleAdjustment.NONE);
            when(relayViewMock.resolveCategories())
                .thenReturn(new RelayCategoriesFake());
            when(relayViewMock.resolveViewReading(any()))
                .thenReturn(new ViewReading(relayViewMock, new RelayReadingFake(), relaySourceFake));
        }

        @AfterEach
        void closeTheRebuildSeams() {
            seams.closeEverySeam();
        }

        @Test
        void paintsEachSystemByTheLayersOwnRuleWithNoHolderPassOpened() {
            // The core asks the layer which systems each owner holds and reads no colony itself, so
            // a layer keyed by a rule with nothing to do with colonies rebuilds without a holder
            // pass ever being opened - one opened anyway would be the core reading colonies for a
            // layer that never asked it to.
            var cache = buildCacheSeeding(CellSeedRule.SEED_DRAWN_SYSTEMS);

            try (var passConstructionMock = mockConstruction(HolderPass.class)) {

                cache.refreshDrawLists(relayViewMock, ScreenMemoryScopes.createStandInScreen());

                assertThat(passConstructionMock.constructed())
                    .isEmpty();
            }

            assertThat(cache.getClusters().getOccupancy().getHolderBySystemKey())
                .extractingFromEntries(entry -> entry.getKey().systemId(), entry -> entry.getValue().ownerId())
                .containsExactlyInAnyOrder(
                    tuple(RELAY_WEST, RELAY),
                    tuple(DARK_SYSTEM, NO_RELAY),
                    tuple(RELAY_EAST, RELAY));
        }

        @Test
        void handsTheSourceTheWalkOfItsOwnSector() {
            // The walk the cut was taken from, so the owners are resolved for the sector the cells
            // stand on rather than whichever is running.
            buildCacheSeeding(CellSeedRule.SEED_DRAWN_SYSTEMS)
                .refreshDrawLists(relayViewMock, ScreenMemoryScopes.createStandInScreen());

            assertThat(relaySourceFake.walks)
                .singleElement()
                .extracting(SectorWalk::sector)
                .isSameAs(relaySectorMock);
        }

        @Test
        void cutsCellsOnlyForTheSystemsTheLayersSeedRuleAdmits() {
            // The layer states which systems seed its cells, so a rule narrower than the drawn set
            // leaves a drawn system with no cell - which a cut inheriting another layer's rule
            // would have given one.
            var cache = buildCacheSeeding((pass, system) -> !DARK_SYSTEM.equals(system.getId()));

            cache.refreshDrawLists(relayViewMock, ScreenMemoryScopes.createStandInScreen());

            assertThat(cache.getClusters().getStyledCellByCellKey().keySet())
                .extracting(SystemKey::systemId)
                .containsExactlyInAnyOrder(RELAY_WEST, RELAY_EAST);
        }

        // A cache over this sector's own machinery, drawing no names and seeding under the given
        // rule.
        private OwnerMapCache buildCacheSeeding(CellSeedRule seedRule) {

            var nameFormatMock = mock(NameFormatPreference.class);

            when(nameFormatMock.getSelectedNameFormat(any()))
                .thenReturn(FactionNameFormatChoice.NONE);

            var preferences = OwnerMapBodyPreferencesFixtures.createUnderTestKeys();

            return new OwnerMapCache(
                new SectorMapMachinery(relaySectorMock),
                new OwnerMapBodyPreferences(
                    nameFormatMock,
                    preferences.uninhabitedOutline(),
                    preferences.filterRecede()),
                seedRule);
        }

        // A system reachable from hyperspace, which is what puts it on the drawn set.
        private static StarSystemAPI buildReachableSystem(String systemId, float x) {

            var systemMock = StarSystemFixture.buildSystemAt(systemId, x, 0f);

            when(systemMock.getJumpPoints())
                .thenReturn(List.of(mock(SectorEntityToken.class)));

            return systemMock;
        }
    }

    // The map the relay layer's rule resolves: both relay systems to one owner, the dark system to
    // the other, the settled system to nobody though something stands there.
    private OwnerMapClusters buildRelayMap() {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();
        ownerBySystemKey.put(buildCellKey(RELAY_WEST), new SystemOwner(RELAY, RELAY_PALETTE));
        ownerBySystemKey.put(buildCellKey(RELAY_EAST), new SystemOwner(RELAY, RELAY_PALETTE));
        ownerBySystemKey.put(buildCellKey(DARK_SYSTEM), new SystemOwner(NO_RELAY, NO_RELAY_PALETTE));

        var owners = new ResolvedOwners(
            ownerBySystemKey,
            Set.of(),
            Set.of(),
            Set.of(
                buildCellKey(RELAY_WEST),
                buildCellKey(RELAY_EAST),
                buildCellKey(DARK_SYSTEM),
                buildCellKey(SETTLED_SYSTEM)),
            Set.of());

        return OwnerMapBuilder.buildClusters(
            geometryCacheMock,
            new ViewReading(viewMock, new RelayReadingFake(), OwnerSourceFake.createAnswering(owners)),
            ContentInputsFixtures.createInputsSpellingNames(FactionNameFormatChoice.FULL),
            owners);
    }

    // That map's names, fitted the way a production rebuild fits them.
    private List<ClusterAnchor> labelRelayMap() {

        var standingAnchors = new StandingClusterAnchors();

        ClusterAnchorsBuilder.rebuildClusterAnchors(
            standingAnchors,
            new RevisedCellGeometry(geometryCacheMock, 1),
            ClusterLabelStylingSnapshot.resolveFrom(buildRelayMap(), StarsectorFont.VANILLA_INSIGNIA_42));

        return standingAnchors.getAnchors();
    }

    // The five cells in first-seen order, the relay pair meeting along x = 2000 and every other
    // cell well clear of the rest.
    private static Map<String, List<CellEdge>> listOrderedEdges() {

        var edgesByCellId = new LinkedHashMap<String, List<CellEdge>>();

        edgesByCellId.put(RELAY_WEST, List.of(
            buildEdgeFacing(0, 0, 2000, 0, null),
            buildEdgeFacing(2000, 0, 2000, 2000, RELAY_EAST),
            buildEdgeFacing(2000, 2000, 0, 2000, null),
            buildEdgeFacing(0, 2000, 0, 0, null)));
        edgesByCellId.put(RELAY_EAST, List.of(
            buildEdgeFacing(2000, 0, 4000, 0, null),
            buildEdgeFacing(4000, 0, 4000, 2000, null),
            buildEdgeFacing(4000, 2000, 2000, 2000, null),
            buildEdgeFacing(2000, 2000, 2000, 0, RELAY_WEST)));
        edgesByCellId.put(DARK_SYSTEM, buildSquareAt(20000));
        edgesByCellId.put(SETTLED_SYSTEM, buildSquareAt(40000));
        edgesByCellId.put(EMPTY_SYSTEM, buildSquareAt(60000));

        return edgesByCellId;
    }

    // A lone 2000-unit square whose left edge stands at the given x, bordering nothing.
    private static List<CellEdge> buildSquareAt(double left) {
        return List.of(
            buildEdgeFacing(left, 0, left + 2000, 0, null),
            buildEdgeFacing(left + 2000, 0, left + 2000, 2000, null),
            buildEdgeFacing(left + 2000, 2000, left, 2000, null),
            buildEdgeFacing(left, 2000, left, 0, null));
    }

    // Every cell drawing as its own star.
    private static Map<SystemKey, SystemKey> listIdentityCellsFor(Iterable<SystemKey> cellKeys) {

        var systemKeyByCellKey = new LinkedHashMap<SystemKey, SystemKey>();
        for (var cellKey : cellKeys) {
            systemKeyByCellKey.put(cellKey, cellKey);
        }
        return systemKeyByCellKey;
    }

    /**
     * The relay layer's owner source: a system carries a relay or it does not, read off its ID alone
     * over the walk the tier hands it - no colony, no faction, no grouping.
     */
    private static final class RelaySourceFake implements OwnerSource {

        private final List<SectorWalk> walks = new ArrayList<>();

        @Override
        public ResolvedOwners resolveOwners(SectorWalk walk, String spotlitOwnerId) {

            walks.add(walk);

            var ownerBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();

            for (var systemKey : walk.sectorIndex().readSystemsByKey().keySet()) {
                ownerBySystemKey.put(
                    systemKey,
                    systemKey.systemId().startsWith("relay")
                        ? new SystemOwner(RELAY, RELAY_PALETTE)
                        : new SystemOwner(NO_RELAY, NO_RELAY_PALETTE));
            }
            return new ResolvedOwners(
                ownerBySystemKey,
                Set.of(),
                Set.of(),
                ownerBySystemKey.keySet(),
                Set.of());
        }

        @Override
        public SystemOwnerResolve openSystemResolve(SectorWalk walk, String spotlitOwnerId) {
            throw new UnsupportedOperationException("no case here re-derives a system");
        }

        @Override
        public SystemRibbonPlanner resolveRibbonPlanner(SectorWalk walk, RibbonPlanRules rules) {
            return system -> RibbonPlan.NONE;
        }
    }

    /** The relay layer's answers about its two owners, none of them read off a faction. */
    private static final class RelayReadingFake implements OwnerReading {

        @Override
        public OwnerPalette resolvePalette(String ownerId) {
            return RELAY.equals(ownerId) ? RELAY_PALETTE : NO_RELAY_PALETTE;
        }

        @Override
        public String resolveName(String ownerId, FactionNameFormatChoice nameFormat) {
            return RELAY.equals(ownerId) ? "Relay Net" : "Dark Space";
        }

        @Override
        public String resolveCrestPath(String ownerId) {
            return null;
        }

        @Override
        public ElementStyleAdjustment resolveStyleAdjustment(
                String ownerId,
                ContentInputs contentInputs) {
            return ElementStyleAdjustment.NONE;
        }

        @Override
        public MapStyleCategory resolveCategory(String ownerId, ElementStyleAdjustment adjustment) {
            return RELAY.equals(ownerId) ? RelayCategory.CARRIES_RELAY : RelayCategory.LACKS_RELAY;
        }

        @Override
        public Color resolveUnownedColour() {
            return UNOWNED_COLOUR;
        }

        @Override
        public OwnerPalette resolveRecedePalette() {
            return new OwnerPalette(Color.DARK_GRAY, Color.BLACK);
        }
    }

    /**
     * The relay layer's own categories: the relay-bearing systems fill and name from the primary
     * slot and the rest from the secondary, so a colour read back names the category it came from;
     * a settled unowned cell fills and an empty one only outlines.
     */
    private static final class RelayCategoriesFake implements OwnerCategories {

        @Override
        public Map<MapStyleCategory, CategoryStyle> readCategoryStyles(ContentInputs contentInputs) {
            return Map.of(
                RelayCategory.CARRIES_RELAY, buildOwnedStyle(FactionPaletteSlot.PRIMARY),
                RelayCategory.LACKS_RELAY, buildOwnedStyle(FactionPaletteSlot.SECONDARY),
                RelayCategory.SETTLED, buildUnownedStyle(true),
                RelayCategory.EMPTY, buildUnownedStyle(false));
        }

        @Override
        public Map<MapStyleCategory, ElementStyle> readNameStyles() {
            return Map.of(
                RelayCategory.CARRIES_RELAY, new ElementStyle(FactionPaletteSlot.PRIMARY, 1.0),
                RelayCategory.LACKS_RELAY, new ElementStyle(FactionPaletteSlot.SECONDARY, 1.0));
        }

        @Override
        public MapStyleCategory resolveFullStrengthCategory() {
            return RelayCategory.CARRIES_RELAY;
        }

        @Override
        public MapStyleCategory resolveUnownedCategory(boolean isSettled) {
            return isSettled ? RelayCategory.SETTLED : RelayCategory.EMPTY;
        }

        // A fill from the given slot, a border from the secondary and seams from the primary, all
        // at full weight.
        private static CategoryStyle buildOwnedStyle(FactionPaletteSlot fillSlot) {
            return new CategoryStyle(
                new ElementStyle(fillSlot, 1.0),
                new ElementStyle(FactionPaletteSlot.SECONDARY, 1.0),
                1.0,
                new ElementStyle(FactionPaletteSlot.PRIMARY, 1.0),
                1.0);
        }

        // An outline always, and a fill only where something stands.
        private static CategoryStyle buildUnownedStyle(boolean isFilled) {
            return new CategoryStyle(
                isFilled ? new ElementStyle(FactionPaletteSlot.PRIMARY, 1.0) : ElementStyle.NOT_DRAWN,
                new ElementStyle(FactionPaletteSlot.PRIMARY, 1.0),
                1.0,
                ElementStyle.NOT_DRAWN,
                0);
        }
    }
}
