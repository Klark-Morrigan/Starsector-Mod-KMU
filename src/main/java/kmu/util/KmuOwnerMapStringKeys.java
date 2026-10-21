package kmu.util;

/**
 * The IDs of the strings the owner-painted tier reads, shared by every layer that paints by owner:
 * the sidebar's name, recede and uninhabited controls, and the sort modes every owner picker offers.
 *
 * <p>Read through {@link KmuStringKeys}' accessors, which bind the category.
 */
public final class KmuOwnerMapStringKeys {

    public static final String OWNER_MAP_CTL_UNINHABITED = "owner_map_ctl_uninhabited";
    public static final String OWNER_MAP_CTL_NAME_FULL = "owner_map_ctl_name_full";
    public static final String OWNER_MAP_CTL_NAME_SHORT = "owner_map_ctl_name_short";
    public static final String OWNER_MAP_CTL_NAME_NONE = "owner_map_ctl_name_none";
    public static final String OWNER_MAP_CTL_MUTED = "owner_map_ctl_muted";
    public static final String OWNER_MAP_CTL_DESATURATED = "owner_map_ctl_desaturated";
    public static final String OWNER_MAP_CTL_SORT_NAME = "owner_map_ctl_sort_name";
    public static final String OWNER_MAP_CTL_SORT_MARKET_SIZE = "owner_map_ctl_sort_market_size";
    public static final String OWNER_MAP_CTL_SORT_ATTITUDE = "owner_map_ctl_sort_attitude";
    public static final String OWNER_MAP_CTL_SORT_ATTITUDE_RANGE_SEPARATOR =
        "owner_map_ctl_sort_attitude_range_separator";

    private KmuOwnerMapStringKeys() {
    }
}
