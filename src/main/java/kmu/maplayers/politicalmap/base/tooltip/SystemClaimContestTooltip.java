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
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The shape every box built on a hovered system's claim contest takes: what the system is, then who
 * claims it, then the rivals who could have taken it, then the factions present that were never
 * eligible to.
 *
 * <p>The claim mechanic publishes only a winner, so a fill on its own leaves the player guessing at a
 * border they cannot check. Naming the claimant beside the contest behind it is what turns the layer
 * from a colouring into something readable: a system reads as narrowly contested, uncontested, or
 * held by decree over rivals who out-score its holder.
 *
 * <p>The claim block is drawn wherever there is an answer worth stating: a claimant, or a populated
 * system nobody has taken - which is a real finding, since the factions listed below are present and yet
 * none of them holds it. What it does not do is state "None" beneath a banner already saying the system
 * holds nobody, which answers the same absence twice over. A decree stays either way: holding a system
 * with nothing in it is the one thing that banner does not say.
 *
 * <p>All of that is settled here rather than per box because two boxes over one system have to be two
 * amounts of detail about the same contest, not two contests. The breakdown is read once, the status is
 * judged against that same read, and every faction present is placed into its block by the one rule - so
 * a box stating more detail cannot name a different claimant, judge the system populated where the other
 * called it empty, or sort a rival into a block the other put it elsewhere in. What is left open is the
 * one thing the detail is: what, if anything, a listed faction breaks down into.
 *
 * <p>Stateless past the reader it is built around, so one shared instance per box serves the layer.
 */
public abstract class SystemClaimContestTooltip extends PoliticalMapCellTooltip {

    protected SystemClaimContestTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
    }

    @Override
    protected final List<TooltipSection> buildBodySections(SectorAPI sector, StarSystemAPI system) {
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
                breakdown,
                selectRivalScores(breakdown, claimantFactionId, FactionClaimScore::isTerritorial)));

        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_NON_TERRITORIAL),
            buildFactionEntries(
                sector,
                breakdown,
                selectRivalScores(breakdown, claimantFactionId, score -> !score.isTerritorial())));

        return sections;
    }

    @Override
    protected final Optional<String> resolveExpandedDetailName(
            SectorAPI sector,
            StarSystemAPI system) {

        // The counterpart accounts for the colonies behind a standing, so a system nobody scored in has
        // nothing for it to account for: both boxes would state the same claim line and the key would
        // do nothing the player could see. Asked of the very read the box is built from, so it can
        // never offer to expand a contest it is about to draw as empty.
        if (claimBreakdownReader.readBreakdown(system).scores().isEmpty()) {
            return Optional.empty();
        }
        // Answered for the pair at once rather than by each box, because it is the one thing they agree
        // on: the counterpart accounts for the very scores the ordinary box states, so a player
        // switching either way is being offered the same account. Which direction the hint reads
        // follows from which of the two is being drawn, and is none of this class's business.
        return Optional.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_DETAIL_CONTRIBUTIONS));
    }

    /**
     * Resolves what hangs beneath each faction the box lists, as the account of where that faction's
     * standing came from. The seam the whole class exists around: which factions are listed, under
     * which heading, above what, and how each presents are all settled by the time this is called, so
     * what is left to answer is only whether a listed faction breaks down further and into what.
     *
     * <p>Asked per faction rather than once per paint, since a standing already carries the markets
     * behind it - there is no second read of the economy for a box to save by asking earlier. The
     * whole contest is handed over beside it because an account may turn on how the system was
     * settled rather than on the faction alone, and reading that a second way here is what would let
     * the account and the claim line above it disagree.
     *
     * <p>Listing a faction as the line naming it is the ordinary answer and the default, so a box with
     * nothing further to say overrides nothing.
     *
     * @param breakdown the whole contest the box is being built from, in case the account turns on it
     * @param standing  the faction's ranked place in that contest
     * @return the entries listed beneath its line, in the order they are read; empty leaves the faction
     *         listed as its line alone
     */
    protected List<CellTooltipEntry> resolveAccountEntries(
            SystemClaimBreakdown breakdown,
            FactionClaimScore standing) {

        return List.of();
    }

    // What the claim block lists: the one line naming whoever holds the system, and nothing at all
    // where nobody does and the banner above has already said the system holds nobody. Answered as an
    // empty listing rather than by skipping the call, so the block is dropped through the same rule that
    // drops every other empty one and the box cannot grow a heading standing over nothing.
    private List<CellTooltipEntry> buildClaimEntries(
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
    private CellTooltipEntry buildClaimantEntry(
            SectorAPI sector,
            SystemClaimBreakdown breakdown) {

        var claimantFactionId = breakdown.claimantFactionId();

        if (!KmlibStrings.hasText(claimantFactionId)) {
            return CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
                null,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_NONE),
                CellTooltipRows.NO_SCORE));
        }
        // The claimant's own market standing, or none at all when it holds none there: a core imposed on
        // a system its faction has no colony in is claimed without ever having been scored for it - so
        // the number and the account beneath it are both read off the one standing rather than looked up
        // apart, which is what stops a line showing one faction's score over another's colonies.
        var standing = findStanding(breakdown, claimantFactionId);
        var claimantLine = FactionTooltipEntry.buildFactionLine(
            sector,
            claimantFactionId,
            standing
                .map(score -> KmlibNumbers.formatGroupedInteger(score.score()))
                .orElse(CellTooltipRows.NO_SCORE));

        // A core is held by decree rather than won, so the claim is qualified on the very line it is
        // made - it is why that line outranks a higher-scoring one beneath it, which the banner heading
        // the box does not answer. Its market standing stays in the value column beside the qualifier:
        // a decreed hold does not erase the faction's presence.
        if (isCoreClaim(breakdown)) {
            claimantLine = claimantLine.qualifiedWith(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_MARKER));
        }
        return CellTooltipEntry
            .createEntry(claimantLine)
            .nesting(standing
                .map(score -> resolveAccountEntries(breakdown, score))
                .orElseGet(List::of));
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
    // is read in, and the same order the mechanic itself settles it in. Each carries whatever this box
    // accounts for it with, subordinated: an account explains the line it hangs under rather than
    // restating it more finely.
    private List<CellTooltipEntry> buildFactionEntries(
            SectorAPI sector,
            SystemClaimBreakdown breakdown,
            List<FactionClaimScore> scores) {

        var entries = new ArrayList<CellTooltipEntry>(scores.size());

        for (var score : scores) {
            entries.add(FactionTooltipEntry
                .buildFactionEntry(
                    sector,
                    score.factionId(),
                    KmlibNumbers.formatGroupedInteger(score.score()))
                .nesting(resolveAccountEntries(breakdown, score)));
        }
        return entries;
    }

    // One faction's place in the contest, or none where it took no standing of its own.
    private static Optional<FactionClaimScore> findStanding(
            SystemClaimBreakdown breakdown,
            String factionId) {

        return breakdown
            .scores()
            .stream()
            .filter(score -> score.factionId().equals(factionId))
            .findFirst();
    }
}
