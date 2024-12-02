package ch.sbb.polarion.extension.aad.synchronizer.filter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Whitelist extends FilterList {
    public Whitelist(@Nullable Map<?, ?> parameters) {
        super(parameters);
    }

    @Override
    public @NotNull List<String> filterMembers(@NotNull List<String> memberIds) {
        List<String> filtered1 = null;
        if (filter != null && !filter.isEmpty()) {
            filtered1 = memberIds.stream()
                    .filter(memberId -> memberId.matches(filter))
                    .toList();
        }

        List<String> filtered2 = null;
        if (accounts != null && !accounts.isEmpty()) {
            filtered2 = memberIds.stream()
                    .filter(accounts::contains)
                    .toList();
        }

        if (filtered1 == null && filtered2 == null) {
            return memberIds;
        } else if (filtered1 == null) {
            return filtered2;
        } else if (filtered2 == null) {
            return filtered1;
        } else {
            return Stream.concat(filtered1.stream(), filtered2.stream()).toList();
        }
    }

}
