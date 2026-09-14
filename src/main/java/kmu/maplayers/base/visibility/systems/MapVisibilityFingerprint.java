package kmu.maplayers.base.visibility.systems;

import kmlib.math.hashing.Avalanche;
import kmlib.starsector.systems.SystemKey;

/**
 * Fingerprints the set of systems a map layer draws, so a poll can tell that the set moved without
 * holding the last one to compare against.
 *
 * <p>One scalar per sector, summed from a contribution per drawn system. Summing is what makes it
 * order-independent - it still moves when one system enters as another leaves, whatever order the
 * sector lists its systems in - and {@link Avalanche} is what keeps a sum of related values from
 * cancelling back to no change.
 *
 * <p>A system is identified by its whole {@link SystemKey} rather than by its ID, because two
 * systems may answer to one id. Both would then contribute the same value, so one entering the
 * drawn set as the other left would move the fingerprint by nothing, and the map would go on
 * showing whatever it last built there.
 *
 * <p>Only membership is hashed. Who holds each system is tracked as a per-system map instead,
 * because a holder change reshapes one system while a membership change rebuilds the whole
 * partition, and a scalar could not name which system to reshape.
 */
public final class MapVisibilityFingerprint {

    // Salt XORed into a decivilised system's folded key, so a shown collapse - a draw-class change
    // on a system already on the map - lands a different contribution from that same system drawn
    // live, and the fingerprint moves even when the membership set does not.
    private static final int DECIVILISED_FINGERPRINT_SALT = 31;

    // Seed XORed into every contribution, because the avalanche's one fixed point is 0: a key
    // folding to 0 would otherwise contribute 0 and vanish from the sum, letting that system join
    // or leave the drawn set unnoticed. The value is arbitrary - the golden-ratio constant - and
    // only has to be one no real key folds to.
    private static final int FINGERPRINT_SEED = 0x9e3779b9;

    private MapVisibilityFingerprint() {
    }

    /**
     * The fingerprint contribution of one drawn system, folding in its draw class so a decivilised
     * shell reads differently from a live colony on the same system.
     *
     * @param systemKey             the drawn system's key
     * @param isRevealedDecivilised whether the system is drawn only as a revealed collapsed
     *                              colony, which salts its contribution so a live-to-dead flip is
     *                              caught
     * @return the value to add into the visibility fingerprint
     */
    public static int computeSystemContribution(
            SystemKey systemKey,
            boolean isRevealedDecivilised) {

        var drawClassSalt = isRevealedDecivilised ? DECIVILISED_FINGERPRINT_SALT : 0;

        return Avalanche.mixBits(foldKeyArms(systemKey) ^ FINGERPRINT_SEED ^ drawClassSalt);
    }

    // A key's three arms folded into one value, each arm mixed into the chain before the next is
    // XORed in.
    //
    // Chained rather than folded linearly - an arm weighted by a multiplier, the way a string
    // hashes its characters - because a linear fold lets the arms cancel. String hashes are
    // themselves linear in their characters, so the pair a live sector actually holds (one ID,
    // procgen centre names one character apart, short engine-minted anchor IDs) can shift one arm
    // by exactly what another shifts back, and fold to a single value. Chaining also keeps the fold
    // positional: an entity ID standing as one system's centre and another's anchor enters the
    // chain at a different link.
    //
    // The last arm is mixed here rather than left for the caller's own mix, so that no arm shares
    // an XOR with the draw-class salt: an anchor pair differing by exactly the salt would otherwise
    // alias a live system onto its twin's collapsed contribution.
    //
    // Folded here rather than taken off the key's own hash, which is derived from the same three
    // arms but by a combination the language does not pin down - a fingerprint has to state how it
    // identifies a system rather than inherit it.
    private static int foldKeyArms(SystemKey systemKey) {

        var fold = Avalanche.mixBits(systemKey.systemId().hashCode());

        fold = Avalanche.mixBits(fold ^ systemKey.centreEntityId().hashCode());

        return Avalanche.mixBits(fold ^ systemKey.anchorEntityId().hashCode());
    }
}
