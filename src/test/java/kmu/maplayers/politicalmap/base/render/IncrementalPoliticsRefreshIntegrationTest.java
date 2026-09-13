package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
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
import kmu.settings.KmuMapVisibilitySettings;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;
import kmu.settings.KmuPoliticalMapGeometrySettings;
import kmu.settings.KmuPoliticalMapRibbonSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeFacing;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
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
 * ({@link IncrementalPoliticsRefreshTest}) mocks {@code PaintedCellBuilder} and
 * {@code FactionTerritoryBuilder} at their static seams - which is what lets it assert that a
 * neighbour was not re-shaped - and that leaves the produced cell unobservable, the seam it would
 * be observed through being the one that was neutralised. Here both legs shape, style, trace and
 * band for real over a hand-built partition and a stubbed sector, so the only thing varying
 * between them is which path produced the map. The settings and theme reads are seamed, being the
 * pass's inputs rather than its subject.
 *
 * <p>The names are switched off for the comparison, and that is a decision rather than a
 * convenience. Cluster-name placements are the one derivation deliberately carried across a
 * rebuild, so a second rebuild handed no standing placements re-fits from scratch and may put a
 * name where the first did not - and a band keeps clear of the names, so the two legs would then
 * lay different bands over identical holdings. Off, the bands are a function of the map alone,
 * which is what this comparison is about; the carried-over fit has its own suite.
 *
 * <p>The fixture starts with one bloc per system, so most of these cases compare two maps whose
 * bands are the uncontested kind - the footprint each cell's lone holder leaves. That is the
 * common shape on a live map too, and it is the one a refresh has most opportunity to get wrong,
 * a cell restyled without its count being re-read looking exactly like one that was.
 */
final class IncrementalPoliticsRefreshIntegrationTest {

    // The three factions the fixture's colonies belong to, so one system can change hands between
    // two of them while a third arrives somewhere nothing stood.
    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PIRATES = "pirates";

    // The four systems, each drawing one square cell abutting the next in a row. Named for what
    // each case does to it: the core is the system no holder ever moves in, and every other one
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

    // A rival arriving beside the core's own colony: small enough that the core does not change
    // hands, so the case is about the band alone.
    private static final int RIVAL_COLONY_SIZE = 1;

    // The core's colony one size up, for the case where a resize moves nothing at all.
    private static final int GROWN_CORE_COLONY_SIZE = CORE_COLONY_SIZE + 1;

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

        // The classes this arrangement stands in for, closed on the way out.
        private final StaticSeams seams = new StaticSeams();

        // The four cells, hand-built rather than partitioned from the sector: the comparison is
        // about which path produced the map, so the partition has to be the one thing that cannot
        // differ between the legs.
        private final CellGeometryCache cellsMock = buildRowOfAbuttingCells();

        // What the caller drained off its own machinery's board this frame, in the order it was
        // marked - the refresh is handed the batch rather than draining one of its own.
        private final Set<String> staleSystemIds = new LinkedHashSet<>();

        private FactionAPI hegemonyMock;
        private FactionAPI tritachyonMock;
        private FactionAPI piratesMock;

        // The systems the fixture sector lists, kept by ID so a case can re-stub the colonies of
        // the one it moves.
        private Map<String, StarSystemAPI> systemMocksById;
        private EconomyAPI economyMock;
        private SectorAPI sectorMock;

