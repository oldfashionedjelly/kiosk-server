package org.millburn.kioskserver.kiosk;

import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.millburn.kioskserver.relations.AccessRelationships;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

/**
 * Contains all the active kiosk tokens in memory
 *
 * @author Alex Kolodkin, Keming Fei
 */
public class AccessTokens {
    /**
     * Stores all the active kiosk tokens
     */
    private final HashMap<String, AccessToken> tokens;
    /**
     * The object that will make using JDBC easier for us
     */
    private JdbcTemplate jt;
    private static final Logger LOG = LogManager.getLogger(AccessTokens.class);


    public AccessTokens(AccessRelationships accessRelationships, JdbcTemplate jt) {
        // Loads all the tokens from the database
        tokens = new HashMap<>();
        this.jt = jt;
        SqlRowSet rs = jt.queryForRowSet("SELECT * FROM access_tokens");

        while(rs.next()) {
            AccessToken token = new AccessToken(rs.getString(1), rs.getInt(2));
            // Since access relations are already loaded we check if the access level exists in the relations
            if(accessRelationships.containsRelation(token.getAccess())) {
                tokens.put(token.getAccessToken(), token);
                continue;
            }

            LOG.info("Ignoring token with invalid access level: " + token.getAccessToken());
        }
    }

    /**
     * Adds a new token to the list of active tokens
     *
     * @param uuid the token of the kiosk
     * @param accessLevel the access level of the kiosk
     * @return whether the token was added
     */
    public boolean addToken(String uuid, int accessLevel) {
        if(!tokens.containsKey(uuid)) {
            tokens.put(uuid, new AccessToken(uuid, accessLevel));
            return true;
        }

        return false;
    }

    /**
     * Removes a token from the list of active tokens
     *
     * @param uuid the token of the kiosk
     * @return whether the token was removed
     */
    public boolean removeToken(String uuid) {
        if(!tokens.containsKey(uuid)) {
            return false;
        }
        tokens.remove(uuid);
        return true;
    }

    /**
     * Returns an access token object from a token
     *
     * @param token the token of the kiosk
     * @return the token object, or null if the token does not exist
     */
    public AccessToken getTokenInfo(String token) {
        return tokens.get(token);
    }
}
