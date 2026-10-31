package io.mateu.space.infra.in.rest;

import java.util.Collection;

/** A closed-set field given a value outside its set. Answered as a 400 naming what is accepted. */
public class InvalidValueException extends RuntimeException {
  public InvalidValueException(String field, String value, Collection<String> allowed) {
    super(field + " cannot be '" + value + "'. Accepted: " + String.join(", ", allowed) + ".");
  }
}
