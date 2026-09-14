package kmu.maplayers.base.sidebar;

import kmlib.starsector.memory.SectorMemoryFlag;

import kmu.maplayers.base.layer.ScreenMemoryScope;

/**
 * A screen's sidebar fold persisted in sector memory under that screen's key, so the panel reopens at the
 * end the player left it at. Each screen holds its own instance under its own scope and its own default, so
 * the folds stay independent - folding one screen's panel writes only its key - while both screens persist
 * the same way, for one consistent behaviour across screens. A save holding no fold yet resolves to the
 * default, which is how each screen keeps the opening fold that suits it: the on-map panel opens out, the
 * intel panel folded clear of the visor.
 *
 * <p>The key is composed from one base key and the screen's scope, the way every other per-screen
 * preference's is, so the fold's slot has the same shape as the rest and no host spells a key of its own.
 *
 * <p>A consumer offers the settled fold every frame its panel draws, so a write happens only where the
 * offered fold differs from the one already stored. That comparison is made against sector memory itself
 * rather than a remembered value, which is what keeps it honest across a save load - a cache would still
 * hold the previous save's fold and read the new save's first frame as a change - and across a write made
 * before the sector exists, which is dropped and simply retried on the next frame.
 */
public final class PersistedSidebarFold implements SidebarFoldSelection {

    // Save-serialised identity of a screen's resting fold, before the screen's own segment; frozen once
    // shipped, since renaming it silently returns every existing save to the opening default.
    private static final String SIDEBAR_DOCKED_KEY = "$kmu_political_sidebar_docked";

    // The stored fold and the default a save that holds no choice yet resolves to.
    private final SectorMemoryFlag isRailDockedFlag;

    /**
     * @param memoryScope     the screen whose fold this is; its key is the base key resolved under it
     * @param isDockedDefault the fold a save holding no choice yet opens at - true to open folded to the
     *                        rail, false to open out
     */
    public PersistedSidebarFold(ScreenMemoryScope memoryScope, boolean isDockedDefault) {
        this.isRailDockedFlag = new SectorMemoryFlag(
            memoryScope.resolveKeyFor(SIDEBAR_DOCKED_KEY),
            isDockedDefault);
    }

    @Override
    public boolean isRailDocked() {
        return isRailDockedFlag.isSet();
    }

    @Override
    public void recordFold(boolean isRailDocked) {
        // Nothing to store when the save already reads this way, which is every frame but the one a fold
        // actually changes on. Reading to decide also means a save that has never stored a fold is left
        // untouched while the panel sits at its default, so the key appears only once a player moves it.
        if (isRailDocked == isRailDockedFlag.isSet()) {
            return;
        }
        isRailDockedFlag.set(isRailDocked);
    }
}
