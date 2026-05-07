package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.aad.synchronizer.model.GroupPatterns;
import ch.sbb.polarion.extension.aad.synchronizer.model.GroupPrefixes;
import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobDescriptor.IJobParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ch.sbb.polarion.extension.aad.synchronizer.AADUserSynchronizationJobUnitFactory.*;
import static org.assertj.core.api.Assertions.assertThat;


class AADUserSynchronizationJobUnitFactoryTest {

    @Test
    void getJobDescriptor() {
        AADUserSynchronizationJobUnitFactory factory = new AADUserSynchronizationJobUnitFactory();
        IJobDescriptor descriptor = factory.getJobDescriptor(null);

        assertThat(descriptor.getLabel()).isEqualTo("Synchronization job");
        Stream.of(AUTHENTICATION_PROVIDER_ID, GROUP_PREFIX, GROUP_PREFIXES, GROUP_PATTERNS,
                DRY_RUN, CHECK_LAST_SYNCHRONIZATION,
                GRAPH_ID_FIELD, GRAPH_NAME_FIELD, GRAPH_EMAIL_FIELD).forEach(
                param -> assertThat(descriptor.getParameter(param)).isNotNull()
        );
    }

    @Test
    void groupPrefixesParameterConvertsMapToWrapperAndRejectsOtherShapes() {
        // The convertValue lambda is what Polarion calls when it has finished marshalling the
        // <groupPrefixes> XML block into a Map. Anything other than a Map (e.g. a stray String)
        // must round-trip to null so the job's mutual-exclusion logic treats the parameter as
        // "not provided" rather than throwing.
        IJobParameter param = new AADUserSynchronizationJobUnitFactory()
                .getJobDescriptor(null)
                .getParameter(GROUP_PREFIXES);

        Object converted = param.convertValue(Map.of(GroupPrefixes.GROUP_PREFIX_NAME, List.of("A_", "B_")));
        assertThat(converted)
                .isInstanceOfSatisfying(GroupPrefixes.class,
                        gp -> assertThat(gp.getPrefixes()).containsExactly("A_", "B_"));

        assertThat(param.convertValue("not-a-map")).isNull();
        assertThat(param.convertValue(null)).isNull();
    }

    @Test
    void groupPatternsParameterConvertsMapToWrapperAndRejectsOtherShapes() {
        IJobParameter param = new AADUserSynchronizationJobUnitFactory()
                .getJobDescriptor(null)
                .getParameter(GROUP_PATTERNS);

        Object converted = param.convertValue(Map.of(GroupPatterns.GROUP_PATTERN_NAME, "^X_.*"));
        assertThat(converted)
                .isInstanceOfSatisfying(GroupPatterns.class,
                        gp -> assertThat(gp.getPatterns()).containsExactly("^X_.*"));

        assertThat(param.convertValue("not-a-map")).isNull();
        assertThat(param.convertValue(null)).isNull();
    }

    @Test
    void getName() {
        AADUserSynchronizationJobUnitFactory factory = new AADUserSynchronizationJobUnitFactory();
        String name = factory.getName();

        assertThat(name).isEqualTo("aad_user_synchronization.job");
    }
}
