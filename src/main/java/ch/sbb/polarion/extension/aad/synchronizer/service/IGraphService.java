package ch.sbb.polarion.extension.aad.synchronizer.service;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Pattern;

public interface IGraphService {
    Set<String> getAadMemberIds(@Nullable String groupPrefix, @Nullable Pattern groupPattern);

    void checkLastSynchronization();
}
