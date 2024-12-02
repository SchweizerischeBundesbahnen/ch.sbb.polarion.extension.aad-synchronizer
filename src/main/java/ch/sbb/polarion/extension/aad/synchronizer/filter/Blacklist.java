package ch.sbb.polarion.extension.aad.synchronizer.filter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Blacklist extends FilterList {
    public Blacklist(@Nullable Map<?, ?> parameters) {
        super(parameters);
    }

    @Override
    public @NotNull List<String> filterMembers(@NotNull List<String> memberIds) {
        List<String> result = new ArrayList<>(memberIds);

        if (filter != null && !filter.isEmpty()) {
            result = result.stream()
                    .filter(memberId -> !memberId.matches(filter))
                    .toList();
        }

        if (accounts != null && !accounts.isEmpty()) {
            result = result.stream()
                    .filter(memberId -> !accounts.contains(memberId))
                    .toList();
        }

        return Collections.unmodifiableList(result);
    }

}
