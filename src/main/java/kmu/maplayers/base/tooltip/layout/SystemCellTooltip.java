package kmu.maplayers.base.tooltip.layout;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.render.gl.tooltip.CursorTooltipRenderer;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipLineStyle;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The shared shape of a map-layer cell tooltip: the hovered system's name on top, the layer's own
 * content below it, and one render call for both. A layer tooltip extends this and supplies only its
 * content, so the header, the economy precondition, and the order the box is assembled in are settled
 * in one place and no two layers can drift on them - the difference between two layers' hovers is what
 * they say about the system, never how the box is framed or named.
 *
 * <p>The parts of that box each answer a question of their own and are held apart accordingly: which
 * lines the content may be written in is {@link CellTooltipRows}, how the box is set is
 * {@link CellTooltipLook}, and the line it ends on - with the rule saying whether the cycle key has
 * anything to offer over this system - is {@link CellTooltipFooter}.
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
        var style = CellTooltipLook.buildStyle();

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
        // for - and asked at every level, the deepest included: how far this box reaches is exactly
        // what says whether the collapse would show the player anything, and a box the level has
        // already outrun draws the same box on both sides of the press.
        return CellTooltipFooter.resolveOfferedLevel(
            detailLevel,
            resolveDeepestHeldLevelFor(sector, system));
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
        CellTooltipFooter
            .buildSection(detailLevel, body.deepestHeldLevel(), drawnBody.withheldEntryCount())
            .ifPresent(sections::add);

        return sections;
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

}
