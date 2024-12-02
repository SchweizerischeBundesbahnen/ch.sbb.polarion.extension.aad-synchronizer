package ch.sbb.polarion.extension.aad.synchronizer.filter;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Data
public abstract class FilterList {
    public static final String FILTER_NAME = "filter";
    public static final String ACCOUNTS_NAME = "accounts";

    protected final @Nullable String filter;
    protected final @Nullable List<String> accounts;

    @SuppressWarnings("unchecked")
    protected FilterList(@Nullable Map<?, ?> parameters) {
        if (parameters != null) {
            this.filter = (String) parameters.get(FILTER_NAME);
            this.accounts = (List<String>) parameters.get(ACCOUNTS_NAME);
        } else {
            this.filter = null;
            this.accounts = null;
        }
    }

    public abstract @NotNull List<String> filterMembers(@NotNull List<String> memberIds);

    @Override
    public String toString() {
        return String.format(
                "filter = %s, accounts = %s",
                filter != null ? "'" + filter + "'" : "null",
                accounts != null ? "'" + accounts + "'" : "null"
        );
    }
}
