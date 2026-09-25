package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a batch opens its holder read, recording what it was opened over.
 *
 * <p>A batch takes one reading of the sector and answers every question about a marked system from
 * it, so the two facts a case pins are that it opened exactly one and that it opened it over the
 * sector it was handed rather than whichever is loaded. Both are read off what this recorded.
 *
 * <p>The holding each open answers with is stated by the case, on the resolve this hands back.
 */
public final class SystemHolderResolveSourceFake implements SystemHolderResolveSource {

    private final List<SectorAPI> sectorsOpenedOver = new ArrayList<>();
    private final List<SystemHolderResolveFake> resolvesHandedOut = new ArrayList<>();
    private final Map<String, SystemOwner> holderBySystemId = new LinkedHashMap<>();

    /**
     * Every sector a batch opened a reading over, in the order it opened them.
     *
     * @return the recorded sectors; one entry per batch
     */
    public List<SectorAPI> readSectorsOpenedOver() {
        return List.copyOf(sectorsOpenedOver);
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
    public SystemHolderResolve openResolveOver(SectorAPI sector, HolderGrouping grouping) {
        sectorsOpenedOver.add(sector);

        var resolve = SystemHolderResolveFake.createOver(
            HolderPass.readFromLunaSettings(sector, grouping), holderBySystemId);
        resolvesHandedOut.add(resolve);
        return resolve;
    }
}
