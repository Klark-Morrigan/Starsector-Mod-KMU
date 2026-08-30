package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.Segment;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelLineBoxes;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.AnchorFitFingerprint;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.ClusterIdentity;
import kmu.maplayers.base.labels.anchor.ClusterNameBoxes;
import kmu.maplayers.base.labels.anchor.ClusterNameDisturbance;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.ribbon.RibbonSettingsFixtures;
import kmu.maplayers.politicalmap.base.render.territories.FactionTerritoryBuilder;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.maplayers.politicalmap.base.render.territories.StyledCellBuilder;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.KmuPoliticalMapSettings;
import kmu.settings.RibbonNameClearanceChoice;

import org.apache.log4j.Logger;
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

import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.CELL_LESS_SYSTEM;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.DISTANT_SYSTEM;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.FLIPPED_SYSTEM;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.NEIGHBOUR_SYSTEM;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.buildHolderOf;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.buildHoldersOf;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.buildSectorWithSystems;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.buildSquareCellFacing;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.buildTwoAdjacentCells;
import static kmu.maplayers.politicalmap.base.render.StalePoliticsFixtures.matchSystemArg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the redraw {@link IncrementalPoliticsRefresh} performs over what a drained batch
 * disturbed: which cells are re-shaped, which factions' territories are rebuilt, and which cells
 * owe a fresh band. The claim under test is the one the incremental path exists to make - that it
 * touches the cells whose drawing actually moved and their neighbours, and nothing else - since
 * anything wider costs the frame it was written to save, and anything narrower leaves the map
 * drawing a stale holder.
 *
 * <p>What the batch reads back out of the sector before any of that, and what it records as
 * having been disturbed, is {@link MarkedSystemRederiveTest}'s. The seams for those reads are
 * still opened here, because a case about the redraw has to state the change that provoked it.
 *
 * <p>A unit test, so the primitives the redraw delegates to are mocked at their static seams:
 * the dominance resolve that answers who holds a system, the two builders that turn a
 * re-shaped cell and a faction's members into draw records, and the anchor and label
 * rebuilds that ride along. Each is pinned by its own suite; what belongs here is only the
 * decision about which of them to call and with what.
 *
 * <p>The same "only what moved" claim is pinned on the placements the redraw carries along: a
 * frame that re-fits them hands the caller's own pair down for the fit to replace, and a frame
 * that leaves them alone calls no fit at all - so what the caller holds is only ever relabelled
 * when the placements it labels were themselves rebuilt.
 */
final class IncrementalPoliticsRefreshTest {

    // The revision the caller's cells stand at. This path re-shapes cells but never recuts
    // them, so it fits against the geometry it was handed and reports that same revision back.
    private static final int GEOMETRY_REVISION = 7;

    // What the caller's standing placements were fitted under, standing in the pair so the
    // re-fit can carry over the clusters this fold did not move. Opaque here - the tuning
    // inside is the fit's own business and no case reads it.
    // What the stubbed planner reports for the marked system: one run, which is all the case
    // reads - that a band was baked at all, rather than what it says.
    private static final RibbonPlan BAND_OF_ONE_RUN =
        new RibbonPlan(List.of(new RibbonSegment(Color.WHITE, 1)));

    private static final AnchorFitFingerprint STANDING_FIT =
        new AnchorFitFingerprint(null, GEOMETRY_REVISION - 1);

    @Nested
    class ApplyStalePoliticsUpdates {

        // Closed in reverse on the way out, so a seam opened over another is never left
        // standing when the inner one is already gone.
        private final List<MockedStatic<?>> openStaticSeams = new ArrayList<>();

        // The placements the caller holds across frames, standing at what a previous pass
        // fitted them under. Empty of placements because the re-fit is neutralised here and
        // would leave none: what a case reads off it is the label, which is the half this fold
        // can leave wrong.
        private final StandingClusterAnchors standingAnchors = new StandingClusterAnchors();

        // The cells the caller holds, paired with the revision they stand at. Built once per
        // case so what a re-fit is handed can be read back as the caller's own pair rather than
        // as a value that merely compares equal to it.
        private final RevisedCellGeometry cellGeometry =
            new RevisedCellGeometry(buildTwoAdjacentCells(), GEOMETRY_REVISION);

