package kmu.conditions.ui.editor;

import kmu.conditions.ui.context.KmuMarketUiContext;
import kmu.conditions.ui.context.KmuMarketUiContextSource;
import kmu.util.KmuMarketConditionStringKeys;
import kmu.util.KmuStringKeys;

import java.util.Objects;
import java.util.Optional;

public final class StarsectorConditionEditorTargetValidator implements KmuConditionEditorTargetValidator {
    @Override
    public Optional<String> getUnsupportedReason(KmuMarketUiContext context) {
        Objects.requireNonNull(context, "context");

        if (context.getSource() == KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET) {
            return Optional.empty();
        }

        var market = context.getMarket();
        if (market.getPlanetEntity() != null || market.isPlanetConditionMarketOnly()) {
            return Optional.empty();
        }

        return Optional.of(KmuStringKeys.get(KmuMarketConditionStringKeys.CONDITION_MANAGER_OPEN_UNSUPPORTED_TARGET));
    }
}
