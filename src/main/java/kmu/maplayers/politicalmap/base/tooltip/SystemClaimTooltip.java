package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.text.KmlibNumbers;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.base.tooltip.CellTooltipSections;
import kmu.maplayers.politicalmap.base.PoliticalMapDevToggles;
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
 * <p>The claim section is drawn wherever there is an answer worth stating: a claimant, or a populated
 * system nobody has taken - which is a real finding, since the factions listed below are present and yet
 * none of them holds it. What it does not do is state "None" beneath a banner already saying the system
 * holds nobody, which answers the same absence twice over. A decree stays either way: holding a system
 * with nothing in it is the one thing that banner does not say.
 *
 * <p>Stateless past the reader it is built around, so one shared instance serves the layer.
 */
public final class SystemClaimTooltip extends PoliticalMapCellTooltip {

    /**
     * The one shared instance, explaining vanilla claims - the same mechanic the layer's fills are
     * resolved through, so the box and the cell under it can never name different claimants.
     */
    public static final SystemClaimTooltip INSTANCE =
        new SystemClaimTooltip(VANILLA_CLAIM_BREAKDOWN_READER);

    SystemClaimTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
    }

    @Override
    protected List<TooltipSection> buildBodySections(SectorAPI sector, StarSystemAPI system) {
        // One read for the whole box: the claimant, the override behind it, and every standing are all
        // taken from a single pass, so no two lines can describe different states of the system.
        var breakdown = claimBreakdownReader.readBreakdown(system);
        var claimantFactionId = breakdown.claimantFactionId();
        var sections = new ArrayList<TooltipSection>();

        // Why the system holds nobody comes before who claims it, so a dead system names its state
        // first and the claim below reads as a hold over an empty system rather than over a colony.
        // The reveal is read live off the same toggle the faction layer's pass samples, so crossing
        // between the two layers cannot make one call a system empty that the other calls held.
        var statusRow = SystemStatusRow.resolveStatusRow(
            sector,
            system,
            PoliticalMapDevToggles.readFromLunaSettings().isShowingAllFactions());

        CellTooltipSections.appendBannerSection(sections, statusRow);

        // The status is what the claim block is judged against, so the two are read from the one
        // resolve: a banner that appeared and a claim that says nobody would otherwise be settled by
        // two reads of the economy, one of which could call the system populated after the other had
        // already told the player it was not.
        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CLAIM),
            buildClaimEntries(sector, breakdown, statusRow.isPresent()));

        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED),
            buildFactionEntries(
                sector,
                selectRivalScores(breakdown, claimantFactionId, FactionClaimScore::isTerritorial)));

        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_NON_TERRITORIAL),
            buildFactionEntries(
                sector,
                selectRivalScores(breakdown, claimantFactionId, score -> !score.isTerritorial())));

        return sections;
    }

    // What the claim block lists: the one line naming whoever holds the system, and nothing at all
    // where nobody does and the banner above has already said the system holds nobody. Answered as an
    // empty listing rather than by skipping the call, so the block is dropped through the same rule that
    // drops every other empty one and the box cannot grow a heading standing over nothing.
    private static List<CellTooltipEntry> buildClaimEntries(
            SectorAPI sector,
            SystemClaimBreakdown breakdown,
            boolean isSystemHoldingNobody) {

        if (isSystemHoldingNobody && !KmlibStrings.hasText(breakdown.claimantFactionId())) {
            return List.of();
        }
        return List.of(buildClaimantEntry(sector, breakdown));
    }

    // The one entry the claim section lists where it has one: whoever holds the system, or the plain
    // word for nobody when no eligible faction scored and no decree imposed one.
    private static CellTooltipEntry buildClaimantEntry(
            SectorAPI sector,
            SystemClaimBreakdown breakdown) {

        var claimantFactionId = breakdown.claimantFactionId();

        if (!KmlibStrings.hasText(claimantFactionId)) {
            return CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
                null,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_NONE),
                CellTooltipRows.NO_SCORE));
        }
        var claimantLine = FactionTooltipEntry.buildFactionLine(
            sector,
            claimantFactionId,
            resolveScoreText(breakdown, claimantFactionId));

        // A core is held by decree rather than won, so the claim is qualified on the very line it is
        // made - it is why that line outranks a higher-scoring one beneath it, which the banner heading
        // the box does not answer. Its market standing stays in the value column beside the qualifier:
        // a decreed hold does not erase the faction's presence.
        if (isCoreClaim(breakdown)) {
            claimantLine = claimantLine.qualifiedWith(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_MARKER));
        }
        return CellTooltipEntry.createEntry(claimantLine);
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

    // The standings as entries, already ranked strongest first by the breakdown - the order a contest
    // is read in, and the same order the mechanic itself settles it in. Each stands on its own: claims
    // resolve per faction, so nothing here is ever made up of anything.
    private static List<CellTooltipEntry> buildFactionEntries(
            SectorAPI sector,
            List<FactionClaimScore> scores) {

        var entries = new ArrayList<CellTooltipEntry>(scores.size());

        for (var score : scores) {
            entries.add(FactionTooltipEntry.buildFactionEntry(
                sector,
                score.factionId(),
                KmlibNumbers.formatGroupedInteger(score.score())));
        }
        return entries;
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
