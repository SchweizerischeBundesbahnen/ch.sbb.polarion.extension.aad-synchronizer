package ch.sbb.polarion.extension.aad.synchronizer.filter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class MemberFilter {
    private final @Nullable Whitelist whitelist;
    private final @Nullable Blacklist blacklist;

    public MemberFilter(@Nullable Whitelist whitelist, @Nullable Blacklist blacklist) {
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }

    public @NotNull List<String> filterMembers(@NotNull List<String> memberIds) {
        List<String> filteredMemberIds = memberIds;
        if (whitelist != null) {
            filteredMemberIds = whitelist.filterMembers(filteredMemberIds);
        }
        if (blacklist != null) {
            filteredMemberIds = blacklist.filterMembers(filteredMemberIds);
        }
        return filteredMemberIds;
    }

}
