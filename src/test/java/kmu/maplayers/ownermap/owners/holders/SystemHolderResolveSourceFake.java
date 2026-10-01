package kmu.maplayers.ownermap.owners.holders;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a batch opens its holder read, recording the pass it was opened over.
 *
 * <p>A batch takes one reading of the sector and answers every question about a marked system from
 * it, so what a case pins is that the holder resolve was opened over the very pass the batch's
 * other answers came from. That is read off what this recorded.
 *
 * <p>The holding each open answers with is stated by the case, on this source.
 */
public final class SystemHolderResolveSourceFake implements SystemHolderResolveSource {

    private final List<HolderPass> passesOpenedOver = new ArrayList<>();
    private final List<SystemHolderResolveFake> resolvesHandedOut = new ArrayList<>();
    private final Map<String, SystemOwner> holderBySystemId = new LinkedHashMap<>();

    /**
     * Every pass a batch opened a resolve over, in the order it opened them.
     *
     * @return the recorded passes; one entry per batch
     */
    public List<HolderPass> readPassesOpenedOver() {
        return List.copyOf(passesOpenedOver);
    }

    /**
     * Every system a batch asked this source's resolves about, across all of them.
     *
     * @return the system IDs resolved, in the order they were asked
     */
    public List<String> readResolvedSystemIds() {
        return resolvesHandedOut.stream()
            .flatMap(resolve -> resolve.readResolvedSystems().stream())
            .map(system -> SystemKey.readKeyOf(system).systemId())
            .toList();
    }

    /**
     * States who holds one system, for every resolve this source hands out.
     *
     * @param systemId the system's own id
     * @param holder   the holder to answer with, or null for a system nobody holds
     */
    public void recordHolderOf(String systemId, SystemOwner holder) {
        holderBySystemId.put(systemId, holder);
    }

    @Override
    public SystemHolderResolve openResolveOver(HolderPass pass) {
        passesOpenedOver.add(pass);

        var resolveFake = SystemHolderResolveFake.createHolding(holderBySystemId);
        resolvesHandedOut.add(resolveFake);
        return resolveFake;
    }
}
