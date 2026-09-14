package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlRuns;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;

import kmu.maplayers.base.labels.anchor.DiagnosticPalette;
import kmu.maplayers.base.render.MapFrame;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Collection;
import java.util.List;

/**
 * Paints the debug band-path overlay on the sector (M) map: the ring each cell's presence band
 * would run along, in the shade of what that cell's room lets a band do with it, the ring the
 * cell's own shape denied it in the discarded shade, plus a dot at the point every band starts
 * from.
 *
 * <p>The overlay answers the one question the bands themselves cannot. A cell drawing nothing is
 * the sector's normal state and also every one of its failures - nothing present to report, a ring
 * with no room, a name lying across it - and all of them look identical on the map. The path is
 * what was there underneath: where a band would have run, and by
 * {@link DiagnosticPalette}'s ramp whether the ring ever offered it room.
 *
 * <p>The ramp is read twice, at two scales. Per cell it says what the ring as a whole let a band
 * do; per stretch it separates the ring a band may lie on from the ring too narrow to hold one.
 * Both readings are the same three shades, so a stretch drawn in the discarded colour means what
 * a cell drawn in it means - outline no band is laid on.
 *
 * <p>Its own layer over the bands rather than a mode replacing them, so a band and the path it was
 * laid on can be read against each other - a path drawn over its own band is how a run that leaves
 * the path or stops short of it shows at all.
 *
 * <p>No opacity knobs of its own. Every size and shade here is a diagnostic's, tuned to be read
 * rather than looked at, and a knob answering a question nobody asks costs more than it settles.
 */
public final class CellRibbonPathRenderer {

    // Held rather than named per frame: a section found by reference costs the pass nothing where
    // one found by name is a lookup a frame.
    private static final ProfileSection RENDER_SECTION =
        ProfileSection.registerSection("mapLayer.render.ribbonPaths");

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

    // The packed [x, y] the path is walked from, which is the point a band opens at - named so
    // the dot pass reads by what it marks rather than by bare index.
    private static final int START_X = 0;
    private static final int START_Y = 1;

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
     * @param mapFrame    the scale every coordinate is multiplied by, and the map's own fade
     *                    applied over each path's shade
     */
    public static void renderOnMap(
            Collection<CellRibbonPath> ribbonPaths,
            MapFrame mapFrame) {

        if (ribbonPaths.isEmpty() || mapFrame.isFadedOut()) {
            return;
        }
        // Aliased, like the other measurement overlays: smoothing spreads a one-pixel rule across
        // two, which is the opposite of what a diagnostic read for where a line lands wants.
        GlPasses.runBlendedPass(
            GlBlendMode.ALPHA,
            GlLineQuality.ALIASED,
            () -> {
                try (var renderScope = ActiveProfiler.resolveProfiler().open(RENDER_SECTION)) {
                    drawRibbonPaths(ribbonPaths, mapFrame);
                }
            });
    }

    // The carved ring first, then the ring a band may lie on over it, then the start dots over
    // both. Carved underneath so that where the two meet the usable reading wins the pixel, and
    // the dots last so one is never buried under the path of the cell next door - which is also
    // what lets the point size be bound once for all of them.
    private static void drawRibbonPaths(
            Collection<CellRibbonPath> ribbonPaths,
            MapFrame mapFrame) {

        var pathAlpha = PATH_ALPHA * mapFrame.alphaMult();

        GL11.glLineWidth(PATH_LINE_WIDTH);
        for (var ribbonPath : ribbonPaths) {

            // The ring the cell's own shape denied a band, in the ramp's discarded shade whatever
            // the cell's verdict is: a stretch too narrow for the band is refused outline, and on a
            // cell narrow enough to fold its ring that stretch runs outside the cell's own border.
            // Read as part of the path it would say the geometry escaped the cell; read as its own
            // shade it says how much of the outline the band never had.
            GlColour.set(DiagnosticPalette.DISCARDED_COLOUR, pathAlpha);
            drawStretches(ribbonPath.carvedStretches(), mapFrame.factor());

            GlColour.set(resolveVerdictColour(ribbonPath.verdict()), pathAlpha);
            drawStretches(ribbonPath.heldStretches(), mapFrame.factor());
        }

        // The path's own start - the cell's top centre, where a band begins and runs clockwise
        // from. Marked because it is a convention rather than a feature of the ring: nothing about
        // a traced ring says which point of it a band would open at, and the carve can take the
        // very stretch that opens there.
        GL11.glPointSize(PATH_START_DOT_SIZE);
        GL11.glBegin(GL11.GL_POINTS);
        for (var ribbonPath : ribbonPaths) {
            GlColour.set(resolveVerdictColour(ribbonPath.verdict()), pathAlpha);
            GL11.glVertex2f(
                ribbonPath.startPoint()[START_X] * mapFrame.factor(),
                ribbonPath.startPoint()[START_Y] * mapFrame.factor());
        }
        GL11.glEnd();
    }

    // One open strip per stretch. Open rather than closed because a carved ring is no longer a
    // loop: closing each stretch would draw a chord straight across the gap the carve made, which
    // is precisely the ring being reported as unusable.
    private static void drawStretches(List<float[]> stretches, float factor) {

        for (var stretch : stretches) {
            GlRuns.drawScaled(GL11.GL_LINE_STRIP, stretch, factor);
        }
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
