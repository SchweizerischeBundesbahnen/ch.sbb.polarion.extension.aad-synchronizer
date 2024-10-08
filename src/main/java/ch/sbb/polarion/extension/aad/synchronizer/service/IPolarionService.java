package ch.sbb.polarion.extension.aad.synchronizer.service;

import java.util.List;

public interface IPolarionService {
    void deletePolarionUsers(List<String> externalMembersIds);

    void checkDuplicatedPolarionUsers();

    void createPolarionUsers(List<String> externalMemberIds);
}
