package io.mateu.space;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The API's behaviour, pinned against the real seeded dataset. These are the claims a client is
 * entitled to rely on: what the numbers are, what filters mean, what a write does to a reference,
 * and which failures are the caller's fault rather than the server's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "space.write-key=testkey")
class SpaceApiTest {

  private static final String KEY = "X-Api-Key";

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("the whole dataset is seeded at boot, references included")
  void seeded() throws Exception {
    mvc.perform(get("/api/missions")).andExpect(jsonPath("$.totalElements").value(54));
    mvc.perform(get("/api/astronauts")).andExpect(jsonPath("$.totalElements").value(77));
    mvc.perform(get("/api/spacecraft")).andExpect(jsonPath("$.totalElements").value(43));
    mvc.perform(get("/api/components")).andExpect(jsonPath("$.totalElements").value(138));
    mvc.perform(get("/api/bodies")).andExpect(jsonPath("$.totalElements").value(31));
    mvc.perform(get("/api/launch-sites")).andExpect(jsonPath("$.totalElements").value(15));
    mvc.perform(get("/api/launches")).andExpect(jsonPath("$.totalElements").value(50));
    mvc.perform(get("/api/tasks")).andExpect(jsonPath("$.totalElements").value(53));
    mvc.perform(get("/api/budgets")).andExpect(jsonPath("$.totalElements").value(135));

    // Apollo 11 keeps the crew, the destination and the site Wikidata gave it
    mvc.perform(get("/api/missions").param("search", "Apollo 11"))
        .andExpect(jsonPath("$.content[0].name").value("Apollo 11"))
        .andExpect(jsonPath("$.content[0].launchDate").value("1969-07-16"))
        .andExpect(jsonPath("$.content[0].destinationName").value("Moon"))
        .andExpect(jsonPath("$.content[0].launchSiteName").value(containsString("39A")))
        .andExpect(jsonPath("$.content[0].crewNames", containsInAnyOrder("Neil Armstrong", "Buzz Aldrin", "Michael Collins")));
  }

  @Test
  @DisplayName("every photograph is a NASA library asset, with its credit and its source page")
  void images() throws Exception {
    mvc.perform(get("/api/missions/6"))
        .andExpect(jsonPath("$.image").value(startsWith("https://images-assets.nasa.gov/image/")))
        .andExpect(jsonPath("$.imageLarge").value(containsString("~large")))
        .andExpect(jsonPath("$.imageCredit").value(containsString("NASA")))
        .andExpect(jsonPath("$.sourceUrl").value(startsWith("https://images.nasa.gov/details/")))
        .andExpect(jsonPath("$.gallery.length()").value(greaterThan(2)));
    mvc.perform(get("/api/credits"))
        .andExpect(jsonPath("$.length()").value(greaterThan(300)))
        .andExpect(jsonPath("$[*].credit").value(everyItem(containsString("NASA"))))
        .andExpect(jsonPath("$[*].sourceUrl").value(everyItem(startsWith("https://images.nasa.gov/details/"))));
  }

  @Test
  @DisplayName("a page carries the total, so a client can render a pager without fetching everything")
  void pagedEnvelope() throws Exception {
    mvc.perform(get("/api/astronauts").param("size", "10"))
        .andExpect(jsonPath("$.content.length()").value(10))
        .andExpect(jsonPath("$.totalElements").value(77))
        .andExpect(jsonPath("$.totalPages").value(8))
        .andExpect(jsonPath("$.page").value(0));
  }

  @Test
  @DisplayName("an EXACT filter is a pick, not a prefix; repeated or comma-joined is an OR")
  void exactAndMultiSelect() throws Exception {
    mvc.perform(get("/api/missions").param("status", "ACTIVE"))
        .andExpect(jsonPath("$.content[*].status").value(everyItem(is("ACTIVE"))));
    long active = count("/api/missions?status=ACTIVE");
    long planned = count("/api/missions?status=PLANNED");
    mvc.perform(get("/api/missions").param("status", "ACTIVE,PLANNED"))
        .andExpect(jsonPath("$.totalElements").value(active + planned));
    mvc.perform(get("/api/missions").param("status", "ACTIVE").param("status", "PLANNED"))
        .andExpect(jsonPath("$.totalElements").value(active + planned));
  }

