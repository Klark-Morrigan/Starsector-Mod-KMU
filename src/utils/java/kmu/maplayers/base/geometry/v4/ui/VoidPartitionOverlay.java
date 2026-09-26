package kmu.maplayers.base.geometry.v4.ui;

import kmlib.math.geometry.RingRegion;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.render.FillLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.v4.LakeCoast;
import kmu.maplayers.base.geometry.v4.LandableFrontage;
import kmu.maplayers.base.geometry.v4.ReachLine;
import kmu.maplayers.base.geometry.v4.VoidPartition;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What v4 has to show: the pieces of void, the lines each tier laid to divide them, and the
 * stretches of cell border facing them.
 *
 * <p>What is drawn is the walk's own reading - the faces the frontier and the laid lines close
 * into. A tier's lines go into that walk rather than over it: switching the lake coast on does
 * not add a layer above the pieces, it divides them, so the fill redraws as the pieces those
 * lines left. That is what makes a tier checkable - the partition after it is the partition
 * before it with lines in, rather than a second reading laid on top. The lines themselves
 * arrive traced, as reaches, which is the point: the smoothing is the tracer's, and v4 only
 * decides where they go.
 *
 * <p>The frontage is drawn beside the pieces rather than under them, because it is a
 * diagnostic of them: nothing is laid from it yet, and it is on screen so that what a span may
 * be anchored on can be looked at before any span is made to obey it.
 *
 * <p>The void is read on each refresh rather than held from startup, for the reason every other
 * overlay reads its own: the reach and the flattening are knobs, and a copy taken when the
 * window opened would go on drawing the sector those knobs used to describe.
 */
public final class VoidPartitionOverlay {

    private final ViewerSettings settings;

    // What the last refresh read, kept so a frame paints the void the rest of the frame was
    // drawn from rather than a reading taken while painting.
    private List<RingRegion> regions = List.of();

    private List<List<double[]>> landable = List.of();

    private List<List<double[]>> lakeCoastLines = List.of();

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
     * @param lakeReaches the lakes' coast reaches, which are the lines this tier lays; read
     *                    off the trace the rebuild that calls this made, so the two
     *                    constructions are drawn from one coast
     */
    public void refresh(
            Map<?, List<CellEdge>> cellEdges,
            SectorFixture fixture,
            List<ReachLine> lakeReaches) {

        var sites = fixture.getSites();
        var laid = settings.isLakeCoastV4Shown()
            ? LakeCoast.layCoastWalls(lakeReaches, sites, settings.parameters)
            : null;

        lakeCoastLines = laid != null ? laid.reachLines() : List.of();

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
        var partition = VoidPartition.readVoidPartition(
            cellEdges,
            sites,
            settings.parameters,
            laid != null ? laid.walls() : List.of());

        // Each read only for the layer that draws it: the pieces are inset and smoothed, which
        // is the dearest pass here, and a window showing only the frontage has no use for it.
        regions = settings.isVoidPiecesV4Shown()
            ? collectRegions(partition, settings)
            : List.of();
        landable = settings.isLandableFrontageV4Shown()
            ? collectLandableRuns(partition)
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

    // Every piece's landable runs as the point runs the painting takes, which cell each is on
    // being a fact for a reader of the map and not for the stroke.
    private static List<List<double[]>> collectLandableRuns(VoidPartition partition) {

        var runs = new ArrayList<List<double[]>>();

        for (var piece : partition.collectPieces()) {
            for (var run : LandableFrontage.collectLandableRuns(piece)) {
                runs.add(run.points());
            }
        }
        return List.copyOf(runs);
    }
}
