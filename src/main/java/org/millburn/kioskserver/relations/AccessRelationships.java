package org.millburn.kioskserver.relations;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.millburn.kioskserver.LoadedMemory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

/**
 * Stores all the access relations
 *
 * @author Alex Kolodkin, Keming Fei
 */
public class AccessRelationships {
    /**
     * All the access relationships
     */
    private final HashMap<Integer, AccessRelationship> relations;
    /**
     * The object that will make using JDBC easier for us
     */
    private final JdbcTemplate jt;
    private final LoadedMemory lm;

    public AccessRelationships(LoadedMemory lm, JdbcTemplate jt) {
        relations = new HashMap<>();
        this.lm = lm;
        this.jt = jt;
        this.getRelationsFromDatabase();
    }

    /**
     * Returns the relationship between an access level and a privilege
     *
     * @param level the access level
     * @return an AccessRelationship object
     */
    public AccessRelationship getRelation(int level) {
        return relations.get(level);
    }

    /**
     * Returns whether the access level exists
     *
     * @param level the access level
     * @return a boolean saying whether it exists
     */
    public boolean containsRelation(int level) {
        return relations.containsKey(level);
    }

    /**
     * Loads the set of relations from the database into memory
     */
    public void getRelationsFromDatabase() {
        relations.clear();

        // Loads all the relations from the database
        SqlRowSet rs = jt.queryForRowSet("SELECT * FROM relations");

        while(rs.next()) {
            AccessRelationship ar = new AccessRelationship(rs.getString(1), rs.getInt(2), rs.getInt(3),
                    rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8));
            relations.put(ar.accessLevel(), ar);
        }
    }

    /**
     * Loads a set of new relations from a file
     *
     * @param path the path to the file
     * @return whether the upload was a success or not
     */
    public boolean uploadNewRelations(File path) {
        // Verify that the file path is valid
        JSONParser parser = new JSONParser();
        Object obj;
        try {
            obj = parser.parse(new FileReader(path));
        } catch(FileNotFoundException | ParseException e) {
            return false;
        }
        JSONArray relations = (JSONArray)((JSONObject)obj).get("AccessLevels");

        jt.execute("DROP TABLE relations;");
        jt.execute("CREATE TABLE relations ("
                + "name varchar(255),"
                + "access_level int,"
                + "privilege int,"
                + "direction int,"
                + "start_hour int signed,"
                + "start_minute int signed,"
                + "end_hour int signed,"
                + "end_minute int signed,"
                + "PRIMARY KEY (access_level)"
                + ");");

        for(Object r : relations) {
            JSONObject relation = (JSONObject)r;
            String name = relation.getAsString("Name");
            int accessLevel = relation.getAsNumber("AccessLevel").intValue();
            int privilege = relation.getAsNumber("RequiredPermissionLevel").intValue();
            int direction = relation.getAsNumber("PermissionLevelsAllowed").intValue();
            int startHour = relation.getAsNumber("TimeStartHour").intValue();
            int startMinute = relation.getAsNumber("TimeStartMinute").intValue();
            int endHour = relation.getAsNumber("TimeEndHour").intValue();
            int endMinute = relation.getAsNumber("TimeEndMinute").intValue();
            jt.execute(
                    "INSERT INTO `relations` (`name`, `access_level`, `privilege`, `direction`, `start_hour`, `start_minute`, `end_hour`, `end_minute`) VALUES ("
                            + name + ", " + accessLevel + ", " + privilege + ", " + direction + ", "
                            + startHour + ", " + startMinute + ", " + endHour + ", " + endMinute
                            + ");");
        }

        getRelationsFromDatabase();
        this.lm.reloadAccessTokens();
        return true;
    }

}
