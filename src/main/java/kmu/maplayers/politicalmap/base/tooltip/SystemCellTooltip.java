package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.render.gl.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.CursorTooltipStyle;
import kmlib.starsector.ui.widgets.TooltipRow;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared shape of a political-map cell tooltip: the hovered system's name on top, the layer's own
 * content below it, one look and one render call for both. A layer tooltip extends this and supplies
 * only its body, so the header, the box style, and the economy precondition are settled in one place
 * and no two layers can drift on them - the difference between two layers' hovers is what they say
 * about the system, never how the box is framed or named.
 *
 * <p>Naming the system in the header is what makes the hover read as landing on a real system: a body
 * that resolves to nothing at all draws no box, since a lone name repeats what the cursor already sits
 * on.
 *
 * <p>Subclasses are expected to be stateless - the body is rebuilt from the live sector each paint -
 * so one shared instance per layer serves every view that injects it.
 */
public abstract class SystemCellTooltip implements MapHoverTooltip {

    /**
     * The value of a row that carries no number, such as a status or section line. Rendered as-is it
     * draws nothing and measures zero width, so the value column collapses for that row.
     */
    protected static final String NO_SCORE = "";

    // The inset a nested row draws at, so it reads as belonging to the line above it; a top-tier row
    // sits flush at zero. Only the two row builders apply it, which is what keeps the two tiers a
    // choice of builder at the call site rather than an indent every caller has to remember.
    private static final float MEMBER_INDENT = 14f;

    // The insignia body face, a graphics/fonts basename the font cache resolves to a loadable path,
    // and the size every row's text renders at - which is also each row's line height for the box fit.
    private static final String BODY_FONT = "insignia15LTaa";
    private static final double FONT_SIZE = 15d;

    // The box's own look, handed to the tooltip widget as its style: a thin bright frame over a near
    // opaque black fill, so the content reads over the map without blocking it entirely.
    private static final float BORDER_WIDTH = 1f;
    private static final float OPACITY = 0.9f;

    @Override
    public final void renderFor(SectorAPI sector, StarSystemAPI system) {
        // Bodies read the live economy for what a faction holds in the system, so a sector without one
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
     * per paint with the live sector, after the economy precondition holds.
     *
     * @param sector the live sector, whose economy the content may read
     * @param system the star system under the cursor
     * @return the body rows, or an empty list when the layer has nothing to show for this system
     */
    protected abstract List<TooltipRow> buildBodyRows(SectorAPI sector, StarSystemAPI system);

    /**
     * Builds a top-tier row: flush left, its label bright and its value in the highlight colour, so a
     * section heading, a bloc header, or the system name reads as opening a block rather than sitting
     * inside one.
     *
     * @param crestSpritePath the leading crest's texture path, or null for a crestless row
     * @param text            the row's label
     * @param value           the right-aligned value, or {@link #NO_SCORE} for a row carrying none
     * @return the row, ready to add to a body
     */
    protected static TooltipRow buildTopTierRow(String crestSpritePath, String text, String value) {
        return TooltipRow
                .createRow(text, StarsectorUiColor.VANILLA_PLAYER_BRIGHT.resolve())
                .carriesCrest(crestSpritePath)
                .carriesValue(value, StarsectorUiColor.VANILLA_HIGHLIGHT_GOLD.resolve());
    }

    /**
     * Builds a nested row: indented under the top-tier row above it and drawn in the plain text
     * colour, so a section's entry or a bloc's member faction reads as belonging to that block.
     *
     * @param crestSpritePath the leading crest's texture path, or null for a crestless row
     * @param text            the row's label
     * @param value           the right-aligned value, or {@link #NO_SCORE} for a row carrying none
     * @return the row, ready to add to a body
     */
    protected static TooltipRow buildNestedRow(String crestSpritePath, String text, String value) {
        var textColour = StarsectorUiColor.VANILLA_TEXT.resolve();
        return TooltipRow
                .createRow(text, textColour)
                .carriesCrest(crestSpritePath)
                .carriesValue(value, textColour)
                .indentsBy(MEMBER_INDENT);
    }

    /**
     * Builds a nested row whose label carries a trailing marker in the highlight colour - a status or
     * flag called out on the line it qualifies, rather than stated on a line of its own. One place
     * decides that a marker reads gold, so two layers marking different facts still mark them alike.
     *
     * @param crestSpritePath the leading crest's texture path, or null for a crestless row
     * @param text            the row's label
     * @param marker          the qualifier drawn just after the label, in the highlight colour
     * @param value           the right-aligned value, or {@link #NO_SCORE} for a row carrying none
     * @return the row, ready to add to a body
     */
    protected static TooltipRow buildMarkedNestedRow(
            String crestSpritePath, String text, String marker, String value) {
        return buildNestedRow(crestSpritePath, text, value)
                .carriesMarker(marker, StarsectorUiColor.VANILLA_HIGHLIGHT_GOLD.resolve());
    }

    /**
     * Builds a standalone row: flush at the box's left content edge, outside the crest column, in the
     * plain text colour and carrying neither crest nor value - for a line stating something about the
     * hovered system as a whole. Flush rather than inset, since an indent would read as the line
     * belonging to an entry above it, and there is no entry for it to belong to.
     *
     * @param text the row's label
     * @return the row, ready to add to a body
     */
    protected static TooltipRow buildStandaloneRow(String text) {
        return TooltipRow
                .createRow(text, StarsectorUiColor.VANILLA_TEXT.resolve())
                .clearsCrestColumn();
    }

    // The header every cell tooltip opens with: the hovered system's own name, crestless and drawn in
    // the highlight colour, so the body below it never has to repeat which system it describes and the
    // one proper name in the box is the one span that reads gold. Centred over the box rather than laid
    // into the columns below it, since it titles the whole box rather than sitting in its table - which
    // also frees it of the crest gutter and the value column those rows align to.
    private static TooltipRow buildHeaderRow(StarSystemAPI system) {
        return TooltipRow
                .createRow(
                        system.getName(),
                        StarsectorUiColor.VANILLA_HIGHLIGHT_GOLD.resolve())
                .centred();
    }

    // The tooltip's fixed look: the body font and size every row draws in, the shared opacity, and the
    // frame over a black fill in the map's own player palette. Built per paint so its colours resolve
    // live rather than being baked at class load.
    private static CursorTooltipStyle buildStyle() {
        return new CursorTooltipStyle(
                new TextFace(BODY_FONT, FONT_SIZE),
                OPACITY,
                BORDER_WIDTH,
                StarsectorUiColor.BLACK.resolve(),
                StarsectorUiColor.VANILLA_PLAYER_BASE.resolve());
    }
}
