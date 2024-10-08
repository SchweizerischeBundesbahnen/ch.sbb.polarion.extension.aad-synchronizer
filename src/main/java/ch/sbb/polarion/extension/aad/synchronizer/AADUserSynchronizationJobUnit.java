package ch.sbb.polarion.extension.aad.synchronizer;

import com.polarion.platform.jobs.IJobUnit;

public interface AADUserSynchronizationJobUnit extends IJobUnit {

    String JOB_NAME = "aad_user_synchronization.job";

    void setGraphApiTokenUrl(String graphApiTokenUrl);

    void setGraphApiClientId(String graphApiClientId);

    void setGraphApiClientSecret(String graphApiClientSecret);

    void setGraphApiScope(String graphApiScope);

    void setGroupPrefix(String groupPrefix);

    void setDryRun(Boolean dryRun);

    void setCheckLastSynchronization(Boolean checkLastSynchronization);
}
