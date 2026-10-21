package kmu.maplayers.politicalmap.claims;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.ClaimBreakdownReaderSource;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;

/**
 * Opens a claim reader over one pass - its walk of each system, under its colony rule.
 *
 * <p>Named once so the pair is never assembled at a call site. A reader given the pass's walk but
 * some other colony rule would score the sector the pass read while reporting a different sector's
 * worth of it as known, and nothing on screen would say which half was wrong.
 */
public final class PassClaimReaders {

    private PassClaimReaders() {
    }

    /**
     * @param pass              the pass whose walk and colony rule the reader answers off
     * @param claimReaderSource the source to open through
     * @return a reader explaining claims off that pass, to be discarded with it
     */
    public static ClaimBreakdownReader openBreakdownReaderOver(
            HolderPass pass,
            ClaimBreakdownReaderSource claimReaderSource) {

        return claimReaderSource.openReaderOver(pass.colonyKnowledge(), pass.sectorIndex());
    }

    /**
     * For a reader with no holder pass behind it, such as a hover box's, which walks the sector
     * itself and samples the colony rule per hover.
     *
     * @param sectorIndex       the walk the reader answers off
     * @param colonyVisibility  the colony rule sampled beside that walk
     * @param claimReaderSource the source to open through
     * @return a reader explaining claims off that walk, to be discarded with it
     */
    public static ClaimBreakdownReader openBreakdownReaderOver(
            SectorPassIndex sectorIndex,
            ColonyVisibility colonyVisibility,
            ClaimBreakdownReaderSource claimReaderSource) {

        return claimReaderSource.openReaderOver(
            ColonyKnowledge.over(sectorIndex.getSector(), colonyVisibility),
            sectorIndex);
    }

    /**
     * @param pass              the pass whose walk and colony rule the reader answers off
     * @param claimReaderSource the source to open through
     * @return a reader answering off that pass, to be discarded with it
     */
    public static ClaimReader openClaimReaderOver(HolderPass pass, ClaimReaderSource claimReaderSource) {
        return claimReaderSource.openReaderOver(pass.colonyKnowledge(), pass.sectorIndex());
    }
}
