package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;

import java.util.List;


public interface IGraphConnector {

    List<Group> getGroups(String groupPrefix);

    List<Member> getMembers(String key);

    OrganizationData getOrganizationData();
}
