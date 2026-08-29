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
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
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

    /**
     * The weighting a suite installs when its cases are about how groups are laid out rather than
     * about what any of them was weighed at. Forwarded to the stood-in ranking, so it never reaches
     * an assertion - which is exactly why it is shared: two suites spelling out weights neither of
     * them reads is two chances to state a rule that means nothing, differently.
     */
    public static final DominanceRules ANY_RULES = new DominanceRules(false,
        new BaseSizeWeighting(1.0, null, 1.0, 1.0),
        new StationWeighting(false, 1.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    /**
     * That weighting over no sector under {@link #VIEW_GROUPING} - what {@link #installSeams} is
     * handed by a suite whose boxes read no colonies of their own, so the set behind the pass is
     * nothing its cases have an opinion about.
     *
     * <p>A suite turning on the rule or the dev reveal builds its own and installs it instead.
     */
    public static final DominancePass ANY_PASS =
        DominancePass.over(null, ANY_RULES, ColonyVisibility.BASE_FOG, VIEW_GROUPING);

    // What a standing carries when a case is about how groups are laid out rather than about what any
    // of them is made of. Never asserted on - a case that cares states its own standing. The bloc is
    // a stem a rank is appended to, so no two stood-up groups are the same bloc.
    private static final String ANY_BLOC_ID_STEM = "bloc-";
    private static final int ANY_SCORE = 0;

    // Where the standings a block is being named sit in the resolver's parameters, which is what the
    // stand-in reads to answer that block with its own entries rather than with the whole box's.
    private static final int STANDINGS_ARGUMENT = 1;

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
     * game cannot produce. Each standing is its own bloc and nothing else worth reading, since a case
     * about how groups are laid out is not about what any of them is made of - and no two of them
     * stand together under any alliance set, which is the ordinary shape.
     *
     * @param groupEntries the system's groups in ranked order, each as the entry naming it
     */
    public static void stubGroupEntries(CellTooltipEntry... groupEntries) {

        var rankedStandings = new ArrayList<GroupStanding>(groupEntries.length);

        for (var index = 0; index < groupEntries.length; index++) {
            rankedStandings.add(new GroupStanding(ANY_BLOC_ID_STEM + index, ANY_SCORE, List.of()));
        }
        stubRankedGroups(rankedStandings, List.of(groupEntries));
    }

    /**
     * The same, with the blocs the groups are spelled out - for a case turning on which bloc a group
     * is, which is what an alliance set is read against.
     *
     * <p>The resolution answers per block rather than for the box as a whole: a box lists its groups
     * under several headings, so a fixture answering every call with every entry would put every
     * group under every heading and no case about routing could fail.
     *
     * @param rankedStandings the system's groups in ranked order, as the blocs they are
     * @param groupEntries    the entry naming each of those groups, in the same order
     */
    public static void stubRankedGroups(
            List<GroupStanding> rankedStandings,
            List<CellTooltipEntry> groupEntries) {

        var entriesByStanding = new HashMap<GroupStanding, CellTooltipEntry>();

        for (var index = 0; index < rankedStandings.size(); index++) {
            entriesByStanding.put(rankedStandings.get(index), groupEntries.get(index));
        }

        standingsMock
            .when(() -> SystemStandings.rankByDominationScore(
                any(StarSystemAPI.class),
                any(DominancePass.class)))
            .thenReturn(List.copyOf(rankedStandings));

        rowResolverMock
            .when(() -> StandingRowResolver.resolveRows(any(), any(), any(), any()))
            .thenAnswer(invocation -> selectEntriesFor(
                invocation.getArgument(STANDINGS_ARGUMENT),
                entriesByStanding));
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

    /**
     * Asserts the ranked groups were named under that same grouping.
     *
     * <p>Matched over the calls rather than counted, a box naming its groups one block at a time:
     * every block goes through the one call site, so what is worth pinning is the grouping that
     * reached it and not how many blocks the box happens to offer.
     */
    public static void verifyGroupsResolvedUnderTheViewsGrouping(SectorAPI sector) {
        rowResolverMock.verify(
            () -> StandingRowResolver.resolveRows(same(sector), any(), same(VIEW_GROUPING), any()),
            atLeastOnce());
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
            () -> StandingRowResolver.resolveRows(any(), any(), any(), same(accountResolver)),
            atLeastOnce());
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

    // The entries naming exactly the groups a block was handed, in the order it handed them over -
    // which is what the real resolver answers with, one entry per group and no reordering of its own.
    private static List<CellTooltipEntry> selectEntriesFor(
            List<GroupStanding> blockStandings,
            Map<GroupStanding, CellTooltipEntry> entriesByStanding) {

        return blockStandings
            .stream()
            .map(entriesByStanding::get)
            .toList();
    }

    // A system with nothing to say about itself beyond its standings, which is the ordinary case and
    // what keeps the line above the contest out of the way of cases about the contest.
    private static void stubNoStatus() {
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(ColonyKnowledge.class)))
            .thenReturn(Optional.empty());
    }
}
