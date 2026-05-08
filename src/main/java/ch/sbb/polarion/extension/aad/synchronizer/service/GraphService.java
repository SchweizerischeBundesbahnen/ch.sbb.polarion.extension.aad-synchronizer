package ch.sbb.polarion.extension.aad.synchronizer.service;

import ch.sbb.polarion.extension.aad.synchronizer.connector.IGraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.exception.NotFoundException;
import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;
import ch.sbb.polarion.extension.aad.synchronizer.utils.TimeUtils;
import ch.sbb.polarion.extension.generic.util.JobLogger;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@AllArgsConstructor
public class GraphService implements IGraphService {
    private final IGraphConnector graphConnector;

    /**
     * Resolves the union of AAD members covered by the configured selectors.
     *
     * <p>Selector semantics — each acts as an independent OR-source:
     * <ul>
     *   <li>{@code groupPrefixes} only — one Graph request with a server-side
     *       {@code startswith($filter)}.</li>
     *   <li>{@code groupPatterns} only — one unfiltered Graph request, regex applied client-side.</li>
     *   <li>both — both requests are issued and the resulting groups are unioned by id. This lets
     *       a configuration include a broad prefix family <em>plus</em> outliers captured only by a
     *       pattern, without forcing every group to satisfy both selectors.</li>
     * </ul>
     *
     * <p>The job init layer (see {@code UserSynchronizationJobUnit#initializationCheck}) guarantees
     * at least one selector is non-empty, so the "both empty" branch is unreachable here.
     */
    @Override
    public Set<String> getAadMemberIds(@NotNull List<String> groupPrefixes, @NotNull List<Pattern> groupPatterns) {
        boolean hasPrefixes = !groupPrefixes.isEmpty();
        boolean hasPatterns = !groupPatterns.isEmpty();

        Map<String, Group> selected = new LinkedHashMap<>();

        if (hasPrefixes) {
            collectGroupsByPrefixes(groupPrefixes, hasPatterns, selected);
        }
        if (hasPatterns) {
            collectGroupsByPatterns(groupPatterns, selected);
        }

        if (selected.isEmpty()) {
            // Every configured selector yielded nothing — fail loudly rather than handing an
            // empty set to PolarionService.deletePolarionUsers (which would wipe every
            // AAD-managed account). Tailor the message to what was actually configured so an
            // operator on the patterns-only path doesn't see the confusing "groupPrefixes/..."
            // wording.
            throw new NotFoundException("No AAD groups matched the configured " + describeSelectors(hasPrefixes, hasPatterns) + ".");
        }
        if (hasPrefixes && hasPatterns) {
            JobLogger.getInstance().log("%d unique group(s) after union of groupPrefixes and groupPatterns",
                    selected.size());
        }

        Set<String> members = getAadMemberIds(selected.values());
        JobLogger.getInstance().log("%d unique member(s) in AAD for Polarion have been found", members.size());
        return members;
    }

    private static String describeSelectors(boolean hasPrefixes, boolean hasPatterns) {
        if (hasPrefixes && hasPatterns) {
            return "groupPrefixes/groupPatterns";
        }
        return hasPrefixes ? "groupPrefixes" : "groupPatterns";
    }

    private void collectGroupsByPrefixes(List<String> groupPrefixes, boolean hasPatterns, Map<String, Group> sink) {
        try {
            List<Group> byPrefix = graphConnector.getGroups(groupPrefixes);
            JobLogger.getInstance().log("%d group(s) returned by Microsoft Graph for prefixes %s",
                    byPrefix.size(), groupPrefixes);
            byPrefix.forEach(g -> sink.putIfAbsent(g.getId(), g));
        } catch (NotFoundException e) {
            // GraphConnector#getGroups throws NotFoundException when the prefix filter matched
            // zero groups. When patterns are also configured, the union may still be non-empty,
            // so swallow here and let the bottom-of-method check decide. When patterns aren't
            // configured the swallow would mask the only signal, so re-throw.
            if (!hasPatterns) {
                throw e;
            }
            JobLogger.getInstance().log(
                    "groupPrefixes %s matched no AAD groups; relying on groupPatterns to populate the result.",
                    groupPrefixes);
        }
    }

    private void collectGroupsByPatterns(List<Pattern> groupPatterns, Map<String, Group> sink) {
        // No $filter — let Graph stream every group; patterns are evaluated locally below. Any
        // server-side narrowing belongs to groupPrefixes; mixing the two filters in one request
        // would defeat the union semantics.
        List<Group> all = graphConnector.getGroups(List.of());
        JobLogger.getInstance().log("%d group(s) returned by Microsoft Graph (unfiltered fetch for patterns)",
                all.size());
        List<Group> matched = all.stream()
                .filter(g -> g.getDisplayName() != null
                        && groupPatterns.stream().anyMatch(p -> p.matcher(g.getDisplayName()).matches()))
                .toList();
        JobLogger.getInstance().log("%d group(s) matched %d pattern(s)", matched.size(), groupPatterns.size());
        matched.forEach(g -> sink.putIfAbsent(g.getId(), g));
    }

    private Set<String> getAadMemberIds(Collection<Group> groups) {
        List<String> groupKeys = new ArrayList<>(groups.size());
        for (Group g : groups) {
            groupKeys.add(g.getId());
        }
        return groupKeys.stream()
                .flatMap(key -> graphConnector.getMembers(key).stream())
                .map(Member::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public void checkLastSynchronization() {
        OrganizationData data = graphConnector.getOrganizationData();

        if (TimeUtils.isExpiredAADSync(data.getOnPremisesLastSyncDateTime())) {
            JobLogger.getInstance().log("The last sync with the on-premises directory is older then 1 hour");
        }
    }
}
