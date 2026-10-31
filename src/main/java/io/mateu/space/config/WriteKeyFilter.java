package io.mateu.space.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads are open; writes need a key.
 *
 * <p>This is a public API on the open internet that accepts writes, and an unguarded one is a
 * database anybody can empty. A shared key is not authentication — it does not say WHO is writing —
 * but it is the difference between "you have to be told about it" and "a crawler can do it", and it
 * keeps the read path free so a static page needs no credentials to render.
 *
 * <p>If no key is configured the filter refuses every write rather than allowing them: a deployment
 * that forgot to set one should be inert, not wide open.
 */
@Component
public class WriteKeyFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(WriteKeyFilter.class);
  private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  private final String header;
  private final String expected;

  public WriteKeyFilter(
      @Value("${space.write-key-header}") String header,
      @Value("${space.write-key:}") String expected) {
    this.header = header;
    this.expected = expected == null ? "" : expected.trim();
    if (this.expected.isEmpty()) {
      log.warn("No space.write-key configured — every write will be refused with 401");
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (READ_METHODS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/")) {
      chain.doFilter(request, response);
      return;
    }

    String provided = request.getHeader(header);
    if (expected.isEmpty() || provided == null || !constantTimeEquals(expected, provided)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.getWriter().write(
          "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Writing needs the "
              + header + " header.\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  /** Compared without an early exit, so the response time says nothing about the key. */
  private static boolean constantTimeEquals(String a, String b) {
    if (a.length() != b.length()) return false;
    int diff = 0;
    for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
    return diff == 0;
  }
}
