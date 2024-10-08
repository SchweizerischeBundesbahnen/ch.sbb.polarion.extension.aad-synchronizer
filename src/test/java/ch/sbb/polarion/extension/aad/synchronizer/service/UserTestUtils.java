package ch.sbb.polarion.extension.aad.synchronizer.service;

import com.polarion.alm.projects.model.IUser;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.persistence.spi.PObjectList;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@UtilityClass
public class UserTestUtils {

    public static IPObjectList<IUser> getProjectServiceUsers(List<IUser> pobjects) {
        return (IPObjectList<IUser>) new PObjectList(null, pobjects, true);
    }

    @NotNull
    public static List<IUser> getPolarionUsers(String... usernames) {
        @NotNull List<IUser> result = new ArrayList<>(usernames.length);

        for (String username : usernames) {
            final IUser user = mock(IUser.class);
            when(user.getId()).thenReturn(username);
            result.add(user);
        }

        return result;
    }

    public static int getUsersFromLogMessage(String message) {
        Matcher matcher = Pattern.compile("\\d+").matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        throw new IllegalArgumentException("nothing has been found");
    }
}
