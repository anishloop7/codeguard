package com.codeguard.model;

/**
 * Defines the type of AI review to perform.
 * Each profile uses a different system prompt template and focuses
 * on a specific dimension of code quality.
 */
public enum ReviewProfile {

    /** General code quality, readability, and best practices. */
    GENERAL,

    /** Focuses on OWASP vulnerabilities, injection risks, auth flaws, sensitive data exposure. */
    SECURITY,

    /** Focuses on algorithmic complexity, N+1 queries, memory usage, caching opportunities. */
    PERFORMANCE
}
