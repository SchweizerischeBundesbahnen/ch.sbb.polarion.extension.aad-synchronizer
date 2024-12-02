package ch.sbb.polarion.extension.aad.synchronizer.filter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Blacklist extends FilterList {

    public Blacklist(@Nullable Map<?, ?> parameters) {
        super(parameters);
    }

    @Override
    public @NotNull List<String> filterMembers(@NotNull List<String> memberIds) {
        if ((filter == null || filter.isEmpty()) && (accounts == null || accounts.isEmpty())) {
            return Collections.unmodifiableList(memberIds);
        }

        return memberIds.stream()
                .filter(this::matchesRegexFilter)
                .filter(this::containedInAccountList)
                .toList();
    }

    private boolean matchesRegexFilter(String memberId) {
        return filter == null || filter.isEmpty() || !memberId.matches(filter);
    }

    private boolean containedInAccountList(String memberId) {
        return accounts == null || accounts.isEmpty() || !accounts.contains(memberId);
    }

}
