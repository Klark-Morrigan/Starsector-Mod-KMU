package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import java.util.List;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.dominance.DecivilisedColonyHabitation.COUNTS_AS_POPULATED;
import static kmu.maplayers.politicalmap.base.tooltip.ContestWordingFixtures.CONTESTED_WORDING;

/**
 * How an integration suite stands one of the two hover families up over a stubbed sector and reads
 * what it drew. Shared because the pair is what a suite about the map's agreement with itself reads:
 * the boxes name the same colonies of one system off carriers neither shares with the other, so a
 * case comparing them stands both up the same way or compares two arrangements rather than two boxes.
 *
 * <p>Beside {@link PoliticalMapBoxSeamsFake} rather than inside it, on the split
 * {@link kmu.maplayers.base.tooltip.layout.CellTooltipRowReads} already draws: that stands the game
 * in, and this drives the real thing and hands back what it said. Nothing here is stood in for.
 *
 * <p>The claim reader is the real mechanic over the suite's own walk, since a stubbed contest holds
 * whatever markets the stub was handed - which is the one thing a suite reading a system for real
 * cannot pose. The dominance box takes the fake one because it never spends a claim on its listing:
 * a real reader there would be a walk of the sector to answer a question the box does not ask.
 */
final class PoliticalMapBoxReads {

    private PoliticalMapBoxReads() {
    }

    /**
     * The dominance box over one system, read to whatever depth a case is about.
     *
     * <p>A colony's own line is drawn only at the depths that open a faction up - the shallowest
     * hangs nothing beneath one - so a case about what a colony's line says names a deeper level.
     *
     * @param sector      the stubbed sector the box reads
     * @param system      the hovered system
     * @param detailLevel how far the box is opened
     * @return the body's blocks, in reading order
     */
    static List<TooltipSection> readDominanceSections(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        return buildDominanceBox()
            .composeBody(sector, system, detailLevel).blocks().readSections();
    }

    /**
     * The claims box over one system at that same depth, driven through the real claim mechanic over
     * its own walk of the sector.
     *
     * @param sector      the stubbed sector the box reads
     * @param system      the hovered system
     * @param detailLevel how far the box is opened
     * @return the body's blocks, in reading order
     */
    static List<TooltipSection> readClaimSections(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        return new SystemClaimTooltip(
                buildClaimBreakdownReaderOver(sector),
                HolderGrouping::identity,
                CONTESTED_WORDING)
            .composeBody(sector, system, detailLevel).blocks().readSections();
    }

    /**
     * The dominance box itself, for a case asking it something other than what it drew.
     *
     * @return a box over the plain faction view
     */
    static SystemDominationTooltip buildDominanceBox() {
        return new SystemDominationTooltip(
            new ClaimBreakdownReaderFake(),
            HolderGrouping::identity,
            CONTESTED_WORDING);
    }

    /**
     * The claim contest behind a sector, read through the real mechanic over one walk of it - what a
     * case reads when it is about the contest rather than about the box drawn from it.
     *
     * <p>Its own walk per call, since a pass carries the sector it was opened over: one held across
     * cases would answer a later case's system off an earlier case's sector.
     *
     * @param sector the stubbed sector to read
     * @return the reader, over that sector's own colony walk
     */
    static VanillaClaimBreakdownReader buildClaimBreakdownReaderOver(SectorAPI sector) {

        var pass = HolderPass.over(sector, BASE_FOG, COUNTS_AS_POPULATED, HolderGrouping.identity());

        return new VanillaClaimBreakdownReader(pass.colonyKnowledge(), pass.sectorIndex());
    }
}
