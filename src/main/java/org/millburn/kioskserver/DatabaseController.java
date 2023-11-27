package org.millburn.kioskserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.millburn.kioskserver.kiosk.AccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The main class to respond to database changes
 *
 * @author Alex Kolodkin, Keming Fei
 */
@RestController
public class DatabaseController {
    // TODO add ability to change student privilege
    /**
     * The object that will make using JDBC easier for us
     */
    private final JdbcTemplate jt;
    /**
     * The default captcha value
     */
    private static final int DEFAULT_CAPTCHA = -11111;
    /**
     * The captcha required for the user to enter when uploading new student IDs
     */
    private int captcha = DEFAULT_CAPTCHA;

    private final LoadedMemory lm;

    private static final Logger LOG = LogManager.getLogger(DatabaseController.class);

    /**
     * Initializes the controller, we inject JdbcTemplate to make using JDBC easier for us
     *
     * @param jt the object that will make using JDBC easier for us
     */
    @Autowired
    public DatabaseController(JdbcTemplate jt, LoadedMemory lm) {
        // Ensure jt is not null
        Assert.notNull(jt, "JdbcTemplate must not be null");
        this.jt = jt;
        this.lm = lm;
    }

    /**
     * /removeKiosk?token=...
     * Removes an active kiosk from the list of valid kiosks
     *
     * @param token the token belonging to the Kiosk that will have its access removed
     * @return a text response telling the user that the kiosk token was removed
     */
    @GetMapping("/removeKiosk")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> removeKiosk(@RequestParam(value = "token") String token) {
        // Deletes token from memory if it exists
        if (!this.lm.getAccessTokens().removeToken(token)) {
            // Token does not exist
            return new ResponseEntity<>(HttpStatusCode.valueOf(422));
        }

        // Deletes the token from the database
        jt.update("DELETE FROM access_tokens WHERE access_token='?';", token);
        return new ResponseEntity<>("Successfully Deleted the Token", HttpStatusCode.valueOf(200));
    }

