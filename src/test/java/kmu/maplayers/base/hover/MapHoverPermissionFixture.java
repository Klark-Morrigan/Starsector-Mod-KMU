package kmu.maplayers.base.hover;

/**
 * The screen a {@link MapHoverPermission} is asked about, posed by name rather than by two booleans
 * at the call site.
 *
 * <p>Named because the pair cannot be told apart otherwise. Both reads are {@code BooleanSupplier},
 * so {@code new MapHoverPermission(() -> true, () -> false)} states nothing about which screen it
 * poses, and the two are only different on the frames every case here is about. A case that meant
 * game space and wrote the vanilla host would pass for the wrong reason.
 *
 * <p>These pose the screen alone. Which of those screens the permission then admits is the player's
 * settings to say, so a case combines one of these with the scope granting the switch it is about -
 * see {@link HoverSwitchScopes}.
 */
public final class MapHoverPermissionFixture {

    private MapHoverPermissionFixture() {
    }

    /**
     * @return the permission over a frame where the player is looking at the campaign world itself -
     *         no map open, no screen, no dialog. The frames a docked map surface is the only map on
     */
    public static MapHoverPermission buildPermissionInGameSpace() {
        return new MapHoverPermission(() -> false, () -> true);
    }

    /**
     * @return the permission over a frame where neither a vanilla map nor the campaign world is
     *         showing - some other screen, which is most of the frames this is ever asked on
     */
    public static MapHoverPermission buildPermissionOffEveryMap() {
        return new MapHoverPermission(() -> false, () -> false);
    }

    /**
     * @return the permission over a frame a vanilla map host owns, which answers with no permission
     *         granted at all
     */
    public static MapHoverPermission buildPermissionOnAVanillaHost() {
        return new MapHoverPermission(() -> true, () -> false);
    }
}
