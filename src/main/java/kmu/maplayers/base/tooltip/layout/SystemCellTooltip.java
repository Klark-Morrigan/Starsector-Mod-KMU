package kmu.maplayers.base.tooltip.layout;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

import kmu.maplayers.base.tooltip.HoverTooltipDetailLevelInput;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.settings.KmuMapTooltipSettings;
import kmu.util.KmuStrings;

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
 * <p>How much of that content there is room for is settled here too, against the screen
 * ({@link CellTooltipContentFit}), because a box is sized by what the hovered system happens to hold
 * and then clamped: unfitted, the systems most worth reading about are exactly the ones drawn past
 * both edges. A layer states what it found and never how tall that comes to, so a layer cannot arrive
 * at a fit of its own - and the box compresses before it withholds anything, a tooltip taking no input
 * to reach what it left out with.
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

    // What a box that drew everything it was asked for withheld. Named so the line at the foot reads as
    // asking whether anything was left out rather than as comparing against a bare zero.
    private static final int NOTHING_WITHHELD = 0;

    // The deepest level the cycle declares, which is the deepest bound any box could state. A box read
    // at it collapses on the next press however far its own tree reaches, so the answer is settled
    // without asking - which is what keeps the read behind that question off the frames it cannot
    // change.
    private static final HoverTooltipDetailLevel DEEPEST_LEVEL_IN_CYCLE =
        HoverTooltipDetailLevel.resolveDeepestLevel();

    // The two halves of that line a given box may have nothing for: a box at a level that offers no
    // further reading of this system, and one that had room for all of it. Named so the composition
    // below states what the line is missing rather than handing it unexplained nulls.
    private static final String NO_OFFER = null;
    private static final String NOTHING_TO_STATE = null;

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
        if (titleRows.isEmpty() && body.blocks().isEmpty()) {
            return;
        }
        var style = buildStyle();

        // The box is assembled against the room it has rather than drawn at whatever height its
        // content came to. A box lists as much as the hovered system holds and is then clamped on
        // screen, so an unfitted one runs past both edges over exactly the systems worth reading about
        // - and a tooltip takes no input, so nothing it lost can be reached.
        //
        // Assembled through a call rather than built once, because the fit settles how much of the
        // body there is room for and the answer changes what the box holds. Cheap to repeat: the
        // sector was read once, above, and everything below that read is line building.
        var fittedBox = CellTooltipContentFit.fitToHeight(
            entryAllowance -> assembleSections(system, titleRows, body, detailLevel, entryAllowance),
            style.typography(),
            CursorTooltipRenderer.resolveHeightBudget(),
            body.blocks().countLongestListing());

        CursorTooltipRenderer.render(
            fittedBox.sections(),
            style.restyledAs(fittedBox.typography()));
    }

    @Override
    public final Optional<HoverTooltipDetailLevel> resolveNextLevelFor(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        // The press-time entry to the same question the paint answers off its own composition, and
        // the one place a read is unavoidable: a key press composes nothing, so there is no body to
        // take the answer from. Judged by the one rule either way, so the key acts exactly where the
        // box says it would rather than under a second rule that could drift from it.
        //
        // Once per press rather than once per frame, so the read it costs is one the player asked
        // for - and skipped outright at the deepest level the cycle declares, where the press
        // collapses the box whatever this one holds and its own bound cannot change the answer.
        var deepestHeldLevel = detailLevel.isReadingAtLeast(DEEPEST_LEVEL_IN_CYCLE)
            ? DEEPEST_LEVEL_IN_CYCLE
            : resolveDeepestHeldLevelFor(sector, system);

        return resolveOfferedLevel(detailLevel, deepestHeldLevel);
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
     * The deepest level this box holds anything at for {@code system} - where its cycle wraps, so the
     * press after it collapses the box instead of offering a tier that would redraw what is already on
     * screen. Answered by a box taking part in the detail cycle, and left at the shallowest level for
     * one that does not, which is the ordinary case.
     *
     * <p>This is the <em>press-time</em> entry to that question, reached when the key is struck and
     * nothing has been composed to take the answer from. A paint gets the same answer out of
     * {@link #composeBody} instead, off the read the body was built from, so the box never pays for
     * this twice in a frame. A layer answering both states one rule and reaches it two ways.
     *
     * <p>Asked per hovered system rather than once per box, because how deep a box reaches is partly a
     * fact about the system: one it lists nothing for holds nothing at any level, whatever tiers its
     * account could carry elsewhere.
     *
     * <p>What the press would then be called is not asked of a box at all: the phrase belongs to the
     * level being arrived at ({@link HoverTooltipDetailLevel#resolveArrivalPhrase}), so every layer
     * names one step the same way.
     *
     * @param sector the live sector, whose economy the answer may read
     * @param system the star system under the cursor
     * @return the deepest level with something to show for this system
     */
    protected HoverTooltipDetailLevel resolveDeepestHeldLevelFor(
            SectorAPI sector,
            StarSystemAPI system) {

        return HoverTooltipDetailLevel.FACTIONS;
    }

    // The whole box at one entry allowance: the heading, the body laid out within that allowance, and
    // the line at the foot stating what the press would do and what the box could not fit.
    //
    // Assembled in one place rather than built up around the body, because the footer answers a fact
    // about the very laying-out beside it: a box that assembled the two apart could state a figure for
    // withheld content that the rows above it do not bear out.
    private static List<TooltipSection> assembleSections(
            StarSystemAPI system,
            List<TooltipRow> titleRows,
            ComposedCellBody body,
            HoverTooltipDetailLevel detailLevel,
            int entryAllowance) {

        var drawnBody = body.blocks().readBodyWithin(entryAllowance);
        var sections = new ArrayList<TooltipSection>();

        sections.add(buildTitleSection(system, titleRows));
        sections.addAll(drawnBody.sections());

        // Added after the emptiness check the caller made rather than counted by it: the hint is about
        // the box rather than about the system, so a box with nothing to say about the system stays
        // undrawn instead of appearing as a lone line offering to expand into nothing.
        //
        // Drawn from what the composition already found rather than from a read of its own: the hint
        // answers a fact about the body beside it, and a second read would charge the whole layer's
        // economy walk to a line of fine print - once per frame the cursor rests on the cell.
        buildFooterSection(detailLevel, body.deepestHeldLevel(), drawnBody.withheldEntryCount())
            .ifPresent(sections::add);

        return sections;
    }

    // The line the box ends on, or none at all: the cycle key and what pressing it would do to this
    // box, and what the box had no room to show. Its own block, so the shared parting sets it off from
    // the content the way any two blocks are set off - a hint about the box reading as the last line of
    // a list would be read as part of that list.
    //
    // Takes the offer the body came back with rather than asking for one, so the line is drawn from
    // the very reading it describes, and drawn under the same rule the key is claimed by - a hint
    // and a press settled separately are one edit away from advertising a key that does nothing.
    //
    // The withheld figure keeps the line where the offer alone would have dropped it. What the box left
    // out is the one thing it must not keep to itself: the rows standing in for withheld entries say it
    // listing by listing, and this says it over the box, so a reader can tell a short list from a cut
    // one wherever the cut happened to land.
    private static Optional<TooltipSection> buildFooterSection(
            HoverTooltipDetailLevel detailLevel,
            HoverTooltipDetailLevel deepestHeldLevel,
            int withheldEntryCount) {

        var offeredLevel = resolveOfferedLevel(detailLevel, deepestHeldLevel);
        var isStatingWithheld = withheldEntryCount > NOTHING_WITHHELD;

        if (offeredLevel.isEmpty() && !isStatingWithheld) {
            return Optional.empty();
        }
        return Optional.of(TooltipSection.createSection(List.of(buildFooterRow(
            offeredLevel.map(HoverTooltipDetailLevel::resolveArrivalPhrase).orElse(NO_OFFER),
            isStatingWithheld ? formatWithheldPhrase(withheldEntryCount) : NOTHING_TO_STATE))));
    }

    // Where one press would take a box read at detailLevel whose own tree ends at deepestHeldLevel:
    // the one rule behind both the hint the box draws and the key the input pass claims.
    //
    // Empty is the one case where the press would change nothing the player can see - a box holding
    // nothing past the shallowest level, read at the shallowest level, which the cycle has nowhere to
    // step to and nothing to collapse. Everywhere else there is either a deeper tier to open or a
    // collapse to take, both of which the player sees.
    private static Optional<HoverTooltipDetailLevel> resolveOfferedLevel(
            HoverTooltipDetailLevel detailLevel,
            HoverTooltipDetailLevel deepestHeldLevel) {

        var nextLevel = detailLevel.resolveNextLevelWithin(deepestHeldLevel);

        return nextLevel == detailLevel ? Optional.empty() : Optional.of(nextLevel);
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

    // The line itself, in the game's own colours for the job: the key picked out in the shade every
    // vanilla button highlights its shortcut with, the words about it in the grey vanilla states such
    // hints in, and the figure for what the box left out in that same grey. Runs rather than one string
    // because that is exactly what vanilla draws - the key is the part the eye is meant to find, and the
    // sentence around it is deliberately quiet.
    //
    // The withheld figure is quiet for a reason of its own: it is the box speaking about its own
    // account rather than about the system, which is the shade this box states all such asides in. What
    // it stands for is loud enough where it happened, on the rows standing in for the entries.
    //
    // Composed from whichever runs the box has rather than branched over, so a line missing one half is
    // the same line short a run. Laid at the box's content edge rather than centred: it sits at the foot
    // of the box the way the game's own does, and centring it would read as a verdict over the content
    // above.
    private static TooltipRow buildFooterRow(String phrase, String withheldPhrase) {

        var runs = new ArrayList<TextSpan>();

        if (phrase != NO_OFFER) {
            runs.add(new TextSpan(
                HoverTooltipDetailLevelInput.CYCLE_KEY_NAME,
                StarsectorUiColour.VANILLA_BUTTON_SHORTCUT.resolve()));
            runs.add(new TextSpan(phrase, StarsectorUiColour.VANILLA_GRAY.resolve()));
        }
        if (withheldPhrase != NOTHING_TO_STATE) {
            runs.add(new TextSpan(withheldPhrase, StarsectorUiColour.VANILLA_GRAY.resolve()));
        }
        var remainingRuns = runs.iterator();
        var row = TooltipRow
            .createRow(remainingRuns.next())
            .clearsCrestColumn();

        while (remainingRuns.hasNext()) {
            row = row.continuesWith(remainingRuns.next());
        }
        return row.readsAs(TooltipLineStyle.FOOTNOTE);
    }

    // What the box says at its foot about the entries it could not fit - the count over the whole box,
    // whichever listings the cut fell in.
    private static String formatWithheldPhrase(int withheldEntryCount) {
        return KmuStrings.format(KmuStrings.MAP_LAYER_TOOLTIP_FOOTER_WITHHELD, withheldEntryCount);
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
                .shrunkPerLevel(KmuMapTooltipSettings.getMapTooltipNestingLevelShrink())
                .stackedAt(buildLineGaps()),
            OPACITY,
            BORDER_WIDTH,
            StarsectorUiColour.BLACK.resolve(),
            StarsectorUiColour.VANILLA_PLAYER_BASE.resolve())
            .ruledBy(buildLeaderLineStyle())
            .redactedAt(KmuMapTooltipSettings.getMapTooltipRedactionDarkeningStrength());
    }

    // How heavily the line from a label across to its value draws. Layered over KMLib's own weights
    // rather than left at them, because how heavy a solid run looks beside a line of glyphs turns on the
    // face, the size, and the atlas behind it - so where it sits against the text is a judgement made on
    // screen, at whatever scale the player runs the game at, and therefore the player's to make.
    private static TooltipLeaderLineStyle buildLeaderLineStyle() {
        return new TooltipLeaderLineStyle(
            KmuMapTooltipSettings.getMapTooltipLeaderThickness(),
            KmuMapTooltipSettings.getMapTooltipLeaderOpacity());
    }

    // How far apart the box's lines stand, by the depth of the line above the gap: the box's own spacing
    // everywhere, and the two depths a listing runs long at tightened on their own. Each gap belongs to
    // the tier just drawn, so a slider closes up a run of like lines and leaves the line that opens it
    // standing where the shallower line above it put it.
    private static TooltipLineGaps buildLineGaps() {
        return TooltipLineGaps
            .createGaps(KmuMapTooltipSettings.getMapTooltipLineGap())
            .gappedAtLevel(TIER_2_LEVEL, KmuMapTooltipSettings.getMapTooltipTier2LineGap())
            .gappedAtLevel(TIER_3_LEVEL, KmuMapTooltipSettings.getMapTooltipTier3LineGap());
    }
}