        private MockedStatic<Global> globalMock;
        private MockedStatic<SectorPolitics> politicsMock;
        private MockedStatic<PoliticalMapInhabitation> inhabitationMock;
        private MockedStatic<StyledCellBuilder> styledCellsMock;
        private MockedStatic<FactionTerritoryBuilder> territoriesMock;
        private MockedStatic<ClusterAnchorsBuilder> anchorsMock;
        private MockedStatic<NameFormatPreference> nameFormatMock;
        private MockedStatic<KmuPoliticalMapSettings> settingsMock;

        private SectorAPI sectorMock;

        @BeforeEach
        void openSeamsAndClearTheStaleSet() {

            globalMock = openSeam(Global.class);
            // The class logs through a static field initialised on first touch, which may
            // happen inside this block; without this the logger would come back null and
            // the debug lines below would fault before the assertion was reached.
            globalMock
                .when(() -> Global.getLogger(any(Class.class)))
                .thenReturn(Logger.getLogger(IncrementalPoliticsRefreshTest.class));

            sectorMock = buildSectorWithSystems(
                FLIPPED_SYSTEM,
                NEIGHBOUR_SYSTEM,
                DISTANT_SYSTEM);

            globalMock
                .when(Global::getSector)
                .thenReturn(sectorMock);

            politicsMock = openSeam(SectorPolitics.class);

            // What still stands in a marked system, which the fold reads beside the holder. Every
            // fixture below marks systems its territories already count as settled, so the seam
            // answers "still settled" and a case about inhabitation says so by re-stubbing it -
            // which keeps the holder cases free of a second fact moving underneath them.
            inhabitationMock = openSeam(PoliticalMapInhabitation.class);
            inhabitationMock
                .when(() -> PoliticalMapInhabitation.isSystemInhabited(any(), any()))
                .thenReturn(true);

            styledCellsMock = openSeam(StyledCellBuilder.class);
            styledCellsMock
                .when(() -> StyledCellBuilder.buildStyledCellForSystem(
                    any(),
                    any(),
                    any()))
                .thenReturn(PoliticalMapTerritoryFixtures.createPlaceholderStyledCell());

            territoriesMock = openSeam(FactionTerritoryBuilder.class);
            territoriesMock
                .when(() -> FactionTerritoryBuilder.buildFactionTerritory(
                    any(),
                    any(),
                    any(),
                    anyList()))
                .thenReturn(PoliticalMapTerritoryFixtures.createTerritoryWithLoops(List.of()));

            // Both ride along after a flip and are pinned by their own suites; opening them
            // leaves each a no-op, so a case here asserts the fold and not their output. The
            // re-fit writes the pair it is handed, which is exactly what a neutralised seam does
            // not do - so a case reads whether the fold called it, not what it left behind.
            anchorsMock = openSeam(ClusterAnchorsBuilder.class);

            // What the re-fit reports about the names it moved decides which bands are re-baked,
            // so the neutralised seam has to answer something: no name moved, which is the
            // reading every case starts from and the one about a moved name replaces.
            anchorsMock
                .when(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    any(),
                    any(),
                    any(),
                    any()))
                .thenReturn(ClusterNameDisturbance.NONE);

            openSeam(LabelsBuilder.class);

            // Read as an argument to the label rebuild, so it evaluates even with that
            // rebuild neutralised - and it reads save-backed memory no test JVM has. Held as a
            // field because a band's re-bake reads it too: the room a name takes is reserved only
            // while the names are drawing, so a case about that re-stubs this.
            nameFormatMock = openSeam(NameFormatPreference.class);
            nameFormatMock
                .when(NameFormatPreference::getSelectedNameFormat)
                .thenReturn(FactionNameFormatChoice.NONE);

            // The pair arrives labelled by a pass that ran before this frame, which is what
            // every case here folds into: a fold that overwrote the label without re-fitting
            // would read as agreeing with itself if the pair started blank.
            standingAnchors.replaceAnchors(List.of(), STANDING_FIT);

            // A re-bake reads the player's band sizes, which reach LunaLib - so the knobs answer
            // from a seam here, at the sizes the mod ships, since no case in this suite is about
            // what a band is sized at. Held as a field for the reason the name choice above is:
            // whether a band keeps clear of the names is one of these knobs, so the case about
            // that answer re-stubs it.
            settingsMock = openSeam(KmuPoliticalMapSettings.class);
            RibbonSettingsFixtures.stubBandsOnAtSizesThatDraw(settingsMock);

