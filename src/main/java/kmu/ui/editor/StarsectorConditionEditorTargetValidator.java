package kmu.ui.editor;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.context.KmuMarketUiContextSource;

import java.util.Objects;
import java.util.Optional;

public final class StarsectorConditionEditorTargetValidator implements KmuConditionEditorTargetValidator {
    @Override
    public Optional<String> getUnsupportedReason(KmuMarketUiContext context) {
        Objects.requireNonNull(context, "context");

        if (context.getSource() == KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET) {
            return Optional.empty();
        }

        MarketAPI market = context.getMarket();
        if (market.getPlanetEntity() != null || market.isPlanetConditionMarketOnly()) {
            return Optional.empty();
        }

        return Optional.of("Current market does not support planetary condition editing.");
    }
}
