package org.millburn.kioskserver.student;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.shaded.gson.Gson;
import lombok.extern.java.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.millburn.kioskserver.LoadedMemory;
import org.millburn.kioskserver.Records;
import org.millburn.kioskserver.WebSocketHandler;
import org.millburn.kioskserver.kiosk.AccessToken;
import org.millburn.kioskserver.relations.AccessRelationship;
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
 * The main class to respond to a student sign in/out request from any Kiosk apps
 *
 * @author Keming Fei, Alex Kolodkin
 */
@RestController
public class StudentController {
    /**
     * We find the student by studentId, then we lock the row completely, then we read the rest of
     * the attributes
     */
    private static final String DB_GET_STUDENT_BY_ID = "SELECT * FROM students WHERE id = ? FOR UPDATE;";
    /**
     * Update student's status by student ID
     */
    private static final String DB_SET_STUDENT_STATUS_BY_ID = "UPDATE students set status = ? WHERE id = ?;";
    /**
     * Logs student transaction into record
     */
    private static final String DB_LOG_TRANSACTION = "INSERT INTO `record` (`id`, `prev_status`, `new_status`, `date`, `kiosk_name`) VALUES (?, ?, ?, ?, ?);";

    /**
     * The object that will make using JDBC easier for us
     */
    private final JdbcTemplate jt;
    /**
     * The object that will make converting things into JSON easier for us
     */
    private final ObjectMapper om;
    private static final Logger LOG = LogManager.getLogger(StudentController.class);
    private final LoadedMemory lm;
    private final DateTimeFormatter dtf;


    /**
     * Initializes the controller, we inject JdbcTemplate to make using JDBC easier for us
     *
     * @param jt the object that will make using JDBC easier for us
     */
    @Autowired
    public StudentController(JdbcTemplate jt, LoadedMemory lm) {
        // ensure jt is not null
        Assert.notNull(jt, "JdbcTemplate must not be null");
        this.jt = jt;
        this.om = new ObjectMapper();
        this.lm = lm;
        this.dtf = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy");
    }

    /**
     * a method to extract a ResultSet into a Student
     *
     * @return a student or null if rs is empty
     */
    private static Student extractStudentData(ResultSet rs) throws SQLException {
        // checks if the ResultSet is empty
        if(!rs.isBeforeFirst()) {
            return null;
        }

        // Gets the first (and only) row returned
        rs.next();
        return new Student(rs.getInt("id"), rs.getString("name"), rs.getInt("privilege_type"),
                rs.getInt("status"));
    }

