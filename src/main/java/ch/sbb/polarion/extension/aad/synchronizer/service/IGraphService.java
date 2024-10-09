package ch.sbb.polarion.extension.aad.synchronizer.service;

import java.util.Set;

public interface IGraphService {
    Set<String> getAadMemberIds(String groupPrefix);

    void checkLastSynchronization();
}