            // A re-bake also opens its own pass, which samples the dev reveal off the map-layer
            // knobs - LunaLib again. No case here turns on the reveal, so the seam's own false is
            // the answer.
            openSeam(KmuMapLayerSettings.class);

            // The stale set is static and shared, so a residue from another suite would
            // read here as a system this one never marked.
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
        void applyStalePoliticsUpdatesReadsNoSectorWhenNothingIsStale() {
            // The per-frame path: this runs every frame, and on almost all of them the
            // stale set is empty, so it must cost nothing before it returns.
            //
            // Read as "nothing was asked of the sector" rather than "the sector was never named":
            // the drain resolves the running sector to reach its board, which is a field read and
            // a lookup. What the empty batch must not cost is the pass over that sector and the
            // walk behind it, and neither leaves the sector untouched.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            applyTo(territories);

            verifyNoInteractions(sectorMock);
            assertThat(territories.getStyledCellByCellId())
                .isEmpty();
        }

        @Test
        void applyStalePoliticsUpdatesSkipsAStaleSystemThatSeedsNoCell() {
            // A resize changes holding over cells already drawn; it never admits a
            // system to the map, so one with no cell has nothing to re-shape and must not
            // reach the re-derive at all.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(CELL_LESS_SYSTEM);
            applyTo(territories);

            politicsMock.verifyNoInteractions();

            assertThat(territories.getStyledCellByCellId())
                .isEmpty();
        }

        @Test
        void applyStalePoliticsUpdatesRedrawsNothingWhenNoFactMoved() {
            // The common resize: a colony grows, its faction still wins, and what stands there is
            // what stood there - so the batch disturbed nothing and no cell or territory is built
            // again. The marked system's band is re-baked all the same, which the case below
            // states; here the cell carries none, so the re-bake finds nothing to write.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            styledCellsMock.verifyNoInteractions();
            territoriesMock.verifyNoInteractions();
        }

        @Test
        void applyStalePoliticsUpdatesRebakesTheBandOfAMarkedSystemThatDidNotFlip() {
            // What marks a system is a colony appearing, growing, or changing hands - which is
            // exactly what changes how many colonies a band counts. So a marked system owes a
            // re-baked band even on the frame where nothing about its fill moved, and waiting for
            // a flip would leave the band reporting a colony that is no longer there.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            territories.putStyledCell(
                FLIPPED_SYSTEM,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                buildBandSizedCell());

            // The band starts above the cell's own site, so the marked system needs one; the
            // shared geometry fixture records none, every other case being about shapes.
            when(cellGeometry.cells().getSiteBySystemId())
                .thenReturn(Map.of(FLIPPED_SYSTEM, new double[] {2000.0, 2000.0}));

            when(territories.getView().resolveRibbonPlanner(any()))
                .thenReturn(system -> BAND_OF_ONE_RUN);

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(FLIPPED_SYSTEM);

            // Nothing else moved: a re-bake is not a re-shape.
            styledCellsMock.verifyNoInteractions();
            territoriesMock.verifyNoInteractions();
        }

        @Test
        void applyStalePoliticsUpdatesRebakesABandClearOfTheNamesAlreadyPlaced() {
            // A band is laid around the cluster names, so a re-bake has to read the placements the
            // map is drawing rather than lay a band as though there were none. This name lies
            // across the whole marked cell, which leaves its ring with nowhere to put one - a
            // re-bake blind to the names would hand it a band running under the word.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            territories.putStyledCell(
                FLIPPED_SYSTEM,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                buildBandSizedCell());

            when(cellGeometry.cells().getSiteBySystemId())
                .thenReturn(Map.of(FLIPPED_SYSTEM, new double[] {2000.0, 2000.0}));

            when(territories.getView().resolveRibbonPlanner(any()))
                .thenReturn(system -> BAND_OF_ONE_RUN);

            // A name takes up room only while the names are being drawn at all.
            nameFormatMock
                .when(NameFormatPreference::getSelectedNameFormat)
                .thenReturn(FactionNameFormatChoice.SHORT);