    /**
     * /checkin?access_token=...&kiosk_name=...&student_id=...
     * <p>
     * Runs a login attempt for a given student from a kiosk.
     *
     * @param accessToken access token held by kiosk
     * @param kioskName   name of the kiosk
     * @param studentId   id the student entered
     * @return either a JSON if every argument is correct, or a http status code if things went
     * wrong
     */
    @GetMapping("/checkin")
    public ResponseEntity<String> checkIn(@RequestParam(value = "access_token") String accessToken,
            @RequestParam(value = "kiosk_name") String kioskName,
            @RequestParam(value = "student_id") String studentId) throws JsonProcessingException {
        LOG.info("-----------------------------");
        LOG.info("New checkin transaction");
        LOG.info("Access Token: " + accessToken);
        LOG.info("Kiosk Name: " + kioskName);
        LOG.info("Student ID: " + studentId);

        AccessToken at = this.lm.getAccessTokens().getTokenInfo(accessToken);
        if(at == null) {
            LOG.info("Invalid access token, denied");
            return new ResponseEntity<>(HttpStatusCode.valueOf(403));
        }

        // If the token is disabled
        if(at.getAccess() < 0) {
            LOG.info("Access token is disabled");
            // TODO change this response so that the kiosk knows to disable itself
            return new ResponseEntity<>(HttpStatusCode.valueOf(403));
        }

        Student s;

        // Attempts to parse the student id into an integer and request it from the database
        try {
            s = this.jt.query(DB_GET_STUDENT_BY_ID, StudentController::extractStudentData,
                    Integer.parseInt(studentId));
        } catch(NumberFormatException e) {
            LOG.info("Invalid student id characters, id: " + studentId);
            // Unprocessable Entity, the student id format is not correct
            return new ResponseEntity<>(HttpStatusCode.valueOf(422));
        }

        CheckinResponse checkinResponse;
        int newStatus;

        /* Assumes that the kiosk has a valid access level, otherwise it will crash
            This should never be an issue as the adding kiosk function should make sure that the kiosk
            has a valid access level. */
        AccessRelationship ar = this.lm.getAccessRelations().getRelation(at.getAccess());
        boolean accepted = false;

        // Determines the response to the student sign in
        if(s != null && !ar.accept(s.getPrivilegeType())) {
            LOG.info("Student denied");
            checkinResponse = new CheckinResponse(CheckinResponse.NOT_APPROVED);
            // We don't change the status
            newStatus = s.getStatus();
        } else if(s != null) {
            LOG.info("Student partially accepted");
            checkinResponse = new CheckinResponse(CheckinResponse.APPROVED);
            // TODO implement better status'
            newStatus = s.getStatus() == 0 ? 1 : 0;
            accepted = true;
        } else {
            LOG.info("Invalid student id");
            // Creates "fake" student data for logging the invalid sign in
            checkinResponse = new CheckinResponse(CheckinResponse.INVALID_STUDENT_ID);
            // We know that parsing the id as an integer can't error because we would have caught it earlier
            s = new Student(Integer.parseInt(studentId), "", -1, -1);
            newStatus = -1;
        }

        // TODO prevent double write of name into csv file (CAN CHECK WITH STATUSES)
        ZonedDateTime currentTime = ZonedDateTime.now(ZoneId.systemDefault());
        // Checks if the kiosk is within its open time
        // Note: relations don't work if the time interval goes past midnight
        int startInMinutes = ar.startHour() * 60 + ar.startMinute();
        int endInMinutes = ar.endHour() * 60 + ar.endMinute();
        int timeInMinutes = currentTime.getHour() * 60 + currentTime.getMinute();
        if(startInMinutes <= timeInMinutes && endInMinutes >= timeInMinutes) {
            try {
                // Create new csv every day
                File csvOutputFile = new File(
                        "./" + ar.name() + "-" + currentTime.getMonthValue() + "." + currentTime.getDayOfMonth() + "."
                                + currentTime.getYear() + ".csv");

                csvOutputFile.createNewFile();

                // Write student down in if the student was accepted
                if (accepted) {
                    LOG.info("Student written down");
                    FileWriter myWriter = new FileWriter(csvOutputFile, true);
                    BufferedWriter bw = new BufferedWriter(myWriter);
                    bw.append(s.getName()).append(",").append(String.valueOf(s.getId()));
                    bw.newLine();
                    bw.close();
                }
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        } else if (ar.startHour() != -1){
            LOG.info("Sign in not in time interval");
            // If the sign in is not within the time interval of the kiosk
            // TODO add response that tells kiosk app to say that the sign in is outside of working time
             return new ResponseEntity<>("The sign in is outside of the kiosk active time!",
                    HttpStatusCode.valueOf(200));
        }

        // Update student status
        if (accepted) {
            LOG.info("Student fully accepted");
            this.jt.update(DB_SET_STUDENT_STATUS_BY_ID, newStatus, s.getId());
        }

        // Log the event to the database
        this.jt.update(DB_LOG_TRANSACTION, s.getId(), s.getStatus(), newStatus,
                this.dtf.format(currentTime), kioskName);
        List<Records> records = WebSocketHandler.getRecords();
        String json = new Gson().toJson(records);
        WebSocketHandler.broadcast(json);
        return new ResponseEntity<>(this.om.writeValueAsString(checkinResponse),
                HttpStatusCode.valueOf(200));
    }

    @GetMapping(value = "/getStudents", produces = "application/json")
    @CrossOrigin(origins = "*")
    public ResponseEntity<String> getStudents(
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
        String sql = "SELECT * FROM students" + Records.Filter.generateSQL(filters) + "ORDER BY id DESC LIMIT " + pageSizeInt + " OFFSET " + (pageInt * pageSizeInt);

        //noinspection SqlSourceToSinkFlow
        List<Map<String, Object>> rows = jt.queryForList(sql);
        List<Student> students = rows.stream().map(row -> new Student(
                (int) row.get("id"),
                (String) row.get("Name"),
                (int) row.get("privilege_type"),
                (int) row.get("status")
        )).toList();

        return new ResponseEntity<>(students.toString(), HttpStatusCode.valueOf(200));
    }

    @GetMapping(value = "setStudentStatus", produces = "application/json")
    @CrossOrigin(origins = "*")
    public ResponseEntity<String> setStudentStatus(@RequestParam String id, @RequestParam String status) {
        try {
            int idInt = Integer.parseInt(id);
            int statusInt = Integer.parseInt(status);
            if (statusInt < 0 || statusInt > 1) {
                LOG.error("Invalid status: " + status);
                return new ResponseEntity<>("{\"error\":\"Invalid status\"}", HttpStatusCode.valueOf(400));
            }
            jt.update("UPDATE students SET status = ? WHERE id = ?", statusInt, idInt);
            return new ResponseEntity<>("{\"success\":\"true\"}", HttpStatusCode.valueOf(200));
        } catch (NumberFormatException e) {
            LOG.error("Invalid id or status: " + id + ", " + status);
            return new ResponseEntity<>("{\"error\":\"Invalid id or status\"}", HttpStatusCode.valueOf(400));
        }
    }

    @GetMapping(value = "setStudentPrivilege", produces = "application/json")
    @CrossOrigin(origins = "*")
    public ResponseEntity<String> setStudentPrivilege(@RequestParam String id, @RequestParam String privilege) {
        try {
            int idInt = Integer.parseInt(id);
            int privilegeInt = Integer.parseInt(privilege);
            if (privilegeInt < 0 || privilegeInt > 2) { // TODO: Get rest of the privileges
                LOG.error("Invalid privilege: " + privilege);
                return new ResponseEntity<>("{\"error\":\"Invalid privilege\"}", HttpStatusCode.valueOf(400));
            }
            jt.update("UPDATE students SET privilege_type = ? WHERE id = ?", privilegeInt, idInt);
            return new ResponseEntity<>("{\"success\":\"true\"}", HttpStatusCode.valueOf(200));
        } catch (NumberFormatException e) {
            LOG.error("Invalid id or privilege: " + id + ", " + privilege);
            return new ResponseEntity<>("{\"error\":\"Invalid id or privilege\"}", HttpStatusCode.valueOf(400));
        }
    }
}