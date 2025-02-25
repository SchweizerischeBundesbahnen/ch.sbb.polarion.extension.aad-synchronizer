package ch.sbb.polarion.extension.aad.synchronizer.service;

import ch.sbb.polarion.extension.aad.synchronizer.connector.IGraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;
import ch.sbb.polarion.extension.aad.synchronizer.utils.TimeUtils;
import ch.sbb.polarion.extension.generic.util.JobLogger;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
public class GraphService implements IGraphService {
    private final IGraphConnector graphConnector;

    @Override
    public Set<String> getAadMemberIds(String groupPrefix) {
        List<Group> groups = graphConnector.getGroups(groupPrefix);
        JobLogger.getInstance().log("%d group(s) have been found with prefix '%s'", groups.size(), groupPrefix);

        Set<String> members = getAadMemberIds(groups);

        JobLogger.getInstance().log("%d unique member(s) in AAD for Polarion have been found", members.size());
        return members;
    }

    private Set<String> getAadMemberIds(List<Group> groups) {
        List<String> groupKeys = groups.stream()
                .map(Group::getId)
                .toList();

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
