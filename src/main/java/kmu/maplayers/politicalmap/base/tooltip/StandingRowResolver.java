package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionCrests;

import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a hovered system's pure two-tier standings into the render-ready rows the tooltip draws,
 * turning each id into the name and crest it presents as.
 *
 * <p>Separates "who ranks where" - {@code SystemStandings}, pure over footprints and a grouping -
 * from "how a group and its factions present", the Starsector and grouping lookups gathered here.
 * Confining {@link FactionAPI} and the grouping's label and crest reads to this resolver keeps the
 * render layer consuming plain rows, mirroring how {@code SectorPolitics} confines the ownership
 * palette lookups.
 *
 * <p>A member is always a faction, resolved by its long name and crest. A group header presents by
 * its kind: a lone-faction group reuses its one member's name and crest, so a singleton reads
 * identically to the member it wraps and the flat faction view falls out for free; an alliance group
 * takes its name from the grouping and its crest from the alliance's colour (lead) faction, the same
 * crest the alliances view paints the bloc's region by. A blank or absent crest resolves to a null
 * path the render layer draws around, so a header or member with no authored crest still shows its
 * name and score.
 */
public final class StandingRowResolver {

    private StandingRowResolver() {
    }

    /**
     * Resolves a hovered system's ranked groups into presentation rows in the same order, each
     * group's members resolved beneath it. The rows the tooltip renders top to bottom.
     *
     * @param sector    the sector whose {@link FactionAPI} names and crests are read
     * @param standings the two-tier standings ranked by {@code SystemStandings}, in draw order
     * @param grouping  the active view's grouping, supplying an alliance header's name and colour
     *                  faction; the identity grouping makes every group a lone faction
     * @return one row per group in ranked order, each carrying its members' rows; empty when the
     *         standings are empty
     */
    public static List<StandingGroupRow> resolveRows(
            SectorAPI sector,
            List<GroupStanding> standings,
            OwnershipGrouping grouping) {
        var rows = new ArrayList<StandingGroupRow>(standings.size());
        for (var standing : standings) {
            rows.add(resolveGroupRow(sector, standing, grouping));
        }
        return List.copyOf(rows);
    }

    // Resolves one group into a header over its members. The members resolve first so a lone-faction
    // header can reuse its single member's already-resolved name and crest, which is what keeps a
    // singleton group from ever drifting from the member it wraps.
    private static StandingGroupRow resolveGroupRow(
            SectorAPI sector,
            GroupStanding standing,
            OwnershipGrouping grouping) {
        var members = resolveMemberRows(sector, standing.members());
        var blocId = standing.blocId();
        String displayName;
        String crestSpritePath;
        if (grouping.isAlliance(blocId)) {
            // An alliance header carries the alliance's own name and its lead (colour) member's
            // crest - the same name and crest the alliances view paints the bloc's region by.
            displayName = grouping.resolveAllianceName(blocId);
            crestSpritePath = FactionCrests.resolveCrestPath(
                    sector.getFaction(grouping.resolveColorFactionId(blocId)));
        } else {
            // A lone-faction group is a singleton, so its header is exactly its one member: reuse
            // that resolved row rather than reading the faction a second time.
            var headerMember = members.get(0);
            displayName = headerMember.fullName();
            crestSpritePath = headerMember.crestSpritePath();
        }
        return new StandingGroupRow(
                blocId,
                displayName,
                crestSpritePath,
                standing.aggregateScore(),
                members);
    }

    // Resolves each ranked member standing into its rendered row, preserving the ranking order, so a
    // group's members draw in the order the standing placed them.
    private static List<FactionStandingRow> resolveMemberRows(
            SectorAPI sector,
            List<FactionStanding> members) {
        var rows = new ArrayList<FactionStandingRow>(members.size());
        for (var member : members) {
            var faction = sector.getFaction(member.factionId());
            rows.add(new FactionStandingRow(
                    member.factionId(),
                    resolveFactionName(faction, member.factionId()),
                    FactionCrests.resolveCrestPath(faction),
                    member.score()));
        }
        return rows;
    }

    // A faction's long display title, falling back to its id when the faction will not resolve, so a
    // member row is never nameless even for a footprint id the sector no longer knows. A tooltip row
    // shows one faction per line, so a bare id reads better than the null the picker tolerates for a
    // stand-in band.
    private static String resolveFactionName(FactionAPI faction, String factionId) {
        return faction == null ? factionId : faction.getDisplayNameLong();
    }
}
