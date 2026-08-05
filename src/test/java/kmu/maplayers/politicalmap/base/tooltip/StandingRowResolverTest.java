package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
 */
final class StandingRowResolverTest {

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
                HolderGrouping.identity());

            // A lone faction is made up of nothing: its one member would only repeat the line above it.
            assertThat(entries)
                .containsExactly(CellTooltipEntry.createEntry(
                    CellTooltipEntryLine.createLine(
                        "graphics/hegemony_crest.png",
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
                HolderGrouping.identity());

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
                HolderGrouping.identity());

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
                buildAllianceGrouping());

            // The bloc takes its own name and its lead (colour) member's crest, and the members stay in
            // the ranking order the standing placed them.
            assertThat(entries)
                .containsExactly(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(
                        "graphics/heg.png",
                        "Allied Powers",
                        "11"))
                    .nesting(List.of(
                        CellTooltipEntryLine.createLine(
                            "graphics/heg.png",
                            "The Hegemony",
                            "8"),
                        CellTooltipEntryLine.createLine(
                            "graphics/aa.png",
                            "Astral Armada",
                            "3"))));
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
                buildAllianceGrouping());

            assertThat(entries)
                .containsExactly(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(null, "Allied Powers", "11"))
                    .nesting(List.of(
                        CellTooltipEntryLine.createLine(null, "The Hegemony", "8"),
                        CellTooltipEntryLine.createLine(
                            "graphics/aa.png",
                            "Astral Armada",
                            "3"))));
        }

        @Test
        void resolveRowsIsEmptyForEmptyStandings() {
            // An uninhabited system ranks no groups, so the tooltip has nothing to list.
            assertThat(StandingRowResolver.resolveRows(
                    buildEmptySector(),
                    List.of(),
                    HolderGrouping.identity()))
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
                HolderGrouping.identity());

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
                HolderGrouping.identity());

            assertThat(entries)
                .containsExactly(CellTooltipEntry.createEntry(
                    CellTooltipEntryLine.createLine(null, "ghost", "5")));
        }

        @Test
        void resolveRowsNestsASingleMemberAllianceRatherThanCollapsingIt() {
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
                buildAllianceGrouping());

            assertThat(entries)
                .containsExactly(CellTooltipEntry
                    .createEntry(CellTooltipEntryLine.createLine(
                        "graphics/heg.png",
                        "Allied Powers",
                        "8"))
                    .nesting(List.of(CellTooltipEntryLine.createLine(
                        "graphics/heg.png",
                        "The Hegemony",
                        "8"))));
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
