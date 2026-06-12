package io.github.qbsstg.protocol.runtime.app;

record ManagementAccessDecision(
        boolean allowed,
        int statusCode,
        String errorCode,
        String message,
        String rejectionReason) {

    static ManagementAccessDecision granted() {
        return new ManagementAccessDecision(true, 200, null, null, null);
    }

    static ManagementAccessDecision unauthorized(String reason) {
        return rejected(
                401,
                "unauthorized",
                "Management access requires a valid bearer token.",
                reason);
    }

    static ManagementAccessDecision forbidden(String reason) {
        return rejected(
                403,
                "forbidden",
                "Management access is restricted by the configured access policy.",
                reason);
    }

    private static ManagementAccessDecision rejected(
            int statusCode,
            String errorCode,
            String message,
            String rejectionReason) {
        return new ManagementAccessDecision(false, statusCode, errorCode, message, rejectionReason);
    }
}