  @Test
  @DisplayName("ranges are inclusive on numbers and dates; a reference filters by id")
  void rangesAndReferences() throws Exception {
    mvc.perform(get("/api/missions").param("launchDate_from", "1969-01-01").param("launchDate_to", "1969-12-31"))
        .andExpect(jsonPath("$.content[*].name", contains("Apollo 11")));
    mvc.perform(get("/api/missions").param("destinationId", "6").param("type", "ROVER"))
        .andExpect(jsonPath("$.content[*].destinationName").value(everyItem(is("Mars"))))
        .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(4)));
    mvc.perform(get("/api/launches").param("launchYear_from", "2020").param("launchYear_to", "2029"))
        .andExpect(jsonPath("$.content[*].launchYear").value(everyItem(greaterThanOrEqualTo(2020))));
    mvc.perform(get("/api/spacecraft").param("massKg_to", "100"))
        .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Sojourner", "Ingenuity")));
  }

  @Test
  @DisplayName("a to-many filter matches members, once each; a boolean filter takes true and false")
  void memberAndBoolean() throws Exception {
    // Neil Armstrong (astronaut 5) flew Gemini 8 and Apollo 11
    mvc.perform(get("/api/missions").param("crewId", "5"))
        .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Gemini 8", "Apollo 11")));
    mvc.perform(get("/api/astronauts").param("missionId", "6"))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get("/api/missions").param("featured", "true"))
        .andExpect(jsonPath("$.content[*].featured").value(everyItem(is(true))));
    mvc.perform(get("/api/missions").param("featured", "maybe")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("a table sorts by the column it shows, even when that column is a reference's name")
  void sortByShownName() throws Exception {
    mvc.perform(get("/api/missions").param("sort", "destinationName,desc").param("size", "1"))
        .andExpect(jsonPath("$.content[0].destinationName").value("Titan"));
    mvc.perform(get("/api/missions").param("sort", "nope")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("an undeclared filter is a 400 naming what is supported, never an unfiltered page")
  void unknownFilter() throws Exception {
    mvc.perform(get("/api/missions").param("planet", "Mars"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("destinationId")));
  }

  @Test
  @DisplayName("the trees: bodies by what they orbit, spacecraft by their subsystems and instruments")
  void trees() throws Exception {
    mvc.perform(get("/api/bodies/tree"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Sun"))
        .andExpect(jsonPath("$.content[0].children[?(@.name == 'Jupiter')].children[*].name",
            hasItems("Io", "Europa", "Ganymede", "Callisto")));
    mvc.perform(get("/api/bodies/tree").param("search", "europa"))
        .andExpect(jsonPath("$.content[0].children.length()").value(1))
        .andExpect(jsonPath("$.content[0].children[0].children[0].name").value("Europa"));
    mvc.perform(get("/api/spacecraft/tree"))
        .andExpect(jsonPath("$.content[*].name", hasItems("Curiosity", "James Webb Space Telescope")));
    mvc.perform(get("/api/spacecraft/26/components/tree"))
        .andExpect(jsonPath("$.content[?(@.name == 'Science payload')].children[*].name", hasItem("ChemCam")));
  }

  @Test
  @DisplayName("one search over every collection, each hit with the route that opens it")
  void searchEverything() throws Exception {
    mvc.perform(get("/api/search").param("search", "mars"))
        .andExpect(jsonPath("$.content[0].name").value("Mars"))
        .andExpect(jsonPath("$.content[0].route").value("bodies/6"))
        .andExpect(jsonPath("$.content[*].kind", hasItems("BODY", "MISSION")));
    mvc.perform(get("/api/search").param("search", "mars").param("kind", "MISSION"))
        .andExpect(jsonPath("$.content[*].kind").value(everyItem(is("MISSION"))));
  }

  @Test
  @DisplayName("stats count what is there and give the chart series")
  void stats() throws Exception {
    mvc.perform(get("/api/stats"))
        .andExpect(jsonPath("$.missions").value(54))
        .andExpect(jsonPath("$.launchesByDecade[0].label").value("1960s"))
        .andExpect(jsonPath("$.budgetByYear.length()").value(15))
        .andExpect(jsonPath("$.tasksByStatus[*].label", hasItems("BACKLOG", "DONE")));
  }

  @Test
  @DisplayName("reads are open; writes without the key are refused")
  void writesNeedTheKey() throws Exception {
    mvc.perform(post("/api/missions").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/missions/1").header(KEY, "wrong")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("a body written by a form template — numbers, dates and lists as strings — is read as meant")
  @DirtiesContext
  void formTemplateBody() throws Exception {
    mvc.perform(post("/api/missions").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Mars Sample Return","type":"sample_return","status":"PLANNED",
                 "launchDate":"","costUsd":"","destinationId":"6","spacecraftId":"",
                 "crewIds":"[5, 11]","featured":"true"}"""))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("/api/missions/")))
        .andExpect(jsonPath("$.type").value("SAMPLE_RETURN"))
        .andExpect(jsonPath("$.launchDate").doesNotExist())
        .andExpect(jsonPath("$.costUsd").doesNotExist())
        .andExpect(jsonPath("$.destinationName").value("Mars"))
        .andExpect(jsonPath("$.crewNames", containsInAnyOrder("Neil Armstrong", "Buzz Aldrin")))
        .andExpect(jsonPath("$.featured").value(true));
    mvc.perform(post("/api/missions").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"X\",\"crewIds\":\"5,11\",\"status\":\"\"}"))
        .andExpect(jsonPath("$.crewIds", contains(5, 11)))
        .andExpect(jsonPath("$.status").value("PLANNED"));
    // a money widget's value and a date-range widget's value, as server-side interpolation prints them
    mvc.perform(post("/api/missions").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Y\",\"cost\":\"{value=1.5E9, currency=USD, locale=en-US}\","
                + "\"launchWindow\":\"{from=2027-01-01, to=2027-02-15}\"}"))
        .andExpect(jsonPath("$.costUsd").value(1500000000))
        .andExpect(jsonPath("$.cost.currency").value("USD"))
        .andExpect(jsonPath("$.launchWindow.from").value("2027-01-01"))
        .andExpect(jsonPath("$.launchWindow.to").value("2027-02-15"));
    mvc.perform(post("/api/missions").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Z\",\"cost\":{\"value\":42,\"currency\":\"USD\"},\"launchWindow\":{\"from\":\"2028-07-05\"}}"))
        .andExpect(jsonPath("$.costUsd").value(42))
        .andExpect(jsonPath("$.launchWindow.from").value("2028-07-05"));
  }

  @Test
  @DisplayName("a replace keeps the photographs and the crew a form left out, and replaces the rest")
  @DirtiesContext
  void replaceKeepsWhatIsLeftOut() throws Exception {
    mvc.perform(put("/api/missions/6").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Apollo XI\",\"status\":\"COMPLETED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Apollo XI"))
        .andExpect(jsonPath("$.crewSize").value(3))
        .andExpect(jsonPath("$.image").value(startsWith("https://images-assets.nasa.gov/")))
        .andExpect(jsonPath("$.gallery.length()").value(greaterThan(2)))
        .andExpect(jsonPath("$.destinationId").doesNotExist());
    mvc.perform(put("/api/missions/6").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Apollo XI\",\"crewIds\":[]}"))
        .andExpect(jsonPath("$.crewSize").value(0));
  }

  @Test
  @DisplayName("a closed set refuses what is outside it, naming what it accepts")
  void closedSets() throws Exception {
    mvc.perform(put("/api/missions/6").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Apollo 11\",\"status\":\"bogus\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("COMPLETED")));
  }

  @Test
  @DisplayName("a write pointing at a row that does not exist is a 400, not a 404")
  void badReference() throws Exception {
    mvc.perform(post("/api/missions").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"X\",\"destinationId\":9999}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("9999")));
  }

  @Test
  @DisplayName("a tree cannot be made into a loop")
  void noCycles() throws Exception {
    // the Sun cannot orbit Jupiter, which orbits the Sun
    mvc.perform(put("/api/bodies/1").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Sun\",\"type\":\"STAR\",\"parentId\":10}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("a launch takes its date and time as one value, and a card moves column by itself")
  @DirtiesContext
  void launchDateTimeAndMovingACard() throws Exception {
    mvc.perform(put("/api/launches/1").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"launchDateTime\":\"1961-05-05T14:34\",\"missionId\":1,\"outcome\":\"SUCCESS\"}"))
        .andExpect(jsonPath("$.launchDate").value("1961-05-05"))
        .andExpect(jsonPath("$.launchTimeUtc").value("14:34:00"))
        .andExpect(jsonPath("$.launchYear").value(1961));
    mvc.perform(post("/api/tasks/1/move").header(KEY, "testkey").param("status", "DONE"))
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.progress").value(100));
  }

  @Test
  @DisplayName("deleting a row others still point at is a 409 and removes nothing")
  @DirtiesContext
  void deleteReferenced() throws Exception {
    mvc.perform(delete("/api/missions/6").header(KEY, "testkey")).andExpect(status().isConflict());
    mvc.perform(get("/api/missions/6")).andExpect(status().isOk());
    mvc.perform(delete("/api/budgets/1").header(KEY, "testkey")).andExpect(status().isNoContent());
    mvc.perform(get("/api/budgets/1")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CORS answers any origin, and lets the write key through the preflight")
  void cors() throws Exception {
    mvc.perform(options("/api/missions").header("Origin", "http://localhost:8602")
            .header("Access-Control-Request-Method", "PUT")
            .header("Access-Control-Request-Headers", "content-type,x-api-key"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8602"));
  }

  private long count(String url) throws Exception {
    String body = mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
    return Long.parseLong(body.replaceAll(".*\"totalElements\":(\\d+).*", "$1"));
  }

  @Test
  @DisplayName("a new photo brings its sizes: the NASA library's renditions, or the same URL")
  @DirtiesContext
  void newPhotoBringsItsSizes() throws Exception {
    mvc.perform(put("/api/astronauts/14").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Alfred Worden\",\"image\":\"https://images-assets.nasa.gov/image/AS15-88-11866/AS15-88-11866~medium.jpg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.thumbnail").value("https://images-assets.nasa.gov/image/AS15-88-11866/AS15-88-11866~thumb.jpg"))
        .andExpect(jsonPath("$.imageLarge").value("https://images-assets.nasa.gov/image/AS15-88-11866/AS15-88-11866~large.jpg"));
    mvc.perform(put("/api/astronauts/14").header(KEY, "testkey").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Alfred Worden\",\"image\":\"https://example.org/worden.jpg\"}"))
        .andExpect(jsonPath("$.thumbnail").value("https://example.org/worden.jpg"))
        .andExpect(jsonPath("$.imageLarge").value("https://example.org/worden.jpg"));
  }
}
