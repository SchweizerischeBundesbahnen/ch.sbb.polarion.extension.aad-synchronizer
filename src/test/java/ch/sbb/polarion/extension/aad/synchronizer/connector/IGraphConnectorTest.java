package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IGraphConnectorTest {

    @Test
    void defaultRequestCountIsZeroForImplementationsThatDoNotTrackRequests() {
        // External IGraphConnector implementations registered via OSGi are not required to
        // implement the request-count API. The interface provides a default of 0 which the job
        // logger treats as "unavailable" (suppresses the summary line).
        IGraphConnector connector = new IGraphConnector() {
            @Override
            public List<Group> getGroups(String groupPrefix) {
                return List.of();
            }

            @Override
            public List<Member> getMembers(String key) {
                return List.of();
            }

            @Override
            public OrganizationData getOrganizationData() {
                return null;
            }
        };

        assertThat(connector.getRequestCount()).isZero();
    }
}
