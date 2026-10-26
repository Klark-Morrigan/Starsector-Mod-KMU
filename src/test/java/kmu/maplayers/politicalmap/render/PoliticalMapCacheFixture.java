package kmu.maplayers.politicalmap.render;

import kmu.maplayers.base.geometry.CellSeedRule;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.ownermap.OwnerMapRebuildSeams;
import kmu.maplayers.ownermap.render.rebuild.OwnerMapCache;
import kmu.maplayers.ownermap.render.rebuild.OwnerMapRefreshStrategy;

/**
 * The political map's cache as the rebuild suites drive it: drawing no names, seeding a cell for
 * every drawn system and folding marked systems in.
 *
 * <p>One home for the shape because what those suites claim turns on the machinery alone, and a
 * cache built under a pick or a strategy that drifted between them would let one suite pass for a
 * reason the others never pose.
 */
final class PoliticalMapCacheFixture {

    // Fixtures only; never instantiated.
    private PoliticalMapCacheFixture() {
    }

    // The cache over the given machinery, drawing under the shape above.
    static OwnerMapCache createCacheOver(SectorMapMachinery machinery) {

        return new OwnerMapCache(
            machinery,
            OwnerMapRebuildSeams.createPreferencesNamingNothing(),
            CellSeedRule.SEED_DRAWN_SYSTEMS,
            OwnerMapRefreshStrategy.INCREMENTAL);
    }
}
