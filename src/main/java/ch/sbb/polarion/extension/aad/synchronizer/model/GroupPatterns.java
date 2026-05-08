package ch.sbb.polarion.extension.aad.synchronizer.model;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Job-parameter wrapper for the {@code <groupPatterns>} list. Holds raw pattern strings; regex
 * compilation is intentionally deferred to the job's initialization step so {@code
 * PatternSyntaxException} can be reported as a configuration error against the specific offending
 * pattern instead of failing here on the parameter conversion path.
 *
 * <p>Mirrors {@link GroupPrefixes} for the Polarion-shape dispatch in {@link #fromRawValue(Object)}
 * — see that class's javadoc for the {@code Map}/{@code List}/blank shapes Polarion can deliver.
 */
@Data
public class GroupPatterns {
    public static final String GROUP_PATTERN_NAME = "groupPattern";

    private final @NotNull List<String> patterns;

    /**
     * Direct constructor for production code and tests that already have the pattern list in
     * hand. Polarion's marshaller goes through {@link #fromRawValue(Object)} instead.
     *
     * <p>Defensive {@link ArrayList} copy — tolerates {@code null} entries (which
     * {@link List#copyOf} rejects) and decouples from the source list's mutability. Null/blank
     * entries are dropped later in {@code UserSynchronizationJobUnit} init.
     */
    public GroupPatterns(@NotNull List<String> patterns) {
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
    }

    /**
     * @param rawValue the value Polarion's job-parameter marshaller hands to {@code convertValue}
     * @return a wrapper for any recognized Polarion shape, or {@code null} when the parameter
     *         should be treated as not provided (absent, blank, or wrong type)
     */
    @SuppressWarnings("unchecked")
    public static @Nullable GroupPatterns fromRawValue(@Nullable Object rawValue) {
        return switch (rawValue) {
            case Map<?, ?> map -> fromMap(map);
            case List<?> list -> new GroupPatterns((List<String>) list);
            case null, default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static GroupPatterns fromMap(Map<?, ?> map) {
        return switch (map.get(GROUP_PATTERN_NAME)) {
            case List<?> list -> new GroupPatterns((List<String>) list);
            case String s -> new GroupPatterns(List.of(s));
            case null, default -> new GroupPatterns(Collections.emptyList());
        };
    }
}
