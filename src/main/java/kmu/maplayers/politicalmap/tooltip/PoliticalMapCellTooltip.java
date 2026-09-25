package kmu.maplayers.politicalmap.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.factions.relation.StarsectorFactionRelations;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.layout.SystemCellTooltip;
import kmu.maplayers.ownermap.holding.BlocAffiliation;
import kmu.maplayers.ownermap.holding.BlocFriendliness;
import kmu.maplayers.ownermap.holding.HolderGroupingSource;
import kmu.maplayers.ownermap.tooltip.BlocRelations;

import java.util.List;

/**
 * What every political-map view's hover box is beneath the framework's shape: a cell tooltip that may
 * cite the system's claim. Each view explains the mechanic its own fills were painted by, but any of
 * them can have to say the system is held by decree - the claims view as the claim itself, the faction
 * and alliance views as a fact standing over their standings - so the read answering that is bound
 * here rather than by each body.
 *
 * <p>Binding it once is what stops two boxes running on different readers: a decree resolved one way
 * on one tab and another way on the next would answer one hover two ways, a keystroke apart, with
 * nothing on screen to say which was right.
 *
 * <p>A decree is also drawn here, as the banner heading the layer's boxes, rather than by whichever body
 * happens to mention it. It is a fact about the system rather than about the mechanic a view paints by,
 * so it reads the same under all of them - and stating it once is what stops a view added later heading
 * its box with the name alone while the tab beside it names the faction holding the system. A box whose
 * body makes the same statement says so and goes without the banner, so the one decree is met once
 * either way.
 */
public abstract class PoliticalMapCellTooltip extends SystemCellTooltip {

    /**
     * The reader every political-map tooltip runs on in game. Stateless, so the boxes share one
     * rather than each minting its own, and named in a single place so a change of binding cannot
     * reach one view and miss the other.
     *
     * <p>It resolves the player's colony rule per read rather than holding one, since a box
     * lives for the whole session while that rule is a setting the player may move between two
     * hovers.
     */
    public static final ClaimBreakdownReader VANILLA_CLAIM_BREAKDOWN_READER =
        new LiveVisibilityClaimBreakdownReader();

    /**
     * The claim read a body draws on - the whole scored contest, or the decree alone, whichever it
     * has something to say about. Held rather than reached for statically, so a body draws on the
     * read it is handed rather than on whichever one a running game happens to have.
     */
    protected final ClaimBreakdownReader claimBreakdownReader;

    // Where the alliance set behind an allied block is taken from. A source rather than a grouping,
    // because a box lives for the whole session while alliances form and dissolve inside it - one
    // captured at construction would go on filing a group under the alliance it left an hour ago.
    private final HolderGroupingSource holderGroupingSource;

    // Where the wording of the block beneath the friendly one is taken from. A source rather than a
    // wording for the reason that port sets out: these boxes stand in static fields, so construction
    // can fall before the game has a mod set to read.
    private final ContestWordingSource contestWordingSource;

    protected PoliticalMapCellTooltip(
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource,
            ContestWordingSource contestWordingSource) {

        this.claimBreakdownReader = claimBreakdownReader;
        this.holderGroupingSource = holderGroupingSource;
        this.contestWordingSource = contestWordingSource;
    }

    @Override
    protected final List<TooltipRow> buildTitleRows(SectorAPI sector, StarSystemAPI system) {
        // Whose space this is heads the box rather than sitting in it: a decree settles the system
        // outright, so it is read straight off the system name above it instead of being found among
        // the findings below. Being a title line is also what parts it from the body - the box's break
        // under the heading falls beneath it rather than above it.
        //
        // The decree is read on its own rather than out of the full claim breakdown: a box may only
        // need to know whether one holds the system, and scoring every market in it to answer that
        // would charge the whole claim computation to every faction and alliance hover.
        return CoreTerritoryHeading.resolveHeadingRows(
            sector,
            claimBreakdownReader.readCoreFactionId(system),
            isStatingCoreClaimInBody());
    }

