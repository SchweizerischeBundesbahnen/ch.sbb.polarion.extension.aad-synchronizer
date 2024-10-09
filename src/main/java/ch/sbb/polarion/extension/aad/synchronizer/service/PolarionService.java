package ch.sbb.polarion.extension.aad.synchronizer.service;

import ch.sbb.polarion.extension.aad.synchronizer.utils.CollectionUtils;
import ch.sbb.polarion.extension.generic.util.JobLogger;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IUser;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.security.ISecurityService;
import com.polarion.subterra.base.data.identification.IContextId;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toCollection;

@AllArgsConstructor
public class PolarionService implements IPolarionService {

    public static final String POLARION = "polarion";
    public static final String ADMIN = "admin";
    public static final String CAN_BE = "can be";
    public static final String WILL_BE = "will be";
    public static final String COULD_BE = "could be";
    public static final String HAVE_BEEN = "have been";
    public static final String HAS_BEEN = "has been";

    private final ISecurityService securityService;
    private final IProjectService projectService;
    private final boolean dryRun;

    @Override
    public void deletePolarionUsers(List<String> externalMembersIds) {
        Collection<String> polarionUsers = getAllPolarionUsers();
        JobLogger.getInstance().log("%d polarion user(s) have been found", polarionUsers.size());

        List<String> deletedUsersInAAD = polarionUsers.stream()
                .filter(userId -> !externalMembersIds.contains(userId))
                .toList();

        JobLogger.getInstance().log("%d polarion user(s) %s deleted", deletedUsersInAAD.size(), dryRun ? CAN_BE : WILL_BE);
        if (!deletedUsersInAAD.isEmpty()) {
            JobLogger.getInstance().log(CollectionUtils.usersAsString(deletedUsersInAAD));
        }

        IContextId[] contextIds = getContextIds();

        final List<String> deletedUsers = deleteUsers(externalMembersIds, deletedUsersInAAD, contextIds);
        JobLogger.getInstance().log("%d polarion user(s) %s deleted", deletedUsers.size(), dryRun ? COULD_BE : HAVE_BEEN);
    }

    @NotNull
    protected List<String> getAllPolarionUsers() {
        final List<String> allPolarionUsers = projectService.getUsers().stream()
                .map(IUser::getId)
                .collect(toCollection(ArrayList::new)); // List must be modifiable
        removeSystemUserFromList(allPolarionUsers);
        return allPolarionUsers;
    }

    private List<String> deleteUsers(List<String> externalMembersIds, List<String> deletedUsersInAAD, IContextId[] contextIds) {
        List<String> deletedUsers = new ArrayList<>();

        deletedUsersInAAD.forEach(user -> {
            JobLogger.getInstance().log("Processing user '%s'...", user);

            final List<String> caseInsensitiveDuplicatesInAAD = getDuplicates(externalMembersIds, user);
            final List<String> caseInsensitiveDuplicatesInPolarion = getDuplicates(getAllPolarionUsers(), user);

            deleteNonDuplicatedUsers(contextIds, user, caseInsensitiveDuplicatesInAAD, caseInsensitiveDuplicatesInPolarion, deletedUsers);
        });

        return deletedUsers;
    }

    private void deleteNonDuplicatedUsers(IContextId[] contextIds, String user, List<String> caseInsensitiveDuplicatesInAAD, List<String> caseInsensitiveDuplicatesInPolarion, List<String> deletedUsers) {
        if (caseInsensitiveDuplicatesInAAD.isEmpty() && caseInsensitiveDuplicatesInPolarion.isEmpty()) {
            removeContextRoles(contextIds, user);
            deletedUsers.add(user);
            JobLogger.getInstance().log("User '%s' %s deleted", user, dryRun ? CAN_BE : HAS_BEEN);
        } else {
            if (!caseInsensitiveDuplicatesInAAD.isEmpty()) {
                JobLogger.getInstance().log("User '%s' has been found in AzureAD as %s... SKIPPED!!!", user, CollectionUtils.usersAsString(caseInsensitiveDuplicatesInAAD));
            }
            if (!caseInsensitiveDuplicatesInPolarion.isEmpty()) {
                JobLogger.getInstance().log("User '%s' has been found in Polarion as %s... SKIPPED!!!", user, CollectionUtils.usersAsString(caseInsensitiveDuplicatesInPolarion));
            }
        }
    }

    private void removeContextRoles(IContextId[] contextIds, String user) {
        Map<IContextId, Collection<String>> mapRole = securityService.getContextRolesForUser(user, contextIds);
        mapRole.forEach((context, roles) ->
                roles.forEach(role -> {
                    if (!dryRun) {
                        securityService.removeContextRoleFromUser(user, role, context);
                    }

                    JobLogger.getInstance().log("Role '%s' %s deleted for user '%s' in project '%s'", role, (dryRun ? CAN_BE : HAS_BEEN), user, context.getContextName());
                })
        );
    }

