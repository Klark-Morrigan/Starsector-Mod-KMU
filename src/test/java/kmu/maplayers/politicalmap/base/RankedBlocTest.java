package kmu.maplayers.politicalmap.base;

import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a ranked bloc answers the framework's picker seam. The stats half is the calling view's own
 * and needs no pinning here; what needs pinning is the identity half reaching the seam intact, since a
 * row drawn from one bloc's identity and a spotlight resolved from another's id would light a row the
 * map cannot paint.
 */
final class RankedBlocTest {

    // Any metrics stand in: the seam never opens the stats half, so its values are immaterial here.
    private static final DominanceStats ANY_STATS = new DominanceStats(5, 8, 40, 12);

    @Nested
    class ItemId {

        @Test
        void itemIdIsTheBlocId() {
            // The id is the one value the seam renames, and it must stay the bloc id the filter stores.
            var bloc = new RankedBloc<>(
                new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
                ANY_STATS);

            assertThat(bloc.itemId())
                .isEqualTo("hegemony");
        }
    }

    @Nested
    class DisplayName {

        @Test
        void displayNameIsTheIdentitysLabel() {

            var bloc = new RankedBloc<>(
                new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
                ANY_STATS);

            assertThat(bloc.displayName())
                .isEqualTo("Hegemony");
        }

        @Test
        void displayNameIsNullWhenTheBlocResolvedNoName() {
            // An unlabelled bloc is a case the row draws, not an error, so the null passes through.
            var bloc = new RankedBloc<>(
                new SelectableBloc("ghost", null, null),
                ANY_STATS);

            assertThat(bloc.displayName())
                .isNull();
        }
    }

    @Nested
    class CrestSpritePath {

        @Test
        void crestSpritePathIsTheIdentitysCrest() {
            
            var bloc = new RankedBloc<>(
                new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
                ANY_STATS);

            assertThat(bloc.crestSpritePath())
                .isEqualTo("crest_heg");
        }

        @Test
        void crestSpritePathIsNullWhenTheCrestFactionHasNone() {
            // A crest-less bloc still paints territory, so it stays a row and simply draws its name.
            var bloc = new RankedBloc<>(
                new SelectableBloc("ghost", "Ghost", null),
                ANY_STATS);

            assertThat(bloc.crestSpritePath())
                .isNull();
        }
    }
}
