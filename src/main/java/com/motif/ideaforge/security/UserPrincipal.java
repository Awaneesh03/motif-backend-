package com.motif.ideaforge.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * User principal for security context
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {
    private UUID id;
    private String email;

    public UserPrincipal(String id, String email) {
        this.id = UUID.fromString(id);
        this.email = email;
    }
}