    /**
     * Samples how a bloc may stand with the holder of the hovered system, for the one read a box
     * builds its blocks from.
     *
     * <p>Bound here rather than by each shape because both are live and both place the same blocs:
     * two boxes sampling for themselves are two chances to file a group as an ally on one tab and as
     * a rival on the next, a keystroke apart, with nothing on screen to say which was right.
     *
     * <p>The alliance set is read through the session-long source at the moment of sampling, which is
     * where a grouping stops being a fold and becomes the one question the blocks ask. Disposition is
     * read against the hovered sector rather than through the game's own current one, so a box drawn
     * over a second sector reports that sector's relations.
     *
     * @param sector the sector the hovered system stands in, whose relations are read
     * @return the two relations, sampled together
     */
    protected final BlocRelations sampleBlocRelations(SectorAPI sector) {

        return new BlocRelations(
            new BlocAffiliation(holderGroupingSource.resolveGrouping()),
            new BlocFriendliness(StarsectorFactionRelations.createDispositionReader(sector)));
    }

    /**
     * Takes the wording for the block listing everyone present who stands neither in the holder's
     * alliance nor on good terms with it.
     *
     * <p>Bound here rather than by each shape for the reason the relations above are: both shapes
     * draw that block, and two of them reading the install for themselves are two chances to word it
     * as a contest on one tab and as plain presence on the next.
     *
     * @return the wording the install calls for as the block is drawn
     */
    protected final ContestWording resolveContestWording() {
        return contestWordingSource.resolveWording();
    }

    /**
     * The deepest level this box holds anything at for the hovered system.
     *
     * <p>Asked per hovered system rather than once per box: the deeper tiers account for the colonies
     * behind what this box lists, so a system it lists nothing for holds nothing below the shallowest
     * level, and every level would draw the same thing. The hint is dropped there rather than offering
     * a key that changes nothing on screen.
     *
     * <p>Answered through the very read the body is built from, so a box cannot offer to expand a
     * listing it is about to draw as empty - which the fog alone can produce, a faction present only
     * through colonies the player has not found leaving nothing this box may state.
     *
     * <p>Re-declared abstract rather than left at the inherited shallowest, because there is no honest
     * default here: a box of this layer inheriting that answer would silently withhold the hint over a
     * system it has plenty more to say about, and nothing on screen would say the key was worth
     * pressing.
     *
     * @param sector the live sector, whose economy the answer may read
     * @param system the star system under the cursor
     * @return the deepest level with something to show for this system
     */
    @Override
    protected abstract HoverTooltipDetailLevel resolveDeepestHeldLevelFor(
        SectorAPI sector,
        StarSystemAPI system);

    /**
     * The deepest level this box's account of one listed faction reaches, over a system it names
     * somebody in.
     *
     * <p>Asked of the box that supplies the account rather than settled here, because it is a fact
     * about that account's own subject matter and a constant of it: what tiers an account carries
     * follows from what it explains, not from what one system happened to yield.
     *
     * @return the deepest level this box's account ever puts a line at
     */
    protected abstract HoverTooltipDetailLevel resolveDeepestAccountLevel();

    /**
     * How deep the box goes over one hovered system: as deep as its account reaches where the read
     * behind it named somebody to account for, and no deeper than the box's own voice where it did
     * not.
     *
     * <p>The one place the two halves are joined, so a shape cannot combine them one way for the
     * paint and another for the press - the drift that would put the hint and the key at odds over
     * one system. Taken by the shape from the read it already holds rather than reading the system
     * again, which is why the halves arrive here separately.
     *
     * @param isNamingAnybody whether the read behind the box left it a faction to account for
     * @return the level the cycle wraps at for this box over this system
     */
    protected final HoverTooltipDetailLevel resolveDeepestHeldLevel(boolean isNamingAnybody) {
        return isNamingAnybody
            ? resolveDeepestAccountLevel()
            : HoverTooltipDetailLevel.FACTIONS;
    }

    /**
     * Whether this box's own body names the faction holding the system by decree. A box that does goes
     * without the heading, since one hover stating the same decree twice reads as two separate facts
     * about the system rather than as one said over.
     *
     * <p>Answered by the box rather than worked out from what it drew, because it follows from what the
     * body is for rather than from what a given system happened to yield: a body that states the claim
     * states it whenever there is one, and a system under no decree makes the question moot either way.
     *
     * <p>Not stating it is the ordinary case and the default, so a box whose subject is something other
     * than the claim overrides nothing.
     *
     * @return true where the body already states the decree, so the heading would repeat it
     */
    protected boolean isStatingCoreClaimInBody() {
        return false;
    }
}
