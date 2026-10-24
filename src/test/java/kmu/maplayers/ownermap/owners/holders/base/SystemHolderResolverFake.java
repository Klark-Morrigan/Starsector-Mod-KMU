package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.Map;

/**
 * A per-system holder read a holder-side case can hand over without naming a mechanic.
 *
 * <p>The holder source re-derives a marked system through {@link SystemHolderResolver}, so a case
 * states the holding one system at a time rather than opening a weighted pass to derive it. Every
 * stated holder fills solid; an unstated system is held by nobody.
 */
public final class SystemHolderResolverFake implements SystemHolderResolver {

    private final Map<String, SystemOwner> holderBySystemId;

    private SystemHolderResolverFake(Map<String, SystemOwner> holderBySystemId) {
        this.holderBySystemId = holderBySystemId;
    }

    /**
     * A resolver answering the stated holding per system ID.
     *
     * @param holderBySystemId who holds each system, by the system's own ID
     * @return the resolver
     */
    public static SystemHolderResolverFake createHolding(Map<String, SystemOwner> holderBySystemId) {
        return new SystemHolderResolverFake(holderBySystemId);
    }

    @Override
    public ResolvedSystemOwner resolveHolderIn(StarSystemAPI system) {

        return ResolvedSystemOwner.createSolidlyFilledBy(
            holderBySystemId.get(SystemKey.readKeyOf(system).systemId()));
    }
}
