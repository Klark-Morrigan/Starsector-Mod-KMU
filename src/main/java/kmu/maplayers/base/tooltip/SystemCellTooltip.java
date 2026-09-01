package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.render.gl.tooltip.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.tooltip.CursorTooltipStyle;
import kmlib.starsector.ui.render.gl.tooltip.TooltipLeaderLineStyle;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.text.TextStyle;
import kmlib.starsector.ui.widgets.tooltip.TooltipLineGaps;
import kmlib.starsector.ui.widgets.tooltip.TooltipLineStyle;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.starsector.ui.widgets.tooltip.TooltipStyle;

import kmu.settings.KmuMapLayerSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

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
 * {@linkplain #composeBody blocks} follow beneath it. A layer that has nothing to head its box
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
    // info" line in, so a KM box tells the player about a key the way every vanilla box does. Its
    // narrowness is what sets the line apart from the body; the size is not, so it is drawn at the
    // body's rather than at the atlas's own 12, which reads as fine print beside 15pt content.
    private static final StarsectorFont FOOTNOTE_FONT = StarsectorFont.VANILLA_ORBITRON_12_CONDENSED;

    // The box's own look, handed to the tooltip widget as its style: a thin bright frame over a near
    // opaque black fill, so the content reads over the map without blocking it entirely.
    private static final float BORDER_WIDTH = 1f;
    private static final float OPACITY = 0.9f;

    // The two depths the tier gap sliders are bound to. A box states the terms one listed thing's
    // number was summed from two steps under its own voice, and breaks one of those terms down a step
    // below that - so those are the runs of like lines long enough to be worth tightening, whatever a
    // given layer lists there. Named here rather than in the layer that fills them because the box is
    // shared: two layers binding the sliders to different depths would leave the same knob doing
    // different things depending on which box is open.
    private static final int TIER_2_LEVEL = 2;
    private static final int TIER_3_LEVEL = 3;

    @Override
    public final void renderFor(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        // Bodies read the live economy for what a layer holds in the system, so a sector without one
        // (never on the open campaign map, but guarded since the reads assume it) has nothing to show.
        if (sector.getEconomy() == null) {
            return;
        }
        var titleRows = buildTitleRows(sector, system);
        var body = composeBody(sector, system, detailLevel);
        // Nothing to say about the system - drawing the name alone would only echo the cursor.
        if (titleRows.isEmpty() && body.sections().isEmpty()) {
            return;
        }
        var sections = new ArrayList<TooltipSection>();
        sections.add(buildTitleSection(system, titleRows));
        sections.addAll(body.sections());
        // Added after the emptiness check above rather than counted by it: the hint is about the box
        // rather than about the system, so a box with nothing to say about the system stays undrawn
        // instead of appearing as a lone line offering to expand into nothing.
        //
        // Drawn from what the composition above already found rather than from a read of its own: the
        // hint answers a fact about the body beside it, and a second read would charge the whole
        // layer's economy walk to a line of fine print - once per frame the cursor rests on the cell.
        buildFooterSection(detailLevel, body.hasDeeperDetail()).ifPresent(sections::add);

        CursorTooltipRenderer.render(sections, buildStyle());
    }

    @Override
    public final boolean isOfferingExpansionFor(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        // The press-time entry to the same question the paint answers off its own composition, and
        // the one place a read is unavoidable: a key press composes nothing, so there is no body to
        // take the answer from. Judged by the one rule either way, so the key acts exactly where the
        // box says it would rather than under a second rule that could drift from it.
        //
        // Once per press rather than once per frame, so the read it costs is one the player asked
        // for - and skipped outright where the level already settles the answer.
        return isOfferingExpansionAt(detailLevel, () -> hasDeeperDetailFor(sector, system));
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
     * Composes this layer's contribution to one paint: the content blocks drawn top to bottom under
     * the heading, and whether the deeper detail levels hold anything beyond the one asked for.
     * Called once per paint with the live sector, after the economy precondition holds. The seam the
     * whole class exists around: the box is framed here and its content is the layer's, so this shape
     * can be shared by layers it names none of.
     *
     * <p>Stated as blocks rather than as lines because how far apart the box's content stands follows
     * from how it is grouped: a layer says which of its lines belong together, and every parting in the
     * box - including the one under the heading above - is then the same one decision.
     *
     * <p>The offer comes back beside them rather than being asked for separately, because a layer
     * reads its system once to build the body and already holds the answer
     * ({@link ComposedCellBody}). Asked apart, the box would pay for that read twice a frame and the
     * hint could describe a reading the body no longer agrees with.
     *
     * <p>The level travels with the sector rather than being read here, because it reaches further than
     * the layout: a body composes down to the depth asked for and hands what it built to the blocks to
     * be cut ({@link CellTooltipBody}), reading nothing the level has already ruled out of the box.
     *
     * @param sector      the live sector, whose economy the content may read
     * @param system      the star system under the cursor
     * @param detailLevel how deep the player has asked the box to read
     * @return the body and its offer, or {@link ComposedCellBody#NOTHING} when the layer has nothing
     *         to show for this system
     */
    protected abstract ComposedCellBody composeBody(
        SectorAPI sector,
        StarSystemAPI system,
        HoverTooltipDetailLevel detailLevel);

    /**
     * Whether the deeper detail levels hold anything more about {@code system} than the level being
     * read already shows. Answered by a box taking part in the detail cycle, and false for one that
     * does not, which is the ordinary case.
     *
     * <p>This is the <em>press-time</em> entry to that question, reached when the key is struck and
     * nothing has been composed to take the answer from. A paint gets the same answer out of
     * {@link #composeBody} instead, off the read the body was built from, so the box never pays for
     * this twice in a frame. A layer answering both states one rule and reaches it two ways.
     *
     * <p>Asked per hovered system rather than once per box, because whether there is anything to expand
     * into is a fact about the system: a box whose deeper tiers would state nothing more for this one
     * answers false, and the hint is dropped rather than offering a key press that changes nothing.
     *
     * <p>What that press would then be called is not asked of a box at all: the phrase is the level's
     * ({@link HoverTooltipDetailLevel#resolveNextActionPhrase}), so every layer names one step the
     * same way.
     *
     * @param sector the live sector, whose economy the answer may read
     * @param system the star system under the cursor
     * @return true where a deeper level would state something this one does not
     */
    protected boolean hasDeeperDetailFor(SectorAPI sector, StarSystemAPI system) {
        return false;
    }

    // The line the box ends on, or none at all: the cycle key and what pressing it would do to this
    // box. Its own block, so the shared parting sets it off from the content the way any two blocks are
    // set off - a hint about the box reading as the last line of a list would be read as part of that
    // list.
    //
    // Takes the offer the body came back with rather than asking for one, so the line is drawn from
    // the very reading it describes, and drawn under the same rule the key is claimed by - a hint
    // and a press settled separately are one edit away from advertising a key that does nothing.
    private static Optional<TooltipSection> buildFooterSection(
            HoverTooltipDetailLevel detailLevel,
            boolean hasDeeperDetail) {

        if (!isOfferingExpansionAt(detailLevel, () -> hasDeeperDetail)) {
            return Optional.empty();
        }
        return Optional.of(TooltipSection.createSection(
            List.of(buildFooterRow(detailLevel.resolveNextActionPhrase()))));
    }

    // Whether one press would change what the player sees: the one rule behind both the hint the box
    // draws and the key the input pass claims.
    //
    // The level settles it first, and at the deepest level settles it outright - the cycle wraps, so
    // the press there collapses the box, which acts over any system at all. Only below that does the
    // answer turn on the box having something deeper for this system, which is why the read behind it
    // is deferred: at the deepest level it is never taken.
    private static boolean isOfferingExpansionAt(
            HoverTooltipDetailLevel detailLevel,
            BooleanSupplier hasDeeperDetail) {

        return detailLevel.isCollapsingOnNextPress() || hasDeeperDetail.getAsBoolean();
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

        return TooltipSection.createSection(rows);
    }

    // The header every cell tooltip opens with: the hovered system's own name, crestless and drawn in
    // the highlight colour, so the body below it never has to repeat which system it describes and the
    // one proper name in the box is the one span that reads gold. Centred over the box rather than laid
    // into the columns below it, since it titles the whole box rather than sitting in its table - which
    // also frees it of the value column those rows align to.
    //
    // Titled by the display read rather than by getName(), which composes a system named after its star
    // into "Penelope's Star Star System" - a stutter a box heading reads badly, since the name is the
    // largest thing in it.
    //
    // Reading as a heading is the row saying what it is, not which face it wants: the box's typography
    // below turns that into the title face, so the two decisions - what a line is, how that kind of line
    // looks - stay on the sides that own them.
    private static TooltipRow.CentredRow buildHeaderRow(StarSystemAPI system) {
        return TooltipRow
            .createCentredRow(new TextSpan(
                StarSystems.readDisplayName(system),
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
                HoverTooltipDetailLevelInput.CYCLE_KEY_NAME,
                StarsectorUiColour.VANILLA_BUTTON_SHORTCUT.resolve()))
            .clearsCrestColumn()
            .continuesWith(new TextSpan(
                phrase,
                StarsectorUiColour.VANILLA_GRAY.resolve()))
            .readsAs(TooltipLineStyle.FOOTNOTE);
    }

    // The tooltip's fixed look: the typography each kind of row draws in, the shared opacity, and the
    // frame over a black fill in the map's own player palette. Built per paint so its colours resolve
    // live rather than being baked at class load.
    //
    // The heading and the body name no size: each face is a bitmap atlas crisp at exactly one size, and
    // the box has no fit of its own to squeeze text into, so a line speaking in the box's own voice takes
    // the native size and is drawn 1:1 rather than scaled.
    //
    // Two kinds of line are scaled off their atlas anyway, both knowingly. The note at the foot, because
    // its atlas is rasterised at 12, which beside 15pt content reads as fine print rather than as a
    // quieter line of the same box - it takes the body's size instead, and what sets it apart is its
    // narrowness and its colours, neither of which costs it a size of its own. And any line standing
    // under that voice, by the player's own step per level, which is the whole point of asking for it.
    //
    // How dense the box is set is read live rather than fixed here: a box lists as much as the hovered
    // system holds, so what reads comfortably on a two-colony system and what fits on screen for a
    // twelve-colony one are not the same setting, and which of the two matters is the player's call.
    //
    // The two solid marks the box draws among its glyphs - the rule from a label across to its value, and
    // the blocks a withheld name stands as - take the player's weights for the same reason the leader
    // line does: how heavy a solid run looks beside text is a judgement made on screen, at whatever scale
    // the game is run at.
    private static CursorTooltipStyle buildStyle() {
        return CursorTooltipStyle.createStyle(
            TooltipStyle
                .createStyle(
                    TextStyle.createStyle(HEADER_FONT),
                    TextStyle.createStyle(BODY_FONT))
                .footnotedIn(TextStyle
                    .createStyle(FOOTNOTE_FONT)
                    .sizedAt(BODY_FONT.getNativeSize()))
                .shrunkPerLevel(KmuMapLayerSettings.getMapTooltipNestingLevelShrink())
                .stackedAt(buildLineGaps()),
            OPACITY,
            BORDER_WIDTH,
            StarsectorUiColour.BLACK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_BASE.resolve())
            .ruledBy(buildLeaderLineStyle())
            .redactedAt(KmuMapLayerSettings.getMapTooltipRedactionDarkeningStrength());
    }

    // How heavily the line from a label across to its value draws. Layered over KMLib's own weights
    // rather than left at them, because how heavy a solid run looks beside a line of glyphs turns on the
    // face, the size, and the atlas behind it - so where it sits against the text is a judgement made on
    // screen, at whatever scale the player runs the game at, and therefore the player's to make.
    private static TooltipLeaderLineStyle buildLeaderLineStyle() {
        return new TooltipLeaderLineStyle(
            KmuMapLayerSettings.getMapTooltipLeaderThickness(),
            KmuMapLayerSettings.getMapTooltipLeaderOpacity());
    }

    // How far apart the box's lines stand, by the depth of the line above the gap: the box's own spacing
    // everywhere, and the two depths a listing runs long at tightened on their own. Each gap belongs to
    // the tier just drawn, so a slider closes up a run of like lines and leaves the line that opens it
    // standing where the shallower line above it put it.
    private static TooltipLineGaps buildLineGaps() {
        return TooltipLineGaps
            .createGaps(KmuMapLayerSettings.getMapTooltipLineGap())
            .gappedAtLevel(TIER_2_LEVEL, KmuMapLayerSettings.getMapTooltipTier2LineGap())
            .gappedAtLevel(TIER_3_LEVEL, KmuMapLayerSettings.getMapTooltipTier3LineGap());
    }
}
