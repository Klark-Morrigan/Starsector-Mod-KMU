package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;
import kmlib.starsector.ui.widgets.TooltipRow;
import kmlib.text.KmlibNumbers;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.base.tooltip.SystemCellTooltip;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Why the claims layer paints a hovered star system in the colours it does: who claims it, and the
 * standings that settled the claim - the rivals who could have taken it, and the factions present
 * that were never eligible to.
 *
 * <p>The claim mechanic publishes only a winner, so a fill on its own leaves the player guessing at a
 * border they cannot check. Naming the claimant beside the contest behind it is what turns the layer
 * from a colouring into something readable: a system reads as narrowly contested, uncontested, or
 * held by decree over rivals who out-score its holder.
 *
 * <p>The claim section is drawn whatever the system holds, so a hover always answers the question the
 * layer poses - an unclaimed or dead system says so rather than showing a box with no claim in it.
 *
 * <p>Stateless past the reader it is built around, so one shared instance serves the layer.
 */
public final class SystemClaimTooltip extends SystemCellTooltip {

    // The breakdown scores every market present, found by the player or not, so the status line above
    // it has to count the same ones. Resolved under the narrower reveal, a system whose only colony
    // the player has not yet found would be called unpopulated directly above the rows scoring the
    // faction holding it.
    private static final boolean ADMITS_UNDISCOVERED_MARKETS = true;

    /**
     * The one shared instance, explaining vanilla claims - the same mechanic the layer's fills are
     * resolved through, so the box and the ground under it can never name different claimants.
     */
    public static final SystemClaimTooltip INSTANCE =
        new SystemClaimTooltip(new VanillaClaimBreakdownReader());

    private final ClaimBreakdownReader claimBreakdownReader;

    SystemClaimTooltip(ClaimBreakdownReader claimBreakdownReader) {
        this.claimBreakdownReader = claimBreakdownReader;
    }

    @Override
    protected List<TooltipRow> buildBodyRows(SectorAPI sector, StarSystemAPI system) {
        // One read for the whole box: the claimant, the override behind it, and every standing are all
        // taken from a single pass, so no two lines can describe different states of the system.
        var breakdown = claimBreakdownReader.readBreakdown(system);
        var claimantFactionId = breakdown.claimantFactionId();
        var rows = new ArrayList<TooltipRow>();

        // Why the system holds nobody comes before who claims it, so a dead system names its state
        // first and the claim below reads as a hold over empty ground rather than over a colony.
        SystemStatusRow
            .resolveStatusRow(sector, system, ADMITS_UNDISCOVERED_MARKETS)
            .ifPresent(rows::add);

        appendSection(
            rows,
            KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CLAIM,
            List.of(buildClaimantRow(sector, breakdown)));

        appendSection(
            rows,
            KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED,
            buildFactionRows(
                sector,
                selectRivalScores(breakdown, claimantFactionId, FactionClaimScore::isTerritorial)));

        appendSection(
            rows,
            KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_NON_TERRITORIAL,
            buildFactionRows(
                sector,
                selectRivalScores(breakdown, claimantFactionId, score -> !score.isTerritorial())));

        return rows;
    }

    // Adds a section - its heading over its rows - and nothing at all when it has no rows, so a
    // heading is never left standing over an empty block. Every section is added through here, which
    // is what makes the order sections read in the order of these calls rather than a rule spread
    // across them. The heading opens a section, parting its block from the one above it.
    private static void appendSection(
            List<TooltipRow> rows,
            String headingKey,
            List<TooltipRow> sectionRows) {

        if (sectionRows.isEmpty()) {
            return;
        }
        rows.add(CellTooltipRows
            .buildTopTierRow(null, KmuStrings.get(headingKey), CellTooltipRows.NO_SCORE)
            .opensSection());

        rows.addAll(sectionRows);
    }

    // The one line the claim section always carries: whoever holds the system, or the plain word for
    // nobody when no eligible faction scored and no decree imposed one.
    private static TooltipRow buildClaimantRow(SectorAPI sector, SystemClaimBreakdown breakdown) {
        var claimantFactionId = breakdown.claimantFactionId();

        if (!KmlibStrings.hasText(claimantFactionId)) {
            return CellTooltipRows.buildNestedRow(
                null,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_NONE),
                CellTooltipRows.NO_SCORE);
        }
        var row = buildFactionRow(
            sector,
            claimantFactionId,
            resolveScoreText(breakdown, claimantFactionId));

        // A core is held by decree rather than won, so the claim is qualified on the very line it is
        // made. Its market standing stays in the value column beside the marker: a decreed hold does
        // not erase the faction's presence, and a line of its own would read as a second claim.
        return isCoreClaim(breakdown)
            ? row.continuesWith(CellTooltipRows.buildQualifierSpan(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_MARKER)))
            : row;
    }

    // The standings shown under a section other than the claim: everyone present but the claimant,
    // narrowed to the kind of presence that section is about. The claimant is dropped from both, since
    // a faction named twice would read as holding two separate presences in the system.
    private static List<FactionClaimScore> selectRivalScores(
            SystemClaimBreakdown breakdown,
            String claimantFactionId,
            Predicate<FactionClaimScore> isWantedKind) {

        return breakdown
            .scores()
            .stream()
            .filter(score -> !score.factionId().equals(claimantFactionId))
            .filter(isWantedKind)
            .toList();
    }

    // Whether the claim was imposed rather than won. Read off the claimant matching the override
    // rather than off the override merely being set, so the marker states what the line above it
    // actually shows - the faction named there holding the system by decree.
    private static boolean isCoreClaim(SystemClaimBreakdown breakdown) {
        return breakdown.claimantFactionId() != null
            && breakdown.claimantFactionId().equals(breakdown.overrideFactionId());
    }

    // The standings as lines, already ranked strongest first by the breakdown - the order a contest is
    // read in, and the same order the mechanic itself settles it in.
    private static List<TooltipRow> buildFactionRows(
            SectorAPI sector,
            List<FactionClaimScore> scores) {

        var rows = new ArrayList<TooltipRow>(scores.size());

        for (var score : scores) {
            rows.add(buildFactionRow(
                sector,
                score.factionId(),
                KmlibNumbers.formatGroupedInteger(score.score())));
        }
        return rows;
    }

    // One faction's line: its crest, its name, and whatever the caller has to say about it in the
    // value column. Nested, since every line here belongs to the section heading above it.
    private static TooltipRow.TableRow buildFactionRow(
            SectorAPI sector,
            String factionId,
            String valueText) {

        var faction = sector.getFaction(factionId);

        return CellTooltipRows.buildNestedRow(
            FactionCrests.resolveCrestPath(faction),
            TooltipFactionNames.resolveLongName(faction, factionId),
            valueText);
    }

    // The claimant's own market standing, or no number at all when it holds none there: a core imposed
    // on a system its faction has no colony in is claimed without ever having been scored for it.
    private static String resolveScoreText(SystemClaimBreakdown breakdown, String factionId) {
        return breakdown
            .scores()
            .stream()
            .filter(score -> score.factionId().equals(factionId))
            .findFirst()
            .map(score -> KmlibNumbers.formatGroupedInteger(score.score()))
            .orElse(CellTooltipRows.NO_SCORE);
    }
}
