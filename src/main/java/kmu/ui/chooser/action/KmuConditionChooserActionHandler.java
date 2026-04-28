package kmu.ui.chooser.action;

import kmu.conditions.KmuConditionAddResult;
import kmu.conditions.KmuConditionService;
import kmu.conditions.KmuEditableMarket;
import kmu.ui.chooser.model.KmuConditionChooserModel;
import kmu.ui.chooser.model.KmuConditionChooserModelFactory;

import java.util.Objects;
import java.util.Optional;

public final class KmuConditionChooserActionHandler {
    private final KmuConditionService conditionService;
    private final KmuConditionChooserModelFactory modelFactory;
    private final KmuEditableMarket market;
    private KmuConditionChooserModel model;
    private KmuConditionChooserFeedback feedback;

    public KmuConditionChooserActionHandler(
            KmuConditionService conditionService,
            KmuConditionChooserModelFactory modelFactory,
            KmuEditableMarket market) {
        this.conditionService = Objects.requireNonNull(conditionService, "conditionService");
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory");
        this.market = Objects.requireNonNull(market, "market");
        this.model = modelFactory.create(market);
    }

    public KmuConditionChooserModel getModel() {
        return model;
    }

    public Optional<KmuConditionChooserFeedback> getFeedback() {
        return Optional.ofNullable(feedback);
    }

    public KmuConditionAddResult handle(KmuConditionChooserAction action) {
        Objects.requireNonNull(action, "action");

        if (action.isPresentAtRender()) {
            feedback = null;
            return KmuConditionAddResult.alreadyPresent(action.getConditionId());
        }

        KmuConditionAddResult result = conditionService.addPlanetaryConditionIfAbsent(market, action.getConditionId());
        model = modelFactory.create(market);
        feedback = KmuConditionChooserFeedback.from(result);
        return result;
    }
}
