package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;

import java.util.List;


public interface IGraphConnector {

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
