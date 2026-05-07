package ch.sbb.polarion.extension.aad.synchronizer.service;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public interface IGraphService {
    Set<String> getAadMemberIds(@NotNull List<String> groupPrefixes, @NotNull List<Pattern> groupPatterns);

    void checkLastSynchronization();
}
