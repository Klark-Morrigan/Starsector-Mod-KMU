package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.SystemStandings;
import kmu.maplayers.politicalmap.base.tooltip.SystemStandingsTooltip.ListedGroup;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Everything a standings box reaches past to reach the game, stood in for: the active view, the
 * settings the pass is read from, the ranking, the resolution of a ranked group into the line naming
 * it, and the system's status line.
 *
 * <p>Held as one fixture because a box reads all five before it draws anything, so a suite asserting
 * on any of them has to stand all five up first - and each suite writing that itself restates both the
 * seams and the order they must be installed and taken down in. Which is also what makes them easy to
 * get subtly wrong: a suite that forgets one gets a live LunaLib read or a live economy walk inside a
 * unit test rather than a failure naming the seam it missed.
 *
 * <p>The stubs read as intentions rather than as handles, so a case says what the game answered rather
 * than which static it went through - the point being that a box's collaborators are what a case is
 * setting, not Mockito.
 */
public final class StandingsTooltipSeamsFake {

    /**
     * The grouping the stood-in view answers with, and deliberately not the identity one: a grouping a
     * box could have reached for on its own would let one that ignored the view still pass. Asserted
     * by identity, since what matters is that this instance is the one that reached the ranking.
     */
    public static final HolderGrouping VIEW_GROUPING = new HolderGrouping(
        Map.of("hegemony", "rebel_pact", "tritachyon", "rebel_pact"),
        Map.of("rebel_pact", "hegemony"),
        Map.of("rebel_pact", "Rebel Pact"));

    // What a standing carries when a case is about how groups are laid out rather than about what any
    // of them is made of. Never asserted on - a case that cares states its own standing.
    private static final String ANY_BLOC_ID = "rebel_pact";
    private static final int ANY_SCORE = 0;

    private static MockedStatic<PoliticalMapViewRegistry> viewRegistryMock;
    private static MockedStatic<DominancePass> dominancePassMock;
    private static MockedStatic<SystemStandings> standingsMock;
    private static MockedStatic<StandingRowResolver> rowResolverMock;
    private static MockedStatic<SystemStatusRow> statusRowMock;

    private StandingsTooltipSeamsFake() {
    }

    /**
     * Stands every seam up with a view painting under {@link #VIEW_GROUPING} and a system that is
     * populated and under no decree, so a case states only what it is about.
     *
     * @param pass the pass the settings read answers with, which a case varies to change the rule or
     *             the dev reveal a box resolves under
     */
    public static void installSeams(DominancePass pass) {

        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.resolveGrouping())
            .thenReturn(VIEW_GROUPING);

        viewRegistryMock = Mockito.mockStatic(PoliticalMapViewRegistry.class);
        viewRegistryMock
            .when(PoliticalMapViewRegistry::getActiveView)
            .thenReturn(viewMock);

        dominancePassMock = Mockito.mockStatic(DominancePass.class);
        standingsMock = Mockito.mockStatic(SystemStandings.class);
        rowResolverMock = Mockito.mockStatic(StandingRowResolver.class);
        statusRowMock = Mockito.mockStatic(SystemStatusRow.class);

        stubPass(pass);
        stubNoStatus();
    }

    /** Takes every seam back down, so a suite leaves no statics mocked. */
    public static void clearSeams() {
        statusRowMock.close();
        rowResolverMock.close();
        standingsMock.close();
        dominancePassMock.close();
        viewRegistryMock.close();
    }

    /**
     * Switches the tab away from the political map, so no view is painting and there is no grouping to
     * rank under.
     */
    public static void stubNoActiveView() {
        viewRegistryMock
            .when(PoliticalMapViewRegistry::getActiveView)
            .thenReturn(null);
    }

    /**
     * Answers the settings read with {@code pass}, for a case turning on the rule or the dev reveal a
     * box resolves under.
     *
     * @param pass the pass every read of the player's settings answers with
     */
    public static void stubPass(DominancePass pass) {
        dominancePassMock
            .when(() -> DominancePass.readFromLunaSettings(any()))
            .thenReturn(pass);
    }

    /**
     * Hands the box the groups it is about: each ranked standing together with the line the resolver
     * would have named it with, standing in for both the economy walk that ranks them and the faction
     * and grouping lookups that name them.
     *
     * <p>Taken as pairs rather than as two stubs a case sets one at a time, because a box reads them
     * as pairs: a case that stubbed the lines and forgot the standings would fail on a seam it never
     * meant to be about, which is a fixture inventing a state the game cannot produce.
     *
     * @param listedGroups the system's groups in ranked order, each with the line naming it
     */
    public static void stubListedGroups(ListedGroup... listedGroups) {

        standingsMock
            .when(() -> SystemStandings.rankByDominationScore(
                any(SectorAPI.class),
                any(StarSystemAPI.class),
                any(DominancePass.class)))
            .thenReturn(Stream
                .of(listedGroups)
                .map(ListedGroup::standing)
                .toList());

        rowResolverMock
            .when(() -> StandingRowResolver.resolveRows(any(), any(), any()))
            .thenReturn(Stream
                .of(listedGroups)
                .map(ListedGroup::entry)
                .toList());
    }

    /**
     * Pairs a group's line with a standing carrying nothing worth reading, for the cases about how a
     * box lays groups out rather than about what any of them is made of.
     *
     * @param groupEntry the line naming the group
     * @return the pair, ready to hand to {@link #stubListedGroups}
     */
    public static ListedGroup listAnyGroup(CellTooltipEntry groupEntry) {
        return new ListedGroup(groupEntry, new GroupStanding(ANY_BLOC_ID, ANY_SCORE, List.of()));
    }

    /**
     * Stands the system's status line in as present, which the resolver behind it would otherwise need
     * a live economy to decide.
     *
     * @param statusText what the line says about the system
     * @return the line itself, so a case can assert the body carries that very row
     */
    public static TooltipRow stubStatusRow(String statusText) {
        
        var statusRow = CellTooltipRows.buildBannerRow(null, statusText);

        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), any(Boolean.class)))
            .thenReturn(Optional.of(statusRow));

        return statusRow;
    }

    /** Asserts the pass was read under the grouping the active view answered with. */
    public static void verifyPassReadUnderTheViewsGrouping() {
        dominancePassMock.verify(
            () -> DominancePass.readFromLunaSettings(same(VIEW_GROUPING)));
    }

    /** Asserts the ranked groups were named under that same grouping. */
    public static void verifyGroupsResolvedUnderTheViewsGrouping(SectorAPI sector) {
        rowResolverMock.verify(
            () -> StandingRowResolver.resolveRows(same(sector), any(), same(VIEW_GROUPING)));
    }

    /**
     * Asserts the system's status was judged under a given dev reveal - the same filter the standings
     * beside it were ranked through.
     *
     * @param sector                           the sector the box was drawn for
     * @param system                           the hovered system
     * @param shouldIncludeUndiscoveredMarkets the reveal the status is expected to have been judged
     *                                         under
     */
    public static void verifyStatusJudgedUnderReveal(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets) {

        statusRowMock.verify(
            () -> SystemStatusRow.resolveStatusRow(
                sector,
                system,
                shouldIncludeUndiscoveredMarkets));
    }

    // A system with nothing to say about itself beyond its standings, which is the ordinary case and
    // what keeps the line above the contest out of the way of cases about the contest.
    private static void stubNoStatus() {
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), any(Boolean.class)))
            .thenReturn(Optional.empty());
    }
}
