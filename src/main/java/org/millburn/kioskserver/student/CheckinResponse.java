package org.millburn.kioskserver.student;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An object to be converted to JSON that represents server's respond to whether the student is
 * approvalStatus
 *
 * @author Keming Fei, Alex Kolodkin
 */
public record CheckinResponse(@JsonProperty("approval_status") Byte approvalStatus) {
    public static final Byte NOT_APPROVED = 0;
    public static final Byte APPROVED = 1;
    public static final Byte INVALID_STUDENT_ID = 2;
    public static final Byte LATE_STUDENT = 3;
}
