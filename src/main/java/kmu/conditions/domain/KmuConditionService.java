package kmu.conditions.domain;

import kmu.KmuErrorReporter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static kmu.util.KmuValues.normaliseText;

public final class KmuConditionService {
    private final KmuConditionRepository repository;
    private final KmuErrorReporter errorReporter;
    private final KmuConditionOfferPolicy offerPolicy;

    public KmuConditionService(KmuConditionRepository repository) {
        this(repository, KmuErrorReporter.noop());
    }

    public KmuConditionService(KmuConditionRepository repository, KmuErrorReporter errorReporter) {
        this(repository, errorReporter, KmuConditionOfferPolicy.planetaryOnly());
    }

    public KmuConditionService(
            KmuConditionRepository repository,
            KmuErrorReporter errorReporter,
            KmuConditionOfferPolicy offerPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
        this.offerPolicy = Objects.requireNonNull(offerPolicy, "offerPolicy");
    }

    public List<KmuConditionSpec> listPlanetaryConditionSpecs() {
        try {
            return repository.getAllConditionSpecs().stream()
                .filter(Objects::nonNull)
                .filter(KmuConditionSpec::isPlanetary)
                .collect(Collectors.toUnmodifiableList());
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to list market condition specs.", exception);
            return Collections.emptyList();
        }
    }

    public List<KmuConditionSpec> listConditionSpecsVisibleForMarket(KmuEditableMarket market) {
        Objects.requireNonNull(market, "market");
        var currentConditionIds = getCurrentConditionIds(market);
        try {
            return repository.getAllConditionSpecs().stream()
                .filter(Objects::nonNull)
                .filter(spec -> offerPolicy.isConditionOfferable(spec)
                    || currentConditionIds.contains(spec.getId()))
                .collect(Collectors.toUnmodifiableList());
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to list visible market condition specs.", exception);
            return Collections.emptyList();
        }
    }

    public Set<String> getCurrentConditionIds(KmuEditableMarket market) {
        Objects.requireNonNull(market, "market");
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(market.getConditionIds()));
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to read market condition ids.", exception);
            return Collections.emptySet();
        }
    }

    public KmuConditionAddResult addOfferableConditionIfAbsent(
            KmuEditableMarket market,
            String conditionId) {
        Objects.requireNonNull(market, "market");

        var normalisedId = normaliseText(conditionId);
        if (normalisedId == null) {
            return KmuConditionAddResult.conditionNotFound(conditionId);
        }

        KmuConditionSpec spec;
        try {
            spec = repository.findConditionSpec(normalisedId).orElse(null);
        } catch (RuntimeException exception) {
            return failed(normalisedId, "Failed to look up condition spec.", exception);
        }

        if (spec == null) {
            return KmuConditionAddResult.conditionNotFound(normalisedId);
        }
        // Guard the add against conditions the picker would not offer, so a direct
        // (e.g. console) call cannot place a condition the policy hides.
        if (!offerPolicy.isConditionOfferable(spec)) {
            return KmuConditionAddResult.notOfferable(normalisedId);
        }

        try {
            if (market.hasCondition(normalisedId)) {
                return KmuConditionAddResult.alreadyPresent(normalisedId);
            }

            market.addCondition(normalisedId);
            market.markConditionSurveyed(normalisedId);
            market.reapplyConditions();
            return KmuConditionAddResult.added(normalisedId);
        } catch (RuntimeException exception) {
            return failed(normalisedId, "Failed to add market condition.", exception);
        }
    }

    private KmuConditionAddResult failed(
            String conditionId,
            String message,
            RuntimeException exception) {
        errorReporter.report(message + " conditionId=" + conditionId, exception);
        return KmuConditionAddResult.failed(conditionId, message, exception);
    }
}
