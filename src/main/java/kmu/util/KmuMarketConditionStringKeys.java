package kmu.util;

/**
 * The IDs of the strings the Market Condition Manager reads: the picker's info panel, its empty
 * grid, the hover over a condition, what adding one answers, what opening the manager answers,
 * and the dialog's close button.
 *
 * <p>Read through {@link KmuStringKeys}' accessors, which bind the category. The {@code CONSOLE_} keys are what
 * the console prints, and are read through {@link kmlib.mods.console.ConsoleStrings} instead: a translation of
 * them carries an English copy the console can draw.
 */
public final class KmuMarketConditionStringKeys {

    public static final String CONDITION_MANAGER_ENTRY_ABSENT = "condition_manager_entry_absent";
    public static final String CONDITION_MANAGER_ENTRY_PRESENT = "condition_manager_entry_present";
    public static final String CONDITION_MANAGER_FEEDBACK = "condition_manager_feedback";
    public static final String CONDITION_MANAGER_FEEDBACK_ADDED = "condition_manager_feedback_added";
    public static final String CONDITION_MANAGER_FEEDBACK_ALREADY_PRESENT =
        "condition_manager_feedback_already_present";
    public static final String CONDITION_MANAGER_FEEDBACK_CONDITION_NOT_FOUND =
        "condition_manager_feedback_condition_not_found";
    public static final String CONDITION_MANAGER_FEEDBACK_FAILED = "condition_manager_feedback_failed";
    public static final String CONDITION_MANAGER_FEEDBACK_NOT_OFFERABLE = "condition_manager_feedback_not_offerable";
    public static final String CONDITION_MANAGER_FEEDBACK_UNKNOWN_CONDITION =
        "condition_manager_feedback_unknown_condition";
    public static final String CONDITION_MANAGER_LOCATION = "condition_manager_location";
    public static final String CONDITION_MANAGER_LOCATION_SYSTEM_CENTER = "condition_manager_location_system_center";
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
    public static final String CONDITION_MANAGER_TOOLTIP_METADATA_TITLE = "condition_manager_tooltip_metadata_title";
    public static final String CONDITION_MANAGER_TOOLTIP_METADATA_LINE = "condition_manager_tooltip_metadata_line";
    public static final String CONDITION_MANAGER_TOOLTIP_METADATA_SOURCE = "condition_manager_tooltip_metadata_source";
    public static final String CONDITION_MANAGER_TOOLTIP_METADATA_ID = "condition_manager_tooltip_metadata_id";
    public static final String CONDITION_MANAGER_TOOLTIP_METADATA_ICON = "condition_manager_tooltip_metadata_icon";
    public static final String CONDITION_MANAGER_TOOLTIP_METADATA_HIDDEN = "condition_manager_tooltip_metadata_hidden";
    public static final String CONDITION_MANAGER_TOOLTIP_METADATA_SUPPRESSED =
        "condition_manager_tooltip_metadata_suppressed";
    public static final String CONDITION_MANAGER_TOOLTIP_METADATA_TRUE = "condition_manager_tooltip_metadata_true";
    public static final String CONDITION_MANAGER_TOOLTIP_METADATA_FALSE = "condition_manager_tooltip_metadata_false";
    public static final String CONSOLE_CONDITION_MANAGER_FAILED_TO_OPEN = "console_condition_manager_failed_to_open";
    public static final String CONSOLE_CONDITION_MANAGER_FAILED_TO_RESOLVE_CONTEXT =
        "console_condition_manager_failed_to_resolve_context";
    public static final String CONSOLE_CONDITION_MANAGER_FAILED_TO_VALIDATE_TARGET =
        "console_condition_manager_failed_to_validate_target";
    public static final String CONSOLE_CONDITION_MANAGER_FAILURE = "console_condition_manager_failure";
    public static final String CONSOLE_CONDITION_MANAGER_FAILURE_WITH_CAUSE =
        "console_condition_manager_failure_with_cause";
    public static final String CONSOLE_CONDITION_MANAGER_NO_MARKET_CONTEXT =
        "console_condition_manager_no_market_context";
    public static final String CONSOLE_CONDITION_MANAGER_OPENED = "console_condition_manager_opened";
    public static final String CONSOLE_CONDITION_MANAGER_SWITCHED_OFF = "console_condition_manager_switched_off";
    public static final String CONSOLE_CONDITION_MANAGER_UNSUPPORTED_TARGET =
        "console_condition_manager_unsupported_target";
    public static final String DIALOG_CLOSE = "dialog_close";

    private KmuMarketConditionStringKeys() {
    }
}
