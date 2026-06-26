package kmu.ui.context;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import kmu.KmuErrorReporter;

import java.util.Objects;
import java.util.Optional;

public final class StarsectorMarketUiContextResolver implements KmuMarketUiContextResolver {
    private final SectorAPI sector;
    private final KmuErrorReporter errorReporter;

    public StarsectorMarketUiContextResolver() {
        this(Global.getSector());
    }

    public StarsectorMarketUiContextResolver(SectorAPI sector) {
        this(sector, KmuErrorReporter.noop());
    }

    public StarsectorMarketUiContextResolver(SectorAPI sector, KmuErrorReporter errorReporter) {
        this.sector = Objects.requireNonNull(sector, "sector");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
    }

    @Override
    public Optional<KmuMarketUiContext> findCurrentMarketContext() {
        var context = fromCurrentlyOpenMarket();
        if (context.isPresent()) {
            return context;
        }

        context = fromInteractionDialogTarget();
        if (context.isPresent()) {
            return context;
        }

        context = fromPlayerFleetInteractionTarget();
        if (context.isPresent()) {
            return context;
        }

        return fromTrackedCoreUiMarket();
    }

    private Optional<KmuMarketUiContext> fromCurrentlyOpenMarket() {
        try {
            var market = sector.getCurrentlyOpenMarket();
            if (market == null) {
                return Optional.empty();
            }
            return Optional.of(KmuMarketUiContext.withoutPanel(
                    market,
                    KmuMarketUiContextSource.CURRENTLY_OPEN_MARKET));
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to resolve currently open market.", exception);
            return Optional.empty();
        }
    }

    private Optional<KmuMarketUiContext> fromInteractionDialogTarget() {
        try {
            var campaignUI = sector.getCampaignUI();
            if (campaignUI == null) {
                return Optional.empty();
            }

            var dialog = campaignUI.getCurrentInteractionDialog();
            if (dialog == null) {
                return Optional.empty();
            }

            return contextFromEntity(
                    dialog.getInteractionTarget(),
                    KmuMarketUiContextSource.INTERACTION_DIALOG_TARGET);
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to resolve interaction dialog market.", exception);
            return Optional.empty();
        }
    }

    private Optional<KmuMarketUiContext> fromPlayerFleetInteractionTarget() {
        try {
            var playerFleet = sector.getPlayerFleet();
            if (playerFleet == null) {
                return Optional.empty();
            }

            return contextFromEntity(
                    playerFleet.getInteractionTarget(),
                    KmuMarketUiContextSource.PLAYER_FLEET_INTERACTION_TARGET);
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to resolve player fleet interaction target market.", exception);
            return Optional.empty();
        }
    }

    private Optional<KmuMarketUiContext> fromTrackedCoreUiMarket() {
        try {
            var listenerManager = sector.getListenerManager();
            if (listenerManager == null) {
                return Optional.empty();
            }

            var trackers =
                    listenerManager.getListeners(StarsectorMarketUiContextTracker.class);
            if (trackers == null) {
                return Optional.empty();
            }

            for (var tracker : trackers) {
                if (tracker == null) {
                    continue;
                }

                var market = tracker.getTrackedMarket();
                if (market.isPresent()) {
                    return Optional.of(KmuMarketUiContext.withoutPanel(
                            market.get(),
                            KmuMarketUiContextSource.TRACKED_CORE_UI_MARKET));
                }
            }

            return Optional.empty();
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to resolve tracked core UI market.", exception);
            return Optional.empty();
        }
    }

    private Optional<KmuMarketUiContext> contextFromEntity(
            SectorEntityToken entity,
            KmuMarketUiContextSource source) {
        if (entity == null) {
            return Optional.empty();
        }

        var market = entity.getMarket();
        if (market == null) {
            return Optional.empty();
        }

        return Optional.of(KmuMarketUiContext.withoutPanel(market, source));
    }
}
