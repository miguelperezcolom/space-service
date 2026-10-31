package io.mateu.space.infra.in.rest;

/**
 * A create on a collection that already holds its ceiling of rows. The database lives in the pod's
 * heap, so a write key that leaks — and on a demo API it will — would otherwise let anyone grow it
 * until the JVM dies. Answered as a 507: the request is fine, there is just no room for it.
 */
public class CollectionFullException extends RuntimeException {
  public CollectionFullException(String collection, long max) {
    super("The " + collection + " collection is full (" + max + " rows). "
        + "Delete a row first, or wait for the next restart to re-seed it.");
  }
}
