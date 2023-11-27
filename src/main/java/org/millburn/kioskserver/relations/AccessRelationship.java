package org.millburn.kioskserver.relations;

/**
 * A model storing the different relationships between student privileges and kiosk access levels
 *
 * @author Alex Kolodkin
 */
public record AccessRelationship(String name, int accessLevel, int privilege, int direction, int startHour,
                                 int startMinute, int endHour, int endMinute) {
    /**
     * Determines whether to accept a student with a certain privilege or not based on the relation
     *
     * @param studentPrivilege the student privilege level
     * @return whether the student is accepted or not
     */
    public boolean accept(int studentPrivilege) {
        return switch(direction) {
            // Student privilege must the same as the required one
            case 0 -> studentPrivilege == privilege;
            // Student privilege can be greater than the required one
            case 1 -> studentPrivilege >= privilege;
            // Student privilege can be lower than the required one
            case -1 -> studentPrivilege <= privilege;
            default -> false;
        };
    }
}
