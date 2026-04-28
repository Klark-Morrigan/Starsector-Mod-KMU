package kmu.ui.context;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import java.util.Objects;
import java.util.Optional;

public final class KmuMarketUiContext {
    private final MarketAPI market;
    private final UIPanelAPI panel;
    private final KmuMarketUiContextSource source;

    private KmuMarketUiContext(
            MarketAPI market,
            UIPanelAPI panel,
            KmuMarketUiContextSource source) {
        this.market = Objects.requireNonNull(market, "market");
        this.panel = panel;
        this.source = Objects.requireNonNull(source, "source");
    }

    public static KmuMarketUiContext withoutPanel(
            MarketAPI market,
            KmuMarketUiContextSource source) {
        return new KmuMarketUiContext(market, null, source);
    }

    public static KmuMarketUiContext withPanel(
            MarketAPI market,
            UIPanelAPI panel,
            KmuMarketUiContextSource source) {
        return new KmuMarketUiContext(market, Objects.requireNonNull(panel, "panel"), source);
    }

    public MarketAPI getMarket() {
        return market;
    }

    public Optional<UIPanelAPI> getPanel() {
        return Optional.ofNullable(panel);
    }

    public KmuMarketUiContextSource getSource() {
        return source;
    }
}
