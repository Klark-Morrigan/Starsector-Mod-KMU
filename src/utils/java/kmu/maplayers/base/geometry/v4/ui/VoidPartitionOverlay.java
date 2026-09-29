package kmu.maplayers.base.geometry.v4.ui;

import kmlib.math.geometry.RingRegion;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.render.FillLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.v4.CarriedLines;
import kmu.maplayers.base.geometry.v4.LabelledWall;
import kmu.maplayers.base.geometry.v4.LakeTier;
import kmu.maplayers.base.geometry.v4.LandableFrontage;
import kmu.maplayers.base.geometry.v4.VoidPartition;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What v4 has to show: the pieces of void, the lines each tier laid to divide them, and the
 * stretches of cell border facing them.
 *
 * <p>What is drawn is the walk's own reading - the faces the frontier and the laid lines close
 * into. A tier's lines go into that walk rather than over it: switching the lake coast on does
 * not add a layer above the pieces, it divides them, so the fill redraws as the pieces those
 * lines left. That is what makes a tier checkable - the partition after it is the partition
 * before it with lines in, rather than a second reading laid on top. The lines themselves
 * arrive found - the coast traced, the bridges searched for - which is the point: the smoothing
 * and the search are the other construction's, and v4 only decides where the lines go.
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

    private final ViewerSettings settings;

    // What the last refresh read, kept so a frame paints the void the rest of the frame was
    // drawn from rather than a reading taken while painting.
    private List<RingRegion> regions = List.of();

    private List<List<double[]>> landable = List.of();

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
     * @param fixture     the sector to read, for the sites the pieces are placed against
     * @param lakeReaches the lakes' coast reaches, which are the lines the lake coast lays,
     *                    each running with its lake's water on its left; read off the trace
     *                    the rebuild that calls this made, so the two constructions are drawn
     *                    from one coast
     * @param lakeBridges the bridges across the lakes, asked for only while their switch is
     *                    on: the search behind them runs on the first ask, and with the
     *                    other construction's own bridges off nothing else asks
     */
    public void refresh(
            Map<?, List<CellEdge>> cellEdges,
            SectorFixture fixture,
            List<CellGap> lakeReaches,
            Supplier<List<CellGap>> lakeBridges) {

        var sites = fixture.getSites();
        var coast = settings.isLakeCoastV4Shown()
            ? LakeTier.layCoastWalls(lakeReaches, sites, settings.parameters)
            : NOTHING_LAID;
        var bridges = settings.isLakeBridgesV4Shown()
            ? LakeTier.layBridgeWalls(lakeBridges.get(), sites, settings.parameters)
            : NOTHING_LAID;

        lakeCoastLines = coast.lines();
        lakeBridgeLines = bridges.lines();

        // The walk is what the pieces and the frontage are read off, and nothing else needs
        // it - the laid lines are drawn from the tier, not from the walk. So with neither of
        // those on it is not paid for, even with a tier switched on.
        if (!settings.isVoidPiecesV4Shown() && !settings.isLandableFrontageV4Shown()) {

            regions = List.of();
            landable = List.of();
            return;
        }

        // A tier switched off lays nothing, so the switch takes its lines out of the partition
        // rather than leaving them dividing pieces nobody can see.
        var walls = new ArrayList<LabelledWall>(coast.walls());

        walls.addAll(bridges.walls());

        var partition = VoidPartition.readVoidPartition(
            cellEdges, sites, settings.parameters, walls);

        // Each read only for the layer that draws it: the pieces are inset and smoothed, which
        // is the dearest pass here, and a window showing only the frontage has no use for it.
        regions = settings.isVoidPiecesV4Shown()
            ? collectRegions(partition, settings)
            : List.of();
        landable = settings.isLandableFrontageV4Shown()
            ? collectLandableRuns(partition, lakeReaches)
            : List.of();
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
     * Draws the runs of cell border facing void nothing has captured.
     *
     * @param g2 where to draw, in world space
     */
    public void paintLandableFrontage(Graphics2D g2) {

        if (!settings.isLandableFrontageV4Shown()) {
            return;
        }
        MapPainting.paintLineRuns(g2, landable, settings.landableFrontageV4Colour);
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
            settings.resolveBorderSmoothing());
    }

    // The runs the open pieces face, as the point runs the painting takes - which cell each is
    // on being a fact for a reader of the map and not for the stroke. The reaches are handed
    // over whether the coast is laid or not: with it off no piece has an edge along one, so
    // they are never asked about.
    private static List<List<double[]>> collectLandableRuns(
            VoidPartition partition, List<CellGap> reaches) {

        return LandableFrontage.collectLandableRuns(
                partition.collectPieces(),
                piece -> LakeTier.isCaptured(piece, reaches))
            .stream()
            .map(LandableFrontage.Run::points)
            .toList();
    }
}
