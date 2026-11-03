package io.mateu.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** A record names the rows beside it, in the order the list shows them. */
@SpringBootTest
@AutoConfigureMockMvc
class RecordNeighboursTest {

  private final ObjectMapper json = new ObjectMapper();

  @Autowired private MockMvc mvc;

  private JsonNode read(String url) throws Exception {
    return json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missions", "launch-sites", "astronauts", "spacecraft"})
  void neighboursFollowTheListOrder(String collection) throws Exception {
    JsonNode rows = read("/api/" + collection + "?size=200").get("content");
    assertThat(rows.size()).isGreaterThan(2);
    for (int i = 0; i < rows.size(); i++) {
      JsonNode record = read("/api/" + collection + "/" + rows.get(i).get("id").asLong());
      if (i == 0) assertThat(record.get("previousId").isNull()).isTrue();
      else assertThat(record.get("previousId").asLong()).isEqualTo(rows.get(i - 1).get("id").asLong());
      if (i < rows.size() - 1) assertThat(record.get("nextId").asLong()).isEqualTo(rows.get(i + 1).get("id").asLong());
      else if (rows.size() < 200) assertThat(record.get("nextId").isNull()).isTrue();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"missions", "launch-sites", "astronauts", "spacecraft"})
  void theRowKeepsItsOwnFieldsAtTheTop(String collection) throws Exception {
    long id = read("/api/" + collection + "?size=1").get("content").get(0).get("id").asLong();
    mvc.perform(get("/api/" + collection + "/" + id))
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.row").doesNotExist());
  }
}
