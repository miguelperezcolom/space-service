package io.mateu.space.infra.in.rest.query;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The envelope every collection answers with.
 *
 * <p>A bare JSON array cannot say how many rows matched — so a client could never render "54
 * missions found" or a pager without fetching everything. The envelope carries the total, and a consumer that maps responses by path (a Mateu listing, for one) points
 * `itemsPath` at `content` and `totalPath` at `totalElements`.
 */
public record PageResponse<T>(
    List<T> content, long totalElements, int totalPages, int page, int size) {

  /** A single page holding every row — what a tree answers, since a tree is not paged. */
  public static <T> PageResponse<T> all(List<T> rows) {
    return new PageResponse<>(rows, rows.size(), 1, 0, rows.size());
  }

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }
}