        @BeforeEach
        void openSeamsAndSettleTheSector() {

            var globalMock = seams.openSeam(Global.class);

            // The builders below log through static fields initialised on first touch, which
            // happens inside this block: StubbedGlobalLogger says what an unanswered one costs.
            StubbedGlobalLogger.answerLoggersOn(globalMock);

            hegemonyMock = SectorPoliticsFixtures.buildFaction(
                HEGEMONY,
                SectorPoliticsFixtures.HEGEMONY_BRIGHT);
            tritachyonMock = SectorPoliticsFixtures.buildFaction(
                TRITACHYON,
                SectorPoliticsFixtures.TRITACHYON_BRIGHT);
            piratesMock = SectorPoliticsFixtures.buildFaction(
                PIRATES,
                SectorPoliticsFixtures.PERSEAN_BRIGHT);

            // Assembled in three named steps rather than behind one builder, so the systems a case
            // re-stubs colonies through and the economy it re-stubs them on are visible here
            // instead of being filled in as a side effect of building the sector.
            systemMocksById = buildSystemMocksById();
            economyMock = mock(EconomyAPI.class);
            sectorMock = buildSectorListing(
                systemMocksById.values(),
                economyMock,
                List.of(hegemonyMock, tritachyonMock, piratesMock));

            // Both legs are handed this sector: the refresh takes it from its caller and the
            // rebuild opens its own pass over it, so the comparison is over one sector rather than
            // two. The global read stands for whatever else the builders reach for on the way past.
            globalMock
                .when(Global::getSector)
                .thenReturn(sectorMock);

            // The state both legs start from: two hegemony systems, one tritachyon system, and a
            // frontier nothing stands in.
            placeColoniesIn(CORE_SYSTEM, buildColony(hegemonyMock, CORE_COLONY_SIZE));
            placeColoniesIn(BORDER_SYSTEM, buildColony(hegemonyMock, BORDER_COLONY_SIZE));
            placeColoniesIn(DYING_SYSTEM, buildColony(tritachyonMock, DYING_COLONY_SIZE));
            placeColoniesIn(FRONTIER_SYSTEM);

            // The dev reveal and the map-anchor tuning, both LunaLib-backed: no case turns on
            // either, so the seam's own answers stand for them. The cell seed inputs and the dev
            // overlays are seamed for the same reason, a rebuild reaching both on its way through.
            seams.openSeam(KmuMapVisibilitySettings.class);
            seams.openSeam(KmuPoliticalMapGeometrySettings.class);
            seams.openSeam(KmuPoliticalMapDiagnosticsSettings.class);

            var settingsMock = seams.openSeam(KmuPoliticalMapRibbonSettings.class);

            RibbonSettingsFixtures.stubBandsOnAtSizesThatDraw(settingsMock);

            // The weighting rule the fills and the bands are both resolved under. Read live off
            // LunaLib in production, so it is answered here rather than left to the settings seam,
            // whose zeroes would weigh every colony at nothing and leave the sector unheld.
            var rulesMock = seams.openSeam(DominanceRules.class);
            rulesMock
                .when(DominanceRules::readFromLunaSettings)
                .thenReturn(SectorPoliticsFixtures.buildStabilityWeightedRules());

            var renderStyleMock = seams.openSeam(RenderStyleReader.class);
            renderStyleMock
                .when(() -> RenderStyleReader.readRenderStyle(anyBoolean()))
                .thenReturn(buildThemePaintingEachCategoryApart());

            // The names off, which is what leaves the bands a function of the map alone; the
            // choice is sector-memory state no test JVM has, so it comes off a seam.
            var nameFormatMock = seams.openSeam(NameFormatPreference.class);
            nameFormatMock
                .when(() -> NameFormatPreference.getSelectedNameFormat(any()))
                .thenReturn(FactionNameFormatChoice.NONE);

            // No bloc spotlighted, which the seam's own null answers - the pick is sector-memory
            // state as well.
            seams.openSeam(FilterSelection.class);
        }

