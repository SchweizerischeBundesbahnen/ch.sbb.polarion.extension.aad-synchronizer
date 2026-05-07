package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public interface IGraphConnector {

    /**
     * Resolves AAD groups whose {@code displayName} matches any of the supplied literal prefixes.
     * Implementations are expected to translate this into a single Microsoft Graph request with
     * an OR'd {@code startswith(displayName, ...)} filter. An empty (or {@code null}) list means
     * "fetch all groups" — useful when the caller intends to filter further client-side via
     * {@code groupPatterns}.
     *
     * <p>The default implementation falls back to a per-prefix loop and de-duplicates by group
     * id, preserving backwards compatibility with external {@link IGraphConnector} implementations
     * (registered via OSGi) that only override {@link #getGroups(String)}. Our own
     * {@code GraphConnector} overrides this method to issue a single batched request.</p>
     */
    default List<Group> getGroups(List<String> groupPrefixes) {
        if (groupPrefixes == null || groupPrefixes.isEmpty()) {
            return getGroups((String) null);
        }
        Map<String, Group> deduped = new LinkedHashMap<>();
        for (String prefix : groupPrefixes) {
            for (Group g : getGroups(prefix)) {
                deduped.putIfAbsent(g.getId(), g);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    List<Group> getGroups(String groupPrefix);

    List<Member> getMembers(String key);

    OrganizationData getOrganizationData();

    /**
     * Number of logical Microsoft Graph calls made by this connector so far. Monotonically
     * non-decreasing over the connector's lifetime. Exposed so the synchronization job can log
     * the total Graph load per run and so integration tests can verify batch-vs-per-user call
     * patterns. External {@link IGraphConnector} implementations that don't track requests
     * should return {@code 0}; the job log treats {@code 0} as "unavailable".
     */
    default int getRequestCount() {
        return 0;
    }
}
