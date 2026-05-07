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
 */
@Data
public class GroupPatterns {
    public static final String GROUP_PATTERN_NAME = "groupPattern";

    private final @NotNull List<String> patterns;

    @SuppressWarnings("unchecked")
    public GroupPatterns(@Nullable Map<?, ?> parameters) {
        if (parameters == null) {
            this.patterns = Collections.emptyList();
            return;
        }
        // Defensive copy via ArrayList for the List branch: tolerates null entries (which
        // List.copyOf rejects) and decouples from the source list's mutability.
        // UserSynchronizationJobUnit drops null/blank entries during init.
        this.patterns = switch (parameters.get(GROUP_PATTERN_NAME)) {
            case List<?> list -> Collections.unmodifiableList(new ArrayList<>((List<String>) list));
            case String s -> List.of(s);
            case null, default -> Collections.emptyList();
        };
    }
}
