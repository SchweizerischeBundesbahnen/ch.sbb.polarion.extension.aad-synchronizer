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
public class MemberResponseWrapper {
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String EMAIL = "email";

    private List<Member> value;

    public static @NotNull MemberResponseWrapper fromJsonList(@NotNull String json, @NotNull Map<String, String> fieldsMapping) {
        return JsonListParser.parseList(json, fieldsMapping, MemberResponseWrapper.class, Member.class);
    }
}
