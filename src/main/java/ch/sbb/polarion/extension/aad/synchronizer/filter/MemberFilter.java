package ch.sbb.polarion.extension.aad.synchronizer.filter;

import ch.sbb.polarion.extension.generic.util.JobLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class MemberFilter {
    private final @Nullable Whitelist whitelist;
    private final @Nullable Blacklist blacklist;

    public MemberFilter(@Nullable Whitelist whitelist, @Nullable Blacklist blacklist) {
        this.whitelist = whitelist;
        this.blacklist = blacklist;

        JobLogger.getInstance().log("whitelist: %s", whitelist);
        JobLogger.getInstance().log("blacklist: %s", blacklist);
    }

    public @NotNull List<String> filterMembers(@NotNull List<String> memberIds) {
        List<String> filteredMemberIds = memberIds;
        if (whitelist != null) {
            JobLogger.getInstance().log("Whitelist filtering of provided %d members...".formatted(filteredMemberIds.size()));
            filteredMemberIds = whitelist.filterMembers(filteredMemberIds);
            JobLogger.getInstance().log("Whitelist filtering resulted in %d members".formatted(filteredMemberIds.size()));
        }
        if (blacklist != null) {
            JobLogger.getInstance().log("Blacklist filtering of provided %d members...".formatted(filteredMemberIds.size()));
            filteredMemberIds = blacklist.filterMembers(filteredMemberIds);
            JobLogger.getInstance().log("Blacklist filtering resulted in %d members".formatted(filteredMemberIds.size()));
        }
        return filteredMemberIds;
    }

}
