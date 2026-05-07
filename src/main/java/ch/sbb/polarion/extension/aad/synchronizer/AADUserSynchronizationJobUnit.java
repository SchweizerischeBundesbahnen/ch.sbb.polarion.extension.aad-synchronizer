package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.aad.synchronizer.filter.Blacklist;
import ch.sbb.polarion.extension.aad.synchronizer.filter.Whitelist;
import ch.sbb.polarion.extension.aad.synchronizer.model.GroupPatterns;
import ch.sbb.polarion.extension.aad.synchronizer.model.GroupPrefixes;
import com.polarion.platform.jobs.IJobUnit;

public interface AADUserSynchronizationJobUnit extends IJobUnit {

    String JOB_NAME = "aad_user_synchronization.job";

    void setAuthenticationProviderId(String authenticationProviderId);

    void setGraphIdField(String graphIdField);

    void setGraphNameField(String graphNameField);

    void setGraphEmailField(String graphEmailField);

    /**
     * @deprecated Legacy single-prefix form, kept only for backwards compatibility with existing
     * job configurations. Use {@link #setGroupPrefixes(GroupPrefixes)} instead. Scheduled for
     * removal in the next major release.
     */
    @Deprecated(forRemoval = true)
    void setGroupPrefix(String groupPrefix);

    void setGroupPrefixes(GroupPrefixes groupPrefixes);

    void setGroupPatterns(GroupPatterns groupPatterns);

    void setWhitelist(Whitelist whitelist);

    void setBlacklist(Blacklist blacklist);

    void setDryRun(Boolean dryRun);

    void setCheckLastSynchronization(Boolean checkLastSynchronization);
}
