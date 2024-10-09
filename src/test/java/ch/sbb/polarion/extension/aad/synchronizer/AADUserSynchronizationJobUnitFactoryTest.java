package ch.sbb.polarion.extension.aad.synchronizer;

import com.polarion.platform.jobs.IJobDescriptor;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static ch.sbb.polarion.extension.aad.synchronizer.AADUserSynchronizationJobUnitFactory.*;
import static org.assertj.core.api.Assertions.assertThat;


class AADUserSynchronizationJobUnitFactoryTest {

    @Test
    void getJobDescriptor() {
        AADUserSynchronizationJobUnitFactory factory = new AADUserSynchronizationJobUnitFactory();
        IJobDescriptor descriptor = factory.getJobDescriptor(null);

        assertThat(descriptor.getLabel()).isEqualTo("Synchronization job");
        Stream.of(GRAPH_API_TOKEN_URL, GRAPH_API_CLIENT_ID, GRAPH_API_CLIENT_SECRET, GRAPH_API_SCOPE, GROUP_PREFIX, DRY_RUN, CHECK_LAST_SYNCHRONIZATION).forEach(
                param -> assertThat(descriptor.getParameter(param)).isNotNull()
        );
    }

    @Test
    void getName() {
        AADUserSynchronizationJobUnitFactory factory = new AADUserSynchronizationJobUnitFactory();
        String name = factory.getName();

        assertThat(name).isEqualTo("aad_user_synchronization.job");
    }
}
