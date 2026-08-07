package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.text.KmlibNumbers;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
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
 * name and crest and is made up of nothing, so a singleton reads identically to the member it wraps and
 * the flat faction view falls out for free; an alliance group takes its name from the grouping and its
 * crest from the alliance's colour (lead) faction - the same crest the alliances view paints the bloc's
 * cluster by - gathering the members that make it up as its peers rather than as its account, since
 * membership states the same answer more finely and explains nothing. Keying that on the group's kind
 * rather than its size is what keeps a one-member alliance a bloc over its member rather than collapsing
 * it into a lone faction, and it is settled here because this is the only side that knows the kind. A
 * blank or absent crest resolves to a null path the render layer draws around, so a group or member with
 * no authored crest still shows its name and score.
 */
public final class StandingRowResolver {

    private StandingRowResolver() {
    }

    /**
     * Resolves a hovered system's ranked groups into the entries a block lists, in the same order, each
     * group's members gathered beneath it as its peers.
     *
     * @param sector    the sector whose {@link FactionAPI} names and crests are read
     * @param standings the two-tier standings ranked by {@code SystemStandings}, in draw order
     * @param grouping  the active view's grouping, supplying an alliance's name and colour faction; the
     *                  identity grouping makes every group a lone faction
     * @return one entry per group in ranked order, each gathering its members' lines; empty when the
     *         standings are empty
     */
    public static List<CellTooltipEntry> resolveRows(
            SectorAPI sector,
            List<GroupStanding> standings,
            HolderGrouping grouping) {

        var entries = new ArrayList<CellTooltipEntry>(standings.size());
        for (var standing : standings) {
            entries.add(resolveGroupEntry(sector, standing, grouping));
        }
        return List.copyOf(entries);
    }

    // Resolves one group into the entry it is listed as. The members resolve first so a lone-faction
    // group can reuse its single member's already-resolved name and crest, which is what keeps a
    // singleton from ever drifting from the member it wraps.
    private static CellTooltipEntry resolveGroupEntry(
            SectorAPI sector,
            GroupStanding standing,
            HolderGrouping grouping) {

        var memberEntries = resolveMemberEntries(sector, standing.members());
        var blocId = standing.blocId();
        var aggregateScoreText = KmlibNumbers.formatGroupedInteger(standing.aggregateScore());

        if (!grouping.isAlliance(blocId)) {
            // A lone-faction group has one member, so the group is exactly that member: reuse the
            // resolved line rather than reading the faction a second time, and list nothing beneath it,
            // since a member repeating the line above says nothing the line did not.
            var groupMemberLine = memberEntries.get(0).line();

            return CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
                groupMemberLine.iconSpritePath(),
                groupMemberLine.labelText(),
                aggregateScoreText));
        }
        // An alliance carries the alliance's own name and its lead (colour) member's crest - the same
        // name and crest the alliances view paints the bloc's cluster by - over the factions in it.
        var allianceLine = CellTooltipEntryLine.createLine(
            FactionPresentation
                .resolvePresentation(sector, grouping.resolveColourFactionId(blocId))
                .crestSpritePath(),
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
    // so a group's members read in the order the standing placed them. Each carries nothing beneath it:
    // this resolver states the two tiers the standings have, and whatever accounts for a faction's score
    // is hung on by whichever box asked for that account.
    private static List<CellTooltipEntry> resolveMemberEntries(
            SectorAPI sector,
            List<FactionStanding> members) {

        var entries = new ArrayList<CellTooltipEntry>(members.size());

        for (var member : members) {
            entries.add(FactionTooltipEntry.buildFactionEntry(
                sector,
                member.factionId(),
                KmlibNumbers.formatGroupedInteger(member.score())));
        }
        return entries;
    }
}
