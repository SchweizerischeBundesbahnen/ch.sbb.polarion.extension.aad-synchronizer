package ch.sbb.polarion.extension.aad.synchronizer.filter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Whitelist extends FilterList {

    public Whitelist(@Nullable Map<?, ?> parameters) {
        super(parameters);
    }

    @Override
    public @NotNull List<String> filterMembers(@NotNull List<String> memberIds) {
        boolean hasFilter = filter != null && !filter.isEmpty();
        boolean hasAccounts = accounts != null && !accounts.isEmpty();

        if (!hasFilter && !hasAccounts) {
            return Collections.unmodifiableList(memberIds);
        }

        Stream<String> stream = Stream.empty();

        if (hasFilter) {
            stream = Stream.concat(
                    stream,
                    memberIds.stream().filter(memberId -> memberId.matches(filter))
            );
        }

        if (hasAccounts) {
            stream = Stream.concat(
                    stream,
                    memberIds.stream().filter(accounts::contains)
            );
        }

        return stream.distinct().toList();
    }

}
