package kmu.maplayers.politicalmap.dominance.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.factions.FactionTooltipLine;
import kmu.maplayers.politicalmap.dominance.standings.FactionStanding;
import kmu.maplayers.politicalmap.dominance.standings.GroupStanding;
import kmu.maplayers.politicalmap.dominance.standings.StandingFraction;
import kmu.util.KmuStringKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a hovered system's pure two-tier standings into the entries the tooltip's block lists,
 * turning each ID into the name, crest, and number it presents as.
 *
 * <p>Separates "who ranks where" - {@code SystemStandings}, pure over the factions present and a
 * grouping - from "how a group and its factions present", the Starsector and grouping lookups
 * gathered here, so the render layer consumes plain entries.
 *
 * <p>It resolves into the shared entry model rather than into a model of its own, because a ranked
 * group already <em>is</em> what an entry is - a thing with a name, a mark, and a number, over whatever
 * it is made up of. A parallel record would mean converting one presentation-ready shape into another
 * for nothing, and two records that could come to disagree about what a resolved line holds.
 *
 * <p>A member is always a faction, and how one appears is {@link FactionTooltipLine}'s answer rather
 * than this resolver's, so a faction ranked here and the same faction named anywhere else in the box
 * cannot present as two. A group presents by its kind - a lone faction as its one member, an alliance
 * as its grouping names it - and that is settled here because this is the only side that knows the
 * kind. Keying on the kind rather than the size is what keeps a one-member alliance a bloc over its
 * member rather than collapsing it into a lone faction. A blank or absent crest resolves to a null
 * path the render layer draws around, so a group or member with no authored crest still shows its
 * name and score.
 *
 * <p>Built once per box over what every row of that box shares - the sector, the grouping and the
 * account - so the rows are resolved against one set of them rather than each call threading all three.
 */
public final class StandingRowResolver {

    private final FactionAccountResolver accountResolver;
    private final HolderGrouping grouping;
    private final SectorAPI sector;

    private StandingRowResolver(
            SectorAPI sector,
            HolderGrouping grouping,
            FactionAccountResolver accountResolver) {

        this.sector = sector;
        this.grouping = grouping;
        this.accountResolver = accountResolver;
    }

    /**
     * A resolver for one box.
     *
     * @param sector          the sector whose factions' names and crests are read
     * @param grouping        the active view's grouping, supplying an alliance's name and colour
     *                        faction; the identity grouping makes every group a lone faction
     * @param accountResolver what the listing box hangs beneath each faction as the account of its
     *                        score; {@link FactionAccountResolver#NO_ACCOUNT} lists every faction as
     *                        its line alone
     * @return the resolver
     */
    public static StandingRowResolver createForBox(
            SectorAPI sector,
            HolderGrouping grouping,
            FactionAccountResolver accountResolver) {

        return new StandingRowResolver(sector, grouping, accountResolver);
    }

    /**
     * Resolves a hovered system's ranked groups into the entries a block lists, in the same order, each
     * group's members gathered beneath it as its peers and each member over the account asked for it.
     *
     * @param standings the two-tier standings as the block lists them, in draw order
     * @return one entry per group in ranked order, each gathering its members' lines; empty when the
     *         standings are empty
     */
    public List<CellTooltipEntry> resolveRows(List<RoutedStanding> standings) {

        var entries = new ArrayList<CellTooltipEntry>(standings.size());

        for (var standing : standings) {
            entries.add(resolveGroupEntry(standing));
        }

        return List.copyOf(entries);
    }

    // One group's line, whichever kind of group it is: whatever names it, over the score its members
    // add up to.
    //
    // A group the pass weighed nothing for carries its nought in the quiet shade, under the same
    // treatment its members' lines and the claims box's own presence lines take, so a bloc cannot read
    // as weighed over members that were not. Read off the group rather than off the member line it
    // may have been built from, because a bloc is weighed where any one member is: an alliance holding
    // one weighed colony beside two unregistered ones has an aggregate somebody worked out.
    private static CellTooltipEntryLine buildGroupLine(
            CellTooltipMark mark,
            String labelText,
            GroupStanding standing) {

        var groupLine = CellTooltipEntryLine.createCountedLine(
            mark,
            labelText,
            standing.aggregateScore());

        return standing.hasWeighedMember()
            ? groupLine
            : groupLine.statesUncountedValue();
    }

    // How far the heading over a row reaches, stated after the row's name where it reaches over only
    // part of it. A count out of a total is what the box worked out about the row and reads as a
    // finding, with nothing around it - what it counts is said by the heading the row sits under.
    //
    // Which rows state one and which state nothing is StandingFraction's own rule, so a row under a
    // heading true of all of it asks the same question every other row does. Which of the two readings
    // a row states is settled by whoever builds the row: a bloc's row states how much of its own
    // membership the heading took, while a faction's states how much of the holder it is at odds with.
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

