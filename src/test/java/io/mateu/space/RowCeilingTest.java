package io.mateu.space;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The row ceiling, in its own context: it needs a ceiling low enough to hit with the seed alone, and
 * that ceiling would refuse the creates {@link SpaceApiTest} makes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"space.write-key=testkey", "space.max-rows-per-collection=54"})
class RowCeilingTest {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("a collection at its row ceiling refuses creates, so writes cannot exhaust the heap")
  void rowCeiling() throws Exception {
    // missions are seeded with exactly 54, which is the ceiling this context runs with
    mvc.perform(
            post("/api/missions")
                .header("X-Api-Key", "testkey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mars Sample Return\"}"))
        .andExpect(status().isInsufficientStorage())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("is full")));
    mvc.perform(get("/api/missions")).andExpect(jsonPath("$.totalElements").value(54));

    // a collection still under it takes the write as usual
    mvc.perform(
            post("/api/launch-sites")
                .header("X-Api-Key", "testkey")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Spaceport America\",\"latitude\":32.99,\"longitude\":-106.97}"))
        .andExpect(status().isCreated());
  }
}
