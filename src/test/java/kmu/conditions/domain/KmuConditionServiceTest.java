package kmu.conditions.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmuConditionServiceTest {
    @Test
    void listsOnlyPlanetaryConditionSpecsInRepositoryOrder() {
        FakeConditionRepository repository = new FakeConditionRepository(
                spec("hot", "Hot", true),
                spec("population_3", "Population 3", false),
                spec("farmland_rich", "Farmland: Rich", true));
        KmuConditionService service = new KmuConditionService(repository);

        List<KmuConditionSpec> specs = service.listPlanetaryConditionSpecs();

        assertThat(specs)
                .extracting(KmuConditionSpec::getId)
                .containsExactly("hot", "farmland_rich");
    }

    @Test
    void skipsNullSpecsWhenListingPlanetaryConditionSpecs() {
        KmuConditionService service = new KmuConditionService(new ListBackedConditionRepository(Arrays.asList(
                spec("hot", "Hot", true),
                null,
                spec("farmland_rich", "Farmland: Rich", true))));

        List<KmuConditionSpec> specs = service.listPlanetaryConditionSpecs();

        assertThat(specs)
                .extracting(KmuConditionSpec::getId)
                .containsExactly("hot", "farmland_rich");
    }

    @Test
    void returnsImmutablePlanetaryConditionSpecList() {
        KmuConditionService service = new KmuConditionService(
                new FakeConditionRepository(spec("hot", "Hot", true)));

        List<KmuConditionSpec> specs = service.listPlanetaryConditionSpecs();

        assertThatThrownBy(() -> specs.add(spec("cold", "Cold", true)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void visibleSpecsIncludePlanetarySpecsAndCurrentNonPlanetaryConditions() {
        FakeConditionRepository repository = new FakeConditionRepository(
                spec("hot", "Hot", true),
                spec("abandoned_station", "Abandoned Station", false),
                spec("population_3", "Population 3", false));
        KmuConditionService service = new KmuConditionService(repository);
        FakeEditableMarket market = new FakeEditableMarket("abandoned_station");

        List<KmuConditionSpec> specs = service.listConditionSpecsVisibleForMarket(market);

        assertThat(specs)
                .extracting(KmuConditionSpec::getId)
                .containsExactly("hot", "abandoned_station");
    }

    @Test
    void returnsCurrentConditionIdsFromMarket() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository());
        FakeEditableMarket market = new FakeEditableMarket("hot", "ore_sparse");

        assertThat(service.getCurrentConditionIds(market))
                .containsExactly("hot", "ore_sparse");
    }

    @Test
    void returnsImmutableCurrentConditionIds() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository());
        FakeEditableMarket market = new FakeEditableMarket("hot");

        Set<String> conditionIds = service.getCurrentConditionIds(market);

        assertThatThrownBy(() -> conditionIds.add("cold"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsEmptyConditionIdsWhenMarketReadFails() {
        List<String> reports = new ArrayList<>();
        KmuConditionService service = new KmuConditionService(
                new FakeConditionRepository(),
                (message, cause) -> reports.add(message));
        FakeEditableMarket market = new FakeEditableMarket("hot")
                .failGetConditionIds(new IllegalStateException("market read failed"));

        Set<String> conditionIds = service.getCurrentConditionIds(market);

        assertThat(conditionIds).isEmpty();
        assertThat(reports).containsExactly("Failed to read market condition ids.");
    }

    @Test
    void returnsEmptySpecListWhenRepositoryListFails() {
        List<String> reports = new ArrayList<>();
        KmuConditionService service = new KmuConditionService(
                new ThrowingConditionRepository(new IllegalStateException("settings failed")),
                (message, cause) -> reports.add(message));

        List<KmuConditionSpec> specs = service.listPlanetaryConditionSpecs();

        assertThat(specs).isEmpty();
        assertThat(reports).containsExactly("Failed to list market condition specs.");
    }

    @Test
    void addsValidAbsentPlanetaryConditionAndReappliesMarket() {
        FakeConditionRepository repository = new FakeConditionRepository(spec("habitable", "Habitable", true));
        KmuConditionService service = new KmuConditionService(repository);
        FakeEditableMarket market = new FakeEditableMarket();

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "habitable");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
        assertThat(result.getStatus().isMutationApplied()).isTrue();
        assertThat(result.getConditionId()).contains("habitable");
        assertThat(market.getConditionIds()).containsExactly("habitable");
        assertThat(market.calls).containsExactly(
                "add:habitable",
                "surveyed:habitable",
                "reapply");
    }

    @Test
    void trimsConditionIdBeforeLookupAndMutation() {
        FakeConditionRepository repository = new FakeConditionRepository(spec("hot", "Hot", true));
        KmuConditionService service = new KmuConditionService(repository);
        FakeEditableMarket market = new FakeEditableMarket();

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "  hot  ");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
        assertThat(market.calls).containsExactly("add:hot", "surveyed:hot", "reapply");
    }

    @Test
    void doesNotAddDuplicateCondition() {
        FakeConditionRepository repository = new FakeConditionRepository(spec("hot", "Hot", true));
        KmuConditionService service = new KmuConditionService(repository);
        FakeEditableMarket market = new FakeEditableMarket("hot");

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "hot");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ALREADY_PRESENT);
        assertThat(result.getStatus().isMutationApplied()).isFalse();
        assertThat(market.calls).isEmpty();
        assertThat(market.getConditionIds()).containsExactly("hot");
    }

    @Test
    void rejectsMissingConditionSpec() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository());
        FakeEditableMarket market = new FakeEditableMarket();

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "missing_condition");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.CONDITION_NOT_FOUND);
        assertThat(result.getStatus().isMutationApplied()).isFalse();
        assertThat(market.calls).isEmpty();
    }

    @Test
    void rejectsBlankConditionId() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository());
        FakeEditableMarket market = new FakeEditableMarket();

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "   ");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.CONDITION_NOT_FOUND);
        assertThat(result.getConditionId()).contains("   ");
        assertThat(market.calls).isEmpty();
    }

    @Test
    void rejectsNullConditionId() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository());
        FakeEditableMarket market = new FakeEditableMarket();

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, null);

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.CONDITION_NOT_FOUND);
        assertThat(result.getConditionId()).isEmpty();
        assertThat(market.calls).isEmpty();
    }

    @Test
    void rejectsNonPlanetaryConditionSpec() {
        FakeConditionRepository repository = new FakeConditionRepository(spec("population_3", "Population 3", false));
        KmuConditionService service = new KmuConditionService(repository);
        FakeEditableMarket market = new FakeEditableMarket();

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "population_3");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.NOT_PLANETARY);
        assertThat(result.getStatus().isMutationApplied()).isFalse();
        assertThat(market.calls).isEmpty();
    }

    @Test
    void returnsFailedResultWhenConditionLookupThrows() {
        RuntimeException exception = new IllegalStateException("settings unavailable");
        List<String> reports = new ArrayList<>();
        KmuConditionService service = new KmuConditionService(
                new ThrowingConditionRepository(exception),
                (message, cause) -> reports.add(message + " / " + cause.getMessage()));
        FakeEditableMarket market = new FakeEditableMarket();

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "hot");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(result.getStatus().isMutationApplied()).isFalse();
        assertThat(result.getConditionId()).contains("hot");
        assertThat(result.getMessage()).contains("Failed to look up condition spec.");
        assertThat(result.getCause()).contains(exception);
        assertThat(market.calls).isEmpty();
        assertThat(reports).containsExactly("Failed to look up condition spec. conditionId=hot / settings unavailable");
    }

    @Test
    void returnsFailedResultWhenMarketHasConditionThrows() {
        RuntimeException exception = new IllegalStateException("market unavailable");
        FakeConditionRepository repository = new FakeConditionRepository(spec("hot", "Hot", true));
        List<String> reports = new ArrayList<>();
        KmuConditionService service = new KmuConditionService(
                repository,
                (message, cause) -> reports.add(message + " / " + cause.getMessage()));
        FakeEditableMarket market = new FakeEditableMarket()
                .failHasCondition(exception);

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "hot");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(result.getCause()).contains(exception);
        assertThat(market.calls).isEmpty();
        assertThat(reports).containsExactly("Failed to add market condition. conditionId=hot / market unavailable");
    }

    @Test
    void returnsFailedResultWhenMutationThrowsAfterAdd() {
        RuntimeException exception = new IllegalStateException("survey failed");
        FakeConditionRepository repository = new FakeConditionRepository(spec("hot", "Hot", true));
        KmuConditionService service = new KmuConditionService(repository);
        FakeEditableMarket market = new FakeEditableMarket()
                .failMarkConditionSurveyed(exception);

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "hot");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(result.getCause()).contains(exception);
        assertThat(market.getConditionIds()).containsExactly("hot");
        assertThat(market.calls).containsExactly("add:hot");
    }

    @Test
    void returnsFailedResultWhenAddConditionThrows() {
        RuntimeException exception = new IllegalStateException("add failed");
        FakeConditionRepository repository = new FakeConditionRepository(spec("hot", "Hot", true));
        List<String> reports = new ArrayList<>();
        KmuConditionService service = new KmuConditionService(
                repository,
                (message, cause) -> reports.add(message + " / " + cause.getMessage()));
        FakeEditableMarket market = new FakeEditableMarket()
                .failAddCondition(exception);

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "hot");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(result.getCause()).contains(exception);
        assertThat(market.getConditionIds()).isEmpty();
        assertThat(market.calls).isEmpty();
        assertThat(reports).containsExactly("Failed to add market condition. conditionId=hot / add failed");
    }

    @Test
    void returnsFailedResultWhenReapplyThrowsAfterMutation() {
        RuntimeException exception = new IllegalStateException("reapply failed");
        FakeConditionRepository repository = new FakeConditionRepository(spec("hot", "Hot", true));
        KmuConditionService service = new KmuConditionService(repository);
        FakeEditableMarket market = new FakeEditableMarket()
                .failReapplyConditions(exception);

        KmuConditionAddResult result = service.addPlanetaryConditionIfAbsent(market, "hot");

        assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
        assertThat(result.getCause()).contains(exception);
        assertThat(market.getConditionIds()).containsExactly("hot");
        assertThat(market.calls).containsExactly("add:hot", "surveyed:hot");
    }

    private static KmuConditionSpec spec(String id, String name, boolean planetary) {
        return new KmuConditionSpec(id, name, "graphics/icons/" + id + ".png", planetary);
    }

    private static final class ListBackedConditionRepository implements KmuConditionRepository {
        private final List<KmuConditionSpec> specs;

        private ListBackedConditionRepository(List<KmuConditionSpec> specs) {
            this.specs = new ArrayList<>(specs);
        }

        @Override
        public List<KmuConditionSpec> getAllConditionSpecs() {
            return new ArrayList<>(specs);
        }

        @Override
        public Optional<KmuConditionSpec> findConditionSpec(String conditionId) {
            return specs.stream()
                    .filter(Objects::nonNull)
                    .filter(spec -> spec.getId().equals(conditionId))
                    .findFirst();
        }
    }

    private static final class FakeConditionRepository implements KmuConditionRepository {
        private final LinkedHashMap<String, KmuConditionSpec> specsById = new LinkedHashMap<>();

        private FakeConditionRepository(KmuConditionSpec... specs) {
            for (KmuConditionSpec spec : specs) {
                specsById.put(spec.getId(), spec);
            }
        }

        @Override
        public List<KmuConditionSpec> getAllConditionSpecs() {
            return new ArrayList<>(specsById.values());
        }

        @Override
        public Optional<KmuConditionSpec> findConditionSpec(String conditionId) {
            return Optional.ofNullable(specsById.get(conditionId));
        }
    }

    private static final class ThrowingConditionRepository implements KmuConditionRepository {
        private final RuntimeException exception;

        private ThrowingConditionRepository(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public List<KmuConditionSpec> getAllConditionSpecs() {
            throw exception;
        }

        @Override
        public Optional<KmuConditionSpec> findConditionSpec(String conditionId) {
            throw exception;
        }
    }

    private static final class FakeEditableMarket implements KmuEditableMarket {
        private final LinkedHashSet<String> conditionIds = new LinkedHashSet<>();
        private final List<String> calls = new ArrayList<>();
        private RuntimeException getConditionIdsException;
        private RuntimeException hasConditionException;
        private RuntimeException addConditionException;
        private RuntimeException markConditionSurveyedException;
        private RuntimeException reapplyConditionsException;

        private FakeEditableMarket(String... conditionIds) {
            for (String conditionId : conditionIds) {
                this.conditionIds.add(conditionId);
            }
        }

        private FakeEditableMarket failGetConditionIds(RuntimeException exception) {
            this.getConditionIdsException = exception;
            return this;
        }

        private FakeEditableMarket failHasCondition(RuntimeException exception) {
            this.hasConditionException = exception;
            return this;
        }

        private FakeEditableMarket failAddCondition(RuntimeException exception) {
            this.addConditionException = exception;
            return this;
        }

        private FakeEditableMarket failMarkConditionSurveyed(RuntimeException exception) {
            this.markConditionSurveyedException = exception;
            return this;
        }

        private FakeEditableMarket failReapplyConditions(RuntimeException exception) {
            this.reapplyConditionsException = exception;
            return this;
        }

        @Override
        public Set<String> getConditionIds() {
            if (getConditionIdsException != null) {
                throw getConditionIdsException;
            }
            return new LinkedHashSet<>(conditionIds);
        }

        @Override
        public boolean hasCondition(String conditionId) {
            if (hasConditionException != null) {
                throw hasConditionException;
            }
            return conditionIds.contains(conditionId);
        }

        @Override
        public void addCondition(String conditionId) {
            if (addConditionException != null) {
                throw addConditionException;
            }
            calls.add("add:" + conditionId);
            conditionIds.add(conditionId);
        }

        @Override
        public void markConditionSurveyed(String conditionId) {
            if (markConditionSurveyedException != null) {
                throw markConditionSurveyedException;
            }
            calls.add("surveyed:" + conditionId);
        }

        @Override
        public void reapplyConditions() {
            if (reapplyConditionsException != null) {
                throw reapplyConditionsException;
            }
            calls.add("reapply");
        }
    }
}
