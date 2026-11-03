package io.mateu.space.infra.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * One row with the ids of the rows beside it in the unfiltered list, so a record page can offer
 * previous and next without knowing the list's order. Null at either end.
 */
public record WithNeighbours<D>(@JsonUnwrapped D row, Long previousId, Long nextId) {}
