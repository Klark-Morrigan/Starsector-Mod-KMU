package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.StandingFraction;
import kmu.maplayers.politicalmap.base.dominance.WeighedFactionStanding;
import kmu.util.KmuStringKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a hovered system's pure two-tier standings into the entries the tooltip's block lists,
 * turning each ID into the name, crest, and number it presents as.
 *
 * <p>Separates "who ranks where" - {@code SystemStandings}, pure over the factions present and a
 * grouping - from "how a group and its factions present", the Starsector and grouping lookups
 * gathered here.
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
 * <p>How far the heading over a row reaches is stated on the row, as the fraction the routing worked out
 * for it. Which of the two readings a row states is settled here, this being the one side that knows a
 * group's kind: a bloc's row states how much of its own membership the heading took, while a faction's
 * states how much of the holder it is at odds with - and a lone-faction group, being that faction under
 * another name, states the faction's rather than the bloc-of-one's. A heading true of the whole of a row
 * qualifies it with nothing, which is every row of every block placed by membership.
 *
 * <p>How loudly a score is drawn is settled here too, on the standing's own kind: a faction or a bloc
 * the pass weighed nothing for carries its nought in the quiet shade. The distinction is the
 * standing's rather than the resolver's - what it adds is that the same distinction is drawn at both
 * tiers, so a bloc cannot read as weighed over members that were not.
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
     * @param standings       the two-tier standings as the block lists them, in draw order
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
            List<RoutedStanding> standings,
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
            RoutedStanding routedStanding,
            HolderGrouping grouping,
            FactionAccountResolver accountResolver) {

        var standing = routedStanding.standing();
        var blocId = standing.blocId();

        if (!grouping.isAlliance(blocId)) {
            // A lone-faction group has one member, so the group is exactly that member: it takes that
            // member's own name, crest and account rather than reading any of them a second time. The
            // member's line is never listed beneath the group's, since repeating it would say nothing
            // the line above did not - so the account it would have carried hangs directly off the
            // group, which is the same faction under another name.
            //
            // And the fraction it states is that faction's, for the same reason: how much of the
            // holder this faction is at odds with. The bloc-of-one reading beside it could only ever
            // count one member out of one, which states nothing at either end of its range.
            var groupMember = standing.members().get(0);
            var groupMemberLine = buildMemberLine(sector, groupMember);

            return CellTooltipEntry
                .createEntry(stateFraction(
                    buildGroupLine(
                        groupMemberLine.mark(),
                        groupMemberLine.labelText(),
                        standing),
                    routedStanding.readFractionFor(groupMember.factionId())))
                .nesting(accountResolver.resolveAccountEntries(groupMember));
        }
        // An alliance carries the alliance's own name and its lead (colour) member's crest - the same
        // name and crest the alliances view paints the bloc's cluster by - over the factions in it,
        // and states how much of its own membership the heading above it took.
        var allianceLine = stateFraction(
            buildGroupLine(
                resolveAllianceCrest(sector, grouping, blocId),
                grouping.resolveAllianceName(blocId),
                standing),
            routedStanding.fraction());

        // Gathered rather than subordinated: a bloc's line and the factions inside it are one answer to
        // who holds the system, stated at two granularities, so the members read inset beneath the bloc
        // without being demoted under the box's voice. Nothing has been broken down yet - that is what a
        // box explaining where a score came from hangs below a faction, and it must read the same size
        // whether the faction it hangs under is allied or standing alone.
        return CellTooltipEntry
            .createEntry(allianceLine)
            .grouping(resolveMemberEntries(sector, routedStanding, accountResolver));
    }

    // How far the heading over a row reaches, stated after the row's name where it reaches over only
    // part of it. A count out of a total is what the box worked out about the row and reads as a
    // finding, with nothing around it - what it counts is said by the heading the row sits under.
    //
    // Which rows state one and which state nothing is StandingFraction's own rule, so a row under a
    // heading true of all of it asks the same question every other row does.
    private static CellTooltipEntryLine stateFraction(
            CellTooltipEntryLine line,
            StandingFraction fraction) {

        if (!fraction.isStated()) {
            return line;
        }
        return line.qualifiedWith(KmuStringKeys.format(
            KmuStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_FRACTION,
            fraction.count(),
            fraction.total()));
    }

    // The crest a bloc shows: its colour (lead) faction's, which is the crest the alliances view
    // paints that bloc's cluster by, so the line naming a bloc and the cluster it names are marked
    // alike.
    private static CellTooltipMark resolveAllianceCrest(
            SectorAPI sector,
            HolderGrouping grouping,
            String blocId) {

        return CellTooltipMark.resolveMarkAsAuthored(FactionPresentation
            .resolvePresentation(sector, grouping.resolveColourFactionId(blocId))
            .crestSpritePath());
    }

    // One group's line, whichever kind of group it is: whatever names it, over the score its members
    // add up to.
    //
    // A group the pass weighed nothing for carries its nought in the quiet shade, under the same
    // treatment its members' lines and the claims box's own presence lines take. Read off the group
    // rather than off the member line it may have been built from, because a bloc is weighed where
    // any one member is: an alliance holding one weighed colony beside two unregistered ones has an
    // aggregate somebody worked out.
    private static CellTooltipEntryLine buildGroupLine(
            CellTooltipMark mark,
            String labelText,
            GroupStanding standing) {

        var groupLine = CellTooltipEntryLine.createCountedLine(
            mark,
            labelText,
            standing.aggregateScore());

        return standing.hasWeighedMember() ? groupLine : groupLine.statesUncountedValue();
    }

    // Resolves each ranked member standing into the entry it is listed as, preserving the ranking order,
    // so a group's members read in the order the standing placed them. This resolver states the two tiers
    // the standings have; what accounts for a member's score is the asking box's to say, so each member
    // carries whatever that box's resolver answered for it - subordinated, since an account explains the
    // line it hangs under rather than restating it more finely.
    private static List<CellTooltipEntry> resolveMemberEntries(
            SectorAPI sector,
            RoutedStanding routedStanding,
            FactionAccountResolver accountResolver) {

        var members = routedStanding.standing().members();
        var entries = new ArrayList<CellTooltipEntry>(members.size());

        for (var member : members) {
            entries.add(CellTooltipEntry
                .createEntry(stateFraction(
                    buildMemberLine(sector, member),
                    routedStanding.readFractionFor(member.factionId())))
                .nesting(accountResolver.resolveAccountEntries(member)));
        }
        return entries;
    }

    // One member's line: its crest, its name, and what the pass weighed it at.
    //
    // A presence-only standing's nought is drawn quiet, under the same treatment an unweighed colony
    // line takes beneath it. The pass weighed nothing for that faction - every term of a dominance
    // weight being economy-fed and its colonies unregistered - so the nought is the box's statement
    // about it rather than a weight it competed with. Drawn as loudly as the scores around it, it
    // would read as one competed for and lost, inviting exactly the comparison it cannot bear.
    private static CellTooltipEntryLine buildMemberLine(SectorAPI sector, FactionStanding member) {

        var memberLine = FactionTooltipLine.buildCountedFactionLine(
            sector,
            member.factionId(),
            member.score());

        return member instanceof WeighedFactionStanding
            ? memberLine
            : memberLine.statesUncountedValue();
    }
}
