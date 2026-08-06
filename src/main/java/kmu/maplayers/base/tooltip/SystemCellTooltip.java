package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.render.gl.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.CursorTooltipStyle;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.text.TextStyle;
import kmlib.starsector.ui.widgets.tooltip.TooltipLineStyle;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.starsector.ui.widgets.tooltip.TooltipStyle;

import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The shared shape of a map-layer cell tooltip: the hovered system's name on top, the layer's own
 * content below it, one look and one render call for both. A layer tooltip extends this and supplies
 * only its content, so the header, the box style, and the economy precondition are settled in one place
 * and no two layers can drift on them - the difference between two layers' hovers is what they say
 * about the system, never how the box is framed or named. Which lines that content may be written in is
 * {@link CellTooltipRows}.
 *
 * <p>The box opens with one block of its own - the system name and whatever {@linkplain #buildTitleRows
 * title lines} the layer heads it with, read together as the heading - and the layer's own
 * {@linkplain #buildBodySections blocks} follow beneath it. A layer that has nothing to head its box
 * with supplies only the body and gets the plain title-over-body box, which is the ordinary case.
 *
 * <p>Composing the title as a block rather than parting it by hand is what makes the gap under the
 * heading the same gap that parts every block below it: the box states which lines belong together and
 * the widget spends one parting between any two blocks, so no line anywhere asks for room above itself.
 *
 * <p>Naming the system in the header is what makes the hover read as landing on a real system: content
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

    // The face the box ends its key hint in - the very one the game sets its own "Press F1 for more
    // info" line in, so a KM box tells the player about a key the way every vanilla box does. Smaller
    // and narrower than the body, which is what keeps a line about the box from reading as one of its
    // findings.
    private static final StarsectorFont FOOTNOTE_FONT = StarsectorFont.VANILLA_ORBITRON_12_CONDENSED;

    // What parts the key from the words about it. A run is laid where the one before it ended, so the
    // space is the layout's rather than part of either phrase - and stated once, since a hint whose
    // key ran into its verb would read as a single word.
    private static final String KEY_PHRASE_GAP = " ";

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
        var titleRows = buildTitleRows(sector, system);
        var bodySections = buildBodySections(sector, system);
        // Nothing to say about the system - drawing the name alone would only echo the cursor.
        if (titleRows.isEmpty() && bodySections.isEmpty()) {
            return;
        }
        var sections = new ArrayList<TooltipSection>();
        sections.add(buildTitleSection(system, titleRows));
        sections.addAll(bodySections);
        // Added after the emptiness check above rather than counted by it: the hint is about the box
        // rather than about the system, so a box with nothing to say about the system stays undrawn
        // instead of appearing as a lone line offering to expand into nothing.
        buildFooterSection().ifPresent(sections::add);

        CursorTooltipRenderer.render(sections, buildStyle());
    }

    /**
     * Builds the lines belonging to this layer's title block, read together with the system name above
     * the parting that opens the body. Called once per paint with the live sector, after the economy
     * precondition holds; a layer with nothing to head its box with supplies none, which is the ordinary
     * case.
     *
     * <p>Held apart from the body because the two are parted differently, and that parting is what a
     * reader takes the shape of the box from: a title line is read off the system name as more of the
     * heading, while the body below the break is the layer's findings about it. A verdict that settles
     * the whole system - who holds it by decree - belongs above that break; a status, a score, or an
     * entry of any list belongs below it.
     *
     * @param sector the live sector, whose economy the content may read
     * @param system the star system under the cursor
     * @return the title lines, or an empty list when the layer heads its box with the name alone
     */
    protected List<TooltipRow> buildTitleRows(SectorAPI sector, StarSystemAPI system) {
        return List.of();
    }

    /**
     * Builds this layer's content blocks, drawn top to bottom under the heading. Called once per paint
     * with the live sector, after the economy precondition holds. The seam the whole class exists
     * around: the box is framed here and its content is the layer's, so this shape can be shared by
     * layers it names none of.
     *
     * <p>Stated as blocks rather than as lines because how far apart the box's content stands follows
     * from how it is grouped: a layer says which of its lines belong together, and every parting in the
     * box - including the one under the heading above - is then the same one decision.
     *
     * @param sector the live sector, whose economy the content may read
     * @param system the star system under the cursor
     * @return the body blocks, or an empty list when the layer has nothing to show for this system
     */
    protected abstract List<TooltipSection> buildBodySections(SectorAPI sector, StarSystemAPI system);

    /**
     * Names what this box's {@linkplain #resolveExpandedVariant richer counterpart} states beyond it, in
     * the player's words - "score contributions" for a box whose counterpart accounts for the numbers it
     * shows. Supplied by a box taking part in the detail toggle, and empty for one that does not, which
     * is the ordinary case.
     *
     * <p>What it buys is the line at the foot of the box naming the key and what pressing it would do.
     * That line has to say what the player would actually gain, and only the box knows: the framework
     * knows a counterpart exists but not what is in it, so a hint written here would either be vague or
     * would carry one layer's subject matter into the shape every layer shares.
     *
     * <p>Answered for a pair of boxes at once, by whatever they have in common: both the plain box and
     * the counterpart it selects state the same subject, since a player switching either way is being
     * told about the one richer account. Which direction the hint reads is not asked of a box at all -
     * see {@link #buildFooterSection}.
     *
     * @return what the counterpart adds, or empty for a box that takes no part in the detail toggle
     */
    protected Optional<String> resolveExpandedDetailName() {
        return Optional.empty();
    }

    // The line the box ends on, or none at all: the toggle key and what pressing it would do to this
    // box. Its own block, so the shared parting sets it off from the content the way any two blocks are
    // set off - a hint about the box reading as the last line of a list would be read as part of that
    // list.
    //
    // Which way the toggle reads is taken from whether this box still has a counterpart to switch to:
    // the plain box offers one and so offers to show it, and the counterpart the framework selects
    // offers none and so offers to hide itself again. Read off the box being drawn rather than off the
    // shared mode, which is the only way the two cannot disagree - a mode read here could say "hide"
    // over a box that never expanded.
    private Optional<TooltipSection> buildFooterSection() {
        return resolveExpandedDetailName().map(detailName -> new TooltipSection(List.of(
            buildFooterRow(KmuStrings.format(
                resolveExpandedVariant().isPresent()
                    ? KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_SHOW
                    : KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_HIDE,
                detailName)))));
    }

    // The box's heading as one block: the hovered system's name, and any lines the layer heads its box
    // with read on from it. One block rather than a name plus separately-placed lines, because a block
    // is exactly what "these are read together" means - and what leaves the gap beneath them the box's
    // one parting however many lines the layer added.
    private static TooltipSection buildTitleSection(
            StarSystemAPI system,
            List<TooltipRow> titleRows) {

        var rows = new ArrayList<TooltipRow>();
        rows.add(buildHeaderRow(system));
        rows.addAll(titleRows);

        return new TooltipSection(rows);
    }

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

    // The hint itself, in two runs and the game's own two colours for the job: the key picked out in the
    // shade every vanilla button highlights its shortcut with, and the words about it in the grey vanilla
    // states such hints in. Two runs rather than one because that is exactly what vanilla draws - the key
    // is the part the eye is meant to find, and the sentence around it is deliberately quiet.
    //
    // Laid at the box's content edge rather than centred: it sits at the foot of the box the way the
    // game's own does, and centring it would read as a verdict over the content above.
    private static TooltipRow buildFooterRow(String phrase) {
        return TooltipRow
            .createRow(new TextSpan(
                HoverTooltipDetailModeInput.TOGGLE_KEY_NAME,
                StarsectorUiColour.VANILLA_BUTTON_SHORTCUT.resolve()))
            .clearsCrestColumn()
            .continuesWith(new TextSpan(
                KEY_PHRASE_GAP + phrase,
                StarsectorUiColour.VANILLA_GRAY.resolve()))
            .readsAs(TooltipLineStyle.FOOTNOTE);
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
            TooltipStyle
                .createStyle(
                    TextStyle.createStyle(HEADER_FONT),
                    TextStyle.createStyle(BODY_FONT))
                .footnotedIn(TextStyle.createStyle(FOOTNOTE_FONT)),
            OPACITY,
            BORDER_WIDTH,
            StarsectorUiColour.BLACK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_BASE.resolve());
    }
}
