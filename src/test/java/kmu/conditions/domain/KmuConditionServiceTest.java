package kmu.conditions.domain;

import org.junit.jupiter.api.Nested;
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

    @Nested
    class ListPlanetaryConditionSpecs {

        @Test
        void listsOnlyPlanetaryConditionSpecsInRepositoryOrder() {
            var repositoryFake = new ConditionRepositoryFake(
                    buildSpec("hot", "Hot", true),
                    buildSpec("population_3", "Population 3", false),
                    buildSpec("farmland_rich", "Farmland: Rich", true));
            var service = new KmuConditionService(repositoryFake);

            var specs = service.listPlanetaryConditionSpecs();

            assertThat(specs)
                    .extracting(KmuConditionSpec::getId)
                    .containsExactly("hot", "farmland_rich");
        }

        @Test
        void skipsNullSpecsWhenListingPlanetaryConditionSpecs() {
            var service = new KmuConditionService(new ListBackedConditionRepository(Arrays.asList(
                    buildSpec("hot", "Hot", true),
                    null,
                    buildSpec("farmland_rich", "Farmland: Rich", true))));

            var specs = service.listPlanetaryConditionSpecs();

            assertThat(specs)
                    .extracting(KmuConditionSpec::getId)
                    .containsExactly("hot", "farmland_rich");
        }

        @Test
        void returnsImmutablePlanetaryConditionSpecList() {
            var service = new KmuConditionService(
                    new ConditionRepositoryFake(buildSpec("hot", "Hot", true)));

            var specs = service.listPlanetaryConditionSpecs();

            assertThatThrownBy(() -> specs.add(buildSpec("cold", "Cold", true)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void returnsEmptySpecListWhenRepositoryListFails() {
            var reports = new ArrayList<String>();
            var service = new KmuConditionService(
                    new ThrowingConditionRepository(new IllegalStateException("settings failed")),
                    (message, cause) -> reports.add(message));

            var specs = service.listPlanetaryConditionSpecs();

            assertThat(specs).isEmpty();
            assertThat(reports).containsExactly("Failed to list market condition specs.");
        }
    }

    @Nested
    class ListConditionSpecsVisibleForMarket {

        @Test
        void visibleSpecsIncludePlanetarySpecsAndCurrentNonPlanetaryConditions() {
            var repositoryFake = new ConditionRepositoryFake(
                    buildSpec("hot", "Hot", true),
                    buildSpec("abandoned_station", "Abandoned Station", false),
                    buildSpec("population_3", "Population 3", false));
            var service = new KmuConditionService(repositoryFake);
            var marketFake = new EditableMarketFake("abandoned_station");

            var specs = service.listConditionSpecsVisibleForMarket(marketFake);

            assertThat(specs)
                    .extracting(KmuConditionSpec::getId)
                    .containsExactly("hot", "abandoned_station");
        }

        @Test
        void visibleSpecsIncludeNonPlanetaryConditionsTheOfferPolicyOffers() {
            var repositoryFake = new ConditionRepositoryFake(
                    buildSpec("hot", "Hot", true),
                    buildSpec("decivilized", "Decivilized", false),
                    buildSpec("population_3", "Population 3", false));
            KmuConditionOfferPolicy offerPolicy =
                    candidate -> candidate != null
                            && (candidate.isPlanetary() || "decivilized".equals(candidate.getId()));
            var service = new KmuConditionService(repositoryFake, (message, cause) -> { }, offerPolicy);
            var marketFake = new EditableMarketFake();

            var specs = service.listConditionSpecsVisibleForMarket(marketFake);

            assertThat(specs)
                    .extracting(KmuConditionSpec::getId)
                    .containsExactly("hot", "decivilized");
        }
    }

    @Nested
    class GetCurrentConditionIds {

        @Test
        void returnsCurrentConditionIdsFromMarket() {
            var service = new KmuConditionService(new ConditionRepositoryFake());
            var marketFake = new EditableMarketFake("hot", "ore_sparse");

            assertThat(service.getCurrentConditionIds(marketFake))
                    .containsExactly("hot", "ore_sparse");
        }

        @Test
        void returnsImmutableCurrentConditionIds() {
            var service = new KmuConditionService(new ConditionRepositoryFake());
            var marketFake = new EditableMarketFake("hot");

            var conditionIds = service.getCurrentConditionIds(marketFake);

            assertThatThrownBy(() -> conditionIds.add("cold"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void returnsEmptyConditionIdsWhenMarketReadFails() {
            var reports = new ArrayList<String>();
            var service = new KmuConditionService(
                    new ConditionRepositoryFake(),
                    (message, cause) -> reports.add(message));
            var marketFake = new EditableMarketFake("hot")
                    .failGetConditionIds(new IllegalStateException("market read failed"));

            var conditionIds = service.getCurrentConditionIds(marketFake);

            assertThat(conditionIds).isEmpty();
            assertThat(reports).containsExactly("Failed to read market condition ids.");
        }
    }

    @Nested
    class AddOfferableConditionIfAbsent {

        @Test
        void addsValidAbsentPlanetaryConditionAndReappliesMarket() {
            var repositoryFake = new ConditionRepositoryFake(buildSpec("habitable", "Habitable", true));
            var service = new KmuConditionService(repositoryFake);
            var marketFake = new EditableMarketFake();

            var result = service.addOfferableConditionIfAbsent(marketFake, "habitable");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
            assertThat(result.getStatus().isMutationApplied()).isTrue();
            assertThat(result.getConditionId()).contains("habitable");
            assertThat(marketFake.getConditionIds()).containsExactly("habitable");
            assertThat(marketFake.calls).containsExactly(
                    "add:habitable",
                    "surveyed:habitable",
                    "reapply");
        }

        @Test
        void trimsConditionIdBeforeLookupAndMutation() {
            var repositoryFake = new ConditionRepositoryFake(buildSpec("hot", "Hot", true));
            var service = new KmuConditionService(repositoryFake);
            var marketFake = new EditableMarketFake();

            var result = service.addOfferableConditionIfAbsent(marketFake, "  hot  ");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
            assertThat(marketFake.calls).containsExactly("add:hot", "surveyed:hot", "reapply");
        }

        @Test
        void doesNotAddDuplicateCondition() {
            var repositoryFake = new ConditionRepositoryFake(buildSpec("hot", "Hot", true));
            var service = new KmuConditionService(repositoryFake);
            var marketFake = new EditableMarketFake("hot");

            var result = service.addOfferableConditionIfAbsent(marketFake, "hot");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ALREADY_PRESENT);
            assertThat(result.getStatus().isMutationApplied()).isFalse();
            assertThat(marketFake.calls).isEmpty();
            assertThat(marketFake.getConditionIds()).containsExactly("hot");
        }

        @Test
        void rejectsMissingConditionSpec() {
            var service = new KmuConditionService(new ConditionRepositoryFake());
            var marketFake = new EditableMarketFake();

            var result = service.addOfferableConditionIfAbsent(marketFake, "missing_condition");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.CONDITION_NOT_FOUND);
            assertThat(result.getStatus().isMutationApplied()).isFalse();
            assertThat(marketFake.calls).isEmpty();
        }

        @Test
        void rejectsBlankConditionId() {
            var service = new KmuConditionService(new ConditionRepositoryFake());
            var marketFake = new EditableMarketFake();

            var result = service.addOfferableConditionIfAbsent(marketFake, "   ");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.CONDITION_NOT_FOUND);
            assertThat(result.getConditionId()).contains("   ");
            assertThat(marketFake.calls).isEmpty();
        }

        @Test
        void rejectsNullConditionId() {
            var service = new KmuConditionService(new ConditionRepositoryFake());
            var marketFake = new EditableMarketFake();

            var result = service.addOfferableConditionIfAbsent(marketFake, null);

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.CONDITION_NOT_FOUND);
            assertThat(result.getConditionId()).isEmpty();
            assertThat(marketFake.calls).isEmpty();
        }

        @Test
        void rejectsNonPlanetaryConditionSpecUnderThePlanetaryOnlyDefault() {
            var repositoryFake = new ConditionRepositoryFake(buildSpec("population_3", "Population 3", false));
            var service = new KmuConditionService(repositoryFake);
            var marketFake = new EditableMarketFake();

            var result = service.addOfferableConditionIfAbsent(marketFake, "population_3");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.NOT_OFFERABLE);
            assertThat(result.getStatus().isMutationApplied()).isFalse();
            assertThat(marketFake.calls).isEmpty();
        }

        @Test
        void addsNonPlanetaryConditionWhenOfferPolicyOffersIt() {
            var repositoryFake = new ConditionRepositoryFake(buildSpec("decivilized", "Decivilized", false));
            KmuConditionOfferPolicy offerPolicy =
                    candidate -> candidate != null
                            && (candidate.isPlanetary() || "decivilized".equals(candidate.getId()));
            var service = new KmuConditionService(repositoryFake, (message, cause) -> { }, offerPolicy);
            var marketFake = new EditableMarketFake();

            var result = service.addOfferableConditionIfAbsent(marketFake, "decivilized");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.ADDED);
            assertThat(marketFake.getConditionIds()).containsExactly("decivilized");
        }

        @Test
        void returnsFailedResultWhenConditionLookupThrows() {
            var exception = new IllegalStateException("settings unavailable");
            var reports = new ArrayList<String>();
            var service = new KmuConditionService(
                    new ThrowingConditionRepository(exception),
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));
            var marketFake = new EditableMarketFake();

            var result = service.addOfferableConditionIfAbsent(marketFake, "hot");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(result.getStatus().isMutationApplied()).isFalse();
            assertThat(result.getConditionId()).contains("hot");
            assertThat(result.getMessage()).contains("Failed to look up condition spec.");
            assertThat(result.getCause()).contains(exception);
            assertThat(marketFake.calls).isEmpty();
            assertThat(reports).containsExactly("Failed to look up condition spec. conditionId=hot / settings unavailable");
        }

        @Test
        void returnsFailedResultWhenMarketHasConditionThrows() {
            var exception = new IllegalStateException("market unavailable");
            var repositoryFake = new ConditionRepositoryFake(buildSpec("hot", "Hot", true));
            var reports = new ArrayList<String>();
            var service = new KmuConditionService(
                    repositoryFake,
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));
            var marketFake = new EditableMarketFake()
                    .failHasCondition(exception);

            var result = service.addOfferableConditionIfAbsent(marketFake, "hot");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(result.getCause()).contains(exception);
            assertThat(marketFake.calls).isEmpty();
            assertThat(reports).containsExactly("Failed to add market condition. conditionId=hot / market unavailable");
        }

        @Test
        void returnsFailedResultWhenMutationThrowsAfterAdd() {
            var exception = new IllegalStateException("survey failed");
            var repositoryFake = new ConditionRepositoryFake(buildSpec("hot", "Hot", true));
            var service = new KmuConditionService(repositoryFake);
            var marketFake = new EditableMarketFake()
                    .failMarkConditionSurveyed(exception);

            var result = service.addOfferableConditionIfAbsent(marketFake, "hot");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(result.getCause()).contains(exception);
            assertThat(marketFake.getConditionIds()).containsExactly("hot");
            assertThat(marketFake.calls).containsExactly("add:hot");
        }

        @Test
        void returnsFailedResultWhenAddConditionThrows() {
            var exception = new IllegalStateException("add failed");
            var repositoryFake = new ConditionRepositoryFake(buildSpec("hot", "Hot", true));
            var reports = new ArrayList<String>();
            var service = new KmuConditionService(
                    repositoryFake,
                    (message, cause) -> reports.add(message + " / " + cause.getMessage()));
            var marketFake = new EditableMarketFake()
                    .failAddCondition(exception);

            var result = service.addOfferableConditionIfAbsent(marketFake, "hot");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(result.getCause()).contains(exception);
            assertThat(marketFake.getConditionIds()).isEmpty();
            assertThat(marketFake.calls).isEmpty();
            assertThat(reports).containsExactly("Failed to add market condition. conditionId=hot / add failed");
        }

        @Test
        void returnsFailedResultWhenReapplyThrowsAfterMutation() {
            var exception = new IllegalStateException("reapply failed");
            var repositoryFake = new ConditionRepositoryFake(buildSpec("hot", "Hot", true));
            var service = new KmuConditionService(repositoryFake);
            var marketFake = new EditableMarketFake()
                    .failReapplyConditions(exception);

            var result = service.addOfferableConditionIfAbsent(marketFake, "hot");

            assertThat(result.getStatus()).isEqualTo(KmuConditionAddStatus.FAILED);
            assertThat(result.getCause()).contains(exception);
            assertThat(marketFake.getConditionIds()).containsExactly("hot");
            assertThat(marketFake.calls).containsExactly("add:hot", "surveyed:hot");
        }
    }

    private static KmuConditionSpec buildSpec(String id, String name, boolean planetary) {
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

    private static final class ConditionRepositoryFake implements KmuConditionRepository {
        private final LinkedHashMap<String, KmuConditionSpec> specsById = new LinkedHashMap<>();

        private ConditionRepositoryFake(KmuConditionSpec... specs) {
            for (var spec : specs) {
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

    private static final class EditableMarketFake implements KmuEditableMarket {
        private final LinkedHashSet<String> conditionIds = new LinkedHashSet<>();
        private final List<String> calls = new ArrayList<>();
        private RuntimeException getConditionIdsException;
        private RuntimeException hasConditionException;
        private RuntimeException addConditionException;
        private RuntimeException markConditionSurveyedException;
        private RuntimeException reapplyConditionsException;

        private EditableMarketFake(String... conditionIds) {
            for (String conditionId : conditionIds) {
                this.conditionIds.add(conditionId);
            }
        }

        private EditableMarketFake failGetConditionIds(RuntimeException exception) {
            this.getConditionIdsException = exception;
            return this;
        }

        private EditableMarketFake failHasCondition(RuntimeException exception) {
            this.hasConditionException = exception;
            return this;
        }

        private EditableMarketFake failAddCondition(RuntimeException exception) {
            this.addConditionException = exception;
            return this;
        }

        private EditableMarketFake failMarkConditionSurveyed(RuntimeException exception) {
            this.markConditionSurveyedException = exception;
            return this;
        }

        private EditableMarketFake failReapplyConditions(RuntimeException exception) {
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
