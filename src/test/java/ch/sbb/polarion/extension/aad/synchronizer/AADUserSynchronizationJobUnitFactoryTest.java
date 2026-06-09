package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.aad.synchronizer.connector.IGraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.filter.Blacklist;
import ch.sbb.polarion.extension.aad.synchronizer.filter.Whitelist;
import ch.sbb.polarion.extension.aad.synchronizer.model.GroupPatterns;
import ch.sbb.polarion.extension.aad.synchronizer.model.GroupPrefixes;
import ch.sbb.polarion.extension.aad.synchronizer.utils.OSGiUtils;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobDescriptor.IJobParameter;
import com.polarion.platform.jobs.IJobUnit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ch.sbb.polarion.extension.aad.synchronizer.AADUserSynchronizationJobUnitFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;


class AADUserSynchronizationJobUnitFactoryTest {

    @Test
    void getJobDescriptor() {
        AADUserSynchronizationJobUnitFactory factory = new AADUserSynchronizationJobUnitFactory();
        IJobDescriptor descriptor = factory.getJobDescriptor(null);

        assertThat(descriptor.getLabel()).isEqualTo("Synchronization job");
        Stream.of(AUTHENTICATION_PROVIDER_ID, GROUP_PREFIXES, GROUP_PATTERNS,
                DRY_RUN, CHECK_LAST_SYNCHRONIZATION, VERBOSE_GRAPH_LOG,
                GRAPH_ID_FIELD, GRAPH_NAME_FIELD, GRAPH_EMAIL_FIELD).forEach(
                param -> assertThat(descriptor.getParameter(param)).isNotNull()
        );
    }

    @Test
    void groupPrefixesParameterAcceptsBothPolarionShapes() {
        // The convertValue lambda is what Polarion calls after marshalling the <groupPrefixes>
        // XML block. Polarion delivers two distinct shapes depending on how many children the
        // block has — both must round-trip into a populated GroupPrefixes wrapper.
        IJobParameter param = new AADUserSynchronizationJobUnitFactory()
                .getJobDescriptor(null)
                .getParameter(GROUP_PREFIXES);

        // Single child: <groupPrefixes><groupPrefix>X</groupPrefix></groupPrefixes>
        // Polarion#parseMapParameter delivers {groupPrefix=X}.
        assertThat(param.convertValue(Map.of(GroupPrefixes.GROUP_PREFIX_NAME, "ONLY_")))
                .isInstanceOfSatisfying(GroupPrefixes.class,
                        gp -> assertThat(gp.getPrefixes()).containsExactly("ONLY_"));

        // Multi-child same-tag: <groupPrefixes><groupPrefix>A_</groupPrefix><groupPrefix>B_</groupPrefix></groupPrefixes>
        // Polarion#parseListParameter delivers a bare List ["A_", "B_"]. Regression: this was
        // silently rejected when convertValue only accepted Map.
        assertThat(param.convertValue(List.of("A_", "B_")))
                .isInstanceOfSatisfying(GroupPrefixes.class,
                        gp -> assertThat(gp.getPrefixes()).containsExactly("A_", "B_"));

        // Anything else (blank string, scalar, null) → "not provided".
        assertThat(param.convertValue("not-a-shape")).isNull();
        assertThat(param.convertValue(null)).isNull();
    }

    @Test
    void groupPatternsParameterAcceptsBothPolarionShapes() {
        IJobParameter param = new AADUserSynchronizationJobUnitFactory()
                .getJobDescriptor(null)
                .getParameter(GROUP_PATTERNS);

        assertThat(param.convertValue(Map.of(GroupPatterns.GROUP_PATTERN_NAME, "^X_.*")))
                .isInstanceOfSatisfying(GroupPatterns.class,
                        gp -> assertThat(gp.getPatterns()).containsExactly("^X_.*"));

        assertThat(param.convertValue(List.of("^A_.*", "^B_.*")))
                .isInstanceOfSatisfying(GroupPatterns.class,
                        gp -> assertThat(gp.getPatterns()).containsExactly("^A_.*", "^B_.*"));

        assertThat(param.convertValue("not-a-shape")).isNull();
        assertThat(param.convertValue(null)).isNull();
    }

    @Test
    void whitelistParameterConvertsMapAndRejectsNonMap() {
        IJobParameter param = new AADUserSynchronizationJobUnitFactory()
                .getJobDescriptor(null)
                .getParameter(WHITELIST);

        assertThat(param.convertValue(Map.of("login", "value"))).isInstanceOf(Whitelist.class);
        assertThat(param.convertValue("not-a-map")).isNull();
    }

    @Test
    void blacklistParameterConvertsMapAndRejectsNonMap() {
        IJobParameter param = new AADUserSynchronizationJobUnitFactory()
                .getJobDescriptor(null)
                .getParameter(BLACKLIST);

        assertThat(param.convertValue(Map.of("login", "value"))).isInstanceOf(Blacklist.class);
        assertThat(param.convertValue("not-a-map")).isNull();
    }

    @Test
    void createJobUnitBuildsUserSynchronizationJobUnit() {
        try (MockedStatic<PlatformContext> platform = mockStatic(PlatformContext.class, RETURNS_DEEP_STUBS);
             MockedStatic<OSGiUtils> osgi = mockStatic(OSGiUtils.class)) {
            osgi.when(() -> OSGiUtils.lookupOSGiService(IGraphConnector.class)).thenReturn(null);

            IJobUnit jobUnit = new AADUserSynchronizationJobUnitFactory().createJobUnit("name");

            assertThat(jobUnit).isInstanceOf(UserSynchronizationJobUnit.class);
        }
    }

    @Test
    void getName() {
        AADUserSynchronizationJobUnitFactory factory = new AADUserSynchronizationJobUnitFactory();
        String name = factory.getName();

        assertThat(name).isEqualTo("aad_user_synchronization.job");
    }
}
