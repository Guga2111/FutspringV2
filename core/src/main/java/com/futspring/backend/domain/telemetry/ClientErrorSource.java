package com.futspring.backend.domain.telemetry;

// Where the web app caught the error: window "error", "unhandledrejection", a React ErrorBoundary, or a
// Content-Security-Policy violation ("securitypolicyviolation", also fired by the Report-Only header)
public enum ClientErrorSource {
    WINDOW, PROMISE, BOUNDARY, CSP
}
