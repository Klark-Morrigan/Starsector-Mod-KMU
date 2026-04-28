package kmu.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
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
        Optional<KmuMarketUiContext> context = fromCurrentlyOpenMarket();
        if (context.isPresent()) {
            return context;
        }

        context = fromInteractionDialogTarget();
        if (context.isPresent()) {
            return context;
        }

        return fromPlayerFleetInteractionTarget();
    }

    private Optional<KmuMarketUiContext> fromCurrentlyOpenMarket() {
        try {
            MarketAPI market = sector.getCurrentlyOpenMarket();
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
            CampaignUIAPI campaignUI = sector.getCampaignUI();
            if (campaignUI == null) {
                return Optional.empty();
            }

            InteractionDialogAPI dialog = campaignUI.getCurrentInteractionDialog();
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
            CampaignFleetAPI playerFleet = sector.getPlayerFleet();
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

    private Optional<KmuMarketUiContext> contextFromEntity(
            SectorEntityToken entity,
            KmuMarketUiContextSource source) {
        if (entity == null) {
            return Optional.empty();
        }

        MarketAPI market = entity.getMarket();
        if (market == null) {
            return Optional.empty();
        }

        return Optional.of(KmuMarketUiContext.withoutPanel(market, source));
    }
}
