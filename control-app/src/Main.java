/*
 * Application entry point (wrapper around AppLauncher).
 *
 * Responsibilities:
 * - Contain the public static void main(String[] args)
 * - Delegate startup to AppLauncher
 *
 * Java data types used:
 * - String[]
 *
 * Java technologies involved:
 * - JVM entry point
 *
 * Design intent:
 * Keeps startup logic separate from application wiring.
 * Makes future testing and alternate launch modes easier.
 */