package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.model.MemberResponseWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-field Microsoft Graph property name overrides for the Polarion user attribute mapping.
 * When a field is non-null it replaces the corresponding value from {@code authentication.xml}
 * {@code <mapping>} verbatim when the synchronizer builds {@code $select} for {@code /users/{id}}
 * — no {@code extension_...} auto-expansion is applied to the overridden value.
 *
 * <p>The canonical constructor normalizes blank input to {@code null} so downstream code can rely
 * on {@code != null} as the "override is set" check.</p>
 */
public record GraphFieldOverrides(String idField, String nameField, String emailField) {

    public static final GraphFieldOverrides EMPTY = new GraphFieldOverrides(null, null, null);

    public GraphFieldOverrides {
        idField = normalize(idField);
        nameField = normalize(nameField);
        emailField = normalize(emailField);
    }

    /**
     * Returns the overrides keyed by mapping field role ({@link MemberResponseWrapper#ID},
     * {@link MemberResponseWrapper#NAME}, {@link MemberResponseWrapper#EMAIL}). Entries are only
     * present for fields that have a non-null override, so callers can fall back to their default
     * value with a simple {@code map.get(key) != null} check.
     */
    Map<String, String> asMap() {
        Map<String, String> map = new HashMap<>();
        if (idField != null) {
            map.put(MemberResponseWrapper.ID, idField);
        }
        if (nameField != null) {
            map.put(MemberResponseWrapper.NAME, nameField);
        }
        if (emailField != null) {
            map.put(MemberResponseWrapper.EMAIL, emailField);
        }
        return Map.copyOf(map);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