        @AfterEach
        void closeSeams() {
            seams.closeEverySeam();
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldWhenASystemTakesItsFirstColony() {
            // The system no holder ever accounted for: nobody held it before and somebody holds it
            // now, so the cell goes from empty backdrop to a bloc's territory and owes a band it
            // never had.
            var standingMap = buildMapByFullRebuild();

            placeColoniesIn(FRONTIER_SYSTEM, buildColony(piratesMock, FRONTIER_COLONY_SIZE));

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

            placeColoniesIn(BORDER_SYSTEM, buildColony(tritachyonMock, BORDER_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, BORDER_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldWhenARivalArrivesInAHeldSystem() {
            // The band's own case, and the arm no switch can reach: the holder is unmoved and the
            // fill says exactly what it said before, while the band goes from one bloc's tally to
            // a contest. Nothing but the band reports it, so a batch that re-derived the holder
            // and left the count alone would look right everywhere else.
            var standingMap = buildMapByFullRebuild();

            placeColoniesIn(
                CORE_SYSTEM,
                buildColony(hegemonyMock, CORE_COLONY_SIZE),
                buildColony(tritachyonMock, RIVAL_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, CORE_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldWhenAColonyGrowsAndNothingMoves() {
            // The commonest event of all - a colony resize that leaves the same winner, the same
            // settlement and the same count - so the map must come back exactly as it was. It is
            // not a no-op path: the marked system's band is re-baked whatever happened, and this
            // is what says the band it lays is the band it replaced.
            var standingMap = buildMapByFullRebuild();

            placeColoniesIn(CORE_SYSTEM, buildColony(hegemonyMock, GROWN_CORE_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, CORE_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldAtEveryStepOfASystemsLife() {
            // One map carried through a system's whole life, compared against a rebuild at every
            // step of it - which is the arrangement production actually runs: the cache holds one
            // map for the session and folds batch after batch into it, where every case above
            // starts from a map just rebuilt. So this is the only case that can catch a refresh
            // that is right once and wrong the second time - a fold that half-updates something,
            // leaving a map each individual comparison accepts and the next one builds on.
            //
            // The first comparison marks nothing. It asserts what every case here rests on and
            // none of them states: that two rebuilds of one sector agree, so a difference found
            // after a fold is the fold's and not the rebuild's.
            var standingMap = buildMapByFullRebuild();

            assertRefreshDrawsWhatARebuildWould(standingMap);

            // Settled: nobody held it, somebody does now.
            placeColoniesIn(FRONTIER_SYSTEM, buildColony(piratesMock, FRONTIER_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, FRONTIER_SYSTEM);

            // Contested: the holder is unmoved, and only the band reports the newcomer.
            placeColoniesIn(
                FRONTIER_SYSTEM,
                buildColony(piratesMock, FRONTIER_COLONY_SIZE),
                buildColony(tritachyonMock, RIVAL_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, FRONTIER_SYSTEM);

            // Taken outright: the newcomer is all that is left, so the cell changes hands on a
            // system that has already been folded into twice.
            placeColoniesIn(FRONTIER_SYSTEM, buildColony(tritachyonMock, RIVAL_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, FRONTIER_SYSTEM);

            // Emptied: back to the backdrop it started as, which is also the only step that can
            // show a fold leaving something behind - the map now has to match the one it began at.
            placeColoniesIn(FRONTIER_SYSTEM);

            assertRefreshDrawsWhatARebuildWould(standingMap, FRONTIER_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldAtEveryStepOfAMiddleSystemsFlips() {
            // The same walk over a system with neighbours on both sides, which is what the
            // frontier's own life cannot reach: every step here re-shapes a ring rather than one
            // cell, re-indexes the clusters, and rebuilds two blocs' territories - and does it to
            // a map the previous step already re-shaped. A re-shape that left a neighbour holding
            // an edge from the step before would survive any single fold and show here.
            //
            // The border system carries one colony throughout, the same size each time: what
            // moves is who owns it, so nothing but the holder can account for a difference.
            var standingMap = buildMapByFullRebuild();

            assertRefreshDrawsWhatARebuildWould(standingMap);

            // Sold to the neighbour on its right: the seam with the core hardens into a national
            // border, and the border with the dying system softens into a same-bloc seam.
            placeColoniesIn(BORDER_SYSTEM, buildColony(tritachyonMock, BORDER_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, BORDER_SYSTEM);

            // Taken back, which reverses both of those - so the map has to return to the one it
            // started at, having been re-shaped twice to get there.
            placeColoniesIn(BORDER_SYSTEM, buildColony(hegemonyMock, BORDER_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, BORDER_SYSTEM);

            // Taken by a third bloc that holds nothing else: both its edges are national borders
            // now, and a territory that was not on the map appears with it.
            placeColoniesIn(BORDER_SYSTEM, buildColony(piratesMock, BORDER_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(standingMap, BORDER_SYSTEM);

            // Lost outright, which severs the row: the middle goes back to backdrop, its two
            // neighbours re-shape against empty space, and the third bloc's territory has to come
            // off the map rather than linger with no cells under it.
            placeColoniesIn(BORDER_SYSTEM);

            assertRefreshDrawsWhatARebuildWould(standingMap, BORDER_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesDrawsWhatARebuildWouldWhenOneBatchMovesAllThree() {
            // All three moves in one drain, which is what a fleet action or an economy tick
            // actually delivers. The flip's re-shape ring reaches a system whose own colonies
            // moved in the same batch, so the two redraws have to compose rather than each
            // arriving at the map the other has already left behind.
            var standingMap = buildMapByFullRebuild();

            placeColoniesIn(BORDER_SYSTEM, buildColony(tritachyonMock, BORDER_COLONY_SIZE));
            placeColoniesIn(DYING_SYSTEM);
            placeColoniesIn(FRONTIER_SYSTEM, buildColony(piratesMock, FRONTIER_COLONY_SIZE));

            assertRefreshDrawsWhatARebuildWould(
                standingMap,
                BORDER_SYSTEM,
                DYING_SYSTEM,
                FRONTIER_SYSTEM);
        }

        // Marks the named systems, folds the batch into the standing map, and compares what that
        // leaves against a full rebuild over the very same sector.
        //
        // The refresh runs first because the rebuild it is compared against is taken from the
        // sector as it stands after the fold: one built ahead of the fold would be compared against
        // a map that had not yet accounted for the marks.
        private void assertRefreshDrawsWhatARebuildWould(
                StandingPoliticalMap standingMap,
                String... markedSystemIds) {

            staleSystemIds.addAll(List.of(markedSystemIds));

            IncrementalPoliticsRefresh.applyStalePoliticsUpdates(
                sectorMock,
                standingMap,
                staleSystemIds);

            // Compared structurally rather than by equality: a cell's draw record, a bloc's traced
            // territory and a band's runs are baked geometry, held as float arrays, which compare
            // by identity under equals and so would pass for any two maps whatever.
            //
            // The two occupancy sets are compared as the sets they are, since their iteration
            // order is not a fact about the map: a rebuild's scan fills a hash set, while a
            // refresh folds each marked system into the standing one, so the same membership
            // comes back in two orders. Named rather than taken over the whole comparison,
            // because a band's runs are a draw order and two bands laying the same runs in
            // different orders are two different bands.
            assertThat(readDrawnMap(standingMap.territories()))
                .usingRecursiveComparison()
                .ignoringCollectionOrderInFields("inhabitedSystemKeys", "spotlitPresenceSystemKeys")
                .isEqualTo(readDrawnMap(buildMapByFullRebuild().territories()));
        }

        // One full rebuild of the whole map over the sector as it currently stands, in the order
        // the plugin's cache drives it: the territories, then the names fitted over them, then the
        // bands laid around those names, then the labels minted from them.
        private StandingPoliticalMap buildMapByFullRebuild() {

            // The rebuild's one reading of the sector, opened here as the plugin's cache opens it
            // and handed to both the build and the bake beneath it.
            var pass = HolderPass.readFromLunaSettings(
                sectorMock,
                FactionsView.INSTANCE.resolveGrouping());

            // The rebuild's one sampling of the sidebar picks, taken here as the plugin's cache
            // takes it and carried through every stage below, so the fills, the fit, the bands and
            // the labels are all baked under one reading of them.
            var contentInputs = ContentInputs.sampleForView(
                FactionsView.INSTANCE,
                MapLayerScreens.resolveLivePicks().memoryScope());

            var territories = TerritoryBuilder.buildTerritories(
                cellsMock,
                pass,
                FactionsView.INSTANCE,
                contentInputs,
                TerritoryBuilder.resolveHolding(pass, FactionsView.INSTANCE, contentInputs));

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
                    pass,
                    standingAnchors.getAnchors())
                .bakeAllCellRibbons();

            LabelsBuilder.rebuildLabels(
                factionLabels,
                standingAnchors.getAnchors(),
                contentInputs.nameFormat().areNamesDrawn());

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
    }

    // What the two legs are compared on: everything a cell is drawn from, and everything drawn
    // from it. Copied out of the built map rather than read through it, so the values compared are
    // the ones each leg finished with even though the refresh edits its map in place.
    //
    // The occupancy's three facts are here beside the cells because a difference in them is the
    // failure this suite exists to catch, and a difference that has not yet reached a cell is the
    // one a later change to how a cell is styled would turn into a visible one.
    //
    // The blocs' territories are here because a cell does not carry them: an owned cell draws only
    // its interior seams, while the fill, the national border and the hatch belong to the cluster
    // it fused into. A batch that rebuilt the wrong bloc's territory, or that re-shaped every cell
    // correctly and never re-indexed the clusters, moves nothing a cell would report.
    private record DrawnMap(
        Map<SystemKey, DominantHolder> holderBySystemKey,
        Set<SystemKey> inhabitedSystemKeys,
        Set<SystemKey> spotlitPresenceSystemKeys,
        Map<SystemKey, StyledCell> styledCellByCellKey,
        Map<String, StyledClusterGroup> styledClusterGroupByOwnerId,
        Map<SystemKey, CellRibbon> ribbonByCellKey) {
    }

    private static DrawnMap readDrawnMap(PoliticalMapTerritories territories) {
        return new DrawnMap(
            new LinkedHashMap<>(territories.getHolderBySystemKey()),
            new LinkedHashSet<>(territories.getInhabitedSystemKeys()),
            new LinkedHashSet<>(territories.getSpotlitPresenceSystemKeys()),
            new LinkedHashMap<>(territories.getStyledCellByCellKey()),
            new LinkedHashMap<>(territories.getStyledClusterGroupByOwnerId()),
            new LinkedHashMap<>(territories.getPaintedCells().getRibbonByCellKey()));
    }

    // The systems the fixture sector lists, keyed by ID and in row order.
    private static Map<String, StarSystemAPI> buildSystemMocksById() {

        var systemMocksById = new LinkedHashMap<String, StarSystemAPI>();

        for (var systemId : DRAWN_SYSTEM_IDS) {
            var systemMock = mock(StarSystemAPI.class);

            when(systemMock.getId())
                .thenReturn(systemId);

            systemMocksById.put(systemId, systemMock);
        }
        return systemMocksById;
    }

    // The fixture sector: the given systems, the economy their colonies are listed on, and the
    // factions resolvable by ID so a holder can be coloured.
    private static SectorAPI buildSectorListing(
            Collection<StarSystemAPI> systemMocks,
            EconomyAPI economyMock,
            List<FactionAPI> factionMocks) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.copyOf(systemMocks));
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);

        for (var factionMock : factionMocks) {

            when(sectorMock.getFaction(factionMock.getId()))
                .thenReturn(factionMock);
        }
        return sectorMock;
    }

    // A visible colony of the given size. What the suite varies is which system holds one and
    // whose it is, never what sort of market it is.
    private static MarketAPI buildColony(FactionAPI factionMock, int size) {
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

        var cellEdgesByCellKey = new LinkedHashMap<SystemKey, List<CellEdge>>();
        var systemKeyByCellKey = new LinkedHashMap<SystemKey, SystemKey>();
        var siteBySystemKey = new LinkedHashMap<SystemKey, double[]>();

        for (var index = 0; index < DRAWN_SYSTEM_IDS.size(); index++) {

            var systemId = DRAWN_SYSTEM_IDS.get(index);
            var leftEdgeX = index * CELL_SIDE;

            cellEdgesByCellKey.put(buildCellKey(systemId), buildSquareCellBetween(
                leftEdgeX,
                index == 0 ? null : DRAWN_SYSTEM_IDS.get(index - 1),
                index == DRAWN_SYSTEM_IDS.size() - 1
                    ? null
                    : DRAWN_SYSTEM_IDS.get(index + 1)));

            systemKeyByCellKey.put(buildCellKey(systemId), buildCellKey(systemId));
            siteBySystemKey.put(
                buildCellKey(systemId),
                new double[] {leftEdgeX + CELL_SIDE / 2, CELL_SIDE / 2});
        }
        var geometryCacheMock = mock(CellGeometryCache.class);

        when(geometryCacheMock.getCellEdgesByCellKey())
            .thenReturn(cellEdgesByCellKey);
        when(geometryCacheMock.getSystemKeyByCellKey())
            .thenReturn(systemKeyByCellKey);
        when(geometryCacheMock.getSiteBySystemKey())
            .thenReturn(siteBySystemKey);

        return geometryCacheMock;
    }

    // One closed square cell: its left and right edges face its neighbours in the row, or the
    // reach bound at the ends, and its top and bottom face the reach bound throughout. Real
    // coordinates, since both paths inset these edges for real and lay a band inside what that
    // leaves.
    private static List<CellEdge> buildSquareCellBetween(
            double leftEdgeX,
            String leftSystemId,
            String rightSystemId) {

        var rightEdgeX = leftEdgeX + CELL_SIDE;

        return List.of(
            buildEdgeFacing(leftEdgeX, 0, rightEdgeX, 0, null),
            buildEdgeFacing(rightEdgeX, 0, rightEdgeX, CELL_SIDE, rightSystemId),
            buildEdgeFacing(rightEdgeX, CELL_SIDE, leftEdgeX, CELL_SIDE, null),
            buildEdgeFacing(leftEdgeX, CELL_SIDE, leftEdgeX, 0, leftSystemId));
    }
}
