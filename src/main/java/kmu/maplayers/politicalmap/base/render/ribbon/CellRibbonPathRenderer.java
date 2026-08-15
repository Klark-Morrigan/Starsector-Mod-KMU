package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlRuns;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.labels.anchor.DiagnosticPalette;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Collection;

/**
 * Paints the debug band-path overlay on the sector (M) map: the ring each cell's presence band
 * would run along, in the shade of what that cell's room lets a band do with it, plus a dot at the
 * point every band starts from.
 *
 * <p>The overlay answers the one question the bands themselves cannot. A cell drawing nothing is
 * the sector's normal state and also every one of its failures - nothing present to report, a ring
 * with no room, a name lying across it - and all of them look identical on the map. The path is
 * what was there underneath: where a band would have run, and by
 * {@link DiagnosticPalette}'s ramp whether the ring ever offered it room.
 *
 * <p>Its own layer over the bands rather than a mode replacing them, so a band and the path it was
 * laid on can be read against each other - a path drawn over its own band is how a run that leaves
 * the path or stops short of it shows at all.
 *
 * <p>No opacity knobs of its own. Every size and shade here is a diagnostic's, tuned to be read
 * rather than looked at, and a knob answering a question nobody asks costs more than it settles.
 */
public final class CellRibbonPathRenderer {

    // The path's stroke width and the start dot's diameter, both in screen pixels: the overlay
    // marks where world geometry landed, so it must stay legible at the zoom the cell is being
    // looked at rather than shrinking with it. Sized to read over the fills and the bands without
    // burying the band it is drawn across.
    private static final float PATH_LINE_WIDTH = 2f;
    private static final float PATH_START_DOT_SIZE = 8f;

    // Near-opaque: a diagnostic rule is read for where it lands, and a faint one cannot be
    // followed across the fills the map lays under it. Still short of solid, so a path over its
    // own band does not hide the band it is drawn to be compared with.
    private static final float PATH_ALPHA = 0.9f;

    // Emits only; never instantiated.
    private CellRibbonPathRenderer() {
    }

    /**
     * Draws every cell's band path for one map frame.
     *
     * <p>Empty unless the player has the overlay on - the bake hands over no path for any cell
     * while it is off - so the normal map pays an empty-map check and nothing else, and no setting
     * is read per frame. A fully faded-out map emits nothing rather than every path at zero
     * effective alpha.
     *
     * @param ribbonPaths the frame's traced paths, one per cell that has one
     * @param factor      the map's world-to-screen scale, applied to every coordinate
     * @param alphaMult   the map's own fade, applied over each path's shade
     */
    public static void renderOnMap(
            Collection<CellRibbonPath> ribbonPaths,
            float factor,
            float alphaMult) {

        if (ribbonPaths.isEmpty() || alphaMult <= 0f) {
            return;
        }
        // Aliased, like the other measurement overlays: smoothing spreads a one-pixel rule across
        // two, which is the opposite of what a diagnostic read for where a line lands wants.
        GlPasses.runBlendedPass(
            GlBlendMode.ALPHA,
            GlLineQuality.ALIASED,
            () -> KmuProfiling.getProfiler().measure(
                "mapLayer.render.ribbonPaths",
                () -> drawRibbonPaths(ribbonPaths, factor, alphaMult)));
    }

    // The rings first, then the start dots over them in one batch, so a dot is never buried under
    // the path of the cell next door and the point size is bound once for all of them.
    private static void drawRibbonPaths(
            Collection<CellRibbonPath> ribbonPaths,
            float factor,
            float alphaMult) {

        GL11.glLineWidth(PATH_LINE_WIDTH);
        for (var ribbonPath : ribbonPaths) {
            GlColour.set(resolveVerdictColour(ribbonPath.verdict()), PATH_ALPHA * alphaMult);

            // Closed by the emission rather than by the traced points, which do not repeat the
            // first vertex: a band runs the ring as a loop, and drawing it as an open line would
            // leave a gap at the very point every band starts from.
            GlRuns.drawScaled(GL11.GL_LINE_LOOP, ribbonPath.centreline(), factor);
        }

        // The path's own start - the cell's top centre, where a band begins and runs clockwise
        // from. Marked because it is a convention rather than a feature of the ring: nothing about
        // a traced loop says which point of it a band would open at.
        GL11.glPointSize(PATH_START_DOT_SIZE);
        GL11.glBegin(GL11.GL_POINTS);
        for (var ribbonPath : ribbonPaths) {
            GlColour.set(resolveVerdictColour(ribbonPath.verdict()), PATH_ALPHA * alphaMult);
            GL11.glVertex2f(
                ribbonPath.centreline()[0] * factor,
                ribbonPath.centreline()[1] * factor);
        }
        GL11.glEnd();
    }

    // The shared diagnostic ramp read as this overlay's own outcomes: green for the path that
    // ships to the player as authored, yellow for the one it took a concession to get, red for the
    // path no band is laid on. Switched over the enum rather than carried on it, so the ramp stays
    // one shared statement of what red, yellow, and green mean across every map diagnostic - and
    // an outcome added later cannot compile without an answer here.
    private static Color resolveVerdictColour(RibbonPathVerdict verdict) {
        return switch (verdict) {
            case LAID_AT_PAD -> DiagnosticPalette.ACCEPTED_COLOUR;
            case LAID_UNPADDED -> DiagnosticPalette.INTERMEDIATE_COLOUR;
            case REFUSED -> DiagnosticPalette.DISCARDED_COLOUR;
        };
    }
}
