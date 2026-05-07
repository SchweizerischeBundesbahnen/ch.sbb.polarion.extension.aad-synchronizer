package ch.sbb.polarion.extension.aad.synchronizer.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers every shape Polarion can deliver to the {@code <groupPatterns>} parameter constructor.
 * Mirrors {@link GroupPrefixesTest} — the two wrappers share the same conversion logic and
 * differ only in the inner element name, so any divergence between them is suspicious and
 * must surface in this test pair.
 */
class GroupPatternsTest {

    @Test
    void nullMapYieldsEmptyList() {
        assertThat(new GroupPatterns(null).getPatterns()).isEmpty();
    }

    @Test
    void missingKeyYieldsEmptyList() {
        assertThat(new GroupPatterns(Map.of("unrelated", "value")).getPatterns()).isEmpty();
    }

    @Test
    void singleStringChildYieldsSingletonList() {
        GroupPatterns patterns = new GroupPatterns(Map.of(
                GroupPatterns.GROUP_PATTERN_NAME, "^FOO_.*"));

        assertThat(patterns.getPatterns()).containsExactly("^FOO_.*");
    }

    @Test
    void multipleChildrenYieldList() {
        GroupPatterns patterns = new GroupPatterns(Map.of(
                GroupPatterns.GROUP_PATTERN_NAME, List.of("^A_.*", "^B_.*")));

        assertThat(patterns.getPatterns()).containsExactly("^A_.*", "^B_.*");
    }

    @Test
    void unexpectedValueTypeYieldsEmptyList() {
        assertThat(new GroupPatterns(Map.of(GroupPatterns.GROUP_PATTERN_NAME, 42)).getPatterns()).isEmpty();
    }
}