    /**
     * /disableKiosk?token=...
     * <p>
     * Temporarily disables a token for a kiosk from being used
     *
     * @param token the token belonging to the Kiosk that will be temporarily disabled
     * @return a text response telling the user that the kiosk was disabled
     */
    @GetMapping("/disableKiosk")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> disableKiosk(@RequestParam(value = "token") String token) {
        // Update memory value of token
        AccessToken kioskToken = this.lm.getAccessTokens().getTokenInfo(token);

        // Checks to make sure the token exists
        if (kioskToken == null) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(422));
        }
        // Checks to make sure that we are not enabling the token
        if (kioskToken.getAccess() < 0) {
            return new ResponseEntity<>("This token is already disabled!", HttpStatusCode.valueOf(200));
        }
        kioskToken.setAccess(-kioskToken.getAccess());

        // Update database value of token
        jt.update("UPDATE `access_tokens` SET `access` = '?' WHERE (`access_token` = '?');", kioskToken.getAccess(), token);
        return new ResponseEntity<>("Successfully Disabled the Token", HttpStatusCode.valueOf(200));
    }

    /**
     * /enableKiosk?token=...
     * <p>
     * Enables a token for a kiosk after it was disabled
     *
     * @param token the token belonging to the Kiosk that will be enabled
     * @return a text response telling the user that the kiosk was enabled
     */
    @GetMapping("/enableKiosk")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> enableKiosk(@RequestParam(value = "token") String token) {
        // Update memory value of token
        AccessToken kioskToken = this.lm.getAccessTokens().getTokenInfo(token);

        // Checks to make sure the token exists
        if (kioskToken == null) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(422));
        }
        // Checks to make sure that we are not disabling the token
        if (kioskToken.getAccess() > 0) {
            return new ResponseEntity<>("This token is already enabled!", HttpStatusCode.valueOf(200));
        }
        kioskToken.setAccess(-kioskToken.getAccess());

        // Update database value of token
        jt.update("UPDATE `access_tokens` SET `access` = '?' WHERE (`access_token` = '?');", kioskToken.getAccess(), token);

        return new ResponseEntity<>("Successfully Enabled the Token", HttpStatusCode.valueOf(200));
    }

    /**
     * /addKiosk?access_type=...
     * <p>
     * Creates a token for a new kiosk
     *
     * @param accessType the API level of the kiosk
     * @return a text response telling the user the kiosk token
     */
    @GetMapping("/addKiosk")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> addKiosk(@RequestParam(value = "access_type") String accessType) {
        // Confirms whether the access type is an integer
        int accessLevel;
        try {
            accessLevel = Integer.parseInt(accessType);
        } catch (NumberFormatException e) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(422));
        }

        // If the access level does not exist
        if (!this.lm.getAccessRelations().containsRelation(accessLevel)) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(422));
        }

        // Generates a new token for the kiosk and adds it to the database
        UUID kioskID = UUID.randomUUID();
        jt.update("INSERT INTO `access_tokens` (`access_token`, `access`) VALUES ('?', '?');", kioskID, accessLevel);

        // Adds the token to memory
        this.lm.getAccessTokens().addToken(kioskID.toString(), accessLevel);
        return new ResponseEntity<>("New Kiosk Token: " + kioskID, HttpStatusCode.valueOf(200));
    }

    /**
     * /loadIDs?path=...&confirmation=...
     * <p>
     * Bulk loads all the student ids from file into the database
     *
     * @param path         the path of the (.csv) file from which the student ids will be extracted
     * @param confirmation the captcha required to confirm the change of student ids
     * @return a text response telling the user what to do and what the server is doing
     */
    @GetMapping("/loadIDs")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> loadIDs(@RequestParam(value = "path") String path,
                                          @RequestParam(value = "confirmation", required = false, defaultValue = ""
                                                  + DEFAULT_CAPTCHA) String confirmation) {
        // Gets the file and makes sure that it is valid
        BufferedReader br;
        try {
            File file = new File(path);
            FileReader fr = new FileReader(file);
            br = new BufferedReader(fr);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(422));
        }

        // Checks if there is a current captcha
        if (captcha == DEFAULT_CAPTCHA) {
            // Generates a random number between 10000 and 99999 (5 numbers long)
            Random r = new Random();
            captcha = 10000 + r.nextInt(90000);

            return new ResponseEntity<>(
                    "!WARNING! This will delete all students and their IDs from the current "
                            + "database and cannot be reversed.<br>To confirm this operation, enter the "
                            + "following captcha into the url as the confirmation parameter.<br><br> "
                            + captcha
                            + "<br><br> This would look like adding \"&confirmation=`captcha`\""
                            + " to the end of the url.",
                    HttpStatusCode.valueOf(200));
        }
        int enteredCaptcha;
        // Extracts the captcha that the user entered
        try {
            enteredCaptcha = Integer.parseInt(confirmation);
        } catch (NumberFormatException e) {
            return new ResponseEntity<>(HttpStatusCode.valueOf(422));
        }

        // Upload new student IDs from given file to the database
        if (enteredCaptcha == captcha) {
            // Clears the database of students
            jt.execute("DROP TABLE students;");
            jt.execute("CREATE TABLE students ("
                    + "id int,"
                    + "Name varchar(255),"
                    + "privilege_type int,"
                    + "status int,"
                    + "PRIMARY KEY (id)"
                    + ");");

            // Reads in new students from file
            try {
                String line;
                String[] tempArr;
                while ((line = br.readLine()) != null) {
                    tempArr = line.split(",");
                    if (Objects.equals(tempArr[0], "Student #")) {
                        continue;
                    }

                    jt.update("INSERT INTO `students` (`id`, `Name`, `privilege_type`, `status`) VALUES ('?', '?', '?', '1');", tempArr[0], tempArr[2] + " " + tempArr[1], tempArr[3]);
                }
                br.close();
            } catch (IOException e) {
                return new ResponseEntity<>(HttpStatusCode.valueOf(500));
            }
            captcha = DEFAULT_CAPTCHA;
            return new ResponseEntity<>("Resetting database and uploading new IDs.",
                    HttpStatusCode.valueOf(200));
        }

        // Reset the captcha if wrong captcha entered.
        captcha = DEFAULT_CAPTCHA;
        return new ResponseEntity<>("Incorrect captcha! Resetting captcha.",
                HttpStatusCode.valueOf(200));
    }

    @GetMapping(value = "/records", produces = "application/json")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<String> records(@RequestParam(required = false, defaultValue = "") List<String> filterBy, @RequestParam(required = false, defaultValue = "") List<String> filterValue) {
        // returns count
        if (filterBy.size() != filterValue.size()) {
            LOG.error("FilterBy and FilterValue size mismatch");
            return new ResponseEntity<>("{\"error\":\"FilterBy and FilterValue size mismatch\"}", HttpStatusCode.valueOf(400));
        }
        Records.Filter[] filters = Records.Filter.parseFilters(filterBy.toArray(new String[0]), filterValue.toArray(new String[0]), new String[filterBy.size()]);
        //noinspection SqlResolve (SQL is valid, IntelliJ just doesn't properly parse the concatenation of the SQL statements and returned string from the function)
        String sql = "SELECT COUNT(*) FROM record" + Records.Filter.generateSQL(filters);
        //noinspection SqlSourceToSinkFlow
        int count = jt.queryForObject(sql, Integer.class);
        return new ResponseEntity<>("{\"count\":" + count + "}", HttpStatusCode.valueOf(200));
    }

    @GetMapping(value = "/listRecords", produces = "application/json")
    @CrossOrigin(origins = "http://localhost:3000")
