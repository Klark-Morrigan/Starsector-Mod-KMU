package kmu.conditions.ui.picker.dialog;

@FunctionalInterface
public interface KmuConditionPickerDialogOpener {
    void open(KmuConditionPickerDialogDelegate dialogDelegate);
}
