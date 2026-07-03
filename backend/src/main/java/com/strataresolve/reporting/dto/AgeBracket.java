package com.strataresolve.reporting.dto;

/**
 * Enumeration representing the age brackets for grouping open tickets
 * in the ageing report.
 *
 * <p>Brackets are defined as:
 * <ul>
 *   <li>ZERO_TO_THREE: 0–3 days old</li>
 *   <li>FOUR_TO_SEVEN: 4–7 days old</li>
 *   <li>EIGHT_TO_FOURTEEN: 8–14 days old</li>
 *   <li>FIFTEEN_TO_THIRTY: 15–30 days old</li>
 *   <li>OVER_THIRTY: more than 30 days old</li>
 * </ul>
 */
public enum AgeBracket {

    ZERO_TO_THREE("0-3 days"),
    FOUR_TO_SEVEN("4-7 days"),
    EIGHT_TO_FOURTEEN("8-14 days"),
    FIFTEEN_TO_THIRTY("15-30 days"),
    OVER_THIRTY("Over 30 days");

    private final String label;

    AgeBracket(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Determines which age bracket a ticket belongs to based on its age in days.
     *
     * @param ageDays the number of days since the ticket was created
     * @return the corresponding AgeBracket
     * @throws IllegalArgumentException if ageDays is negative
     */
    public static AgeBracket fromDays(long ageDays) {
        if (ageDays < 0) {
            throw new IllegalArgumentException("Age in days cannot be negative: " + ageDays);
        }
        if (ageDays <= 3) {
            return ZERO_TO_THREE;
        } else if (ageDays <= 7) {
            return FOUR_TO_SEVEN;
        } else if (ageDays <= 14) {
            return EIGHT_TO_FOURTEEN;
        } else if (ageDays <= 30) {
            return FIFTEEN_TO_THIRTY;
        } else {
            return OVER_THIRTY;
        }
    }
}
