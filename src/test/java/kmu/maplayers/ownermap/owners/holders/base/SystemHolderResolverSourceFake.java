package kmu.maplayers.ownermap.owners.holders.base;

import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a batch opens its holder read, recording the pass and the spotlight it was opened under.
 *
 * <p>A batch takes one reading of the sector and answers every question about a marked system from
 * it, so what a case pins is that the holder resolver was opened over the very pass the batch's
 * other answers came from, under the spotlight the standing build was painted under. Both are read
 * off what this recorded.
 *
 * <p>The holding each open answers with is stated by the case, on this source; a source nobody
 * states a holding on opens resolvers holding nothing.
 */
public final class SystemHolderResolverSourceFake implements SystemHolderResolverSource {

    private final Map<String, SystemOwner> holderBySystemId = new LinkedHashMap<>();
    private final List<HolderPass> passesOpenedOver = new ArrayList<>();
    private final List<String> spotlitBlocIdsOpenedUnder = new ArrayList<>();

    /**
     * Every pass a batch opened a resolver over, in the order it opened them.
     *
     * @return the recorded passes; one entry per batch
     */
    public List<HolderPass> readPassesOpenedOver() {
        return List.copyOf(passesOpenedOver);
    }

    /**
     * The spotlit bloc every resolver was opened under, in the order opened.
     *
     * @return the recorded blocs; one entry per batch, null where none was spotlit
     */
    public List<String> readSpotlitBlocIdsOpenedUnder() {
        return new ArrayList<>(spotlitBlocIdsOpenedUnder);
    }

    /**
     * States who holds one system, for every resolver this source hands out.
     *
     * @param systemId the system's own ID
     * @param holder   the holder to answer with, or null for a system nobody holds
     */
    public void recordHolderOf(String systemId, SystemOwner holder) {
        holderBySystemId.put(systemId, holder);
    }

    @Override
    public SystemHolderResolver openResolverOver(HolderPass pass, String spotlitBlocId) {

        passesOpenedOver.add(pass);
        spotlitBlocIdsOpenedUnder.add(spotlitBlocId);

        return SystemHolderResolverFake.createHolding(holderBySystemId);
    }
}
