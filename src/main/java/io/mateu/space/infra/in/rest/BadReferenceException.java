package io.mateu.space.infra.in.rest;

/**
 * A write pointed at a row that does not exist — {@code destinationId: 999}. A 400 and not a 404: the
 * thing that is missing is not what was addressed, it is what the caller sent inside it.
 */
public class BadReferenceException extends RuntimeException {
  public BadReferenceException(String what, Object id) {
    super("No " + what + " with id " + id);
  }
}