//    @CrossOrigin(origins = "*")
    public ResponseEntity<String> listRecords(
            @RequestParam(required = false, defaultValue = "0") String page,
            @RequestParam(required = false, defaultValue = "5") String pageSize,
            @RequestParam(required = false, defaultValue = "") List<String> filterBy,
            @RequestParam(required = false, defaultValue = "") List<String> filterValue,
            @RequestParam(required = false, defaultValue = "") List<String> filterComparator
    ) {
        int pageInt;
        int pageSizeInt;
        try {
            pageInt = Integer.parseInt(page);
            pageSizeInt = Integer.parseInt(pageSize);
        } catch (NumberFormatException e) {
            LOG.error("Invalid page or pageSize: " + page + ", " + pageSize);
            return new ResponseEntity<>("{\"error\":\"Invalid page or pageSize\"}", HttpStatusCode.valueOf(400));
        }
        if (pageInt < 0 || pageSizeInt < 0) {
            LOG.error("Invalid page or pageSize: " + page + ", " + pageSize);
            return new ResponseEntity<>("{\"error\":\"Invalid page or pageSize\"}", HttpStatusCode.valueOf(400));
        }
        if (filterBy.size() != filterValue.size()) {
            LOG.error("FilterBy and FilterValue size mismatch");
            return new ResponseEntity<>("{\"error\":\"FilterBy and FilterValue size mismatch\"}", HttpStatusCode.valueOf(400));
        }
        if (filterComparator.size() != filterValue.size()) {
            // fill end with equals
            for (int i = filterComparator.size(); i < filterValue.size(); i++) {
                filterComparator.add("equals");
            }
            LOG.warn("FilterComparator and FilterValue size mismatch, filling with equals");
        }
        Records.Filter[] filters = Records.Filter.parseFilters(filterBy.toArray(new String[0]), filterValue.toArray(new String[0]), filterComparator.toArray(new String[0]));

        //noinspection SqlResolve (SQL is valid, IntelliJ just doesn't properly parse the concatenation of the SQL statements and returned string from the function)
        String sql = "SELECT * FROM record" + Records.Filter.generateSQL(filters) + "ORDER BY num DESC LIMIT " + pageSizeInt + " OFFSET " + (pageInt * pageSizeInt);

        //noinspection SqlSourceToSinkFlow
        List<Map<String, Object>> rows = jt.queryForList(sql);
        List<Records> records = rows.stream().map(row -> new Records(
                (int) row.get("num"),
                (int) row.get("id"),
                (int) row.get("prev_status"),
                (int) row.get("new_status"),
                row.get("date").toString(),
                row.get("kiosk_name").toString()
        )).toList();

        return new ResponseEntity<>(records.toString(), HttpStatusCode.valueOf(200));

        // return temporary error code
//        return new ResponseEntity<>("{\"error\":\"Not implemented\"}", HttpStatusCode.valueOf(501));
    }
}