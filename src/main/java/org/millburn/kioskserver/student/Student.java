package org.millburn.kioskserver.student;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.millburn.kioskserver.Records;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A model for individual students from the database
 *
 * @author Keming Fei, Alex Kolodkin
 */
@Getter
public class Student {
    /**
     * mirrors id from the database
     */
    private final Integer id;
    /**
     * mirrors name from the database
     */
    @Setter
    private String name;
    /**
     * mirrors privilege_type from the database
     */
    @Setter
    private Integer privilegeType;
    /**
     * mirrors status from the database
     */
    @Setter
    private Integer status;

    /**
     * Initializes this object
     */
    public Student(Integer id, String name, Integer privilegeType, Integer status) {
        this.id = id;
        this.name = name;
        this.privilegeType = privilegeType;
        this.status = status;
    }

    @Override
    public String toString() {
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"privilege_type\":" + privilegeType + ",\"status\":" + status + "}";
    }

    public enum FilterType {
        ALL, ID, NAME, PRIVILEGE_TYPE, STATUS
    }

    public enum FilterComparator {
        EQUALS, NOT_EQUALS, LESS_THAN, GREATER_THAN, LESS_THAN_OR_EQUAL_TO, GREATER_THAN_OR_EQUAL_TO;

        @Override
        public String toString() {
            switch (this) {
                case EQUALS -> {
                    return "=";
                }
                case NOT_EQUALS -> {
                    return "!=";
                }
                case LESS_THAN -> {
                    return "<";
                }
                case GREATER_THAN -> {
                    return ">";
                }
                case LESS_THAN_OR_EQUAL_TO -> {
                    return "<=";
                }
                case GREATER_THAN_OR_EQUAL_TO -> {
                    return ">=";
                }
                default -> {
                    return "";
                }
            }
        }
    }

    // TODO: Add OR filters
    // TODO: Add proper date filter
    @Getter
    public static class Filter {
        private final FilterType type;
        private Object value;
        private FilterComparator comparator;
        private static final Logger logger = LogManager.getLogger(Filter.class);

        public Filter(FilterType type, Object value, FilterComparator comparator) {
            this.type = type;
            this.value = value;
            this.comparator = comparator;
        }

        public Filter(String type, String value, String comparator) {
            if (type == null || type.equalsIgnoreCase("all") || type.equalsIgnoreCase("none") || type.equalsIgnoreCase("any") || type.equalsIgnoreCase("null")) {
                this.type = FilterType.ALL;
            } else {
                this.type = FilterType.valueOf(type.toUpperCase());
                if (this.type != FilterType.NAME) {
                    this.value = Integer.parseInt(value);
                    this.comparator = FilterComparator.valueOf(comparator.toUpperCase());
                } else {
                    this.value = value;
                    this.comparator = FilterComparator.valueOf(comparator.toUpperCase());
                    if (this.comparator != FilterComparator.EQUALS && this.comparator != FilterComparator.NOT_EQUALS) {
                        throw new UnsupportedOperationException("Invalid comparator for string filter: " + comparator);
                    }
                }
            }
        }

        public static Filter[] parseFilters(String[] type, String[] value, String[] comparator) {
            if (type.length != value.length || type.length != comparator.length) {
                throw new UnsupportedOperationException("Filter length mismatch");
            }
            ArrayList<Filter> filters = new ArrayList<>();
            for (int i = 0; i < type.length; i++) {
                Filter filter = new Filter(type[i], value[i], comparator[i]);
                if (filter.getType() == FilterType.ALL) {
                    continue; // ignore ALL filters
                }
                filters.add(filter);
            }
            return filters.toArray(new Filter[0]);
        }

        public static String generateSQL(Filter[] filters) {
            // ignore ALL filters
            Filter[] filtered = Arrays.stream(filters).filter(filter -> filter.getType() != FilterType.ALL).toArray(Filter[]::new);
            if (filtered.length == 0) {
                return " ";
            }
            ArrayList<String> filterStrings = new ArrayList<>();
            for (Filter filter : filtered) {
                filterStrings.add(filter.toString());
            }
            return " WHERE " + String.join(" AND ", filterStrings) + " ";
        }

        @Override
        public String toString() {
            // return SQL
            if (this.type == FilterType.ALL) {
                // In your code, you should filter out ALL filters
                throw new UnsupportedOperationException("Cannot convert ALL to SQL WHERE clause");
            }
            if (this.type == FilterType.NAME) {
                // if it's string, make sure to escape
                return this.type + " " + this.comparator + " \"" + this.value + "\"";
            }
            return this.type + " " + this.comparator + " " + this.value;
        }
    }
}
