package ch.sbb.polarion.extension.aad.synchronizer.model;

import ch.sbb.polarion.extension.aad.synchronizer.utils.JsonListParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupResponseWrapper {
    public static final String ID = "id";

    private List<Group> value;

    public static @NotNull GroupResponseWrapper fromJsonList(@NotNull String json, @NotNull Map<String, String> fieldsMapping) {
        return JsonListParser.parseList(json, fieldsMapping, GroupResponseWrapper.class, Group.class);
    }
}
