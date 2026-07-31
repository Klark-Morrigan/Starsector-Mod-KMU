package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class KmuConditionPickerRenderResult {
    private final LabelAPI summaryLabel;
    private final List<UIComponentAPI> customComponents;
    private final KmuConditionIconGrid.GridHandle gridHandle;

    KmuConditionPickerRenderResult(
            LabelAPI summaryLabel,
            List<UIComponentAPI> customComponents,
            KmuConditionIconGrid.GridHandle gridHandle) {
        this.summaryLabel = summaryLabel;
        this.customComponents = Collections.unmodifiableList(new ArrayList<>(
            Objects.requireNonNull(customComponents, "customComponents")));
        this.gridHandle = gridHandle;
    }

    public LabelAPI getSummaryLabel() {
        return summaryLabel;
    }

    public List<UIComponentAPI> getCustomComponents() {
        return customComponents;
    }

    public boolean updateEntry(KmuConditionPickerEntry entry) {
        return gridHandle != null && gridHandle.updateEntry(entry);
    }
}
