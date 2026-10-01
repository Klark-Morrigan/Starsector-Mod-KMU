package kmu.maplayers.base.geometry.v4.ui;

import kmlib.math.geometry.RingRegion;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.geometry.EdgeInsetRule;
import kmu.maplayers.base.geometry.NamedRegion;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.render.FillLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.v4.CarriedLines;
import kmu.maplayers.base.geometry.v4.Face;
import kmu.maplayers.base.geometry.v4.LabelledWall;
import kmu.maplayers.base.geometry.v4.LakeTier;
import kmu.maplayers.base.geometry.v4.LandableFrontage;
import kmu.maplayers.base.geometry.v4.VoidPartition;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What v4 has to show: the pieces of void, the lines each tier laid to divide them, the water
 * each tier's lines closed, and the stretches of cell border facing them.
 *
 * <p>What is drawn is the walk's own reading - the faces the frontier and the laid lines close
 * into. A tier's lines go into that walk rather than over it: switching the lake coast on does
 * not add a layer above the pieces, it divides them, so the fill redraws as the pieces those
 * lines left. That is what makes a tier checkable - the partition after it is the partition
 * before it with lines in, rather than a second reading laid on top. The lines themselves
 * arrive found - the coast traced, the bridges searched for - which is the point: the smoothing
 * and the search are the other construction's, and v4 only decides where the lines go.
 *
 * <p>A tier's water is the same pieces again, told apart by what closed them: no second walk
 * and no second shape, only a second colour over the pieces the tier says are its own.
 *
 * <p>The frontage is drawn beside the pieces rather than under them, because it is a
 * diagnostic of them: it is on screen so that where a bridge may land can be looked at beside
 * the bridges that did.
 *
 * <p>The void is read on each refresh rather than held from startup, for the reason every other
 * overlay reads its own: the reach and the flattening are knobs, and a copy taken when the
 * window opened would go on drawing the sector those knobs used to describe.
 */
public final class VoidPartitionOverlay {

    // What a tier switched off lays: no walls, and nothing to draw.
    private static final CarriedLines.LaidLines NOTHING_LAID =
        new CarriedLines.LaidLines(List.of(), List.of());

    // What the frontage layer shows while it is off.
    private static final LandableFrontage.Frontage NO_FRONTAGE =
        new LandableFrontage.Frontage(List.of(), List.of());

    private final ViewerSettings settings;

    // What the last refresh read, kept so a frame paints the void the rest of the frame was
    // drawn from rather than a reading taken while painting.
    private List<RingRegion> regions = List.of();

    private List<RingRegion> lakeWater = List.of();

    private List<RingRegion> lakeMargin = List.of();

    private List<NamedRegion> lakeNames = List.of();

    private List<List<double[]>> landable = List.of();

    private List<double[]> landablePoints = List.of();

    private List<List<double[]>> lakeCoastLines = List.of();

    private List<List<double[]>> lakeBridgeLines = List.of();

    public VoidPartitionOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Reads the void again with every tier laid, and the frontage over it.
     *
     * @param cellEdges   each cell as its adjacency-tagged edges, which is where the line
     *                    between cell and void already stands; handed in rather than built
     *                    again, since the rebuild that calls this has just built them
     * @param fixture     the sector to read, for the sites the pieces are placed against and
     *                    the system IDs a piece is named from
     * @param lakes       the lakes as the trace hands them over: the coast reaches, which are
     *                    the lines the lake coast lays, each running with its lake's water on
     *                    its left, and the cells round each lake; read off the trace the
     *                    rebuild that calls this made, so the two constructions are drawn from
     *                    one coast
     * @param lakeBridges the bridges across the lakes, asked for only while their switch is
     *                    on: the search behind them runs on the first ask, and with the
     *                    other construction's own bridges off nothing else asks
     */
    public void refresh(
            Map<?, List<CellEdge>> cellEdges,
            SectorFixture fixture,
            LakeTier.TracedLakes lakes,
            Supplier<List<CellGap>> lakeBridges) {

        var sites = fixture.getSites();

        // The shore the tiers' lines end on, read once for both of them.
        var frontier = VoidPartition.collectFrontier(cellEdges);
        var coast = settings.isLakeCoastV4Shown()
            ? LakeTier.layCoastWalls(lakes.reaches(), frontier, sites, settings.parameters)
            : NOTHING_LAID;
        var bridges = settings.isLakeBridgesV4Shown()
            ? LakeTier.layBridgeWalls(lakeBridges.get(), frontier, sites, settings.parameters)
            : NOTHING_LAID;

        lakeCoastLines = coast.lines();
        lakeBridgeLines = bridges.lines();

        // The walk is what the pieces, the tiers' water and the frontage are read off, and
        // nothing else needs it - the laid lines are drawn from the tier, not from the walk.
        // So with none of those on it is not paid for, even with a tier switched on.
        if (!settings.shouldWalkVoidV4()) {
            clearLayers();
            return;
        }

        // A tier switched off lays nothing, so the switch takes its lines out of the partition
        // rather than leaving them dividing pieces nobody can see.
        var partition = VoidPartition.readVoidPartition(
            cellEdges, sites, settings.parameters, joinWalls(coast, bridges));

        regions = settings.isVoidPiecesV4Shown()
            ? collectRegions(partition, settings)
            : List.of();

        readLakeLayers(partition.collectPieces(), coast.walls(), lakes.rings(), fixture);
        readFrontage(partition, coast.walls(), frontier);
    }

