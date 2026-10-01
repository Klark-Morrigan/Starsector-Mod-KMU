package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A per-system holder read a holder-side case can hand over without naming a mechanic.
 *
 * <p>The holder source re-derives a marked system through {@link SystemHolderResolve}, so a case
 * states the holding one system at a time rather than opening a weighted pass to derive it.
 *
 * <p>Records the systems it was asked about, so a case can pin which of the marked ones were
 * actually re-derived.
 */
public final class SystemHolderResolveFake implements SystemHolderResolve {

    private final Map<String, SystemOwner> holderBySystemId;
    private final List<StarSystemAPI> resolvedSystems = new ArrayList<>();

    private SystemHolderResolveFake(Map<String, SystemOwner> holderBySystemId) {
        this.holderBySystemId = holderBySystemId;
    }

    /**
     * A resolve answering the stated holding per system id.
     *
     * @param holderBySystemId who holds each system, by the system's own id
     * @return the resolve
     */
    public static SystemHolderResolveFake createHolding(Map<String, SystemOwner> holderBySystemId) {
        return new SystemHolderResolveFake(holderBySystemId);
    }

    /**
     * A source opening a resolve that holds nothing, whatever pass it is opened over.
     *
     * @return a source answering empty holding
     */
    public static SystemHolderResolveSource createSourceHoldingNothing() {
        return pass -> new SystemHolderResolveFake(Map.of());
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
    public SystemOwner resolveHolderIn(StarSystemAPI system) {
        resolvedSystems.add(system);
        return holderBySystemId.get(SystemKey.readKeyOf(system).systemId());
    }
}
