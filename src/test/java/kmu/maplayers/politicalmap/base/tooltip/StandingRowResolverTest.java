package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link StandingRowResolver}'s id-to-presentation step against a mocked sector and grouping:
 * a faction standing resolves to its long name and crest, a blank crest collapses to a null path the
 * render layer draws around, and an alliance group takes the bloc name and its lead member's crest
 * above its ordered member rows. Together they show the flat faction view and the nested alliance
 * view fall out of one resolver, and that a missing crest never strips a row of its name or score.
 */
final class StandingRowResolverTest {

    @Nested
    class ResolveRows {

        @Test
        void resolveRowsResolvesAFactionStandingToItsLongNameAndCrest() {
            var sectorMock = mock(SectorAPI.class);
            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/hegemony_crest.png");
            var standings = List.of(
                    new GroupStanding("hegemony", 7, List.of(new FactionStanding("hegemony", 7))));

            var rows = StandingRowResolver.resolveRows(
                    sectorMock, standings, HolderGrouping.identity());

            // A singleton group renders flat: its header is exactly its one member, both carrying the
            // faction's long title and crest.
            assertThat(rows).containsExactly(new StandingGroupRow(
                    "hegemony", "The Hegemony", "graphics/hegemony_crest.png", 7, false,
                    List.of(new FactionStandingRow(
                            "hegemony", "The Hegemony", "graphics/hegemony_crest.png", 7))));
        }

        @Test
        void resolveRowsCollapsesABlankCrestToANullPathKeepingNameAndScore() {
            // A faction with an empty crest string still resolves - the row just carries a null crest
            // path and the render layer shows the name and score alone, rather than a broken sprite.
            var sectorMock = mock(SectorAPI.class);
            stubFaction(sectorMock, "hegemony", "The Hegemony", "");
            var standings = List.of(
                    new GroupStanding("hegemony", 7, List.of(new FactionStanding("hegemony", 7))));

            var rows = StandingRowResolver.resolveRows(
                    sectorMock, standings, HolderGrouping.identity());

            assertThat(rows).containsExactly(new StandingGroupRow(
                    "hegemony", "The Hegemony", null, 7, false,
                    List.of(new FactionStandingRow("hegemony", "The Hegemony", null, 7))));
        }

        @Test
        void resolveRowsResolvesAnAllianceGroupToItsNameAndOrderedMemberRows() {
            var sectorMock = mock(SectorAPI.class);
            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            stubFaction(sectorMock, "astral_armada", "Astral Armada", "graphics/aa.png");
            var standings = List.of(new GroupStanding("alliance-1", 11, List.of(
                    new FactionStanding("hegemony", 8),
                    new FactionStanding("astral_armada", 3))));

            var rows = StandingRowResolver.resolveRows(
                    sectorMock, standings, buildAllianceGrouping());

            // The header takes the bloc name and its lead (colour) member's crest, and the members
            // stay in the ranking order the standing placed them.
            assertThat(rows).containsExactly(new StandingGroupRow(
                    "alliance-1", "Allied Powers", "graphics/heg.png", 11, true,
                    List.of(
                            new FactionStandingRow("hegemony", "The Hegemony", "graphics/heg.png", 8),
                            new FactionStandingRow(
                                    "astral_armada", "Astral Armada", "graphics/aa.png", 3))));
        }

        @Test
        void resolveRowsLeavesAnAllianceHeaderCrestlessWhileMembersStayCrested() {
            // The lead (colour) faction has no authored crest, so the header sprite is absent - but a
            // non-lead member with its own crest keeps it, since a header's missing crest never
            // reaches down into the member rows.
            var sectorMock = mock(SectorAPI.class);
            stubFaction(sectorMock, "hegemony", "The Hegemony", "");
            stubFaction(sectorMock, "astral_armada", "Astral Armada", "graphics/aa.png");
            var standings = List.of(new GroupStanding("alliance-1", 11, List.of(
                    new FactionStanding("hegemony", 8),
                    new FactionStanding("astral_armada", 3))));

            var rows = StandingRowResolver.resolveRows(
                    sectorMock, standings, buildAllianceGrouping());

            assertThat(rows).containsExactly(new StandingGroupRow(
                    "alliance-1", "Allied Powers", null, 11, true,
                    List.of(
                            new FactionStandingRow("hegemony", "The Hegemony", null, 8),
                            new FactionStandingRow(
                                    "astral_armada", "Astral Armada", "graphics/aa.png", 3))));
        }