    // Every tier's walls as one list for the walk, which divides by all of them at once.
    private static List<LabelledWall> joinWalls(
            CarriedLines.LaidLines coast, CarriedLines.LaidLines bridges) {

        var walls = new ArrayList<LabelledWall>(coast.walls());

        walls.addAll(bridges.walls());

        return walls;
    }

    private void clearLayers() {

        regions = List.of();
        lakeWater = List.of();
        lakeMargin = List.of();
        lakeNames = List.of();
        landable = List.of();
        landablePoints = List.of();
    }

    // The lake tier's own pieces, filled and named. The tier reads each piece once, and each
    // layer takes the pieces of its kind: the water and the margin are disjoint by
    // construction, since a piece is of one kind, and a piece of either is named by its kind.
    //
    // Drawn true: every piece as the walk closed it, with no channel. Here the fill is judged
    // for whether it fills each piece the tier closed exactly once, which a channel would
    // hide - and each piece meets the next along the line that divides them.
    private void readLakeLayers(
            List<Face> pieces,
            List<LabelledWall> coastWalls,
            List<Set<Integer>> lakeRings,
            SectorFixture fixture) {

        var water = new ArrayList<Face>();
        var margin = new ArrayList<Face>();
        var names = new ArrayList<NamedRegion>();

        if (settings.isLakeWaterV4Shown()
                || settings.isLakeMarginV4Shown()
                || settings.isLakeNamesV4Shown()) {

            for (var piece : pieces) {

                var kind = LakeTier.readKind(piece, coastWalls, lakeRings);

                if (kind == LakeTier.Kind.UNTOUCHED) {
                    continue;
                }
                (kind == LakeTier.Kind.LAKE ? water : margin).add(piece);

                if (settings.isLakeNamesV4Shown()) {
                    names.add(NamedRegion.nameRegion(
                        LakeTier.namePiece(
                            piece, kind, fixture.getSites(), fixture.getSystemIds()),
                        piece.boundary()));
                }
            }
        }

        lakeWater = settings.isLakeWaterV4Shown() ? collectTrueRegions(water) : List.of();
        lakeMargin = settings.isLakeMarginV4Shown() ? collectTrueRegions(margin) : List.of();
        lakeNames = List.copyOf(names);
    }

    // What the window shows of the frontage: read only while it is drawn.
    private void readFrontage(
            VoidPartition partition,
            List<LabelledWall> coastWalls,
            List<LabelledWall> frontier) {

        var frontage = settings.isLandableFrontageV4Shown()
            ? collectLandableFrontage(partition, coastWalls, frontier)
            : NO_FRONTAGE;

        landable = frontage.runs().stream().map(LandableFrontage.Run::points).toList();
        landablePoints = frontage.points().stream().map(LandableFrontage.Point::point).toList();
    }

    /**
     * Fills each piece of void.
     *
     * @param g2 where to draw, in world space
     */
    public void paintPieces(Graphics2D g2) {

        if (!settings.isVoidPiecesV4Shown()) {
            return;
        }
        MapPainting.paintRegionFills(
            g2,
            regions,
            new FillLook(
                settings.voidPiecesV4Colour,
                settings.voidFillOpacity,
                settings.voidPiecesV4Colour));
    }

