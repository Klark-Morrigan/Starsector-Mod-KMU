package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.Map;

/**
 * A per-system holder read a holder-side case can hand over without naming a mechanic.
 *
 * <p>The holder source re-derives a marked system through {@link SystemHolderResolve}, so a case
 * states the holding one system at a time rather than opening a weighted pass to derive it. Every
 * stated holder fills solid; an unstated system is held by nobody.
 */
public final class SystemHolderResolveFake implements SystemHolderResolve {

    private final Map<String, SystemOwner> holderBySystemId;

    private SystemHolderResolveFake(Map<String, SystemOwner> holderBySystemId) {
        this.holderBySystemId = holderBySystemId;
    }

    /**
     * A resolve answering the stated holding per system ID.
     *
     * @param holderBySystemId who holds each system, by the system's own ID
     * @return the resolve
     */
    public static SystemHolderResolveFake createHolding(Map<String, SystemOwner> holderBySystemId) {
        return new SystemHolderResolveFake(holderBySystemId);
    }

    @Override
    public ResolvedSystemOwner resolveHolderIn(StarSystemAPI system) {

        return ResolvedSystemOwner.createSolidlyFilledBy(
            holderBySystemId.get(SystemKey.readKeyOf(system).systemId()));
    }
}