            standingAnchors.replaceAnchors(List.of(buildNameAcrossTheCell()), STANDING_FIT);

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .isEmpty();
        }

        @Test
        void applyStalePoliticsUpdatesRebakesABandAroundTheWordsWhereTheClearanceReadsThem() {
            // The same name over the same cell, read by the words it draws rather than by the box
            // its placement reserved. The two readings disagree about this cell outright - the
            // fitted box covers it whole, while the words land elsewhere entirely - so a band comes
            // back where the case above had none, and the reading the player did not pick is never
            // asked for at all.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            territories.putStyledCell(
                FLIPPED_SYSTEM,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                buildBandSizedCell());

            when(cellGeometry.cells().getSiteBySystemId())
                .thenReturn(Map.of(FLIPPED_SYSTEM, new double[] {2000.0, 2000.0}));

            when(territories.getView().resolveRibbonPlanner(any()))
                .thenReturn(system -> BAND_OF_ONE_RUN);

            nameFormatMock
                .when(NameFormatPreference::getSelectedNameFormat)
                .thenReturn(FactionNameFormatChoice.SHORT);

            settingsMock
                .when(KmuPoliticalMapSettings::getPoliticalMapRibbonNameClearance)
                .thenReturn(RibbonNameClearanceChoice.WORDS);

            // Both readings are seams here rather than one being left live: what a name measures
            // to is pinned by each reading's own suite, and measuring the words for real would
            // need the label face, which no test JVM loads. What belongs here is which of the two
            // the choice reaches for.
            var fittedBoxesMock = openSeam(ClusterNameBoxes.class);
            var wordBoxesMock = openSeam(LabelLineBoxes.class);

            wordBoxesMock
                .when(() -> LabelLineBoxes.listLineBoxes(anyList()))
                .thenReturn(List.of(buildWordsBoxAwayFromTheCell()));

            standingAnchors.replaceAnchors(List.of(buildNameAcrossTheCell()), STANDING_FIT);

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(FLIPPED_SYSTEM);

            fittedBoxesMock.verifyNoInteractions();
        }

        @Test
        void applyStalePoliticsUpdatesReservesNoRoomForANameWhileTheNamesAreSwitchedOff() {
            // The same name across the same cell, with the names switched off: nothing is drawn
            // for the band to be interrupted by, so it takes the whole ring. The placements are
            // still standing - they are built for the anchor overlay too - which is why the room
            // they would take is read off the name choice rather than off the list being empty.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            territories.putStyledCell(
                FLIPPED_SYSTEM,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                buildBandSizedCell());

            when(cellGeometry.cells().getSiteBySystemId())
                .thenReturn(Map.of(FLIPPED_SYSTEM, new double[] {2000.0, 2000.0}));

            when(territories.getView().resolveRibbonPlanner(any()))
                .thenReturn(system -> BAND_OF_ONE_RUN);

            standingAnchors.replaceAnchors(List.of(buildNameAcrossTheCell()), STANDING_FIT);

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(FLIPPED_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesReservesNoRoomForANameWhileTheBandsIgnoreTheNames() {
            // The same name across the same cell, drawn this time, with the player having asked
            // that the bands not give way to it: the band takes the whole ring and the word draws
            // across it. The pair with the case above is the point - the two reasons a band has
            // nothing to keep clear of reach the same result by different routes, one because
            // there is no name on the map and one because the player would rather have the band.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            territories.putStyledCell(
                FLIPPED_SYSTEM,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                buildBandSizedCell());

            when(cellGeometry.cells().getSiteBySystemId())
                .thenReturn(Map.of(FLIPPED_SYSTEM, new double[] {2000.0, 2000.0}));

            when(territories.getView().resolveRibbonPlanner(any()))
                .thenReturn(system -> BAND_OF_ONE_RUN);

            nameFormatMock
                .when(NameFormatPreference::getSelectedNameFormat)
                .thenReturn(FactionNameFormatChoice.SHORT);

            settingsMock
                .when(KmuPoliticalMapSettings::shouldKeepPoliticalMapRibbonsClearOfNames)
                .thenReturn(false);

            standingAnchors.replaceAnchors(List.of(buildNameAcrossTheCell()), STANDING_FIT);

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(FLIPPED_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesRedrawsOnlyItsOwnCellWhenOnlyInhabitationMoved() {
            // A restyled system - one that just became settled, or that the pick just colonised -
            // is redrawn through the same primitive a flip's cells are, and nothing beyond it: a
            // cell's shape is settled by which of its edges are same-owner seams, and neither fact
            // is one of them. That is what makes this cheap enough to run on a colony event.
            var territories = PoliticalMapTerritoryFixtures.createTerritoriesSettledIn(
                Map.of(),
                Set.of());

            assertResolvesTo(FLIPPED_SYSTEM, null);
            assertSettledIs(FLIPPED_SYSTEM, true);

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            styledCellsMock.verify(
                () -> StyledCellBuilder.buildStyledCellForSystem(
                    any(),
                    eq(FLIPPED_SYSTEM),
                    any()));

            styledCellsMock.verify(
                () -> StyledCellBuilder.buildStyledCellForSystem(
                    any(),
                    eq(NEIGHBOUR_SYSTEM),
                    any()),
                never());

            territoriesMock.verifyNoInteractions();
        }

        @Test
        void applyStalePoliticsUpdatesBandsASystemThatJustBecameSettled() {
            // The band needs no wiring of its own: a marked system is re-baked either way, and
            // what decides whether it gets a band is the very set the fold has just written. Left
            // out of that set, the cell reaches the band pass as empty space and nothing is laid.
            var territories = PoliticalMapTerritoryFixtures.createTerritoriesSettledIn(
                Map.of(),
                Set.of());

            seedBandSizedDistantCell(territories);

            assertResolvesTo(DISTANT_SYSTEM, null);
            assertSettledIs(DISTANT_SYSTEM, true);

            MapLayerRefresh.markSystemGroupingStale(DISTANT_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(DISTANT_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesOpensOnePassHoweverManySystemsAreMarked() {
            // The whole batch is answered off one reading of the sector: a pass per marked system
            // would pay a settings read and a walk of that system's colonies for each of the
            // questions asked about it, which is what this path exists to avoid.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            // Built before the seam is opened, so what the seam hands back is a real pass the
            // reads below can be answered from rather than a stand-in.
            var batchPass = DominancePass.readFromLunaSettings(
                sectorMock,
                HolderGrouping.identity());

            var passMock = openSeam(DominancePass.class);
            passMock
                .when(() -> DominancePass.readFromLunaSettings(any(), any(HolderGrouping.class)))
                .thenReturn(batchPass);

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));
            assertResolvesTo(NEIGHBOUR_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            MapLayerRefresh.markSystemGroupingStale(NEIGHBOUR_SYSTEM);
            applyTo(territories);

            passMock.verify(
                () -> DominancePass.readFromLunaSettings(any(), any(HolderGrouping.class)));
        }

        @Test
        void applyStalePoliticsUpdatesBakesTheBandsOffTheBatchesOwnReadingOfTheSector() {
            // The batch's last stage used to be handed a bare sector and open a second reading of
            // it - for a sector that cannot have moved since the re-derive read it a moment
            // earlier. So the count is taken at the one entry a reading is opened through, and one
            // opening is what says the bake shares rather than repeats.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            seedBandSizedDistantCell(territories);

            // Built before the seam is opened, so what the seam hands back is a real reading the
            // batch and the bake beneath it can both be answered from.
            var batchHolding = HolderPass.readFromLunaSettings(
                sectorMock,
                HolderGrouping.identity());

            var holdingMock = openSeam(HolderPass.class);
            holdingMock
                .when(() -> HolderPass.readFromLunaSettings(any(), any(HolderGrouping.class)))
                .thenReturn(batchHolding);

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            holdingMock.verify(
                () -> HolderPass.readFromLunaSettings(any(), any(HolderGrouping.class)));
        }

        @Test
        void applyStalePoliticsUpdatesReshapesTheFlippedSystemAndItsNeighbour() {
            // The neighbour's own holder did not move, but the edge it shares with the
            // flipped system just turned from a same-faction seam into a national border,
            // so it has to be re-shaped too or the border draws down one side only.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            styledCellsMock.verify(
                () -> StyledCellBuilder.buildStyledCellForSystem(
                    any(),
                    eq(FLIPPED_SYSTEM),
                    any()));

            styledCellsMock.verify(
                () -> StyledCellBuilder.buildStyledCellForSystem(
                    any(),
                    eq(NEIGHBOUR_SYSTEM),
                    any()));
        }

        @Test
        void applyStalePoliticsUpdatesRebuildsTheLosingAndGainingFactionsTerritories() {
            // Both sides of the transfer change shape - one loses the cell, the other gains
            // it - and no third faction's rings trace a cell that moved, so exactly these
            // two rebuild.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            territoriesMock.verify(
                () -> FactionTerritoryBuilder.buildFactionTerritory(
                    any(),
                    any(),
                    eq(HEGEMONY),
                    anyList()));
            territoriesMock.verify(
                () -> FactionTerritoryBuilder.buildFactionTerritory(
                    any(),
                    any(),
                    eq(TRITACHYON),
                    anyList()));
        }

        @Test
        void applyStalePoliticsUpdatesLeavesTheBandOfACellNoMovedNameReaches() {
            // The flip re-fits the names, and the re-fit moved none of them - so a cell the flip
            // never touched is laid out against exactly the names it was laid out against before,
            // and re-baking it would spend a carve, a count and a stroke to arrive at what it is
            // already carrying. Read as a pair with the case below, which is the same flip over
            // the same cell with one name moved onto it.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                DISTANT_SYSTEM,
                HEGEMONY));

            seedDrawnDistantCell(territories);

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .doesNotContainKey(DISTANT_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesRebakesTheBandOfACellAMovedNameReaches() {
            // The reason the whole map used to re-bake after a flip: a re-fitted name is placed
            // wherever its new cluster is roomiest, which can be a cell the flip never went near,
            // and a band laid around where that name used to be is no longer laid around it. Now
            // that the fit says which names it moved, that cell is named rather than reached by
            // re-baking every band in the sector.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                DISTANT_SYSTEM,
                HEGEMONY));

            seedDrawnDistantCell(territories);

            anchorsMock
                .when(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    any(),
                    any(),
                    any(),
                    any()))
                .thenReturn(ClusterNameDisturbance.compareFittedNames(
                    List.of(),
                    List.of(buildNameOverTheDistantCell())));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(DISTANT_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesRebakesTheBandOfAMarkedSystemWhileAnotherFlips() {
            // The third reason a cell owes a band, and the one the other two hide: a colony
            // appeared somewhere that did not change hands, in the same batch as a flip
            // elsewhere. That system is neither re-shaped nor reached by a name, so a bake taking
            // only those two would leave its band counting a colony that is no longer there -
            // exactly what the no-flip path re-bakes the marked systems for.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                DISTANT_SYSTEM,
                HEGEMONY));

            seedDrawnDistantCell(territories);

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));
            assertResolvesTo(DISTANT_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            MapLayerRefresh.markSystemGroupingStale(DISTANT_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(DISTANT_SYSTEM);
        }

        // Seeds a drawn cell far from the flip, with the geometry a band needs to be laid in it:
        // the system it draws as, its own site and edges, and a recorded ring wide enough to hold
        // the authored band. Its edges face open space alone, so nothing that flips can re-shape
        // it - which is what lets a case about the reasons other than a re-shape use it.
        private void seedDrawnDistantCell(PoliticalMapTerritories territories) {

            seedDistantCellGeometry(territories, buildIsolatedSquareCell(10000));

            territories.putStyledCell(
                DISTANT_SYSTEM,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                buildDistantBandSizedCell());
        }

        // The same cell for a case whose cell is redrawn, band-sized in the geometry itself and
        // with no draw record seeded: a redraw replaces the recorded shape with the one its edges
        // actually cut, so a case reading a band back off a redrawn cell needs the edges to cut a
        // ring a band fits in - and reads back a band this batch laid rather than one it was given.
        private void seedBandSizedDistantCell(PoliticalMapTerritories territories) {
            seedDistantCellGeometry(territories, buildBandSizedIsolatedCell());
        }

        // What both distant-cell arrangements share: the distant cell added to the two adjacent
        // ones, its site, and a planner that reports a band for whatever it is asked about. Held in
        // one place because the two differ only in the cell's edges and whether it starts drawn,
        // and a site or planner that drifted between them would leave one case's band unexplained.
        private void seedDistantCellGeometry(
                PoliticalMapTerritories territories,
                List<CellEdge> distantCellEdges) {

            when(cellGeometry.cells().getCellEdgesByCellId())
                .thenReturn(Map.of(
                    FLIPPED_SYSTEM,
                    buildSquareCellFacing(NEIGHBOUR_SYSTEM, 0),
                    NEIGHBOUR_SYSTEM,
                    buildSquareCellFacing(FLIPPED_SYSTEM, 100),
                    DISTANT_SYSTEM,
                    distantCellEdges));

            when(cellGeometry.cells().getSystemIdByCellId())
                .thenReturn(Map.of(
                    FLIPPED_SYSTEM,
                    FLIPPED_SYSTEM,
                    NEIGHBOUR_SYSTEM,
                    NEIGHBOUR_SYSTEM,
                    DISTANT_SYSTEM,
                    DISTANT_SYSTEM));

            when(cellGeometry.cells().getSiteBySystemId())
                .thenReturn(Map.of(DISTANT_SYSTEM, new double[] {12000.0, 12000.0}));

            when(territories.getView().resolveRibbonPlanner(any()))
                .thenReturn(system -> BAND_OF_ONE_RUN);
        }

        @Test
        void applyStalePoliticsUpdatesRefitsAgainstTheCallersGeometry() {
            // The caller's own cells-and-revision pair goes out as it came in, because this path
            // re-shapes cells within a partition it never recut - so the fit ran against the very
            // geometry the caller named, and a re-fit reported against any other one would offer
            // its placements to a later rebuild standing somewhere else.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            anchorsMock.verify(
                () -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    any(),
                    same(cellGeometry),
                    any(),
                    any()));
        }

        @Test
        void applyStalePoliticsUpdatesRefitsIntoTheCallersOwnStandingPair() {
            // The re-fit here is partial for the same reason a full rebuild's is: a flip
            // re-partitions the clusters it touches and leaves the rest alone, so what the
            // standing placements were fitted under has to reach the fit or every cluster is
            // searched again. That record and the placements are one value, and it is the
            // caller's own - handing a copy down would carry over correctly and still leave
            // the caller holding placements labelled by the pass before this one.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            anchorsMock.verify(
                () -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    same(standingAnchors),
                    any(),
                    any(),
                    any()));
        }

        @Test
        void applyStalePoliticsUpdatesLeavesTheStandingPairAloneWhenNothingIsStale() {
            // Nothing was re-fitted, so the standing placements and the rules recorded for them
            // still describe each other and neither half may move.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            applyTo(territories);

            anchorsMock.verifyNoInteractions();
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(STANDING_FIT);
        }

        @Test
        void applyStalePoliticsUpdatesLeavesTheStandingPairAloneWhenTheHolderDidNotChange() {
            // The same claim on the other early return: a resize that leaves the winner alone
            // leaves the placements alone, so there is nothing about them to restate.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, buildHolderOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            anchorsMock.verifyNoInteractions();
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(STANDING_FIT);
        }

        // Opens a static seam and registers it for closing, so a case names what it needs
        // rather than repeating the open-and-remember pair for each.
        private <T> MockedStatic<T> openSeam(Class<T> seamType) {
            MockedStatic<T> staticMock = mockStatic(seamType);
            openStaticSeams.add(staticMock);
            return staticMock;
        }

        // What the re-derive answers for one system this pass. Every case stubs the systems
        // it marks; an unstubbed one comes back null, which reads as a system that lost its
        // holder rather than as a missing stub.
        private void assertResolvesTo(String systemId, DominantHolder holder) {
            // The pass matcher is typed because a sibling entry point takes a sector and a
            // grouping in the same slots, and a bare any() would name neither.
            politicsMock
                .when(() -> SectorPolitics.resolveDominantHolder(
                    matchSystemArg(systemId),
                    any(DominancePass.class)))
                .thenReturn(holder);
        }

        // What the inhabitation read answers for one system this pass, against the seam's own
        // "still settled" default.
        private void assertSettledIs(String systemId, boolean isInhabited) {
            inhabitationMock
                .when(() -> PoliticalMapInhabitation.isSystemInhabited(
                    any(),
                    matchSystemArg(systemId)))
                .thenReturn(isInhabited);
        }

        // Runs the refresh over the two-cell geometry every case shares, handing it the pair
        // the caller holds across frames and an empty stand-in for the label list the plugin
        // owns beside it.
        private void applyTo(PoliticalMapTerritories territories) {
            IncrementalPoliticsRefresh.applyStalePoliticsUpdates(
                territories,
                standingAnchors,
                new ArrayList<Label>(),
                cellGeometry);
        }
    }

    // A placement whose name covers the whole band-sized cell above: the line runs clear across
    // it and the block is girthier than the cell, so no stretch of that cell's ring is left
    // uncovered. Only the accepted line and the girth are read off a placement here.
    private static ClusterAnchor buildNameAcrossTheCell() {
        return new ClusterAnchor(
            new ClusterIdentity(HEGEMONY, Set.of(FLIPPED_SYSTEM)),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            new Segment(-1000.0, 2000.0, 5000.0, 2000.0),
            null,
            null,
            6000f,
            1);
    }

    // The room a name's words take where they fall nowhere near the marked cell. A real box, so
    // the carve does run over it, but one that cell's ring never meets - which is what tells a
    // band laid around the words apart from a band laid as though no name existed.
    private static List<double[]> buildWordsBoxAwayFromTheCell() {
        return List.of(
            new double[] {-10000.0, -10000.0},
            new double[] {-9000.0, -10000.0},
            new double[] {-9000.0, -9000.0},
            new double[] {-10000.0, -9000.0});
    }

    // A placement whose name lies over the distant cell and nowhere near the flip: what a re-fit
    // that moved a name onto an untouched cell leaves behind.
    private static ClusterAnchor buildNameOverTheDistantCell() {
        return new ClusterAnchor(
            new ClusterIdentity(HEGEMONY, Set.of(DISTANT_SYSTEM)),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            new Segment(10500.0, 12000.0, 13500.0, 12000.0),
            null,
            null,
            200f,
            1);
    }

    // The distant cell's own ring: the band-sized square above, moved clear of every other
    // fixture so nothing can reach it by accident.
    private static List<double[]> buildDistantBandSizedCell() {
        return List.of(
            new double[] {10000.0, 10000.0},
            new double[] {14000.0, 10000.0},
            new double[] {14000.0, 14000.0},
            new double[] {10000.0, 14000.0});
    }

    // A cell large enough to hold the authored band clear of its own border, so a re-bake that
    // ran comes back with runs rather than with the empty band a collapsed inset would give.
    private static List<double[]> buildBandSizedCell() {
        return List.of(
            new double[] {0.0, 0.0},
            new double[] {4000.0, 0.0},
            new double[] {4000.0, 4000.0},
            new double[] {0.0, 4000.0});
    }

    // The distant cell's own edges, cutting the band-sized square its recorded ring describes: the
    // geometry a case needs when the cell it is about is redrawn and so cut afresh from its edges.
    private static List<CellEdge> buildBandSizedIsolatedCell() {
        return List.of(
            new CellEdge(10000, 10000, 14000, 10000, EdgeTarget.REACH_BOUND),
            new CellEdge(14000, 10000, 14000, 14000, EdgeTarget.REACH_BOUND),
            new CellEdge(14000, 14000, 10000, 14000, EdgeTarget.REACH_BOUND),
            new CellEdge(10000, 14000, 10000, 10000, EdgeTarget.REACH_BOUND));
    }

    // A closed four-edge cell whose every edge faces its own outer reach, so no flip anywhere can
    // re-shape it - the cell a case needs when what it is about is the reasons other than a
    // re-shape.
    private static List<CellEdge> buildIsolatedSquareCell(double offsetX) {
        return List.of(
            new CellEdge(offsetX, 0, offsetX + 50, 0, EdgeTarget.REACH_BOUND),
            new CellEdge(offsetX + 50, 0, offsetX + 50, 50, EdgeTarget.REACH_BOUND),
            new CellEdge(offsetX + 50, 50, offsetX, 50, EdgeTarget.REACH_BOUND),
            new CellEdge(offsetX, 50, offsetX, 0, EdgeTarget.REACH_BOUND));
    }

    // A territories holding the given systems, every one of which it also counts settled - the only
    // arrangement production builds, a bloc holding a system by having a colony in it.
    private static PoliticalMapTerritories buildOwnedBy(Map<String, String> factionIdBySystemId) {
        return PoliticalMapTerritoryFixtures.createTerritoriesOwnedBy(
            buildHoldersOf(factionIdBySystemId));
    }
}
