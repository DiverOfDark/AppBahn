package eu.appbahn.platform.common.exception;

public class LicenseLimitException extends RuntimeException {

    private final int current;
    private final int limit;

    public LicenseLimitException(int current, int limit) {
        super("Resource limit reached: " + current + "/" + limit);
        this.current = current;
        this.limit = limit;
    }

    public int getCurrent() {
        return current;
    }

    public int getLimit() {
        return limit;
    }
}
