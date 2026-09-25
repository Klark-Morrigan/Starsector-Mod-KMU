package kmu.maplayers.politicalmap.claims;

import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.ownermap.holding.HolderPass;

/**
 * Opens a claim reader over one holder pass - its walk of each system, under its colony rule.
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
     * @return a reader answering off that pass, to be discarded with it
     */
    public static ClaimReader openClaimReaderOver(HolderPass pass, ClaimReaderSource claimReaderSource) {
        return claimReaderSource.openReaderOver(pass.colonyKnowledge(), pass.sectorIndex());
    }
}
