package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.text.KmlibNumbers;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipMark;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a hovered system's pure two-tier standings into the entries the tooltip's block lists,
 * turning each id into the name, crest, and number it presents as.
 *
 * <p>Separates "who ranks where" - {@code SystemStandings}, pure over footprints and a grouping -
 * from "how a group and its factions present", the Starsector and grouping lookups gathered here.
 * Confining {@link FactionAPI} and the grouping's label and crest reads to this resolver keeps the
 * render layer consuming plain entries, mirroring how {@code SectorPolitics} confines the holding
 * palette lookups.
 *
 * <p>It resolves into the shared entry model rather than into a model of its own, because a ranked
 * group already <em>is</em> what an entry is - a thing with a name, a mark, and a number, over whatever
 * it is made up of. A parallel record would mean converting one presentation-ready shape into another
 * for nothing, and two records that could come to disagree about what a resolved line holds.
 *
 * <p>A member is always a faction, and how one appears is {@link FactionPresentation}'s answer rather
 * than this resolver's, so a faction ranked here and the same faction named anywhere else in the box
 * cannot present as two. A group presents by its kind: a lone-faction group reuses its one member's
 * name and crest, so a singleton reads identically to the member it wraps and the flat faction view
 * falls out for free; an alliance group takes its name from the grouping and its crest from the
 * alliance's colour (lead) faction - the same crest the alliances view paints the bloc's cluster by -
 * gathering the members that make it up as its peers rather than as its account, since membership states
 * the same answer more finely and explains nothing. Keying that on the group's kind rather than its size
 * is what keeps a one-member alliance a bloc over its member rather than collapsing it into a lone
 * faction, and it is settled here because this is the only side that knows the kind. A blank or absent
 * crest resolves to a null path the render layer draws around, so a group or member with no authored
 * crest still shows its name and score.
 *
 * <p>What accounts for a faction's score is not this resolver's to know, so it is asked for
 * ({@link FactionAccountResolver}) and hung beneath the faction it was resolved for. Asking here rather
 * than letting a box lay accounts over the answer afterwards is what makes the pairing safe: the standing
 * and the line named from it are both in hand at that point, so no caller has to walk two lists at the
 * same index and risk listing one faction's colonies under another's name.
 */
public final class StandingRowResolver {

    private StandingRowResolver() {
    }

    /**
     * Resolves a hovered system's ranked groups into the entries a block lists, in the same order, each
     * group's members gathered beneath it as its peers and each member over the account asked for it.
     *
     * @param sector          the sector whose {@link FactionAPI} names and crests are read
     * @param standings       the two-tier standings ranked by {@code SystemStandings}, in draw order
     * @param grouping        the active view's grouping, supplying an alliance's name and colour
     *                        faction; the identity grouping makes every group a lone faction
     * @param accountResolver what the listing box hangs beneath each faction as the account of its
     *                        score; {@link FactionAccountResolver#NO_ACCOUNT} lists every faction as
     *                        its line alone
     * @return one entry per group in ranked order, each gathering its members' lines; empty when the
     *         standings are empty
     */
    public static List<CellTooltipEntry> resolveRows(
            SectorAPI sector,
            List<GroupStanding> standings,
            HolderGrouping grouping,
            FactionAccountResolver accountResolver) {

        var entries = new ArrayList<CellTooltipEntry>(standings.size());
        for (var standing : standings) {
            entries.add(resolveGroupEntry(sector, standing, grouping, accountResolver));
        }
        return List.copyOf(entries);
    }

    // Resolves one group into the entry it is listed as. The members resolve first so a lone-faction
    // group can reuse its single member's already-resolved name and crest, which is what keeps a
    // singleton from ever drifting from the member it wraps.
    private static CellTooltipEntry resolveGroupEntry(
            SectorAPI sector,
            GroupStanding standing,
            HolderGrouping grouping,
            FactionAccountResolver accountResolver) {

        var memberEntries = resolveMemberEntries(sector, standing.members(), accountResolver);
        var blocId = standing.blocId();
        var aggregateScoreText = KmlibNumbers.formatGroupedInteger(standing.aggregateScore());

        if (!grouping.isAlliance(blocId)) {
            // A lone-faction group has one member, so the group is exactly that member: reuse both the
            // resolved line and the account already hung beneath it rather than reading either a second
            // time. The member's own line is dropped instead of being listed under the group's, since
            // repeating it would say nothing the line above did not - so the account it carries moves up
            // to hang directly off the group, which is the same faction under another name.
            var groupMemberEntry = memberEntries.get(0);

            return CellTooltipEntry
                .createEntry(CellTooltipEntryLine.createLine(
                    groupMemberEntry.line().mark(),
                    groupMemberEntry.line().labelText(),
                    aggregateScoreText))
                .nesting(groupMemberEntry.children());
        }
        // An alliance carries the alliance's own name and its lead (colour) member's crest - the same
        // name and crest the alliances view paints the bloc's cluster by - over the factions in it.
        var allianceLine = CellTooltipEntryLine.createLine(
            CellTooltipMark.resolveMarkAsAuthored(FactionPresentation
                .resolvePresentation(sector, grouping.resolveColourFactionId(blocId))
                .crestSpritePath()),
            grouping.resolveAllianceName(blocId),
            aggregateScoreText);

        // Gathered rather than subordinated: a bloc's line and the factions inside it are one answer to
        // who holds the system, stated at two granularities, so the members read inset beneath the bloc
        // without being demoted under the box's voice. Nothing has been broken down yet - that is what a
        // box explaining where a score came from hangs below a faction, and it must read the same size
        // whether the faction it hangs under is allied or standing alone.
        return CellTooltipEntry
            .createEntry(allianceLine)
            .grouping(memberEntries);
    }

    // Resolves each ranked member standing into the entry it is listed as, preserving the ranking order,
    // so a group's members read in the order the standing placed them. This resolver states the two tiers
    // the standings have; what accounts for a member's score is the asking box's to say, so each member
    // carries whatever that box's resolver answered for it - subordinated, since an account explains the
    // line it hangs under rather than restating it more finely.
    private static List<CellTooltipEntry> resolveMemberEntries(
            SectorAPI sector,
            List<FactionStanding> members,
            FactionAccountResolver accountResolver) {

        var entries = new ArrayList<CellTooltipEntry>(members.size());

        for (var member : members) {
            entries.add(FactionTooltipEntry
                .buildFactionEntry(
                    sector,
                    member.factionId(),
                    KmlibNumbers.formatGroupedInteger(member.score()))
                .nesting(accountResolver.resolveAccountEntries(member)));
        }
        return entries;
    }
}
