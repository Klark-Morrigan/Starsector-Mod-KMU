package kmu.conditions.domain;

import java.util.List;
import java.util.Optional;

public interface KmuConditionRepository {
    List<KmuConditionSpec> getAllConditionSpecs();

    Optional<KmuConditionSpec> findConditionSpec(String conditionId);
}
