package ua.sumdu.dds.travelerapi.exception;

public class VersionConflictException extends RuntimeException {
    private final int currentVersion;

    public VersionConflictException(int currentVersion) {
        super("Conflict: plan was modified");
        this.currentVersion = currentVersion;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }
}
