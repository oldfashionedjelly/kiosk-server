package org.millburn.kioskserver.kiosk;

import lombok.Getter;
import lombok.Setter;

/**
 * a model representing a valid access token from the database
 *
 * @author Keming Fei
 */
@Getter
public class AccessToken {
    /**
     * mirrors access_token from the database
     */
    private final String accessToken;
    /**
     * mirrors access from the database
     */
    @Setter
    private Integer access;

    /**
     * Initializes this object
     */
    public AccessToken(String accessToken, Integer access) {
        this.accessToken = accessToken;
        this.access = access;
    }
}
