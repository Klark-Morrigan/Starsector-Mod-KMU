package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.base.visibility.ColonyKnowledge;
import kmu.maplayers.base.visibility.ColonyVisibility;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.SystemStandings;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
            .when(() -> DominancePass.readFromLunaSettings(any(), any()))
            .thenReturn(pass);
    }

    /**
     * Hands the box the groups it is about, as the entries the resolver would have named them with -
     * standing in for both the economy walk that ranks them and the faction and grouping lookups that
     * turn each into a line.
     *
     * <p>The ranking behind them is stood up to match, one standing per entry, because a box forwards
     * the ranked standings into the resolution: a case stubbing the entries alone would leave the box
     * naming a different number of groups than it ranked, which is a fixture inventing a state the
     * game cannot produce. What each of those standings carries is deliberately nothing worth reading,
     * since a case about how groups are laid out is not about what any of them is made of.
     *
     * @param groupEntries the system's groups in ranked order, each as the entry naming it
     */
    public static void stubGroupEntries(CellTooltipEntry... groupEntries) {

        standingsMock
            .when(() -> SystemStandings.rankByDominationScore(
                any(StarSystemAPI.class),
                any(DominancePass.class)))
            .thenReturn(Collections.nCopies(
                groupEntries.length,
                new GroupStanding(ANY_BLOC_ID, ANY_SCORE, List.of())));

        rowResolverMock
            .when(() -> StandingRowResolver.resolveRows(any(), any(), any(), any()))
            .thenReturn(List.of(groupEntries));
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
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(ColonyKnowledge.class)))
            .thenReturn(Optional.of(statusRow));

        return statusRow;
    }

    /** Asserts the pass was read under the grouping the active view answered with. */
    public static void verifyPassReadUnderTheViewsGrouping() {
        dominancePassMock.verify(
            () -> DominancePass.readFromLunaSettings(any(), same(VIEW_GROUPING)));
    }

    /** Asserts the ranked groups were named under that same grouping. */
    public static void verifyGroupsResolvedUnderTheViewsGrouping(SectorAPI sector) {
        rowResolverMock.verify(
            () -> StandingRowResolver.resolveRows(same(sector), any(), same(VIEW_GROUPING), any()));
    }

    /**
     * Asserts the factions were named with the account the box itself asked for, which is the one
     * thing a box adds to the shared resolution.
     *
     * @param accountResolver the resolver the box under test hands over
     */
    public static void verifyGroupsResolvedWithTheBoxsAccounts(
            FactionAccountResolver accountResolver) {

        rowResolverMock.verify(
            () -> StandingRowResolver.resolveRows(any(), any(), any(), same(accountResolver)));
    }

    /**
     * Asserts the system's status was judged under a given visibility rule - the same filter the
     * standings beside it were ranked through.
     *
     * <p>The colonies the line was handed are left unmatched: which walk they came off is the
     * box's business and is covered where that box reads for real, while what this pins is the
     * rule, which is the one thing the line and the standings must share.
     *
     * @param colonyVisibility the rule the status is expected to have been judged under
     */
    public static void verifyStatusJudgedUnderVisibility(ColonyVisibility colonyVisibility) {

        // Matched on the rule the knowledge carries rather than on the knowledge itself: the box
        // pairs the rule with the sector's register where it draws, so the value handed over is
        // never one a case could state.
        statusRowMock.verify(
            () -> SystemStatusRow.resolveStatusRow(
                any(),
                argThat(knowledge -> knowledge != null
                    && colonyVisibility.equals(knowledge.rule()))));
    }

    // A system with nothing to say about itself beyond its standings, which is the ordinary case and
    // what keeps the line above the contest out of the way of cases about the contest.
    private static void stubNoStatus() {
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(ColonyKnowledge.class)))
            .thenReturn(Optional.empty());
    }
}
