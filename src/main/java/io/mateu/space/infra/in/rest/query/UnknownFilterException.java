package io.mateu.space.infra.in.rest.query;

import java.util.Collection;

/** A caller asked to filter by something the collection does not offer. Answered as a 400. */
public class UnknownFilterException extends RuntimeException {

  private final Collection<String> unknown;
  private final Collection<String> supported;

  public UnknownFilterException(Collection<String> unknown, Collection<String> supported) {
    super("Unknown filter(s): " + String.join(", ", unknown)
        + ". Supported: " + String.join(", ", supported)
        + ", plus search, page, size and sort.");
    this.unknown = unknown;
    this.supported = supported;
  }

  public Collection<String> getUnknown() {
    return unknown;
  }

  public Collection<String> getSupported() {
    return supported;
  }
}
