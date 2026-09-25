package kmu.maplayers.ownermap.owners.holders;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A holding source a tier case can hand over without reaching for a mechanic's own resolver.
 *
 * <p>The tier resolves holders through {@link HolderProvider} and never names what fills it, so its
 * own suites cannot name one either. What a case usually wants is a source that answers the same
 * thing every time and records nothing, which is what this is.
 *
 * <p>Answers whatever holding it was built over, with nothing contested and nothing unfilled - the
 * two sets a case about those states its own resolution for rather than reaching here.
 *
 * <p>Records every pass it was handed, so a case can pin what the caller opened - that a
 * diagnostic reads under the grouping it says it does, or that a gated path never resolved at all.
 */
public final class HolderProviderFake implements HolderProvider {

    private final Map<SystemKey, SystemOwner> ownerBySystemKey;
    private final List<HolderPass> resolvedPasses = new ArrayList<>();

    private HolderProviderFake(Map<SystemKey, SystemOwner> ownerBySystemKey) {
        this.ownerBySystemKey = ownerBySystemKey;
    }

    /**
     * A source that resolves no holder at all - what a case wanting the pipeline driven over
     * nothing hands over.
     *
     * @return a provider answering empty holding for any pass
     */
    public static HolderProviderFake createHoldingNothing() {
        return new HolderProviderFake(Map.of());
    }

    /**
     * A source answering one stated holding whatever it is passed.
     *
     * @param ownerBySystemKey the holding every resolve answers with
     * @return a provider over that holding
     */
    public static HolderProviderFake createHolding(
            Map<SystemKey, SystemOwner> ownerBySystemKey) {

        return new HolderProviderFake(ownerBySystemKey);
    }

    /**
     * Every pass this source was asked to resolve, in the order it was asked.
     *
     * @return the recorded passes; empty where nothing resolved
     */
    public List<HolderPass> readResolvedPasses() {
        return List.copyOf(resolvedPasses);
    }

    @Override
    public HolderResolution resolveHolder(HolderPass pass, String selectedBlocId) {
        resolvedPasses.add(pass);
        return new HolderResolution(ownerBySystemKey, Set.of(), Set.of());
    }
}
