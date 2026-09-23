package kmu.maplayers.base.geometry.v4.ui;

import kmlib.math.geometry.RingRegion;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.render.FillLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.v4.BareVoid;
import kmu.maplayers.base.geometry.v4.LandableFrontage;

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

    public BareVoidOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Reads the bare void again, and the frontage over it.
     *
     * @param cellEdges each cell as its adjacency-tagged edges, which is where the line between
     *                  cell and void already stands; handed in rather than built again, since
     *                  the rebuild that calls this has just built them
     * @param fixture   the sector to read, for the sites the pieces are placed against
     */
    public void refresh(Map<?, List<CellEdge>> cellEdges, SectorFixture fixture) {

        // Nothing else reads this, so with both layers off the walk would be paid for on every
        // rebuild to answer no one.
        if (!settings.isBareVoidShown() && !settings.isLandableFrontageV4Shown()) {
            regions = List.of();
            landable = List.of();
            return;
        }

        var bare = BareVoid.readBareVoid(cellEdges, fixture.getSites(), settings.parameters);

        regions = collectRegions(bare, settings);
        landable = settings.isLandableFrontageV4Shown()
            ? collectLandableRuns(bare)
            : List.of();
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
