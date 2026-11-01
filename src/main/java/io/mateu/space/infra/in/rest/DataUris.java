package io.mateu.space.infra.in.rest;

import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A picture sent INLINE as a {@code data:} URI — what a browser's file picker gives a form (Mateu's
 * {@code uploadableImage} field reads the file into one), so an upload needs no multipart endpoint:
 * it arrives in the same JSON as the rest of the record.
 */
final class DataUris {

  /** The largest image kept, decoded. */
  static final int MAX_BYTES = 2 * 1024 * 1024;

  static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

  private static final Pattern DATA_URI =
      Pattern.compile("^data:([a-zA-Z0-9.+/-]+)(;[^,]*)?,(.*)$", Pattern.DOTALL);

  record Decoded(String contentType, byte[] bytes) {}

  private DataUris() {}

  static boolean isDataUri(String value) {
    return value != null && value.regionMatches(true, 0, "data:", 0, 5);
  }

  /** The image a data URI carries, or an {@link InvalidImageException} saying why it is refused. */
  static Decoded decode(String uri) {
    var m = DATA_URI.matcher(uri.trim());
    if (!m.matches()) throw new InvalidImageException("image is not a valid data: URI.");
    var type = m.group(1).toLowerCase();
    if (!TYPES.contains(type)) {
      throw new InvalidImageException(
          "image must be a JPEG, PNG, WebP or GIF picture (got " + type + ").");
    }
    var params = m.group(2) == null ? "" : m.group(2);
    if (!params.toLowerCase().contains(";base64")) {
      throw new InvalidImageException("image must be base64-encoded.");
    }
    var data = m.group(3);
    // decoded size from the encoded length, before decoding anything that large
    if ((long) data.length() * 3 / 4 > MAX_BYTES + 3) throw tooBig();
    byte[] bytes;
    try {
      bytes = Base64.getMimeDecoder().decode(data);
    } catch (IllegalArgumentException e) {
      throw new InvalidImageException("image is not valid base64.");
    }
    if (bytes.length > MAX_BYTES) throw tooBig();
    if (bytes.length == 0) throw new InvalidImageException("image is empty.");
    return new Decoded(type, bytes);
  }

  private static InvalidImageException tooBig() {
    return new InvalidImageException("image is larger than 2 MB.");
  }
}
