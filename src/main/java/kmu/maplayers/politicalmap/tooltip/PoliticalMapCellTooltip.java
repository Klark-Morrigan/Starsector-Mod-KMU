package kmu.maplayers.politicalmap.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.factions.relation.FactionSetFriendliness;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.layout.CellTooltipBody;
import kmu.maplayers.base.tooltip.layout.SystemCellTooltip;
import kmu.maplayers.ownermap.owners.holders.alliances.BlocAffiliation;
import kmu.maplayers.ownermap.owners.holders.alliances.BlocRelations;
import kmu.util.KmuStringKeys;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
public abstract class PoliticalMapCellTooltip
        extends SystemCellTooltip {

    // The live reads the box is built around. Held rather than reached for statically, so a box draws
    // on the reads it is handed rather than on whichever ones a running game happens to have.
    private final PoliticalBoxSeams seams;

    protected PoliticalMapCellTooltip(PoliticalBoxSeams seams) {
        this.seams = Objects.requireNonNull(seams, "seams");
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
            seams.claimDecreeReader().readDecreedFactionId(system),
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
     * where the memberships become the one question the blocks ask. Both are read against the
     * hovered sector rather than through the game's own current one, so a box drawn over a second
     * sector reports that sector's alliances and relations.
     *
     * @param sector the sector the hovered system stands in, whose alliances and relations are read
     * @return the two relations, sampled together
     */
    protected final BlocRelations sampleBlocRelations(SectorAPI sector) {

        return new BlocRelations(
            new BlocAffiliation(seams.factionAllianceSource().readAlliances(sector)),
            FactionSetFriendliness.createForSector(sector));
    }

    /**
     * Lays down a box's blocks in the order their set declares them, each under its heading and over
     * the entries it lists. Every block is offered unconditionally, since a block standing over no
     * entries is dropped by the same rule that drops any other.
     *
     * <p>The wording is sampled once for the whole run, so every block of the box is headed under a
     * single reading of the install - bound here rather than by each shape because two shapes reading
     * the install for themselves are two chances to word the rival block as a contest on one tab and
     * as plain presence on the next.
     *
     * @param body          the body the sections are appended to
     * @param blocks        the box's block set, in the order it is laid down
     * @param entriesSource the entries one block lists, in the order they are read
     * @param <B>           the box's own block set
     */
    protected final <B extends ContestBlockHeading> void appendContestBlockSections(
            CellTooltipBody body,
            B[] blocks,
            Function<B, List<CellTooltipEntry>> entriesSource) {

        var contestWording = seams.contestWordingSource().resolveWording();

        for (var block : blocks) {

            body.appendSection(
                KmuStringKeys.get(block.resolveHeadingKey(contestWording)),
                entriesSource.apply(block));
        }
    }

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
     * <p>Judged per hovered system: the deeper tiers account for the colonies behind what the box
     * lists, so a system it lists nobody for holds nothing below the shallowest level - which the fog
     * alone can produce, a faction present only through colonies the player has not found. The one
     * place the two halves are joined, so no shape of this layer combines them its own way. Taken by
     * the shape from the read its body was built from rather than reading the system again, which is
     * why the halves arrive here separately.
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
