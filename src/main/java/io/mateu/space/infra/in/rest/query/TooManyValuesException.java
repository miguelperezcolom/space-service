package io.mateu.space.infra.in.rest.query;

/**
 * A filter given more values than any multi-select would send. Each value is one more OR the
 * database evaluates on every row, so an unbounded list is a cheap way to make one request expensive.
 * Answered as a 400.
 */
public class TooManyValuesException extends RuntimeException {
  public TooManyValuesException(String filter, int given, int max) {
    super("Filter " + filter + " has " + given + " values; at most " + max + " are accepted.");
  }
}
