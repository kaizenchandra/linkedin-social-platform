package dev.network.hiring.application;

public enum ApplicationState {
    SUBMITTED,
    IN_REVIEW,
    SHORTLISTED,
    REJECTED,
    WITHDRAWN;

    public boolean terminal() {
        return this == REJECTED || this == WITHDRAWN;
    }

    public boolean reviewerMayMoveTo(ApplicationState next) {
        if (next == this) return true;
        return switch (this) {
            case SUBMITTED -> next == IN_REVIEW || next == SHORTLISTED || next == REJECTED;
            case IN_REVIEW -> next == SHORTLISTED || next == REJECTED;
            case SHORTLISTED -> next == REJECTED;
            default -> false;
        };
    }
}
