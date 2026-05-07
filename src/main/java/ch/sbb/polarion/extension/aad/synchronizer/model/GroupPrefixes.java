package ch.sbb.polarion.extension.aad.synchronizer.model;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Job-parameter wrapper for the {@code <groupPrefixes>} list. Mirrors the {@link
 * ch.sbb.polarion.extension.aad.synchronizer.filter.Whitelist} pattern: Polarion delivers the
 * nested XML as a {@link Map} keyed by the inner element name (here, {@code groupPrefix}). Single
 * children come through as a bare {@link String}; multiple children come through as a {@link
 * List}. Both shapes collapse to a {@code List<String>} here so callers can ignore the
 * difference.
 */
@Data
public class GroupPrefixes {
    public static final String GROUP_PREFIX_NAME = "groupPrefix";

    private final @NotNull List<String> prefixes;

    @SuppressWarnings("unchecked")
    public GroupPrefixes(@Nullable Map<?, ?> parameters) {
        if (parameters == null) {
            this.prefixes = Collections.emptyList();
            return;
        }
        Object value = parameters.get(GROUP_PREFIX_NAME);
        if (value instanceof List<?> list) {
            // Defensive copy via ArrayList: tolerates null entries (which List.copyOf rejects)
            // and decouples from the source list's mutability. UserSynchronizationJobUnit drops
            // null/blank entries during init.
            this.prefixes = Collections.unmodifiableList(new ArrayList<>((List<String>) list));
        } else if (value instanceof String s) {
            this.prefixes = List.of(s);
        } else {
            this.prefixes = Collections.emptyList();
        }
    }
}
