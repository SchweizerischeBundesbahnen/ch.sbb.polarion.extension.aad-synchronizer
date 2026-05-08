package ch.sbb.polarion.extension.aad.synchronizer.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers every shape Polarion's
 * {@code com.polarion.platform.jobs.internal.service.scheduler.ScheduleDataHandler#parseParameter}
 * can deliver for a {@code <groupPrefixes>} block. The shape varies with the child structure:
 * one child → {@code Map}, two-or-more same-tag children → bare {@code List}, empty/absent → blank
 * {@code String} or {@code null}. The bare-{@code List} case is the regression that motivated
 * this fix; it must not be silently dropped (see
 * {@code GroupPrefixesPolarionShapeRegressionTest} for an end-to-end XML check).
 */
class GroupPrefixesTest {

    @Test
    void fromRawValue_nullYieldsNull() {
        assertThat(GroupPrefixes.fromRawValue(null)).isNull();
    }

    @Test
    void fromRawValue_unexpectedScalarYieldsNull() {
        // Defensive: a stray scalar (Integer, plain String, …) is treated as "not provided".
        assertThat(GroupPrefixes.fromRawValue(42)).isNull();
        assertThat(GroupPrefixes.fromRawValue("not-a-shape")).isNull();
    }

    @Test
    void fromRawValue_singleChildMapYieldsSingletonList() {
        // <groupPrefixes><groupPrefix>X</groupPrefix></groupPrefixes>
        // → ScheduleDataHandler#parseMapParameter produces {groupPrefix=X}.
        GroupPrefixes prefixes = (GroupPrefixes) GroupPrefixes.fromRawValue(
                Map.of(GroupPrefixes.GROUP_PREFIX_NAME, "ONE_"));

        assertThat(prefixes.getPrefixes()).containsExactly("ONE_");
    }

    @Test
    void fromRawValue_multipleChildrenListYieldsList() {
        // <groupPrefixes><groupPrefix>A_</groupPrefix><groupPrefix>B_</groupPrefix></groupPrefixes>
        // → ScheduleDataHandler#parseListParameter produces ["A_", "B_"] (the inner tag name
        // is dropped). This is the shape a previous Map-only convertValue silently dropped.
        GroupPrefixes prefixes = (GroupPrefixes) GroupPrefixes.fromRawValue(List.of("A_", "B_"));

        assertThat(prefixes.getPrefixes()).containsExactly("A_", "B_");
    }

    @Test
    void fromRawValue_mapWithListUnderInnerKeyYieldsList() {
        // Defensive shape: a Map-wrapping-List arrives when an operator nests homogeneously-named
        // children inside the leaf — e.g. <groupPrefixes><groupPrefix><sub>A</sub><sub>B</sub></groupPrefix></groupPrefixes>
        // (atypical but Polarion's parseParameter delivers it as {groupPrefix=[A, B]}). The
        // wrapper must accept this without falling through to the empty-default branch.
        GroupPrefixes prefixes = (GroupPrefixes) GroupPrefixes.fromRawValue(
                Map.of(GroupPrefixes.GROUP_PREFIX_NAME, List.of("A_", "B_")));

        assertThat(prefixes.getPrefixes()).containsExactly("A_", "B_");
    }

    @Test
    void fromRawValue_mapWithMissingInnerKeyYieldsEmptyList() {
        // Defensive: a Map without the expected inner-tag key shouldn't throw — the job's
        // mutual-exclusion logic treats an empty-prefix wrapper as "not provided".
        GroupPrefixes prefixes = (GroupPrefixes) GroupPrefixes.fromRawValue(
                Map.of("unrelated", "value"));

        assertThat(prefixes.getPrefixes()).isEmpty();
    }

    @Test
    void fromRawValue_mapWithUnexpectedInnerTypeYieldsEmptyList() {
        // Defensive: if a future Polarion release ever delivered a non-String/non-List value
        // under the inner key, we degrade to an empty wrapper rather than crashing during
        // parameter conversion.
        GroupPrefixes prefixes = (GroupPrefixes) GroupPrefixes.fromRawValue(
                Map.of(GroupPrefixes.GROUP_PREFIX_NAME, 42));

        assertThat(prefixes.getPrefixes()).isEmpty();
    }

    @Test
    void directConstructorPreservesOrderAndTakesDefensiveCopy() {
        // Production direct-set / test setup goes through the public constructor. Nulls must be
        // preserved at this layer (UserSynchronizationJobUnit init drops them); the wrapper must
        // not share storage with the caller's list.
        java.util.ArrayList<String> source = new java.util.ArrayList<>(Arrays.asList("KEEP_", null, ""));
        GroupPrefixes prefixes = new GroupPrefixes(source);

        assertThat(prefixes.getPrefixes()).containsExactly("KEEP_", null, "");
        source.add("LATE_ADDITION_");
        assertThat(prefixes.getPrefixes()).containsExactly("KEEP_", null, "");
    }
}
