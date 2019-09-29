package io.github.bennofs.wdumper.interfaces;

public interface DumpStatusHandler {
    enum ErrorLevel {
        CRITICAL,
        ERROR,
        WARNING
    }

    void reportError(ErrorLevel level, String message, Exception cause);
}
