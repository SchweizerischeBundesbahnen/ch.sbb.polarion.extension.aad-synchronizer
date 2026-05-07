package ch.sbb.polarion.extension.aad.synchronizer.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers every shape Polarion can deliver to the {@code <groupPrefixes>} parameter constructor:
 * {@code null} map, missing key, single-child case (Polarion delivers the inner element as a
 * bare {@link String} when only one is present), multi-child case (delivered as a {@link List}),
 * and the wrong-type fallback that protects against future changes in Polarion's parameter
 * marshalling.
 */
class GroupPrefixesTest {

    @Test
    void nullMapYieldsEmptyList() {
        assertThat(new GroupPrefixes(null).getPrefixes()).isEmpty();
    }

    @Test
    void missingKeyYieldsEmptyList() {
        assertThat(new GroupPrefixes(Map.of("unrelated", "value")).getPrefixes()).isEmpty();
    }

    @Test
    void singleStringChildYieldsSingletonList() {
        // Polarion collapses <groupPrefixes><groupPrefix>X</groupPrefix></groupPrefixes>
        // to a bare String under the inner element name — wrapper must promote it to a list.
        GroupPrefixes prefixes = new GroupPrefixes(Map.of(GroupPrefixes.GROUP_PREFIX_NAME, "ONE_"));

        assertThat(prefixes.getPrefixes()).containsExactly("ONE_");
    }

    @Test
    void multipleChildrenYieldList() {
        GroupPrefixes prefixes = new GroupPrefixes(Map.of(
                GroupPrefixes.GROUP_PREFIX_NAME, List.of("LEGACY_", "NEW_")));

        assertThat(prefixes.getPrefixes()).containsExactly("LEGACY_", "NEW_");
    }

    @Test
    void unexpectedValueTypeYieldsEmptyList() {
        // Defensive: if a future Polarion release ever delivers a Map or Number under the inner
        // element key (not currently possible, but the type signature allows it), the wrapper
        // must not throw — empty list keeps the job init's mutual-exclusion logic well-defined.
        assertThat(new GroupPrefixes(Map.of(GroupPrefixes.GROUP_PREFIX_NAME, 42)).getPrefixes()).isEmpty();
    }
}