    /**
     * Fills the lake tier's water and its margin, each in its own colour.
     *
     * @param g2 where to draw, in world space
     */
    public void paintLakeFills(Graphics2D g2) {

        if (settings.isLakeWaterV4Shown()) {
            MapPainting.paintRegionFills(
                g2,
                lakeWater,
                settings.resolveWaterLook(settings.lakeWaterV4Colour, settings.lakeWaterV4Colour));
        }
        if (settings.isLakeMarginV4Shown()) {
            MapPainting.paintRegionFills(
                g2,
                lakeMargin,
                settings.resolveWaterLook(
                    settings.lakeMarginV4Colour, settings.lakeMarginV4Colour));
        }
    }

    /**
     * The lake tier's pieces with their names, for the window to write on them.
     *
     * <p>Handed up rather than written here, because names are written in screen space over the
     * finished map, by the window that owns the transform.
     *
     * @return one named region per piece the tier closed; empty while the names are off
     */
    public List<NamedRegion> collectLakeNames() {
        return settings.isLakeNamesV4Shown() ? lakeNames : List.of();
    }

    /**
     * Draws the reaches the lake coast laid.
     *
     * @param g2 where to draw, in world space
     */
    public void paintLakeCoast(Graphics2D g2) {

        if (!settings.isLakeCoastV4Shown()) {
            return;
        }
        MapPainting.paintLineRuns(g2, lakeCoastLines, settings.lakeCoastV4Colour);
    }

    /**
     * Draws the bridges laid across the lakes.
     *
     * @param g2 where to draw, in world space
     */
    public void paintLakeBridges(Graphics2D g2) {

        if (!settings.isLakeBridgesV4Shown()) {
            return;
        }
        MapPainting.paintLineRuns(g2, lakeBridgeLines, settings.lakeBridgesV4Colour);
    }

    /**
     * Draws the runs of cell border facing void nothing has captured, and the points where the
     * void touches a cell without a run.
     *
     * @param g2 where to draw, in world space
     */
    public void paintLandableFrontage(Graphics2D g2) {

        if (!settings.isLandableFrontageV4Shown()) {
            return;
        }
        MapPainting.paintLineRuns(g2, landable, settings.landableFrontageV4Colour);
        MapPainting.paintPointMarks(g2, landablePoints, settings.landableFrontageV4Colour);
    }

    // Shaped and cleaned on the way out rather than held that way, since both the rule and the
    // smoothing are knobs: under NOWHERE what is shaped is the partition itself, so the switch
    // costs a reshape of the same walk rather than a walk of it.
    private static List<RingRegion> collectRegions(
            VoidPartition partition, ViewerSettings settings) {

        return PieceRegions.collectDrawableRegions(
            partition.collectPieces(),
            new EdgeInset(settings.voidInsetRule, settings.parameters.borderInset()),
            settings.parameters.miterSpikeLimit(),
            settings.resolveBorderSmoothing(),
            settings.parameters.measureBoundSagitta());
    }

    // Pieces drawn as the walk closed them: the same sequence under NOWHERE, which hands each
    // piece back as itself, so drawing a tier's water inset later is a change of rule here.
    private List<RingRegion> collectTrueRegions(List<Face> pieces) {

        return PieceRegions.collectDrawableRegions(
            pieces,
            new EdgeInset(EdgeInsetRule.NOWHERE, settings.parameters.borderInset()),
            settings.parameters.miterSpikeLimit(),
            settings.resolveBorderSmoothing(),
            settings.parameters.measureBoundSagitta());
    }

    // What the open pieces face, at the resolution the pieces were walked at. Judged against
    // the coast walls as laid: with the coast off there are none, and no piece has an edge
    // along one. Which cell a run or a point is on is a fact for a reader of the map and not
    // for the stroke, so only the geometry is kept.
    private LandableFrontage.Frontage collectLandableFrontage(
            VoidPartition partition, List<LabelledWall> coastWalls, List<LabelledWall> frontier) {

        return LandableFrontage.collectLandableFrontage(
            partition.collectPieces(),
            piece -> LakeTier.isCaptured(piece, coastWalls),
            frontier,
            settings.parameters.measureBoundSagitta());
    }
}
