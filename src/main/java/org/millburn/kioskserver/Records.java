package org.millburn.kioskserver;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;

public record Records(int number, int id, int prev_status, int new_status, String date, String kiosk_name) {

    @Override
    public String toString() {
        // json
        return "{\"number\":" + number + ",\"id\":" + id + ",\"prev_status\":" + prev_status + ",\"new_status\":" + new_status + ",\"time\":\"" + date + "\",\"kiosk_name\":\"" + kiosk_name + "\"}";
    }

    public enum FilterType {
        ALL, NUM, ID, PREV_STATUS, NEW_STATUS, DATE, KIOSK_NAME
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
                if (type.equalsIgnoreCase("TIME")) {
                    this.type = FilterType.DATE; // time is an alias for date(imo time is superior, but I'm not going change the DB schema)
                } else if (type.equalsIgnoreCase("KIOSK")) {
                    this.type = FilterType.KIOSK_NAME; // same thing, alias for kiosk_name
                } else if (type.equalsIgnoreCase("NUMBER")) {
                    this.type = FilterType.NUM; // same thing, alias for num
                } else {
                    this.type = FilterType.valueOf(type.toUpperCase());
                }
                if (this.type == FilterType.NUM || this.type == FilterType.ID || this.type == FilterType.PREV_STATUS || this.type == FilterType.NEW_STATUS) {
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
            if (this.type == FilterType.DATE || this.type == FilterType.KIOSK_NAME) {
                // if it's string, make sure to escape
                return this.type + " " + this.comparator + " \"" + this.value + "\"";
            }
            return this.type + " " + this.comparator + " " + this.value;
        }
    }
}



