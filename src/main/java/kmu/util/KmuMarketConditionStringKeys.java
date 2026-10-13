package kmu.util;

/**
 * The IDs of the strings the Market Condition Manager reads: the picker's info panel, its empty
 * grid, the hover over a suppressed or hidden condition, and the dialog's close button.
 *
 * <p>Read through {@link KmuStringKeys}' accessors, which bind the category.
 */
public final class KmuMarketConditionStringKeys {

    public static final String CONDITION_MANAGER_LOCATION = "condition_manager_location";
    public static final String CONDITION_MANAGER_LOCATION_UNKNOWN = "condition_manager_location_unknown";
    public static final String CONDITION_MANAGER_LOCATION_OWNED_BY = "condition_manager_location_owned_by";
    public static final String CONDITION_MANAGER_SUMMARY = "condition_manager_summary";
    public static final String CONDITION_MANAGER_SUMMARY_VISIBLE = "condition_manager_summary_visible";
    public static final String CONDITION_MANAGER_SUMMARY_SUPPRESSED = "condition_manager_summary_suppressed";
    public static final String CONDITION_MANAGER_SUMMARY_PRESENT = "condition_manager_summary_present";
    public static final String CONDITION_MANAGER_SUMMARY_HIDDEN = "condition_manager_summary_hidden";
    public static final String CONDITION_MANAGER_SUMMARY_AVAILABLE = "condition_manager_summary_available";
    public static final String CONDITION_MANAGER_SUMMARY_TOTAL = "condition_manager_summary_total";
    public static final String CONDITION_MANAGER_GROUP_SEPARATOR = "condition_manager_group_separator";
    public static final String CONDITION_MANAGER_ITEM_SEPARATOR = "condition_manager_item_separator";
    public static final String CONDITION_MANAGER_EMPTY = "condition_manager_empty";
    public static final String CONDITION_MANAGER_TOOLTIP_SUPPRESSED_TITLE =
        "condition_manager_tooltip_suppressed_title";
    public static final String CONDITION_MANAGER_TOOLTIP_SUPPRESSED_BODY = "condition_manager_tooltip_suppressed_body";
    public static final String CONDITION_MANAGER_TOOLTIP_HIDDEN_TITLE = "condition_manager_tooltip_hidden_title";
    public static final String CONDITION_MANAGER_TOOLTIP_HIDDEN_BODY = "condition_manager_tooltip_hidden_body";
    public static final String DIALOG_CLOSE = "dialog_close";

    private KmuMarketConditionStringKeys() {
    }
}
