package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipMark;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.politicalmap.base.tooltip.FactionAccountResolver.NO_ACCOUNT;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.buildEmptySector;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link StandingRowResolver}'s id-to-presentation step against a mocked sector and grouping: a
 * faction standing resolves to its long name, crest, and grouped score, a blank crest collapses to a
 * null path the render layer draws around, and an alliance takes the bloc name and its lead member's
 * crest over its ordered member lines.
 *
 * <p>Also that whether a group breaks down at all is settled here, which is the one place that knows a
 * group's kind: a lone faction resolves to an entry made up of nothing and an alliance to one carrying
 * its members however few it holds, so the box below simply lays out what it is handed.
 *
 * <p>And that a bloc's members are gathered as its peers rather than subordinated as its account, which
 * is the one relation this resolver is in a position to state - membership - and what keeps a faction's
 * own breakdown reading alike whether the faction is allied or standing alone. The account itself is
 * asked for rather than known here, so what is pinned about it is only that each faction gets its own
 * and that it hangs beneath that faction subordinated.
 */
final class StandingRowResolverTest {

    // An account naming the faction it was resolved for, so a case reads whose colonies landed under
    // which line - the one thing hanging accounts off a two-tier ranking can silently get wrong.
    private static final FactionAccountResolver FACTION_ACCOUNTS = standing -> List.of(
        CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(null, standing.factionId() + " colony", "1")));

    @Nested
    class ResolveRows {

        @Test
        void resolveRowsResolvesAFactionStandingToItsLongNameCrestAndScore() {

            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/hegemony_crest.png");

            var standings = List.of(
                new GroupStanding("hegemony", 7, List.of(new FactionStanding("hegemony", 7))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                HolderGrouping.identity(),
                NO_ACCOUNT);

            // A lone faction is made up of nothing: its one member would only repeat the line above it.
            assertThat(entries)
                .containsExactly(CellTooltipEntry.createEntry(
                    CellTooltipEntryLine.createLine(
                        CellTooltipMark.resolveMarkAsAuthored("graphics/hegemony_crest.png"),
                        "The Hegemony",
                        "7")));
        }

        @Test
        void resolveRowsGroupsTheThousandsOfALargeScore() {
            // The number reaches the box as the words it draws, so the grouping is settled here rather
            // than left to whichever body happens to list the entry.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");

            var standings = List.of(new GroupStanding(
                "hegemony",
                1200,
                List.of(new FactionStanding("hegemony", 1200))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                HolderGrouping.identity(),
                NO_ACCOUNT);

            assertThat(entries.get(0).line().valueText())
                .isEqualTo("1,200");
        }

        @Test
        void resolveRowsCollapsesABlankCrestToANullPathKeepingNameAndScore() {
            // A faction with an empty crest string still resolves - the line just carries a null crest
            // path and the render layer shows the name and score alone, rather than a broken sprite.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "");

            var standings = List.of(
                new GroupStanding("hegemony", 7, List.of(new FactionStanding("hegemony", 7))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                HolderGrouping.identity(),
                NO_ACCOUNT);

            assertThat(entries)
                .containsExactly(CellTooltipEntry.createEntry(
                    CellTooltipEntryLine.createLine(null, "The Hegemony", "7")));
        }

        @Test
        void resolveRowsResolvesAnAllianceToItsNameAndOrderedMemberLines() {

            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            stubFaction(sectorMock, "astral_armada", "Astral Armada", "graphics/aa.png");

            var standings = List.of(new GroupStanding("alliance-1", 11, List.of(
                new FactionStanding("hegemony", 8),
                new FactionStanding("astral_armada", 3))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                buildAllianceGrouping(),
                NO_ACCOUNT);

            // The bloc takes its own name and its lead (colour) member's crest, and the members stay in
            // the ranking order the standing placed them.
            assertThat(entries)
                .containsExactly(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(
                        CellTooltipMark.resolveMarkAsAuthored("graphics/heg.png"),
                        "Allied Powers",
                        "11"))
                    .grouping(List.of(
                        CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
                            CellTooltipMark.resolveMarkAsAuthored("graphics/heg.png"),
                            "The Hegemony",
                            "8")),
                        CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
                            CellTooltipMark.resolveMarkAsAuthored("graphics/aa.png"),
                            "Astral Armada",
                            "3")))));
        }

        @Test
        void resolveRowsLeavesAnAllianceCrestlessWhileMembersStayCrested() {
            // The lead (colour) faction has no authored crest, so the bloc's sprite is absent - but a
            // non-lead member with its own crest keeps it, since a bloc's missing crest never reaches
            // down into the member lines.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "");
            stubFaction(sectorMock, "astral_armada", "Astral Armada", "graphics/aa.png");

            var standings = List.of(new GroupStanding("alliance-1", 11, List.of(
                new FactionStanding("hegemony", 8),
                new FactionStanding("astral_armada", 3))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                buildAllianceGrouping(),
                NO_ACCOUNT);

            assertThat(entries)
                .containsExactly(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(null, "Allied Powers", "11"))
                    .grouping(List.of(
                        CellTooltipEntry.createEntry(
                            CellTooltipEntryLine.createLine(null, "The Hegemony", "8")),
                        CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
                            CellTooltipMark.resolveMarkAsAuthored("graphics/aa.png"),
                            "Astral Armada",
                            "3")))));
        }

        @Test
        void resolveRowsIsEmptyForEmptyStandings() {
            // An uninhabited system ranks no groups, so the tooltip has nothing to list.
            assertThat(StandingRowResolver.resolveRows(
                    buildEmptySector(),
                    List.of(),
                    HolderGrouping.identity(),
                    NO_ACCOUNT))
                .isEmpty();
        }

        @Test
        void resolveRowsPreservesTheRankedOrderAcrossGroups() {
            // The resolver renders groups in the order the ranking handed them over rather than
            // re-sorting, so the tooltip draws top-to-bottom exactly as the standings ranked.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            stubFaction(sectorMock, "tritachyon", "Tri-Tachyon", "graphics/tt.png");

            var standings = List.of(
                new GroupStanding("hegemony", 9, List.of(new FactionStanding("hegemony", 9))),
                new GroupStanding(
                    "tritachyon",
                    4,
                    List.of(new FactionStanding("tritachyon", 4))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                HolderGrouping.identity(),
                NO_ACCOUNT);

            assertThat(entries)
                .extracting(entry -> entry.line().labelText())
                .containsExactly("The Hegemony", "Tri-Tachyon");
        }

        @Test
        void resolveRowsFallsBackToTheIdWhenAFactionDoesNotResolve() {
            // A footprint id the sector no longer knows still ranks, so the line shows the bare id
            // rather than a nameless line - a tooltip draws one faction per line and cannot fall back
            // to the stand-in band the picker uses for a null name. Its crest resolves absent.
            var sectorMock = buildEmptySector();
            var standings = List.of(
                new GroupStanding("ghost", 5, List.of(new FactionStanding("ghost", 5))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                HolderGrouping.identity(),
                NO_ACCOUNT);

            assertThat(entries)
                .containsExactly(CellTooltipEntry.createEntry(
                    CellTooltipEntryLine.createLine(null, "ghost", "5")));
        }

        @Test
        void resolveRowsGroupsASingleMemberAllianceRatherThanCollapsingIt() {
            // The regression this guards: keying on the member count instead of the group's kind would
            // silently flatten a one-member alliance into a lone faction, so the same bloc would read
            // as two different things depending on how many members it happens to hold.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");

            var standings = List.of(new GroupStanding(
                "alliance-1",
                8,
                List.of(new FactionStanding("hegemony", 8))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                buildAllianceGrouping(),
                NO_ACCOUNT);

            assertThat(entries)
                .containsExactly(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(
                        CellTooltipMark.resolveMarkAsAuthored("graphics/heg.png"),
                        "Allied Powers",
                        "8"))
                    .grouping(List.of(CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
                        CellTooltipMark.resolveMarkAsAuthored("graphics/heg.png"),
                        "The Hegemony",
                        "8")))));
        }

        @Test
        void resolveRowsGathersAllianceMembersAsPeersRatherThanAsItsAccount() {
            // The regression this guards: subordinating the members would demote everything hung below
            // them a level, so an allied faction's colonies would draw a size smaller than an unallied
            // faction's in the same list - a difference the alliance has nothing to do with.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            stubFaction(sectorMock, "astral_armada", "Astral Armada", "graphics/aa.png");

            var standings = List.of(new GroupStanding("alliance-1", 11, List.of(
                new FactionStanding("hegemony", 8),
                new FactionStanding("astral_armada", 3))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                buildAllianceGrouping(),
                NO_ACCOUNT);

            assertThat(entries.get(0).isSubordinatingChildren())
                .isFalse();
            assertThat(entries.get(0).children())
                .hasSize(2);
        }

        @Test
        void resolveRowsHangsEachAllianceMembersOwnAccountBeneathIt() {
            // The regression this guards: pairing accounts with lines outside the resolver means
            // walking two lists at the same index, and one off-by-one lists a faction's colonies under
            // an ally's name - which a player reads as a fact about the sector rather than as a bug.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            stubFaction(sectorMock, "astral_armada", "Astral Armada", "graphics/aa.png");

            var standings = List.of(new GroupStanding("alliance-1", 11, List.of(
                new FactionStanding("hegemony", 8),
                new FactionStanding("astral_armada", 3))));

            var memberEntries = StandingRowResolver
                .resolveRows(sectorMock, standings, buildAllianceGrouping(), FACTION_ACCOUNTS)
                .get(0)
                .children();

            assertThat(readLabelTexts(memberEntries.get(0).children()))
                .containsExactly("hegemony colony");
            assertThat(readLabelTexts(memberEntries.get(1).children()))
                .containsExactly("astral_armada colony");
        }

        @Test
        void resolveRowsSubordinatesAnAccountBeneathTheFactionItExplains() {
            // The two relations meeting on one line: the bloc gathers its members as peers, and each
            // member subordinates the account of its own score. Read as one relation, the colonies
            // would sit at the members' own level and stop reading as the reason for their numbers.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");

            var standings = List.of(new GroupStanding(
                "alliance-1",
                8,
                List.of(new FactionStanding("hegemony", 8))));

            var groupEntry = StandingRowResolver
                .resolveRows(sectorMock, standings, buildAllianceGrouping(), FACTION_ACCOUNTS)
                .get(0);

            assertThat(groupEntry.isSubordinatingChildren())
                .isFalse();
            assertThat(groupEntry.children().get(0).isSubordinatingChildren())
                .isTrue();
        }

        @Test
        void resolveRowsHangsALoneFactionsAccountBeneathItsGroupLine() {
            // A lone-faction group is that faction under another name, so there is no member line
            // beneath to carry its account: dropping the member without moving the account up would
            // leave the faction view with a box that can explain nothing.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");

            var standings = List.of(
                new GroupStanding("hegemony", 7, List.of(new FactionStanding("hegemony", 7))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                HolderGrouping.identity(),
                FACTION_ACCOUNTS);

            assertThat(entries)
                .containsExactly(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(
                        CellTooltipMark.resolveMarkAsAuthored("graphics/heg.png"),
                        "The Hegemony",
                        "7"))
                    .nesting(List.of(CellTooltipEntry.createEntry(
                        CellTooltipEntryLine.createLine(null, "hegemony colony", "1")))));
        }
    }

    // Two allied factions folded into one bloc coloured (and so crested) by its lead member hegemony,
    // matching the grouping the ranking step produces for an alliance.
    private static HolderGrouping buildAllianceGrouping() {
        return new HolderGrouping(
            Map.of("hegemony", "alliance-1", "astral_armada", "alliance-1"),
            Map.of("alliance-1", "hegemony"),
            Map.of("alliance-1", "Allied Powers"));
    }
}
