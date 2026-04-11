package eu.appbahn.platform.common.exception;

public class QuotaExceededException extends RuntimeException {

    private final String dimension;
    private final Number limit;
    private final String level;

    public QuotaExceededException(String dimension, Number limit, String level) {
        super("Quota exceeded at " + level + " level: " + dimension + "=" + limit);
        this.dimension = dimension;
        this.limit = limit;
        this.level = level;
    }

    public String getDimension() {
        return dimension;
    }

    public Number getLimit() {
        return limit;
    }

    public String getLevel() {
        return level;
    }
}
