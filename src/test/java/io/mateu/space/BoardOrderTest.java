package io.mateu.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The task board keeps the order its cards were dragged into (Mateu Kanban orderField). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "space.write-key=testkey")
class BoardOrderTest {

  private static final String KEY = "X-Api-Key";
  private final ObjectMapper json = new ObjectMapper();

  @Autowired private MockMvc mvc;

  private JsonNode column(String status) throws Exception {
    String body = mvc.perform(get("/api/tasks").param("status", status)
            .param("sort", "boardOrder").param("size", "200"))
        .andReturn().getResponse().getContentAsString();
    return json.readTree(body).get("content");
  }

  @Test
  @DisplayName("every seeded column is numbered 1000, 2000, … by due date, and sorts by boardOrder")
  void seededColumnsAreNumbered() throws Exception {
    for (String status : List.of("BACKLOG", "IN_PROGRESS", "TESTING", "DONE")) {
      JsonNode cards = column(status);
      assertThat(cards.size()).isPositive();
      String previous = "";
      for (int i = 0; i < cards.size(); i++) {
        assertThat(cards.get(i).get("boardOrder").asDouble()).isEqualTo((i + 1) * 1000.0);
        String due = cards.get(i).get("dueDate").asText();
        assertThat(due.compareTo(previous)).isGreaterThanOrEqualTo(0);
        previous = due;
      }
    }
  }

  @Test
  @DisplayName("a card moved between two neighbours keeps its decimal place and the implied progress")
  @DirtiesContext
  void aMovedCardKeepsItsPlace() throws Exception {
    long id = column("BACKLOG").get(0).get("id").asLong();
    mvc.perform(post("/api/tasks/" + id + "/move").header(KEY, "testkey")
            .param("status", "DONE").param("boardOrder", "1500.5"))
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.progress").value(100))
        .andExpect(jsonPath("$.boardOrder").value(1500.5));
    assertThat(column("DONE").get(1).get("id").asLong()).isEqualTo(id);
  }

  @Test
  @DisplayName("a move inside a column changes only the place")
  @DirtiesContext
  void aMoveInsideAColumn() throws Exception {
    JsonNode card = column("IN_PROGRESS").get(0);
    mvc.perform(post("/api/tasks/" + card.get("id").asLong() + "/move").header(KEY, "testkey")
            .param("status", "IN_PROGRESS").param("boardOrder", "2500"))
        .andExpect(jsonPath("$.progress").value(card.get("progress").asInt()))
        .andExpect(jsonPath("$.boardOrder").value(2500.0));
  }

  @Test
  @DisplayName("a move that names no place sends the card to the end of the new column, or leaves it")
  @DirtiesContext
  void aMoveWithoutAPlace() throws Exception {
    JsonNode testing = column("TESTING");
    double last = testing.get(testing.size() - 1).get("boardOrder").asDouble();
    long id = column("BACKLOG").get(0).get("id").asLong();
    mvc.perform(post("/api/tasks/" + id + "/move").header(KEY, "testkey").param("status", "TESTING"))
        .andExpect(jsonPath("$.boardOrder").value(last + 1000));
    mvc.perform(post("/api/tasks/" + id + "/move").header(KEY, "testkey").param("status", "TESTING"))
        .andExpect(jsonPath("$.boardOrder").value(last + 1000));
  }

  @Test
  @DisplayName("an edit that sends no order keeps the card where it is")
  @DirtiesContext
  void anEditKeepsThePlace() throws Exception {
    ObjectNode card = (ObjectNode) column("DONE").get(1).deepCopy();
    double order = card.get("boardOrder").asDouble();
    card.remove("boardOrder");
    card.put("owner", "Another team");
    mvc.perform(put("/api/tasks/" + card.get("id").asLong()).header(KEY, "testkey")
            .contentType(MediaType.APPLICATION_JSON).content(card.toString()))
        .andExpect(jsonPath("$.owner").value("Another team"))
        .andExpect(jsonPath("$.boardOrder").value(order));
  }

  @Test
  @DisplayName("a new card goes to the end of its column")
  @DirtiesContext
  void aNewCardGoesLast() throws Exception {
    JsonNode backlog = column("BACKLOG");
    double last = backlog.get(backlog.size() - 1).get("boardOrder").asDouble();
    mvc.perform(post("/api/tasks").header(KEY, "testkey")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Condition report\",\"status\":\"BACKLOG\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.boardOrder").value(last + 1000));
  }
}