    private static @NotNull List<String> getDuplicates(List<String> externalMembersIds, String user) {
        return externalMembersIds.stream()
                .filter(externalMembersId -> externalMembersId.equalsIgnoreCase(user) && !externalMembersId.equals(user))
                .toList();
    }

    private IContextId[] getContextIds() {
        IPObjectList<IProject> projects = projectService.getRootProjectGroup().getDeepContainedProjects();
        return projects.stream()
                .map(IPObject::getContextId)
                .toArray(IContextId[]::new);
    }

    private void removeSystemUserFromList(List<String> users) {
        users.remove(POLARION);
        users.remove(ADMIN);
    }

    @Override
    public void checkDuplicatedPolarionUsers() {
        Map<String, List<String>> duplicatedPolarionUsers = findCaseInsensitiveDuplicatedPolarionUsers();

        if (duplicatedPolarionUsers.isEmpty()) {
            JobLogger.getInstance().log("No duplicated users have been found");
        } else {
            duplicatedPolarionUsers.values()
                    .forEach(value -> JobLogger.getInstance().log("The following duplicated users have been found: %s", CollectionUtils.usersAsString(value)));
        }
    }

    @VisibleForTesting
    @NotNull
    protected Map<String, List<String>> findCaseInsensitiveDuplicatedPolarionUsers() {
        Collection<String> polarionUsers = getAllPolarionUsers();

        // map to store duplicates
        Map<String, List<String>> duplicatesMap = new HashMap<>();

        // iterate through each string and add it to the map
        for (String polarionUser : polarionUsers) {
            String lowerCasePolarionUser = polarionUser.toLowerCase();
            if (duplicatesMap.containsKey(lowerCasePolarionUser)) {
                duplicatesMap.get(lowerCasePolarionUser).add(polarionUser);
            } else {
                List<String> duplicatesList = new ArrayList<>();
                duplicatesList.add(polarionUser);
                duplicatesMap.put(lowerCasePolarionUser, duplicatesList);
            }
        }

        // filter the map to keep only the duplicates
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : duplicatesMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    @Override
    public void createPolarionUsers(List<String> externalMemberIds) {
        Collection<String> polarionUsers = getAllPolarionUsers();
        JobLogger.getInstance().log("%d polarion user(s) have been found", polarionUsers.size());

        List<String> createdUsersInAAD = externalMemberIds.stream()
                .filter(user -> !polarionUsers.contains(user))
                .toList();
        JobLogger.getInstance().log("%d polarion user(s) will be created", createdUsersInAAD.size());
        if (!createdUsersInAAD.isEmpty()) {
            JobLogger.getInstance().log(CollectionUtils.usersAsString(createdUsersInAAD));
        }

        List<String> createdUsers = createUsersInPolarion(createdUsersInAAD);
        JobLogger.getInstance().log("%d polarion user(s) %s created", createdUsers.size(), dryRun ? COULD_BE : HAVE_BEEN);
    }

    private List<String> createUsersInPolarion(List<String> createdUsersInAAD) {
        List<String> createdUsers = new ArrayList<>(createdUsersInAAD.size());

        createdUsersInAAD.forEach(user -> {
            Collection<String> polarionProjectUsers = getAllPolarionUsers();

            final boolean alreadyExists = polarionProjectUsers.stream() // very strange situation in current implementation, may be will ber removed in future
                    .anyMatch(polarionProjectUser -> polarionProjectUser.equals(user));

            final List<String> caseInsensitiveDuplicatesInPolarion = polarionProjectUsers.stream()
                    .filter(polarionProjectUser -> polarionProjectUser.equalsIgnoreCase(user))
                    .toList();

            createNotExistedNonDuplicatedUser(user, alreadyExists, caseInsensitiveDuplicatesInPolarion, createdUsers);
        });

        return createdUsers;
    }

    private void createNotExistedNonDuplicatedUser(String user, boolean alreadyExists, List<String> caseInsensitiveDuplicatesInPolarion, List<String> createdUsers) {
        if (!alreadyExists && caseInsensitiveDuplicatesInPolarion.isEmpty()) {
            if (!dryRun) {
                final IUser createdUser = projectService.createUser(user);
                if (createdUser != null) {
                    createdUser.save();
                }
            }
            createdUsers.add(user);
        }

        if (alreadyExists) {
            JobLogger.getInstance().log("User '%s' has been already created... nothing to do", user);
        } else if (!caseInsensitiveDuplicatesInPolarion.isEmpty()) {
            JobLogger.getInstance().log("User '%s' has been found as %s... SKIPPED!!!", user, CollectionUtils.usersAsString(caseInsensitiveDuplicatesInPolarion));
        } else {
            JobLogger.getInstance().log("User '%s' %s created", user, (dryRun ? CAN_BE : HAS_BEEN));
        }
    }

}
