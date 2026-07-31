package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves which faction lays claim to each star system and in which colours that claimant
 * paints - the claim counterpart to {@link SectorPolitics}'s held-dominance resolve.
 *
 * <p>Where {@link SectorPolitics} reads who <em>holds</em> a system from the live economy,
 * this reads who <em>claims</em> it through the {@link ClaimReader} port, then routes the
 * claimant through the same grouping fold and palette lookup the held pass uses. Reusing
 * {@link SectorPolitics#resolveBlocHolder} gives a claim the identical alliance rollup and
 * authored-palette resolve a held holder gets, so a claimed system and a held one of the same
 * bloc resolve to an equal {@link DominantHolder} - and therefore share an holder and
 * fuse into one territory downstream.
 *
 * <p>The claimant comes from the port, not the {@code Misc} static behind it, so the resolve
 * runs against a known set of claimants with no running game.
 */
public final class SectorClaims {

    private SectorClaims() {
    }

    /**
     * Builds the claiming holder - claimant bloc id paired with the shades it paints in - for
     * every claimed star system, under an holder grouping.
     *
     * <p>Each system's claimant faction id is folded to its bloc under the grouping and
     * coloured through {@link SectorPolitics#resolveBlocHolder}, so a claim carries the same
     * bloc key and palette a held system of that bloc would. A system with no claim is absent;
     * a claim whose colour faction does not resolve is dropped, exactly as an unresolved held
     * holder is, so it does not paint a colourless region.
     *
     * @param sector      the sector whose systems are walked and whose faction palette is read;
     *                    null yields an empty map
     * @param grouping    the holder grouping that collapses the claimant faction into its
     *                    bloc before the palette is resolved - identity for the faction view,
     *                    alliance blocs for the alliances view
     * @param claimReader the claim source, read once per system
     * @return the claiming holder keyed by system id, in star-system walk order; a system with
     *         no resolvable claim is absent from the map
     */
    public static Map<String, DominantHolder> resolveClaimingHolderBySystemId(
            SectorAPI sector, HolderGrouping grouping, ClaimReader claimReader) {
        var ownerBySystemId = new LinkedHashMap<String, DominantHolder>();
        if (sector == null) {
            return ownerBySystemId;
        }
        for (var system : sector.getStarSystems()) {
            var claimantId = claimReader.readClaimingFactionId(system);
            if (claimantId == null) {
                continue;
            }
            var holder = SectorPolitics.resolveBlocHolder(
                    sector, grouping, grouping.resolveBlocId(claimantId));
            if (holder != null) {
                ownerBySystemId.put(system.getId(), holder);
            }
        }
        return ownerBySystemId;
    }
}
