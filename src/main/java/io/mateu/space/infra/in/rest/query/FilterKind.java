package io.mateu.space.infra.in.rest.query;

/**
 * How one declared filter compares. Making this explicit per field, instead of inferring it from the
 * column type, is what keeps {@code ?status=ACTIVE} from also matching "INACTIVE" — an enum-like
 * column is a pick, not a prefix, and only the API author knows which is which.
 */
public enum FilterKind {

  /** Case-insensitive containment. For prose-ish columns: a name, a manufacturer, a vehicle. */
  TEXT,

  /** Case-insensitive equality. For a closed set of values: status, type, program, country. */
  EXACT,

  /** Equality, plus the inclusive {@code <field>_from} / {@code <field>_to} range. */
  NUMBER,

  /** Same as NUMBER over an ISO-8601 date. */
  DATE,

  /** {@code true} or {@code false}. */
  BOOLEAN,

  /** Equality on the id of a referenced row — {@code ?destinationId=5}. */
  REF,

  /**
   * Membership in a to-many association, by the id of one of its rows — {@code ?crewId=6} finds the
   * missions astronaut 6 flew. The association is joined, so the query is made distinct.
   */
  MEMBER
}