        @Test
        void resolveRowsIsEmptyForEmptyStandings() {
            // An uninhabited system ranks no groups, so the tooltip has no rows to draw.
            assertThat(StandingRowResolver.resolveRows(
                    mock(SectorAPI.class), List.of(), HolderGrouping.identity()))
                    .isEmpty();
        }

        @Test
        void resolveRowsPreservesTheRankedOrderAcrossGroups() {
            // The resolver renders groups in the order the ranking handed them over rather than
            // re-sorting, so the tooltip draws top-to-bottom exactly as the standings ranked.
            var sectorMock = mock(SectorAPI.class);
            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            stubFaction(sectorMock, "tritachyon", "Tri-Tachyon", "graphics/tt.png");
            var standings = List.of(
                    new GroupStanding("hegemony", 9, List.of(new FactionStanding("hegemony", 9))),
                    new GroupStanding(
                            "tritachyon", 4, List.of(new FactionStanding("tritachyon", 4))));

            var rows = StandingRowResolver.resolveRows(
                    sectorMock, standings, HolderGrouping.identity());

            assertThat(rows).extracting(StandingGroupRow::groupId)
                    .containsExactly("hegemony", "tritachyon");
        }

        @Test
        void resolveRowsFallsBackToTheIdWhenAFactionDoesNotResolve() {
            // A footprint id the sector no longer knows still ranks, so the row shows the bare id
            // rather than a nameless line - a tooltip draws one faction per row and cannot fall back
            // to the stand-in band the picker uses for a null name. Its crest resolves absent.
            var sectorMock = mock(SectorAPI.class);
            var standings = List.of(
                    new GroupStanding("ghost", 5, List.of(new FactionStanding("ghost", 5))));

            var rows = StandingRowResolver.resolveRows(
                    sectorMock, standings, HolderGrouping.identity());

            assertThat(rows).containsExactly(new StandingGroupRow(
                    "ghost", "ghost", null, 5, false,
                    List.of(new FactionStandingRow("ghost", "ghost", null, 5))));
        }

        @Test
        void resolveRowsSetsNestsMembersTrueForASingleMemberAlliance() {
            // An alliance with only one member present still nests - the flag is set from the group's
            // kind, not its size - so the render layer draws the bloc header over its one member
            // (a tree) rather than collapsing it to a single line.
            var sectorMock = mock(SectorAPI.class);
            stubFaction(sectorMock, "hegemony", "The Hegemony", "graphics/heg.png");
            var standings = List.of(new GroupStanding(
                    "alliance-1", 8, List.of(new FactionStanding("hegemony", 8))));

            var rows = StandingRowResolver.resolveRows(sectorMock, standings, buildAllianceGrouping());

            assertThat(rows).containsExactly(new StandingGroupRow(
                    "alliance-1", "Allied Powers", "graphics/heg.png", 8, true,
                    List.of(new FactionStandingRow("hegemony", "The Hegemony", "graphics/heg.png", 8))));
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

    // Stubs one faction's long name and crest on the sector, so a test states each faction's
    // presentation in one line rather than three when-chains.
    private static void stubFaction(
            SectorAPI sectorMock, String factionId, String longName, String crest) {
        var factionMock = mock(FactionAPI.class);
        when(sectorMock.getFaction(factionId)).thenReturn(factionMock);
        when(factionMock.getDisplayNameLong()).thenReturn(longName);
        when(factionMock.getCrest()).thenReturn(crest);
    }
}
