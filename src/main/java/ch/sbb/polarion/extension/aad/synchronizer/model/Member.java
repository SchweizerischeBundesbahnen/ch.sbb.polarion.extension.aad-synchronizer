package ch.sbb.polarion.extension.aad.synchronizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Member {
    private String id;
    private String name;
    private String email;
}
