package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipMark;
import kmu.maplayers.base.tooltip.CellTooltipQualifier;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.PresenceOnlyFactionStanding;
import kmu.maplayers.politicalmap.base.dominance.WeighedFactionStanding;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture.buildAllianceOf;
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
 * <p>And how loudly a score is drawn, which follows from the standing's own kind at both tiers: a
 * faction the pass weighed nothing for carries its nought in the quiet shade, and so does a bloc no
 * member of which was weighed.
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

    // The player's own wording, which the one line stating the bloc a faction was listed apart from
    // reads through. Every other case names nothing the player reads, so the install is inert for
    // them.
    @BeforeEach
    void installWording() {
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearWording() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveRows {

        @Test
        void resolveRowsResolvesAFactionStandingToItsLongNameCrestAndScore() {

            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/hegemony_crest.png");

            var standings = routeWhole(
                new GroupStanding("hegemony", 7, List.of(new WeighedFactionStanding("hegemony", 7))));

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

            var standings = routeWhole(new GroupStanding(
                "hegemony",
                1200,
                List.of(new WeighedFactionStanding("hegemony", 1200))));

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

            var standings = routeWhole(
                new GroupStanding("hegemony", 7, List.of(new WeighedFactionStanding("hegemony", 7))));

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

            var standings = routeWhole(new GroupStanding("alliance-1", 11, List.of(
                new WeighedFactionStanding("hegemony", 8),
                new WeighedFactionStanding("astral_armada", 3))));

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

            var standings = routeWhole(new GroupStanding("alliance-1", 11, List.of(
                new WeighedFactionStanding("hegemony", 8),
                new WeighedFactionStanding("astral_armada", 3))));

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
        void resolveRowsNamesTheBlocAMemberListedApartFromItCameOutOf() {
            // A bloc its members disagree about is listed a member at a time, so the member reads as
            // the lone faction it now stands as - and the alliance is stated after its name, so the
            // grouping the map paints that territory by is never left to be inferred from a row that
            // simply went missing.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            stubFaction(sectorMock, "astral_armada", "Astral Armada", "graphics/aa.png");

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                List.of(RoutedStanding.dissolveFrom(
                    new WeighedFactionStanding("astral_armada", 3),
                    "alliance-1")),
                buildAllianceGrouping(),
                NO_ACCOUNT);

            // The name is what the box worked out about the row and reads as a finding; the word
            // before it and the bloc's own crest are the sentence around it.
            assertThat(entries)
                .containsExactly(CellTooltipEntry.createEntry(CellTooltipEntryLine
                    .createLine(
                        CellTooltipMark.resolveMarkAsAuthored("graphics/aa.png"),
                        "Astral Armada",
                        "3")
                    .callsOut(CellTooltipQualifier.introduceFinding(
                        "of",
                        CellTooltipMark.resolveMarkAsAuthored("graphics/heg.png"),
                        "Allied Powers"))));
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

            var standings = routeWhole(
                new GroupStanding("hegemony", 9, List.of(new WeighedFactionStanding("hegemony", 9))),
                new GroupStanding(
                    "tritachyon",
                    4,
                    List.of(new WeighedFactionStanding("tritachyon", 4))));

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
            var standings = routeWhole(
                new GroupStanding("ghost", 5, List.of(new WeighedFactionStanding("ghost", 5))));

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

            var standings = routeWhole(new GroupStanding(
                "alliance-1",
                8,
                List.of(new WeighedFactionStanding("hegemony", 8))));

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

            var standings = routeWhole(new GroupStanding("alliance-1", 11, List.of(
                new WeighedFactionStanding("hegemony", 8),
                new WeighedFactionStanding("astral_armada", 3))));

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

            var standings = routeWhole(new GroupStanding("alliance-1", 11, List.of(
                new WeighedFactionStanding("hegemony", 8),
                new WeighedFactionStanding("astral_armada", 3))));

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

            var standings = routeWhole(new GroupStanding(
                "alliance-1",
                8,
                List.of(new WeighedFactionStanding("hegemony", 8))));

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

            var standings = routeWhole(
                new GroupStanding("hegemony", 7, List.of(new WeighedFactionStanding("hegemony", 7))));

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

        @Test
        void resolveRowsDrawsAPresenceOnlyFactionsNoughtQuiet() {
            // The nought is what the pass recorded for a faction it weighed nothing for, not a
            // weight the faction competed with - so only the number quietens, the faction being
            // named as loudly as the holders around it.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "tritachyon", "Tri-Tachyon", "graphics/tt.png");

            var standings = routeWhole(new GroupStanding(
                "tritachyon",
                0,
                List.of(new PresenceOnlyFactionStanding("tritachyon"))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                HolderGrouping.identity(),
                NO_ACCOUNT);

            assertThat(entries.get(0).line().isValueUncounted())
                .isTrue();
            assertThat(entries.get(0).line().labelText())
                .isEqualTo("Tri-Tachyon");
            assertThat(entries.get(0).line().valueText())
                .isEqualTo("0");
        }

        @Test
        void resolveRowsDrawsAWeighedNoughtAsAScoreLikeAnyOther() {
            // A colony weighed and found to be worth nothing is a number the arithmetic arrived at,
            // so it reads as loudly as a large one - which is the whole of what parts the two kinds
            // of nought.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");

            var standings = routeWhole(
                new GroupStanding("hegemony", 0, List.of(new WeighedFactionStanding("hegemony", 0))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                HolderGrouping.identity(),
                NO_ACCOUNT);

            assertThat(entries.get(0).line().isValueUncounted())
                .isFalse();
        }

        @Test
        void resolveRowsDrawsAnUnweighedBlocsAggregateQuietOverItsMembers() {
            // A bloc present through unregistered colonies alone: the aggregate is a nought nobody
            // worked out, so the bloc's own line quietens with the member lines beneath it rather
            // than reading as a sum competed for.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            stubFaction(sectorMock, "astral_armada", "Astral Armada", "graphics/aa.png");

            var standings = routeWhole(new GroupStanding("alliance-1", 0, List.of(
                new PresenceOnlyFactionStanding("hegemony"),
                new PresenceOnlyFactionStanding("astral_armada"))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                buildAllianceGrouping(),
                NO_ACCOUNT);

            assertThat(entries.get(0).line().isValueUncounted())
                .isTrue();
            assertThat(entries.get(0).children())
                .allMatch(member -> member.line().isValueUncounted());
        }

        @Test
        void resolveRowsDrawsABlocsAggregateLoudWhereOneMemberWasWeighed() {
            // One weighed member makes the aggregate a sum somebody worked out, so the bloc's line
            // stays a finding however many of its allies are merely present - and the ally's own
            // line still quietens beneath it.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            stubFaction(sectorMock, "astral_armada", "Astral Armada", "graphics/aa.png");

            var standings = routeWhole(new GroupStanding("alliance-1", 8, List.of(
                new WeighedFactionStanding("hegemony", 8),
                new PresenceOnlyFactionStanding("astral_armada"))));

            var entries = StandingRowResolver.resolveRows(
                sectorMock,
                standings,
                buildAllianceGrouping(),
                NO_ACCOUNT);

            assertThat(entries.get(0).line().isValueUncounted())
                .isFalse();
            assertThat(entries.get(0).children())
                .extracting(member -> member.line().isValueUncounted())
                .containsExactly(false, true);
        }
    }

    // The two factions this suite poses in one bloc, coloured (and so crested) by its lead member
    // hegemony, matching the grouping the ranking step produces for an alliance. Named here rather
    // than at each case, since every case about the alliance poses the same two.
    private static HolderGrouping buildAllianceGrouping() {
        return buildAllianceOf("hegemony", "astral_armada");
    }

    // The groups as a block that listed every one of them whole hands them over - what every block
    // placed by membership does, and what the two placed by disposition do with a bloc its members
    // agree about. The state every case but the one about a bloc listed apart is posed in.
    private static List<RoutedStanding> routeWhole(GroupStanding... standings) {
        return Arrays
            .stream(standings)
            .map(RoutedStanding::routeWhole)
            .toList();
    }
}
