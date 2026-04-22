package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.aad.synchronizer.filter.Blacklist;
import ch.sbb.polarion.extension.aad.synchronizer.filter.Whitelist;
import com.polarion.platform.jobs.IJobDescriptor;
import com.polarion.platform.jobs.IJobUnit;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.spi.BasicJobDescriptor;
import com.polarion.platform.jobs.spi.JobParameterPrimitiveType;
import com.polarion.platform.jobs.spi.SimpleJobParameter;

import java.util.Map;

public class AADUserSynchronizationJobUnitFactory implements IJobUnitFactory {

    public static final String AUTHENTICATION_PROVIDER_ID = "authenticationProviderId";
    public static final String GRAPH_ID_FIELD = "graphIdField";
    public static final String GRAPH_NAME_FIELD = "graphNameField";
    public static final String GRAPH_EMAIL_FIELD = "graphEmailField";
    public static final String GROUP_PREFIX = "groupPrefix";
    public static final String WHITELIST = "whitelist";
    public static final String BLACKLIST = "blacklist";
    public static final String DRY_RUN = "dryRun";
    public static final String CHECK_LAST_SYNCHRONIZATION = "checkLastSynchronization";

    @Override
    public IJobUnit createJobUnit(String name) {
        return new UserSynchronizationJobUnit(name, this);
    }

    @Override
    public IJobDescriptor getJobDescriptor(IJobUnit jobUnit) {
        BasicJobDescriptor desc = new BasicJobDescriptor("Synchronization job", jobUnit);
        JobParameterPrimitiveType stringType = new JobParameterPrimitiveType(
                "String",
                String.class);
        JobParameterPrimitiveType booleanType = new JobParameterPrimitiveType(
                "Boolean",
                Boolean.class);

        desc.addParameter(new SimpleJobParameter(
                desc.getRootParameterGroup(),
                AUTHENTICATION_PROVIDER_ID,
                "Authentication Provider ID",
                stringType));

        desc.addParameter(new SimpleJobParameter(
                desc.getRootParameterGroup(),
                GRAPH_ID_FIELD,
                "Overrides the Microsoft Graph user property used as the Polarion user identifier. When unset, the <id> from authentication.xml <mapping> is used. Set this when the OAuth2 claim name differs from the Graph property name (e.g. claim 'mycustomid' vs Graph 'onPremisesSamAccountName'), or to reference a directory schema extension by its fully-qualified name 'extension_<appIdNoDashes>_<field>'.",
                stringType).setRequired(false));

        desc.addParameter(new SimpleJobParameter(
                desc.getRootParameterGroup(),
                GRAPH_NAME_FIELD,
                "Overrides the Microsoft Graph user property used as the display name. When unset, the <name> from authentication.xml <mapping> is used.",
                stringType).setRequired(false));

        desc.addParameter(new SimpleJobParameter(
                desc.getRootParameterGroup(),
                GRAPH_EMAIL_FIELD,
                "Overrides the Microsoft Graph user property used as the email. When unset, the <email> from authentication.xml <mapping> is used.",
                stringType).setRequired(false));

        desc.addParameter(new SimpleJobParameter(
                desc.getRootParameterGroup(),
                GROUP_PREFIX,
                "Group prefix in Azure AD",
                stringType).setRequired(false));

        desc.addParameter(new SimpleJobParameter(
                desc.getRootParameterGroup(),
                DRY_RUN,
                "Flag for the run job in dry mode",
                booleanType).setRequired(false));

        desc.addParameter(
                new SimpleJobParameter(
                        desc.getRootParameterGroup(),
                        WHITELIST,
                        "Only the matching accounts will be synchronised",
                        new JobParameterPrimitiveType(
                                "Whitelist",
                                Whitelist.class
                        )
                ) {
                    @Override
                    public Object convertValue(Object value) {
                        return value instanceof Map<?, ?> map ? new Whitelist(map) : null;
                    }
                }.setRequired(false)
        );
        desc.addParameter(
                new SimpleJobParameter(
                        desc.getRootParameterGroup(),
                        BLACKLIST,
                        "Matching accounts will be ignored",
                        new JobParameterPrimitiveType(
                                "Blacklist",
                                Blacklist.class
                        )
                ) {
                    @Override
                    public Object convertValue(Object value) {
                        return value instanceof Map<?, ?> map ? new Blacklist(map) : null;
                    }
                }.setRequired(false)
        );

        desc.addParameter(new SimpleJobParameter(
                desc.getRootParameterGroup(),
                CHECK_LAST_SYNCHRONIZATION,
                "Enable/disable checking for the last AAD synchronization",
                booleanType).setRequired(false));

        return desc;
    }

    @Override
    public String getName() {
        return AADUserSynchronizationJobUnit.JOB_NAME;
    }
}
