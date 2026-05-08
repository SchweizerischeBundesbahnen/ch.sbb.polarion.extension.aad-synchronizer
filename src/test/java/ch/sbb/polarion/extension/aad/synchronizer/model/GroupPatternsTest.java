package ch.sbb.polarion.extension.aad.synchronizer.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@link GroupPrefixesTest} for the {@code <groupPatterns>} block. The two wrappers share
 * the same Polarion-shape dispatch logic and differ only in the inner element name, so any
 * divergence between this pair of tests is suspicious. See {@link GroupPrefixesTest} for the
 * shape catalog.
 */
class GroupPatternsTest {

    @Test
    void fromRawValue_nullYieldsNull() {
        assertThat(GroupPatterns.fromRawValue(null)).isNull();
    }

    @Test
    void fromRawValue_unexpectedScalarYieldsNull() {
        assertThat(GroupPatterns.fromRawValue(42)).isNull();
        assertThat(GroupPatterns.fromRawValue("not-a-shape")).isNull();
    }

    @Test
    void fromRawValue_singleChildMapYieldsSingletonList() {
        GroupPatterns patterns = (GroupPatterns) GroupPatterns.fromRawValue(
                Map.of(GroupPatterns.GROUP_PATTERN_NAME, "^FOO_.*"));

        assertThat(patterns.getPatterns()).containsExactly("^FOO_.*");
    }

    @Test
    void fromRawValue_multipleChildrenListYieldsList() {
        GroupPatterns patterns = (GroupPatterns) GroupPatterns.fromRawValue(
                List.of("^A_.*", "^B_.*"));

        assertThat(patterns.getPatterns()).containsExactly("^A_.*", "^B_.*");
    }

    @Test
    void fromRawValue_mapWithListUnderInnerKeyYieldsList() {
        // Mirror of GroupPrefixesTest's defensive Map-wrapping-List case.
        GroupPatterns patterns = (GroupPatterns) GroupPatterns.fromRawValue(
                Map.of(GroupPatterns.GROUP_PATTERN_NAME, List.of("^A_.*", "^B_.*")));

        assertThat(patterns.getPatterns()).containsExactly("^A_.*", "^B_.*");
    }

    @Test
    void fromRawValue_mapWithMissingInnerKeyYieldsEmptyList() {
        GroupPatterns patterns = (GroupPatterns) GroupPatterns.fromRawValue(
                Map.of("unrelated", "value"));

        assertThat(patterns.getPatterns()).isEmpty();
    }

    @Test
    void fromRawValue_mapWithUnexpectedInnerTypeYieldsEmptyList() {
        GroupPatterns patterns = (GroupPatterns) GroupPatterns.fromRawValue(
                Map.of(GroupPatterns.GROUP_PATTERN_NAME, 42));

        assertThat(patterns.getPatterns()).isEmpty();
    }

    @Test
    void directConstructorPreservesOrderAndTakesDefensiveCopy() {
        java.util.ArrayList<String> source = new java.util.ArrayList<>(Arrays.asList("^KEEP_.*", null, ""));
        GroupPatterns patterns = new GroupPatterns(source);

        assertThat(patterns.getPatterns()).containsExactly("^KEEP_.*", null, "");
        source.add("^LATE_ADDITION_.*");
        assertThat(patterns.getPatterns()).containsExactly("^KEEP_.*", null, "");
    }
}
