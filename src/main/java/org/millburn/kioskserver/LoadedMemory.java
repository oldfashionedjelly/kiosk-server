package org.millburn.kioskserver;

import lombok.Getter;
import org.millburn.kioskserver.kiosk.AccessTokens;
import org.millburn.kioskserver.relations.AccessRelationships;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * A class representing data that we want to keep in memory to reduce the total number of database
 * queries
 *
 * @author Alex Kolodkin, Keming Fei
 */
@Component
@Scope("singleton")
public class LoadedMemory {
    @Getter
    private final AccessRelationships accessRelations;
    @Getter
    private AccessTokens accessTokens;
    private final JdbcTemplate jt;

    @Autowired
    public LoadedMemory(JdbcTemplate jt) {
        this.jt = jt;
        Assert.notNull(jt, "JdbcTemplate must not be null");
        accessRelations = new AccessRelationships(this, this.jt);
        accessTokens = new AccessTokens(accessRelations, this.jt);
    }

    public void reloadAccessTokens() {
        accessTokens = new AccessTokens(accessRelations, this.jt);
    }
}