    // One member's line: its crest, its name, and what the pass weighed it at. A presence-only
    // standing is one the pass weighed nothing for - every term of a dominance weight being
    // economy-fed and its colonies unregistered - so its nought is drawn quiet.
    private CellTooltipEntryLine buildMemberLine(FactionStanding member) {

        return FactionTooltipLine.buildCountedFactionLine(
            sector,
            member.factionId(),
            member.score(),
            member.isWeighed());
    }

    // The crest a bloc shows: its colour (lead) faction's, which is the crest the alliances view
    // paints that bloc's cluster by, so the line naming a bloc and the cluster it names are marked
    // alike.
    private CellTooltipMark resolveAllianceCrest(String blocId) {
        return FactionTooltipLine.resolveCrestMark(sector, grouping.resolveColourFactionId(blocId));
    }

    // An alliance carries the alliance's own name and its lead (colour) member's crest - the same
    // name and crest the alliances view paints the bloc's cluster by - over the factions in it, and
    // states how much of its own membership the heading above it took.
    private CellTooltipEntry resolveAllianceEntry(RoutedStanding routedStanding) {

        var standing = routedStanding.standing();
        var blocId = standing.blocId();
        var allianceLine = stateFraction(
            buildGroupLine(
                resolveAllianceCrest(blocId),
                grouping.resolveGroupName(blocId),
                standing),
            routedStanding.fraction());

        // Gathered rather than subordinated: a bloc's line and the factions inside it are one answer to
        // who holds the system, stated at two granularities, so the members read inset beneath the bloc
        // without being demoted under the box's voice. Nothing has been broken down yet - that is what a
        // box explaining where a score came from hangs below a faction, and it must read the same size
        // whether the faction it hangs under is allied or standing alone.
        return CellTooltipEntry
            .createEntry(allianceLine)
            .grouping(resolveMemberEntries(routedStanding));
    }

    // Resolves one group into the entry it is listed as, by the group's kind.
    private CellTooltipEntry resolveGroupEntry(RoutedStanding routedStanding) {

        if (!grouping.isGroupedBloc(routedStanding.standing().blocId())) {
            return resolveLoneFactionEntry(routedStanding);
        }

        return resolveAllianceEntry(routedStanding);
    }

    // A lone-faction group has one member, so the group is exactly that member: it takes that member's
    // own name, crest and account rather than reading any of them a second time, which is what keeps a
    // singleton from ever drifting from the member it wraps and lets the flat faction view fall out for
    // free. The member's line is never listed beneath the group's, since repeating it would say nothing
    // the line above did not - so the account it would have carried hangs directly off the group, which
    // is the same faction under another name.
    //
    // And the fraction it states is that faction's, for the same reason: how much of the holder this
    // faction is at odds with. The bloc-of-one reading beside it could only ever count one member out of
    // one, which states nothing at either end of its range.
    private CellTooltipEntry resolveLoneFactionEntry(RoutedStanding routedStanding) {

        var standing = routedStanding.standing();
        var groupMember = standing.members().get(0);
        var groupMemberLine = buildMemberLine(groupMember);

        return CellTooltipEntry
            .createEntry(stateFraction(
                buildGroupLine(
                    groupMemberLine.mark(),
                    groupMemberLine.labelText(),
                    standing),
                routedStanding.readFractionFor(groupMember.factionId())))
            .nesting(accountResolver.resolveAccountEntries(groupMember));
    }

    // Resolves each ranked member standing into the entry it is listed as, preserving the ranking order,
    // so a group's members read in the order the standing placed them.
    //
    // What accounts for a member's score is the asking box's to say, so each member carries whatever that
    // box's resolver answered for it - subordinated, since an account explains the line it hangs under
    // rather than restating it more finely. Asking here rather than letting a box lay accounts over the
    // answer afterwards is what makes the pairing safe: the standing and the line named from it are both
    // in hand, so no caller has to walk two lists at the same index and risk listing one faction's
    // colonies under another's name.
    private List<CellTooltipEntry> resolveMemberEntries(RoutedStanding routedStanding) {

        var members = routedStanding.standing().members();
        var entries = new ArrayList<CellTooltipEntry>(members.size());

        for (var member : members) {

            entries.add(CellTooltipEntry
                .createEntry(stateFraction(
                    buildMemberLine(member),
                    routedStanding.readFractionFor(member.factionId())))
                .nesting(accountResolver.resolveAccountEntries(member)));
        }

        return entries;
    }
}
