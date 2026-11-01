package io.mateu.space.infra.in.rest;

/** An uploaded image this service will not keep: not an image it serves, too big, or unreadable. */
public class InvalidImageException extends RuntimeException {
  public InvalidImageException(String message) {
    super(message);
  }
}
