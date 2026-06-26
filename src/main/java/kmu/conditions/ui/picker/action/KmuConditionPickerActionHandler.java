package kmu.conditions.ui.picker.action;

import kmu.conditions.domain.KmuConditionAddResult;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.model.KmuConditionPickerModelFactory;

import java.util.Objects;
import java.util.Optional;

public final class KmuConditionPickerActionHandler {
    private final KmuConditionService conditionService;
    private final KmuConditionPickerModelFactory modelFactory;
    private final KmuEditableMarket market;
    private KmuConditionPickerModel model;
    private KmuConditionPickerFeedback feedback;

    public KmuConditionPickerActionHandler(
            KmuConditionService conditionService,
            KmuConditionPickerModelFactory modelFactory,
            KmuEditableMarket market) {
        this.conditionService = Objects.requireNonNull(conditionService, "conditionService");
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory");
        this.market = Objects.requireNonNull(market, "market");
        this.model = modelFactory.create(market);
    }

    public KmuConditionPickerModel getModel() {
        return model;
    }

    public Optional<KmuConditionPickerFeedback> getFeedback() {
        return Optional.ofNullable(feedback);
    }

    public KmuConditionAddResult handle(KmuConditionPickerAction action) {
        Objects.requireNonNull(action, "action");

        if (action.isPresentAtRender()) {
            feedback = null;
            return KmuConditionAddResult.alreadyPresent(action.getConditionId());
        }

        var result = conditionService.addPlanetaryConditionIfAbsent(market, action.getConditionId());
        model = modelFactory.create(market);
        feedback = KmuConditionPickerFeedback.from(result);
        return result;
    }
}
