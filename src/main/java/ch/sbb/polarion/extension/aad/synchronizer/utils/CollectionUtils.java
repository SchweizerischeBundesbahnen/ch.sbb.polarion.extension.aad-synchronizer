package ch.sbb.polarion.extension.aad.synchronizer.utils;

import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class CollectionUtils {

    public static String usersAsString(List<String> users) {
        if (users == null || users.isEmpty()) {
            return "";
        }
        if (users.size() == 1) {
            return "'" + users.get(0) + "'";
        }
        return "['" + String.join("', '", users) + "']";
    }
}
