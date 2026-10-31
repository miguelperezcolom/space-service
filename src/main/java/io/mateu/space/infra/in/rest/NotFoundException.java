package io.mateu.space.infra.in.rest;

/** No row with that id in that collection. Answered as a 404. */
public class NotFoundException extends RuntimeException {
  public NotFoundException(String collection, Object id) {
    super("No " + collection + " with id " + id);
  }
}
