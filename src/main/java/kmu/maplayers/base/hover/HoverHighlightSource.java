package kmu.maplayers.base.hover;

import kmu.maplayers.base.theme.ElementPaintSelection;

import java.awt.Color;
import java.util.List;

/**
 * The two things about a hovered cell only the layer that owns the map's clusters can answer:
 * the border loops the cell might sit inside, and the shade the cell under it draws in. The
 * cell's own painted extent is the third thing the highlight needs, and it arrives from
 * {@link PaintedCellShapes} rather than from here - the same shapes the cursor was hit-tested
 * against, so the halo cannot trace an outline the hover never resolved.
 *
 * <p>Both are questions about *this* frame's draw lists, so the answers are the geometry the
 * layer actually put on screen rather than a re-derivation of it - which is what keeps a
 * highlight from ever tracing a shape the map is not painting. The highlight pass owns the
 * halo and the wash on top of them, and owns nothing about who holds the cell.
 *
 * <p>Loops are compared by identity downstream on the same terms the inherited shapes are, so
 * an implementation must return the same instance for as long as the geometry behind it is
 * unchanged, and a fresh one once a rebuild or an incremental re-shape has replaced it.
 */
public interface HoverHighlightSource extends PaintedCellShapes {

    /**
     * The border loops the cell could sit inside - every loop the cluster group it fuses into
     * traced anywhere, from which the highlight picks the one that actually encloses it. The
     * whole group, not the one cluster around this cell: which cluster that is is the question
     * the highlight is here to answer, and an implementation deciding it first would be a second
     * rule for the same answer.
     *
     * @param cellId the hovered cell
     * @return the candidate loops as {@code [x, y, x, y, ...]} runs, empty when the cell
     *         fuses into no cluster or the group it fuses into traced no border
     */
    List<float[]> resolveCandidateFrontierLoopsOf(String cellId);

    /**
     * The shade the highlight burns in - the colour of the cell under the cursor, so the
     * halo and the wash say whose space this is.
     *
     * @param cellId         the hovered cell
     * @param paintSelection which of the owner's palette shades the theme points the
     *                       highlight at
     * @return that shade, or null when the selection paints nothing, so the caller skips the
     *         whole pass
     */
    Color resolveHighlightColourOf(String cellId, ElementPaintSelection paintSelection);
}
