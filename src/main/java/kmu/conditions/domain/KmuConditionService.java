package kmu.conditions.domain;

import kmu.KmuErrorReporter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static kmu.KmuValues.normalizeText;

public final class KmuConditionService {
    private final KmuConditionRepository repository;
    private final KmuErrorReporter errorReporter;

    public KmuConditionService(KmuConditionRepository repository) {
        this(repository, KmuErrorReporter.noop());
    }

    public KmuConditionService(KmuConditionRepository repository, KmuErrorReporter errorReporter) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
    }

    public List<KmuConditionSpec> listPlanetaryConditionSpecs() {
        try {
            return repository.getAllConditionSpecs().stream()
                    .filter(Objects::nonNull)
                    .filter(KmuConditionSpec::isPlanetary)
                    .collect(Collectors.toUnmodifiableList());
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to list planetary condition specs.", exception);
            return Collections.emptyList();
        }
    }

    public List<KmuConditionSpec> listConditionSpecsVisibleForMarket(KmuEditableMarket market) {
        Objects.requireNonNull(market, "market");
        Set<String> currentConditionIds = getCurrentConditionIds(market);
        try {
            return repository.getAllConditionSpecs().stream()
                    .filter(Objects::nonNull)
                    .filter(spec -> spec.isPlanetary() || currentConditionIds.contains(spec.getId()))
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

    public KmuConditionAddResult addPlanetaryConditionIfAbsent(
            KmuEditableMarket market,
            String conditionId) {
        Objects.requireNonNull(market, "market");

        String normalizedId = normalizeText(conditionId);
        if (normalizedId == null) {
            return KmuConditionAddResult.conditionNotFound(conditionId);
        }

        KmuConditionSpec spec;
        try {
            spec = repository.findConditionSpec(normalizedId).orElse(null);
        } catch (RuntimeException exception) {
            return failed(normalizedId, "Failed to look up condition spec.", exception);
        }

        if (spec == null) {
            return KmuConditionAddResult.conditionNotFound(normalizedId);
        }
        if (!spec.isPlanetary()) {
            return KmuConditionAddResult.notPlanetary(normalizedId);
        }

        try {
            if (market.hasCondition(normalizedId)) {
                return KmuConditionAddResult.alreadyPresent(normalizedId);
            }

            market.addCondition(normalizedId);
            market.markConditionSurveyed(normalizedId);
            market.reapplyConditions();
            return KmuConditionAddResult.added(normalizedId);
        } catch (RuntimeException exception) {
            return failed(normalizedId, "Failed to add planetary condition.", exception);
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
