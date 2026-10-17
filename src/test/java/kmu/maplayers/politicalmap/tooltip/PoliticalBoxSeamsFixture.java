package kmu.maplayers.politicalmap.tooltip;

import kmlib.starsector.systems.claims.ClaimDecreeReader;

import kmu.maplayers.base.visibility.colonies.FactionAllianceSource;

/**
 * The reads a political box is stood up over where neither the alliances nor the wording is what a
 * case is about: nothing allied, and a rival block headed as a contest.
 *
 * <p>Named rather than written out per suite, so a suite states which sector it is posing rather than
 * a triple the reader has to unpick - and the one decree read a case does vary stays the only
 * argument it writes.
 */
public final class PoliticalBoxSeamsFixture {

    private PoliticalBoxSeamsFixture() {
    }

    /**
     * Seams heading the box off the given decree read, among factions standing alone.
     *
     * @param claimDecreeReader the decree read the box heads with
     * @return the seams, with no alliance and the contested wording
     */
    public static PoliticalBoxSeams buildPlainSeamsOver(ClaimDecreeReader claimDecreeReader) {

        return new PoliticalBoxSeams(
            claimDecreeReader,
            FactionAllianceSource.NO_ALLIANCES,
            ContestWordingFixtures.CONTESTED_WORDING);
    }
}
