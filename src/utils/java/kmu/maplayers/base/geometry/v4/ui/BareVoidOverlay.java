package kmu.maplayers.base.geometry.v4.ui;

import kmlib.math.geometry.RingRegion;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.render.FillLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.v4.BareVoid;
import kmu.maplayers.base.geometry.v4.LakeCoast;
import kmu.maplayers.base.geometry.v4.LandableFrontage;
import kmu.maplayers.base.geometry.v4.ReachLine;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What v4 has to show: the void the cells close around, before any line divides it, and the
 * stretches of cell border facing it.
 *
 * <p>v4's whole surface on screen for now, and deliberately the least it can be. The
 * constructions either side of the switch have to be comparable from the first frame, and a
 * layer that draws an unfinished division would be compared against a finished one.
 *
 * <p>What is drawn is the walk's own reading - the faces the bare rings close into - and not
 * the sweep's holes, although at this tier the two are the same shapes. Drawing the faces is
 * what puts the walk on screen at all: a line laid into it later shows up as the piece it
 * divides, in this same picture, rather than as a second layer traced separately.
 *
 * <p>The frontage is drawn beside the pieces rather than under them, because it is a
 * diagnostic of them: nothing is laid from it yet, and it is on screen so that what a span may
 * be anchored on can be looked at before any span is made to obey it.
 *
 * <p>A tier's lines go into the walk rather than over it. Switching the lake coast on does not
 * add a layer above the pieces - it divides them, so the fill redraws as the pieces those lines
 * left. That is what makes a tier checkable: the partition after it is the partition before it
 * with lines in, rather than a second reading laid on top. The line itself arrives traced, as
 * reaches, which is the point: the smoothing is the tracer's, and v4 only decides where it goes.
 *
 * <p>The void is read on each refresh rather than held from startup, for the reason every other
 * overlay reads its own: the reach and the flattening are knobs, and a copy taken when the
 * window opened would go on drawing the sector those knobs used to describe.
 */
public final class BareVoidOverlay {

    private final ViewerSettings settings;

    // What the last refresh read, kept so a frame paints the void the rest of the frame was
    // drawn from rather than a reading taken while painting.
    private List<RingRegion> regions = List.of();

    private List<List<double[]>> landable = List.of();

    private List<List<double[]>> lakeCoastLines = List.of();

    public BareVoidOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Reads the void again with every tier laid, and the frontage over it.
     *
     * @param cellEdges  each cell as its adjacency-tagged edges, which is where the line between
     *                   cell and void already stands; handed in rather than built again, since
     *                   the rebuild that calls this has just built them
     * @param fixture     the sector to read, for the sites the pieces are placed against
     * @param lakeReaches the lakes' coast reaches, which are the lines this tier lays; read
     *                    off the trace the rebuild that calls this made, so the two
     *                    constructions are drawn from one coast
     */
    public void refresh(
            Map<?, List<CellEdge>> cellEdges,
            SectorFixture fixture,
            List<ReachLine> lakeReaches) {

        // Nothing else reads this, so with every layer off the walk would be paid for on every
        // rebuild to answer no one.
        if (!settings.isBareVoidShown()
                && !settings.isLandableFrontageV4Shown()
                && !settings.isLakeCoastV4Shown()) {

            regions = List.of();
            landable = List.of();
            lakeCoastLines = List.of();
            return;
        }

        var bare = readVoidWithEveryTierLaid(cellEdges, fixture.getSites(), lakeReaches);

        regions = collectRegions(bare, settings);
        landable = settings.isLandableFrontageV4Shown()
            ? collectLandableRuns(bare)
            : List.of();
    }

    // The walk with every tier so far laid into it, which at present is the one tier.
    //
    // A tier switched off lays nothing, so the switch takes its lines out of the partition
    // rather than leaving them dividing pieces nobody can see.
    private BareVoid readVoidWithEveryTierLaid(
            Map<?, List<CellEdge>> cellEdges,
            List<double[]> sites,
            List<ReachLine> lakeReaches) {

        if (!settings.isLakeCoastV4Shown()) {
            lakeCoastLines = List.of();
            return BareVoid.readBareVoid(cellEdges, sites, settings.parameters);
        }

        var laid = LakeCoast.layCoastWalls(
            lakeReaches,
            DiscUnion.buildAtCellReach(sites, settings.parameters),
            settings.parameters);

        lakeCoastLines = laid.reachLines();

        return BareVoid.readBareVoid(cellEdges, sites, settings.parameters, laid.walls());
    }

    /**
     * Fills each piece of void.
     *
     * @param g2 where to draw, in world space
     */
    public void paintPieces(Graphics2D g2) {

        if (!settings.isBareVoidShown()) {
            return;
        }
        MapPainting.paintRegionFills(
            g2,
            regions,
            new FillLook(
                settings.bareVoidColour, settings.voidFillOpacity, settings.bareVoidColour));
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
    private static List<RingRegion> collectRegions(BareVoid bare, ViewerSettings settings) {

        return PieceRegions.collectDrawableRegions(
            bare.collectPieces(),
            new EdgeInset(settings.voidInsetRule, settings.parameters.borderInset()),
            settings.parameters.miterSpikeLimit(),
            settings.resolveBorderSmoothing());
    }

    // Every piece's landable runs as the point runs the painting takes, which cell each is on
    // being a fact for a reader of the map and not for the stroke.
    private static List<List<double[]>> collectLandableRuns(BareVoid bare) {

        var runs = new ArrayList<List<double[]>>();

        for (var piece : bare.collectPieces()) {
            for (var run : LandableFrontage.collectLandableRuns(piece)) {
                runs.add(run.points());
            }
        }
        return List.copyOf(runs);
    }
}
