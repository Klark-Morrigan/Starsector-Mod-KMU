package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.List;
import java.util.Map;

/**
 * A per-system holder read a tier case can hand over without naming a mechanic.
 *
 * <p>The incremental path resolves holders through {@link SystemHolderResolve}, so a case states
 * the holding one system at a time rather than opening a weighted pass to derive it. What the
 * batch does with a holder is the tier's; what a holder <em>is</em> belongs to whichever layer is
 * painting, and no tier suite is entitled to name one.
 *
 * <p>Records the systems it was asked about, so a case can pin which of the marked ones the batch
 * actually re-derived.
 */
public final class SystemHolderResolveFake implements SystemHolderResolve {

    private final HolderPass pass;
    private final Map<String, SystemOwner> holderBySystemId;
    private final List<StarSystemAPI> resolvedSystems = new java.util.ArrayList<>();

    private SystemHolderResolveFake(HolderPass pass,
                                    Map<String, SystemOwner> holderBySystemId) {

        this.pass = pass;
        this.holderBySystemId = holderBySystemId;
    }

    /**
     * A resolve over one pass, answering the stated holding per system id.
     *
     * @param pass             the reading every other question is asked of
     * @param holderBySystemId who holds each system, by the system's own id
     * @return the resolve
     */
    public static SystemHolderResolveFake createOver(
            HolderPass pass,
            Map<String, SystemOwner> holderBySystemId) {

        return new SystemHolderResolveFake(pass, holderBySystemId);
    }

    /**
     * A source opening a resolve that holds nothing, for a case driving the batch over a sector
     * whose holding it does not state.
     *
     * @return a source answering empty holding for whatever it is opened over
     */
    public static SystemHolderResolveSource createSourceHoldingNothing() {
        return (SectorAPI sector, HolderGrouping grouping) ->
            new SystemHolderResolveFake(
                HolderPass.readFromLunaSettings(sector, grouping), Map.of());
    }

    /**
     * Every system this resolve was asked about, in the order it was asked.
     *
     * @return the recorded systems; empty where nothing resolved
     */
    public List<StarSystemAPI> readResolvedSystems() {
        return List.copyOf(resolvedSystems);
    }

    @Override
    public HolderPass readHolderPass() {
        return pass;
    }

    @Override
    public SystemOwner resolveHolderIn(StarSystemAPI system) {
        resolvedSystems.add(system);
        return holderBySystemId.get(SystemKey.readKeyOf(system).systemId());
    }
}
