package kmu.maplayers.ownermap.owners.holders.base;

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
 * <p>The holding each open answers with is stated by the case, on this source; a source nobody
 * states a holding on opens resolves holding nothing.
 */
public final class SystemHolderResolveSourceFake implements SystemHolderResolveSource {

    private final Map<String, SystemOwner> holderBySystemId = new LinkedHashMap<>();
    private final List<HolderPass> passesOpenedOver = new ArrayList<>();

    /**
     * Every pass a batch opened a resolve over, in the order it opened them.
     *
     * @return the recorded passes; one entry per batch
     */
    public List<HolderPass> readPassesOpenedOver() {
        return List.copyOf(passesOpenedOver);
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
        return SystemHolderResolveFake.createHolding(holderBySystemId);
    }
}
