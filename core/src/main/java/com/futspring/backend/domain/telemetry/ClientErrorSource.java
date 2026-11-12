package com.futspring.backend.domain.telemetry;

// Where the web app caught the error: window "error", "unhandledrejection" or a React ErrorBoundary
public enum ClientErrorSource {
    WINDOW, PROMISE, BOUNDARY
}
