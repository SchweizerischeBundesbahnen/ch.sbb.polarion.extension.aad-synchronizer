package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.aad.synchronizer.filter.Blacklist;
import ch.sbb.polarion.extension.aad.synchronizer.filter.Whitelist;
import com.polarion.platform.jobs.IJobUnit;

public interface AADUserSynchronizationJobUnit extends IJobUnit {

    String JOB_NAME = "aad_user_synchronization.job";

    void setAuthenticationProviderId(String authenticationProviderId);

    void setGroupPrefix(String groupPrefix);

    void setWhitelist(Whitelist whitelist);

    void setBlacklist(Blacklist blacklist);

    void setDryRun(Boolean dryRun);

    void setCheckLastSynchronization(Boolean checkLastSynchronization);
}
