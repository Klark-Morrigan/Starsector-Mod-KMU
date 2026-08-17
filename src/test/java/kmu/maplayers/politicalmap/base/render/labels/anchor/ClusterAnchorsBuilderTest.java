package kmu.maplayers.politicalmap.base.render.labels.anchor;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.Segment;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.label.BandFitSpecification;
import kmlib.starsector.ui.label.NameFitSpecification;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.LabelFonts;
import kmu.maplayers.base.labels.anchor.AnchorFitFingerprint;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.ClusterIdentity;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.labels.anchor.specifications.AnchorDiagnostics;
import kmu.maplayers.base.labels.anchor.specifications.AnchorSearch;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.base.labels.anchor.specifications.LeanScoring;
import kmu.maplayers.base.render.clusters.ClusterBorderTrace;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.base.visibility.MapVisibilityOverrides;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.maplayers.politicalmap.base.render.territories.FilterSnapshot;
import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the label rebuild's two jobs, which are the ones nothing downstream can recover: it decides
 * whether any label exists at all, and it wires the pass's styling into the search that fits them.
 *
 * <p>The gate is the claim worth guarding hardest, because both of its halves are silent when
 * wrong. The placements feed the drawn names and the debug anchor overlay, so building whenever
 * either is switched on is what keeps them one shared computation - and skipping the build when
 * neither is on is what keeps a rebuild off the sector's whole cluster geometry while nobody is
 * looking. The standing list is cleared ahead of that gate, so switching the labels off drops the
 * anchors already fitted rather than leaving the last pass's overlay frozen on the map.
 *
 * <p>The styling half is pinned through the colour a fitted anchor carries, since that is where a
 * label drifting from the territory beneath it would first show: a bloc's own shade off filter, and
 * the pass's shared desaturation palette for a bloc a filter recedes.
 *
 * <p>What each path leaves labelling its list is pinned alongside, on every path rather than only
 * the fitting one: the rebuild is the only place the live tuning is read, so a list left behind
 * without the rules it was made under can never be labelled afterwards - including the lists the
 * two gates leave empty, which are otherwise indistinguishable from lists still made under
 * current rules.
 *
 * <p>What the search does with that wiring is pinned by
 * {@link kmu.maplayers.base.labels.anchor.ClusterAnchorPlacementTest} and the colour rules by
 * {@link ClusterLabelStylingTest}, so both run for real here and are only ever counted or read back
 * off the anchor. Cells are hand-built 2000-unit squares, comfortably clear of the fixed border
 * channel, so which cells fuse into one cluster is plain to read. The settings, holder, theme, and
 * font reads this class makes for itself are stubbed at their seams: none of them answers outside
 * the game, and the font in particular decides only whether a name is measured or a stand-in
 * band is, which the placements are counted independently of.
 */
final class ClusterAnchorsBuilderTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    // Two cells of one bloc meeting along x = 2000, so the pair proves the rebuild fits them one
    // shared label; the rival's cell shares no edge with anything, so it stays its own cluster.
    private static final String HELD_SYSTEM = "held";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";
    private static final String RIVAL_SYSTEM = "rival";

    // The revision the fixture cells stand at. Opaque to the rebuild - it only travels onto the
    // fingerprint - so one value serves every case, and a case that varies it says so.
    private static final int GEOMETRY_REVISION = 7;

    // Each bloc's two shades, kept distinct between blocs so a label drawn in the wrong bloc's
    // colour names which lookup went astray.
    private static final Color HEGEMONY_PRIMARY = Color.RED;
    private static final Color TRITACHYON_PRIMARY = Color.CYAN;
    private static final DominantHolder HEGEMONY_HOLDER =
        new DominantHolder(HEGEMONY, HEGEMONY_PRIMARY, Color.BLUE);
    private static final DominantHolder TRITACHYON_HOLDER =
        new DominantHolder(TRITACHYON, TRITACHYON_PRIMARY, Color.DARK_GRAY);

    // The shades a receded bloc recolours to, garish so a label that kept its own colour through a
    // recede is unmistakable.
    private static final FactionPalette DESATURATION_PALETTE =
        new FactionPalette(Color.GREEN, Color.YELLOW);

    // A palette no unfiltered case should ever reach for, for the same reason.
    private static final FactionPalette UNUSED_PALETTE =
        new FactionPalette(Color.MAGENTA, Color.MAGENTA);

    // Full opacity throughout, so an asserted colour is the resolved shade itself rather than a
    // faded version of it; how a name fades is pinned by the styling's own suite.
    private static final double FULL_OPACITY = 1.0;

    // The border trace the fitted cluster rings are welded and mitred with, loose enough to chain
    // the hand-built corners and at the miter limit the shipped border uses.
    private static final double WELD_TOLERANCE = 1.0;
    private static final double MITER_LIMIT = 4.0;

    // The search tuning every case runs under: no end inset or icon keep-out, a small direction fan
    // and a single centre offset (the fixtures are wide rectangles, so the winner is horizontal
    // whatever the fan's resolution), no slope penalty, no diagnostics, and a single line free to
    // grow to any font, sized to a tolerance finer than anything these cases read back. Sized so a
    // label fits every fixture cluster - a case that comes back empty did so because of the gate,
    // not because the geometry ran out of room.
    private static final double FINE_FONT_TOLERANCE = 0.01;
    private static final LabelAnchorSpecification ANCHOR_SPECIFICATION =
        new LabelAnchorSpecification(
            new AnchorSearch(
                new ClusterBorderTrace(WELD_TOLERANCE, MITER_LIMIT),
                3,
                1),
            new LeanScoring(0.0, 2.0, 0.0),
            new AnchorDiagnostics(false, false),
            new BandFitSpecification(0.0, 0.0, FINE_FONT_TOLERANCE),
            new NameFitSpecification(0.0, 2000.0, 1, 1.0));

    // Both groups' names pointed at the bright primary shade at full opacity, so a label's colour
    // reads back as the palette shade its bloc resolved to.
    private static final BlocNameStyles NAME_STYLES = new BlocNameStyles(
        new ElementStyle(
            FactionPaletteSlot.PRIMARY,
            FULL_OPACITY),
        new ElementStyle(
            FactionPaletteSlot.PRIMARY,
            FULL_OPACITY));

    // What every path here has to report having produced its list under: the stubbed tuning and
    // the revision it was handed. Named once because the claim is that all four paths answer the
    // same thing - a case restating the pair would let one of them drift and still read as
    // asserting the shared rule.
    private static final AnchorFitFingerprint FITTED_UNDER =
        new AnchorFitFingerprint(ANCHOR_SPECIFICATION, GEOMETRY_REVISION);

    // A rebuild standing at a different geometry revision, so the placements a pass left behind
    // were made under rules that no longer hold. One int apart from FITTED_UNDER, since the
    // claim is that any difference at all drops the whole carry-over rather than that this
    // particular input is special.
    private static final int MOVED_GEOMETRY_REVISION = GEOMETRY_REVISION + 1;

    // What a caller's pair says while the placements standing in it were fitted under rules
    // this rebuild no longer runs under. Seeded by the cases that ask what a rebuild leaves
    // behind, so what they read back is this pass's answer rather than a reading that was
    // already there before they called.
    private static final AnchorFitFingerprint FITTED_UNDER_MOVED_RULES =
        new AnchorFitFingerprint(ANCHOR_SPECIFICATION, MOVED_GEOMETRY_REVISION);

    private static final Map<String, List<CellEdge>> EDGES = listOrderedEdges();
    private static final Map<String, double[]> SITES = Map.of(
        HELD_SYSTEM, new double[] {1000, 1000},
        NEIGHBOUR_SYSTEM, new double[] {3000, 1000},
        RIVAL_SYSTEM, new double[] {21000, 1000});

    // The sector is never walked: the one question the rebuild asks of it - who holds what - is
    // stubbed at the resolver, so this stands for the argument those stubs match on. The
    // desaturation palette the debug path resolves does read it, and answers a neutral fallback.
    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final CellGeometryCache geometryCacheMock = mock(CellGeometryCache.class);
    private final PoliticalMapView viewMock = mock(PoliticalMapView.class);

    // The cells every case hands over, carrying the revision they stand at. Paired once because
    // the two travel as one fact: a case that means to fit against geometry standing somewhere
    // else builds its own pair, which is what makes that difference the thing the case is about.
    private final RevisedCellGeometry cellGeometry =
        new RevisedCellGeometry(geometryCacheMock, GEOMETRY_REVISION);

    // The pair the caller owns across rebuilds, standing at its never-fitted reading: no
    // placements and no rules recorded for them, which is what a session's first rebuild and one
    // after a discard both start from. Nothing is offered for reuse from that reading, so a case
    // that does not seed it reads as the whole search it was written as.
    private final StandingClusterAnchors standingAnchors = new StandingClusterAnchors();
    
    private MockedStatic<KmuPoliticalMapSettings> settingsMock;
    private MockedStatic<NameFormatPreference> nameFormatMock;
    private MockedStatic<LabelAnchorSpecification> specificationMock;
    private MockedStatic<BlocNameStyles> nameStylesMock;
    private MockedStatic<LabelFonts> fontsMock;
    private MockedStatic<RenderStyleReader> styleReaderMock;
    private MockedStatic<SectorPolitics> politicsMock;
    private MockedStatic<MapVisibilityOverrides> visibilityOverridesMock;

    @BeforeEach
    void openTheSettingsHolderAndFontSeams() {

        settingsMock = mockStatic(KmuPoliticalMapSettings.class);
        nameFormatMock = mockStatic(NameFormatPreference.class);
        specificationMock = mockStatic(LabelAnchorSpecification.class);
        nameStylesMock = mockStatic(BlocNameStyles.class);
        fontsMock = mockStatic(LabelFonts.class);
        styleReaderMock = mockStatic(RenderStyleReader.class);
        politicsMock = mockStatic(SectorPolitics.class);
        visibilityOverridesMock = mockStatic(MapVisibilityOverrides.class);

        // The debug path opens its own pass, which samples the reveal toggles; no LunaLib
        // answers outside the game, so the no-reveal view stands in for the read.
        visibilityOverridesMock
            .when(MapVisibilityOverrides::readFromLunaSettings)
            .thenReturn(MapVisibilityOverrides.NONE);

        // The live tuning and name styling arrive as the data the rebuild reads them into, so a
        // case states its gate and its styling and nothing about the dozens of knobs behind them.
        specificationMock
            .when(LabelAnchorSpecification::readFromLunaSettings)
            .thenReturn(ANCHOR_SPECIFICATION);
        nameStylesMock
            .when(BlocNameStyles::readFromLunaSettings)
            .thenReturn(NAME_STYLES);
        styleReaderMock
            .when(RenderStyleReader::readGlobalStyle)
            .thenReturn(ThemeFixtures.createInertGlobalStyle());

        // No label font resolves outside the game, which is the fallback the fit is designed to
        // take: the aspect stand-in sizes a band and no name is drawn from it. The placements are
        // counted and read for colour, so which of the two sized them does not move a case.
        fontsMock
            .when(LabelFonts::loadMapLabelFont)
            .thenReturn(null);

        // Names drawn and the debug overlay off by default, so a case names only the half of the
        // gate it is about.
        stubNameFormat(FactionNameFormatChoice.FULL);
        stubAnchorOverlay(false);

        // A view that groups every faction as its own bloc and adjusts none of them, so a case's
        // observed colour comes from the holder and the filter alone.
        when(viewMock.resolveGrouping())
            .thenReturn(HolderGrouping.identity());
        when(viewMock.resolveBlocStyleAdjustment(any(), any()))
            .thenReturn(ElementStyleAdjustment.NONE);

        when(geometryCacheMock.getCellEdgesByCellId())
            .thenReturn(EDGES);
        when(geometryCacheMock.getSiteBySystemId())
            .thenReturn(SITES);
        when(geometryCacheMock.getSystemIdByCellId())
            .thenReturn(listIdentityCellsFor(EDGES.keySet()));
    }

    @AfterEach
    void closeTheSettingsHolderAndFontSeams() {

        visibilityOverridesMock.close();
        politicsMock.close();
        styleReaderMock.close();
        fontsMock.close();
        nameStylesMock.close();
        specificationMock.close();
        nameFormatMock.close();
        settingsMock.close();
    }

    @Nested
    class RebuildClusterAnchors {

        @Test
        void rebuildClusterAnchorsFitsOneLabelPerContiguousCluster() {
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                buildUnfilteredStyling(Map.of(
                    HELD_SYSTEM,
                    HEGEMONY_HOLDER,
                    NEIGHBOUR_SYSTEM,
                    HEGEMONY_HOLDER,
                    RIVAL_SYSTEM,
                    TRITACHYON_HOLDER)));

            // Two labels, not three: the two touching cells of one bloc are named once between
            // them, which is what makes a label read as naming a territory rather than a system.
            assertThat(standingAnchors.getAnchors())
                .hasSize(2);
        }

        @Test
        void rebuildClusterAnchorsDrawsEachClusterInItsOwnBlocsShade() {
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                buildUnfilteredStyling(Map.of(
                    HELD_SYSTEM,
                    HEGEMONY_HOLDER,
                    NEIGHBOUR_SYSTEM,
                    HEGEMONY_HOLDER,
                    RIVAL_SYSTEM,
                    TRITACHYON_HOLDER)));

            // The clusters come back in first-seen cell order, so the fused pair leads and the
            // rival follows - each carrying the shade its own holder resolved to rather than one
            // shared pick, which is the whole point of resolving colour per bloc.
            assertThat(standingAnchors.getAnchors().get(0).colour())
                .isEqualTo(HEGEMONY_PRIMARY);
            assertThat(standingAnchors.getAnchors().get(1).colour())
                .isEqualTo(TRITACHYON_PRIMARY);
        }

        @Test
        void rebuildClusterAnchorsRecedesANonSpotlitBlocToThePassDesaturationPalette() {
            // Under a filter every bloc but the spotlit one recedes, and its label has to recede
            // with its fill: the shared palette this pass recoloured that fill to is the one the
            // name draws in, so the two cannot drift apart while a spotlight is up.
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                new ClusterLabelStylingSnapshot(
                    Map.of(RIVAL_SYSTEM, TRITACHYON_HOLDER),
                    DESATURATION_PALETTE,
                    new ViewGrouping(viewMock, HolderGrouping.identity()),
                    new FilterSnapshot(
                        HEGEMONY,
                        new ElementStyleAdjustment(FULL_OPACITY, true),
                        Set.of(),
                        Set.of())));

            assertThat(standingAnchors.getAnchors().get(0).colour())
                .isEqualTo(Color.GREEN);
        }

        @Test
        void rebuildClusterAnchorsFitsLabelsForTheOverlayWhenNamesAreOff() {
            // The debug anchor overlay draws the same placements the names hang off, so it has to
            // be able to hold the search open on its own - otherwise the diagnostic shows nothing
            // exactly when the player turned the names off to look at it.
            stubNameFormat(FactionNameFormatChoice.NONE);
            stubAnchorOverlay(true);

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                buildUnfilteredStyling(Map.of(HELD_SYSTEM, HEGEMONY_HOLDER)));

            assertThat(standingAnchors.getAnchors())
                .hasSize(1);
        }

        @Test
        void rebuildClusterAnchorsFitsNothingWhenNamesAndTheOverlayAreBothOff() {
            // Neither consumer is looking, so the sector's whole cluster search is skipped rather
            // than run for placements nothing will draw.
            stubNameFormat(FactionNameFormatChoice.NONE);

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                buildUnfilteredStyling(Map.of(HELD_SYSTEM, HEGEMONY_HOLDER)));

            assertThat(standingAnchors.getAnchors())
                .isEmpty();
        }

        @Test
        void rebuildClusterAnchorsDropsStandingLabelsEvenWhenItFitsNone() {
            // The list is the overlay's own, so the gate has to empty it rather than leave it
            // alone: switching the names off has to take the fitted labels off the map, not
            // freeze the last pass's on it.
            stubNameFormat(FactionNameFormatChoice.NONE);
            standingAnchors.replaceAnchors(List.of(buildStaleAnchor()), FITTED_UNDER_MOVED_RULES);

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                buildUnfilteredStyling(Map.of(HELD_SYSTEM, HEGEMONY_HOLDER)));

            assertThat(standingAnchors.getAnchors())
                .isEmpty();
        }

        @Test
        void rebuildClusterAnchorsRecordsTheTuningAndGeometryItFittedUnder() {
            // The placements it leaves behind are only reusable if something states the rules
            // they were sized under, and only this rebuild read them - so the pair it leaves
            // has to name the tuning it fitted with and the geometry it fitted against, not
            // some later re-read of either.
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                buildUnfilteredStyling(Map.of(HELD_SYSTEM, HEGEMONY_HOLDER)));

            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(FITTED_UNDER);
        }

        @Test
        void rebuildClusterAnchorsCarriesStandingPlacementsItFittedUnderTheSameRules() {
            // The rebuild is the only place that knows both what it is about to fit under and
            // what the pair in front of it was fitted under, so it is where the carry-over is
            // decided. Run twice over an unchanged sector, with its own previous answer standing
            // in the pair, it must hand the placements themselves on rather than searching the
            // same clusters again - the accepted line being the very object the first pass
            // produced is what says so.
            var styling = buildUnfilteredStyling(Map.of(
                HELD_SYSTEM,
                HEGEMONY_HOLDER,
                NEIGHBOUR_SYSTEM,
                HEGEMONY_HOLDER));

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                styling);

            var firstPassAxis = standingAnchors.getAnchors().get(0).acceptedAxis();

            // Guarded, because two collapsed passes would both carry a null line and the
            // identity assertion below would hold without anything having been carried.
            assertThat(firstPassAxis)
                .isNotNull();

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                styling);

            assertThat(standingAnchors.getAnchors().get(0).acceptedAxis())
                .isSameAs(firstPassAxis);
        }

        @Test
        void rebuildClusterAnchorsRefitsEveryClusterWhenTheRulesItFittedUnderMoved() {
            // The keep-out sites every box is trimmed clear of are the whole sector's, so a
            // recut of the cells moves fits no membership change would touch - and no cluster's
            // identity can see that. The fingerprint is what catches it, and it drops the whole
            // carry-over rather than any part of it, so a moved rule leaves the rebuild exactly
            // as total as it was before reuse existed.
            var styling = buildUnfilteredStyling(Map.of(
                HELD_SYSTEM,
                HEGEMONY_HOLDER,
                NEIGHBOUR_SYSTEM,
                HEGEMONY_HOLDER));

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                styling);

            var firstPassAxis = standingAnchors.getAnchors().get(0).acceptedAxis();

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                new RevisedCellGeometry(geometryCacheMock, MOVED_GEOMETRY_REVISION),
                sectorMock,
                styling);

            assertThat(standingAnchors.getAnchors().get(0).acceptedAxis())
                .isNotSameAs(firstPassAxis);
        }

        @Test
        void rebuildClusterAnchorsLabelsTheListTheSkippedFitEmpties() {
            // The gate empties the list rather than leaving it alone, so the list it leaves
            // needs labelling exactly as a fitted one does: an unlabelled list is
            // indistinguishable from one made under rules that still hold. The two move
            // together, so a gate that emptied the list and left the previous pass's rules
            // standing beside it would offer those placements to a later rebuild that has none.
            stubNameFormat(FactionNameFormatChoice.NONE);
            standingAnchors.replaceAnchors(List.of(buildStaleAnchor()), FITTED_UNDER_MOVED_RULES);

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                buildUnfilteredStyling(Map.of(HELD_SYSTEM, HEGEMONY_HOLDER)));

            assertThat(standingAnchors.getAnchors())
                .isEmpty();
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(FITTED_UNDER);
        }

        @Test
        void rebuildClusterAnchorsReportsNothingDisturbedWhenEveryPlacementCarriedOver() {
            // The rebuild that changes nothing, which is most of them: every cluster still names
            // the same members and keeps the placement it had, so anything laid around those
            // names may stand. Reported off the same pass that decided the carry-over, so the two
            // cannot disagree about which names moved.
            var styling = buildUnfilteredStyling(Map.of(
                HELD_SYSTEM,
                HEGEMONY_HOLDER,
                NEIGHBOUR_SYSTEM,
                HEGEMONY_HOLDER));

            ClusterAnchorsBuilder.rebuildClusterAnchors(
                standingAnchors,
                cellGeometry,
                sectorMock,
                styling);

            // Guarded, because a pass that fitted nothing would leave two empty lists to compare
            // and report nothing disturbed without having carried anything over.
            assertThat(standingAnchors.getAnchors())
                .isNotEmpty();

            assertThat(ClusterAnchorsBuilder
                    .rebuildClusterAnchors(standingAnchors, cellGeometry, sectorMock, styling)
                    .isDisturbingNothing())
                .isTrue();
        }

        @Test
        void rebuildClusterAnchorsReportsTheRoomGivenUpByTheFitItSkipped() {
            // Switching the names off empties the standing list, which is as much a change to
            // what a map has room for as a re-fit is: the ring those words covered is free now,
            // and whatever kept clear of them has to be told.
            stubNameFormat(FactionNameFormatChoice.NONE);
            standingAnchors.replaceAnchors(
                List.of(buildStandingAnchorOccupyingRoom()),
                FITTED_UNDER_MOVED_RULES);

            assertThat(ClusterAnchorsBuilder
                    .rebuildClusterAnchors(
                        standingAnchors,
                        cellGeometry,
                        sectorMock,
                        buildUnfilteredStyling(Map.of(HELD_SYSTEM, HEGEMONY_HOLDER)))
                    .isDisturbingNothing())
                .isFalse();
        }
    }

    @Nested
    class RebuildClusterAnchorsFromSector {

        @Test
        void rebuildClusterAnchorsFromSectorFitsLabelsToTheSectorsOwnHolders() {
            // The border-tracing diagnostic builds no draw lists to borrow a holder map from, so
            // this path resolves one itself - under the view's own grouping, so the labels key off
            // the same snapshot their names and colours are classified against.
            stubAnchorOverlay(true);
            stubSectorHolders(Map.of(
                HELD_SYSTEM,
                HEGEMONY_HOLDER,
                NEIGHBOUR_SYSTEM,
                HEGEMONY_HOLDER,
                RIVAL_SYSTEM,
                TRITACHYON_HOLDER));

            ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
                standingAnchors,
                cellGeometry,
                sectorMock,
                viewMock);

            assertThat(standingAnchors.getAnchors())
                .hasSize(2);
            assertThat(standingAnchors.getAnchors().get(0).colour())
                .isEqualTo(HEGEMONY_PRIMARY);
        }

        @Test
        void rebuildClusterAnchorsFromSectorFitsLabelsThoughTheNameFormatDrawsNone() {
            // This path exists for the overlay, so the name format has no say over it: the
            // diagnostic still draws its dots for a player reading the map by colour alone.
            stubNameFormat(FactionNameFormatChoice.NONE);
            stubAnchorOverlay(true);
            stubSectorHolders(Map.of(HELD_SYSTEM, HEGEMONY_HOLDER));

            ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
                standingAnchors,
                cellGeometry,
                sectorMock,
                viewMock);

            assertThat(standingAnchors.getAnchors())
                .hasSize(1);
        }

        @Test
        void rebuildClusterAnchorsFromSectorRecordsWhatItFittedUnder() {
            // This path delegates the fit, so it must hand its own caller's pair down rather
            // than fitting into something of its own: the caller of the debug view is left
            // holding the same labelled placements the production caller is.
            stubAnchorOverlay(true);
            stubSectorHolders(Map.of(HELD_SYSTEM, HEGEMONY_HOLDER));

            ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
                standingAnchors,
                cellGeometry,
                sectorMock,
                viewMock);

            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(FITTED_UNDER);
        }

        @Test
        void rebuildClusterAnchorsFromSectorCarriesStandingPlacementsThroughToTheSharedFit() {
            // This path delegates the fit, so it has to hand the caller's pair down whole.
            // Dropping the standing half on the way would leave the diagnostic view re-searching
            // every cluster on every rebuild while the production view reuses - a difference
            // nothing but the frame time would show.
            stubAnchorOverlay(true);
            stubSectorHolders(Map.of(
                HELD_SYSTEM,
                HEGEMONY_HOLDER,
                NEIGHBOUR_SYSTEM,
                HEGEMONY_HOLDER));

            ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
                standingAnchors,
                cellGeometry,
                sectorMock,
                viewMock);

            var firstPassAxis = standingAnchors.getAnchors().get(0).acceptedAxis();

            // Guarded, because two collapsed passes would both carry a null line and the
            // assertion below would hold without anything having been carried.
            assertThat(firstPassAxis)
                .isNotNull();

            ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
                standingAnchors,
                cellGeometry,
                sectorMock,
                viewMock);

            assertThat(standingAnchors.getAnchors().get(0).acceptedAxis())
                .isSameAs(firstPassAxis);
        }

        @Test
        void rebuildClusterAnchorsFromSectorSkipsTheEconomyScanWhileTheOverlayIsOff() {
            // Resolving holders walks the whole economy, so the toggle gates the read itself and
            // not just the drawing - the cost is only paid while someone is looking at the
            // overlay. The standing labels still go, as on every other path.
            standingAnchors.replaceAnchors(List.of(buildStaleAnchor()), FITTED_UNDER_MOVED_RULES);

            ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
                standingAnchors,
                cellGeometry,
                sectorMock,
                viewMock);

            assertThat(standingAnchors.getAnchors())
                .isEmpty();

            politicsMock.verifyNoInteractions();

            // Skipping the scan does not excuse the list from being labelled: this path's
            // caller is left holding the same labelled placements the shared path's is.
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(FITTED_UNDER);
        }
    }

    // The styling one unfiltered pass resolves: the given holders under the plain faction view,
    // with a palette nothing should recolour to since no bloc recedes off filter.
    private ClusterLabelStylingSnapshot buildUnfilteredStyling(
            Map<String, DominantHolder> holderBySystemId) {
        return new ClusterLabelStylingSnapshot(
            holderBySystemId,
            UNUSED_PALETTE,
            new ViewGrouping(viewMock, HolderGrouping.identity()),
            FilterSnapshot.unfiltered());
    }

    private void stubNameFormat(FactionNameFormatChoice nameFormat) {
        nameFormatMock
            .when(NameFormatPreference::getSelectedNameFormat)
            .thenReturn(nameFormat);
    }

    private void stubAnchorOverlay(boolean isOverlayShown) {
        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapShowClusterAnchors)
            .thenReturn(isOverlayShown);
    }

    // The holders the debug path's own resolve answers, matched on the grouping the view hands it
    // so a pass that classified under some other snapshot would find no stub.
    private void stubSectorHolders(Map<String, DominantHolder> holderBySystemId) {
        politicsMock
            .when(() -> SectorPolitics.resolveDominantHolderBySystemId(
                argThat((HolderPass pass) -> HolderGrouping.identity().equals(pass.grouping()))))
            .thenReturn(holderBySystemId);
    }

    // A placement left over from an earlier pass, for the cases that ask whether the standing list
    // is cleared. Only its presence is read, so every field is inert.
    private static ClusterAnchor buildStaleAnchor() {
        return new ClusterAnchor(
            new ClusterIdentity("stale", Set.of("stale")),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            null,
            null,
            null,
            0f,
            0);
    }

    // A placement left over from an earlier pass that does occupy room on the map, for the cases
    // asking what a rebuild reports having disturbed - the stale placement above accepted no line,
    // so it takes up nothing and its going disturbs nobody.
    private static ClusterAnchor buildStandingAnchorOccupyingRoom() {
        return new ClusterAnchor(
            new ClusterIdentity("stale", Set.of("stale")),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            new Segment(0, 1000, 2000, 1000),
            null,
            null,
            200f,
            1);
    }

    // The three fixture cells in first-seen order, so which cluster a case reads back at index 0 is
    // fixed by the map rather than by a hash.
    private static Map<String, List<CellEdge>> listOrderedEdges() {
        var edgesByCellId = new LinkedHashMap<String, List<CellEdge>>();

        edgesByCellId.put(HELD_SYSTEM, List.of(
            buildEdgeFacing(0, 0, 2000, 0, null),
            buildEdgeFacing(2000, 0, 2000, 2000, NEIGHBOUR_SYSTEM),
            buildEdgeFacing(2000, 2000, 0, 2000, null),
            buildEdgeFacing(0, 2000, 0, 0, null)));

        edgesByCellId.put(NEIGHBOUR_SYSTEM, List.of(
            buildEdgeFacing(2000, 0, 4000, 0, null),
            buildEdgeFacing(4000, 0, 4000, 2000, null),
            buildEdgeFacing(4000, 2000, 2000, 2000, null),
            buildEdgeFacing(2000, 2000, 2000, 0, HELD_SYSTEM)));
            
        edgesByCellId.put(RIVAL_SYSTEM, List.of(
            buildEdgeFacing(20000, 0, 22000, 0, null),
            buildEdgeFacing(22000, 0, 22000, 2000, null),
            buildEdgeFacing(22000, 2000, 20000, 2000, null),
            buildEdgeFacing(20000, 2000, 20000, 0, null)));
            
        return edgesByCellId;
    }

    // Every cell drawing as its own star - the partition the cluster walk runs over when no system
    // holds cells beyond its own.
    private static Map<String, String> listIdentityCellsFor(Iterable<String> cellIds) {
        var systemIdByCellId = new LinkedHashMap<String, String>();
        for (var cellId : cellIds) {
            systemIdByCellId.put(cellId, cellId);
        }
        return systemIdByCellId;
    }

    // One cell edge facing the given neighbour system, or the reach bound when it is null.
    private static CellEdge buildEdgeFacing(
            double x1,
            double y1,
            double x2,
            double y2,
            String neighbourSystemId) {
        return new CellEdge(
            x1,
            y1,
            x2,
            y2,
            neighbourSystemId == null
                ? EdgeTarget.REACH_BOUND
                : new EdgeTarget.AcrossSystem(neighbourSystemId));
    }
}
