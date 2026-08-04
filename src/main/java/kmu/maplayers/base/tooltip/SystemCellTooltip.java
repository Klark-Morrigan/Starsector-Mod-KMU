package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.render.gl.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.CursorTooltipStyle;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.text.TextStyle;
import kmlib.starsector.ui.widgets.TooltipLineStyle;
import kmlib.starsector.ui.widgets.TooltipRow;
import kmlib.starsector.ui.widgets.TooltipStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared shape of a map-layer cell tooltip: the hovered system's name on top, the layer's own
 * content below it, one look and one render call for both. A layer tooltip extends this and supplies
 * only its body, so the header, the box style, and the economy precondition are settled in one place
 * and no two layers can drift on them - the difference between two layers' hovers is what they say
 * about the system, never how the box is framed or named. Which lines a body may be written in is
 * {@link CellTooltipRows}.
 *
 * <p>Naming the system in the header is what makes the hover read as landing on a real system: a body
 * that resolves to nothing at all draws no box, since a lone name repeats what the cursor already sits
 * on.
 *
 * <p>Subclasses are expected to be stateless - the body is rebuilt from the live sector each paint -
 * so one shared instance per layer serves every view that injects it.
 */
public abstract class SystemCellTooltip implements MapHoverTooltip {

    // The two faces the box draws in, each at its own atlas's native size - which is also that row's line
    // height for the box fit. Titles are set apart from body text by typeface rather than by colour or
    // size alone because that is how the game's own tooltips are set: reusing vanilla's title-over-body
    // pairing is what makes a KM hover read as part of the interface rather than as text laid over it.
    private static final StarsectorFont HEADER_FONT = StarsectorFont.VANILLA_ORBITRON_20AA;
    private static final StarsectorFont BODY_FONT = StarsectorFont.VANILLA_INSIGNIA_15;

    // The box's own look, handed to the tooltip widget as its style: a thin bright frame over a near
    // opaque black fill, so the content reads over the map without blocking it entirely.
    private static final float BORDER_WIDTH = 1f;
    private static final float OPACITY = 0.9f;

    @Override
    public final void renderFor(SectorAPI sector, StarSystemAPI system) {
        // Bodies read the live economy for what a layer holds in the system, so a sector without one
        // (never on the open campaign map, but guarded since the reads assume it) has nothing to show.
        if (sector.getEconomy() == null) {
            return;
        }
        var bodyRows = buildBodyRows(sector, system);
        // Nothing to say about the system - drawing the name alone would only echo the cursor.
        if (bodyRows.isEmpty()) {
            return;
        }
        var rows = new ArrayList<TooltipRow>();
        rows.add(buildHeaderRow(system));

        // The body opens a section under the title, so the name is parted from what follows it rather
        // than reading as the first entry of the list. Set here, not by each layer: every cell tooltip
        // is a title over a body, so the parting belongs to that shape rather than to any one body.
        rows.add(bodyRows
            .get(0)
            .opensSection());

        rows.addAll(bodyRows.subList(1, bodyRows.size()));
        CursorTooltipRenderer.render(rows, buildStyle());
    }

    /**
     * Builds this layer's content rows, drawn top to bottom under the system-name header. Called once
     * per paint with the live sector, after the economy precondition holds. The seam the whole class
     * exists around: the box is framed here and its content is the layer's, so this shape can be shared
     * by layers it names none of.
     *
     * @param sector the live sector, whose economy the content may read
     * @param system the star system under the cursor
     * @return the body rows, or an empty list when the layer has nothing to show for this system
     */
    protected abstract List<TooltipRow> buildBodyRows(SectorAPI sector, StarSystemAPI system);

    // The header every cell tooltip opens with: the hovered system's own name, crestless and drawn in
    // the highlight colour, so the body below it never has to repeat which system it describes and the
    // one proper name in the box is the one span that reads gold. Centred over the box rather than laid
    // into the columns below it, since it titles the whole box rather than sitting in its table - which
    // also frees it of the crest gutter and the value column those rows align to.
    //
    // Reading as a heading is the row saying what it is, not which face it wants: the box's typography
    // below turns that into the title face, so the two decisions - what a line is, how that kind of line
    // looks - stay on the sides that own them.
    private static TooltipRow.CentredRow buildHeaderRow(StarSystemAPI system) {
        return TooltipRow
            .createCentredRow(new TextSpan(
                system.getName(),
                StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve()))
            .readsAs(TooltipLineStyle.HEADER);
    }

    // The tooltip's fixed look: the typography each kind of row draws in, the shared opacity, and the
    // frame over a black fill in the map's own player palette. Built per paint so its colours resolve
    // live rather than being baked at class load.
    //
    // Neither style names a size: each face is a bitmap atlas crisp at exactly one size, and the box has
    // no fit of its own to squeeze text into, so taking the native size is what keeps both kinds of line
    // drawn 1:1 rather than scaled.
    private static CursorTooltipStyle buildStyle() {
        return new CursorTooltipStyle(
            new TooltipStyle(
                TextStyle.createStyle(HEADER_FONT),
                TextStyle.createStyle(BODY_FONT)),
            OPACITY,
            BORDER_WIDTH,
            StarsectorUiColour.BLACK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_BASE.resolve());
    }
}
