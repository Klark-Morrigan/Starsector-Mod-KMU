package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates the whole-sector {@link ClaimStats} the claims picker sorts and labels its options by,
 * and the systems behind the claim half of them, from one walk of the sector's star systems.
 *
 * <p>The claims counterpart to {@link DominanceStatsAggregator}, and deliberately a separate walk
 * producing a separate output type: the two aggregators share the per-system habitation
 * <em>read</em> ({@link HolderPass#readHabitationIn}) and nothing else, so neither stats record can
 * grow a field for the other's benefit.
 *
 * <p>It reads the layer-generic pass rather than a dominance one, because neither metric weighs
 * anything: a claim comes from the port and a colony size from the colonies themselves. A reader
 * handed a weighting rule it never spends is a reader that could spend it wrongly.
 *
 * <p>The two metrics come from two different sources on the same pass over the systems. Claims come
 * from the {@link ClaimReader} port - not the {@code Misc} static behind it - so the aggregation
 * runs against a known set of claimants with no running game, exactly as {@link SectorClaims}'s
 * resolve does. Market sizes come from the colonies somebody lives on, and are summed over every
 * system a bloc lives in rather than over the systems it claims, since a claimant usually does not
 * hold what it claims (see {@link ClaimStats}).
 *
 * <p>Both are folded to blocs through the pass's grouping, so an alliance grouping totals its
 * members' claims and colonies into the alliance's one entry - the same fold the dominance
 * aggregation applies.
 *
 * <p>The system index beside the totals is filled by the claim arm alone. That is what makes it
 * claim-bound and equal to what this layer paints: a bloc's colonies are summed from wherever they
 * are, so letting the habitation arm contribute would name systems the claims layer draws the bloc
 * nothing in.
 */
public final class ClaimStatsAggregator {

    // One walk's worth of running state, held as fields rather than threaded through each step.
    // Both metrics and the claimed-system index accumulate over every system in the sector, so
    // passing them along made each step's parameter list longer than the step itself and put the
    // pass and the reader - what every step reads through - last among them.
    private final BlocPresenceIndexBuilder claimedSystems = new BlocPresenceIndexBuilder();
    private final ClaimReader claimReader;
    private final HolderPass pass;
    private final Map<String, ClaimStats> statsByBlocId = new LinkedHashMap<>();

    // Single-use and private, so the entry point below stays the only way in: a caller can neither
    // hold a half-filled aggregation nor run a second sector through one that is already full.
    private ClaimStatsAggregator(HolderPass pass, ClaimReader claimReader) {
        this.claimReader = claimReader;
        this.pass = pass;
    }

    /**
     * The whole-sector claim stats for every bloc that claims a system or lives in one, under a
     * pass.
     *
     * <p>No gate is applied here: a bloc that claims nothing is still returned with its market size,
     * because deciding what belongs in a picker is the view's call rather than the fold's. What is
     * <em>not</em> returned is a bloc the walk never surfaced - one that neither claims anything nor
     * lives anywhere - so a caller listing every entry lists everyone who paints or lives somewhere
     * rather than every faction in the sector.
     *
     * @param pass        the sector walk, colony rule, and grouping this read resolves under,
     *                    sampled once by the caller so the whole read resolves under one set of
     *                    knobs; a pass over no sector yields an empty read
     * @param claimReader the claim source, read once per system
     * @return each claiming or living bloc's stats and the systems each claiming bloc claims, keyed
     *         by bloc ID in star-system walk order; empty when the sector holds neither
     */
    public static ClaimStatsRead aggregateClaimStats(
            HolderPass pass,
            ClaimReader claimReader) {

        return new ClaimStatsAggregator(pass, claimReader).aggregateWholeSector();
    }

    // Walks every system the pass offers, folding each into the running state, and seals the result.
    //
    // Neither fold is guarded on the sector having an economy up yet (mid-load, it may not). Claims
    // are read from the port and so stand on their own, and the colony walk beneath the habitation
    // read answers an empty set without one - so the sizes come to nought where the dominance
    // aggregation's own guard makes it report nothing at all.
    private ClaimStatsRead aggregateWholeSector() {
        for (var system : pass.readSystems()) {
            accumulateSystemClaim(system);
            accumulateSystemHabitation(system);
        }
        return new ClaimStatsRead(statsByBlocId, claimedSystems.buildIndex());
    }

    // Folds one system's claimant into the running per-bloc stats and claimed-system sets. A system
    // has exactly one claimant, so this adds at most one claim; an unclaimed system contributes
    // nothing. The claimant faction is folded to its bloc first, so an alliance grouping counts its
    // members' claims as the alliance's and indexes their systems under the alliance too.
    private void accumulateSystemClaim(StarSystemAPI system) {
        var claimantId = claimReader.readClaimingFactionId(system);
        if (claimantId == null) {
            return;
        }
        var blocId = pass.grouping().resolveBlocId(claimantId);
        statsByBlocId.put(
            blocId,
            statsByBlocId.getOrDefault(blocId, ClaimStats.EMPTY).addClaim());

        claimedSystems.recordPresence(blocId, SystemKey.readKeyOf(system));
    }

    // Folds one system's habitation into the running per-bloc stats: the same read the dominance
    // aggregation counts its presences from, so every bloc living in the sector reaches a row on
    // either picker and neither can list a faction the other cannot see.
    //
    // Habitation and not the wider listing, which is the difference between a colony and a hulk
    // somebody has seen. A picker answers "what can I spotlight", and a spotlight lights territory,
    // so a bloc whose only holding is a derelict is offered nothing to light and is left out.
    private void accumulateSystemHabitation(StarSystemAPI system) {
        for (var entry : pass.readHabitationIn(system).colonySizeByBlocId().entrySet()) {
            var blocId = entry.getKey();
            statsByBlocId.put(
                blocId,
                statsByBlocId.getOrDefault(blocId, ClaimStats.EMPTY)
                    .addMarketSize(entry.getValue()));
        }
    }
}
