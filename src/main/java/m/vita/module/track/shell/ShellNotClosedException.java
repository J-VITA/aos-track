package m.vita.module.track.shell;

public class ShellNotClosedException extends RuntimeException {
    public static final String EXCEPTION_NOT_CLOSED = "Application did not close() interactive shell";

    public ShellNotClosedException() {
        super("Application did not close() interactive shell");
    }
}
