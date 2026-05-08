package ch.sbb.polarion.extension.aad.synchronizer.model;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Job-parameter wrapper for the {@code <groupPrefixes>} list.
 *
 * <p>Polarion's {@code com.polarion.platform.jobs.internal.service.scheduler.ScheduleDataHandler}
 * delivers different shapes for the {@code <groupPrefixes>} element depending on its child
 * structure (see {@code parseParameter} in that class — the algorithm is: empty element → text;
 * children with distinct tag names → {@code Map}; two-or-more children sharing a tag name →
 * {@code List}, with the inner element name discarded). The shapes that reach
 * {@link #fromRawValue(Object)} are therefore:
 *
 * <ul>
 *   <li>{@code Map} — the element has exactly one child, e.g.
 *       {@code <groupPrefixes><groupPrefix>X</groupPrefix></groupPrefixes>} arrives as
 *       {@code {groupPrefix=X}}.</li>
 *   <li>{@code List} — the element has 2+ children with the same tag name, e.g.
 *       {@code <groupPrefixes><groupPrefix>X</groupPrefix><groupPrefix>Y</groupPrefix></groupPrefixes>}
 *       arrives as {@code [X, Y]} (the inner tag name is dropped). Treating this shape as
 *       "parameter not provided" is the bug fixed in this class — see issue history.</li>
 *   <li>{@code String} (blank) or {@code null} — empty/absent element.</li>
 * </ul>
 */
@Data
public class GroupPrefixes {
    public static final String GROUP_PREFIX_NAME = "groupPrefix";

    private final @NotNull List<String> prefixes;

    /**
     * Direct constructor for production code and tests that already have the prefix list in hand.
     * Polarion's marshaller goes through {@link #fromRawValue(Object)} instead.
     *
     * <p>Defensive {@link ArrayList} copy — tolerates {@code null} entries (which
     * {@link List#copyOf} rejects) and decouples from the source list's mutability. Null/blank
     * entries are dropped later in {@code UserSynchronizationJobUnit} init.
     */
    public GroupPrefixes(@NotNull List<String> prefixes) {
        this.prefixes = Collections.unmodifiableList(new ArrayList<>(prefixes));
    }

    /**
     * @param rawValue the value Polarion's job-parameter marshaller hands to {@code convertValue}
     * @return a wrapper for any recognized Polarion shape, or {@code null} when the parameter
     *         should be treated as not provided (absent, blank, or wrong type)
     */
    @SuppressWarnings("unchecked")
    public static @Nullable GroupPrefixes fromRawValue(@Nullable Object rawValue) {
        return switch (rawValue) {
            case Map<?, ?> map -> fromMap(map);
            case List<?> list -> new GroupPrefixes((List<String>) list);
            case null, default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static GroupPrefixes fromMap(Map<?, ?> map) {
        return switch (map.get(GROUP_PREFIX_NAME)) {
            case List<?> list -> new GroupPrefixes((List<String>) list);
            case String s -> new GroupPrefixes(List.of(s));
            case null, default -> new GroupPrefixes(Collections.emptyList());
        };
    }
}
