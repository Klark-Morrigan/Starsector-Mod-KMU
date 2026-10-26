package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Who claims each star system while the filter may spotlight one bloc: the selected bloc's claims
 * carry its one spotlight holder, every rival's claim keeps its own.
 *
 * <p>The claim counterpart to the dominance package's {@code FilteredPolitics}, standing to
 * {@link SectorClaims} exactly as that class stands to {@code SectorPolitics} - the same resolve,
 * answered under a spotlight. The key is what the style layer reads: a rekeyed claim draws at full
 * strength, a claim left on its plain bloc key mutes and desaturates, so a claim always shares the
 * fate of the territory it belongs to.
 *
 * <p>How much the rekey moves depends on the caller. Where claims extend held dominance it is a
 * true fusion - the bloc's spotlight footprint spans systems it does not hold, which its claims
 * could never join under their own bloc key. Where claims are the whole layer the claimant's
 * systems already share a key, so the swap regroups nothing and buys only the spotlit answer.
 *
 * <p>Every claim-reading holder source and resolver reads through one, whether claims are the whole
 * layer or an unfilled extension of held dominance, so no two of them can spotlight the same claim
 * differently on two views of one sector.
 *
 * <p>Opened over one pass and discarded with it, as the claim reader inside it is: a reader answers
 * off the colonies the pass walked. The rekey is settled when it opens, so a system asked of it is
 * answered without asking again whether a spotlight is up.
 *
 * <p>Unlike the held resolve there is no contested set to report: a system has exactly one
 * claimant, so there is no present-but-dominated state a claim can be in.
 */
public final class FilteredClaimReader {

    private final HolderPass pass;
    private final ClaimReader claimReader;
    private final UnaryOperator<SystemOwner> claimRekey;

    private FilteredClaimReader(
            HolderPass pass,
            ClaimReader claimReader,
            UnaryOperator<SystemOwner> claimRekey) {

        this.pass = pass;
        this.claimReader = claimReader;
        this.claimRekey = claimRekey;
    }

    /**
     * Opens the claim read of one pass, under the spotlight the pass was opened under.
     *
     * @param pass              the pass whose walk and colony rule the claims are read off, whose
     *                          grouping folds a claimant into its bloc and whose palettes colour it
     * @param claimReaderSource the source the view reads every claim through
     * @param spotlight         the spotlight the caller opened over that same pass, shared with
     *                          whatever else it rekeys; empty off filter
     * @return the pass's claim read
     */
    public static FilteredClaimReader openOver(
            HolderPass pass,
            ClaimReaderSource claimReaderSource,
            Optional<BlocSpotlight> spotlight) {

        return new FilteredClaimReader(
            pass,
            PassClaimReaders.openClaimReaderOver(pass, claimReaderSource),
            spotlight
                .map(FilteredClaimReader::createSpotlitRekey)
                .orElseGet(UnaryOperator::identity));
    }

    /**
     * The claiming holder of one star system, for a batch re-deriving a marked system.
     *
     * @param system the system to read; null yields null
     * @return the claiming holder, or null where nobody claims the system or the claimant's colour
     *         faction does not resolve
     */
    public SystemOwner resolveClaimIn(StarSystemAPI system) {

        var claim = SectorClaims.resolveClaimingHolderIn(system, pass, claimReader);
        return claim == null
            ? null
            : claimRekey.apply(claim);
    }

    /**
     * The claiming holder of every claimed system, for a rebuild.
     *
     * @return the claiming holder keyed by {@link SystemKey}, in star-system walk order; a pass over
     *         no sector yields an empty map
     */
    public Map<SystemKey, SystemOwner> resolveClaimingHolderBySystemKey() {

        var claimingHolderBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();

        SectorClaims
            .resolveClaimingHolderBySystemKey(pass, claimReader)
            .forEach((systemKey, claim) -> claimingHolderBySystemKey.put(systemKey, claimRekey.apply(claim)));

        return claimingHolderBySystemKey;
    }

    // One claim under the spotlight: the selected bloc's own claim takes the spotlight holder, a
    // rival's keeps its own.
    private static UnaryOperator<SystemOwner> createSpotlitRekey(BlocSpotlight spotlight) {

        return claim -> spotlight.isSpotlitBloc(claim.ownerId())
            ? spotlight.holder()
            : claim;
    }
}
