package com.server.contestControl.submissionServer.sse;

public enum SubmissionStreamEventType {
    /** A new submission has been accepted and saved as PENDING. */
    CREATED,

    /** The submission has been picked up by the consumer and is now RUNNING in Judge0. */
    RUNNING,

    /** All Judge0 test-case callbacks have arrived and a final verdict has been written. */
    FINALIZED,

    /** The submission has been marked PENDING_REJUDGE and queued for re-judging. */
    REJUDGE_QUEUED
}
