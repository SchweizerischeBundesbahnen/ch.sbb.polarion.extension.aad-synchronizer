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
        if (!isFilterProvided() && !isAccountListProvided()) {
            return Collections.unmodifiableList(memberIds);
        }

        return memberIds.stream()
                .filter(memberId -> !rejectedByRegex(memberId))
                .filter(memberId -> !rejectedByAccountList(memberId))
                .toList();
    }

    private boolean isFilterProvided() {
        return filter != null && !filter.isEmpty();
    }

    private boolean isAccountListProvided() {
        return accounts != null && !accounts.isEmpty();
    }

    private boolean rejectedByRegex(String memberId) {
        return isFilterProvided() && memberId.matches(filter);
    }

    private boolean rejectedByAccountList(String memberId) {
        return isAccountListProvided() && accounts.contains(memberId);
    }

}
