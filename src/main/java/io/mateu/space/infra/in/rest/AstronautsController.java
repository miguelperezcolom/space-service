package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.Astronaut;
import io.mateu.space.domain.Mission;
import io.mateu.space.domain.Picture;
import io.mateu.space.infra.in.rest.dto.AstronautDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.domain.Portrait;
import io.mateu.space.infra.out.AstronautRepository;
import io.mateu.space.infra.out.PortraitRepository;
import io.mateu.space.infra.out.SpaceRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/astronauts")
@Tag(name = "Astronauts", description = "The people who flew the crewed missions")
public class AstronautsController extends CrudController<Astronaut, AstronautDto> {

  static final List<String> STATUSES = List.of("ACTIVE", "RETIRED", "DECEASED");

  private final AstronautRepository astronauts;
  private final PortraitRepository portraits;
  private final String publicBaseUrl;

  public AstronautsController(
      AstronautRepository astronauts,
      PortraitRepository portraits,
      @Value("${space.public-base-url:}") String publicBaseUrl) {
    this.astronauts = astronauts;
    this.portraits = portraits;
    this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
  }

  @Operation(
      summary = "An uploaded portrait",
      description =
          "The picture uploaded for this astronaut (an update whose `image` is a `data:` URI). The "
              + "astronaut's image, thumbnail and large version point here, with a version that "
              + "changes on every upload.")
  @Transactional(readOnly = true)
  @GetMapping("/{id}/portrait")
  public ResponseEntity<byte[]> portrait(@PathVariable Long id) {
    var portrait =
        portraits.findById(id).orElseThrow(() -> new NotFoundException("portraits", id));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(portrait.getContentType()))
        // the URL names the version: a new upload is a new URL, so this one never changes
        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
        .eTag(portrait.getVersion())
        .body(portrait.getBytes());
  }

  @Override
  protected String collection() {
    return "astronauts";
  }

  @Override
  protected SpaceRepository<Astronaut> repository() {
    return astronauts;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("name", FilterKind.TEXT)
        .filter("nationality", FilterKind.EXACT)
        .filter("gender", FilterKind.EXACT)
        .filter("status", FilterKind.EXACT)
        .filter("selectionGroup", FilterKind.EXACT)
        .filter("selectionYear", FilterKind.NUMBER)
        .filter("birthDate", FilterKind.DATE)
        .filter("timeInSpaceDays", FilterKind.NUMBER)
        .filter("evaHours", FilterKind.NUMBER)
        .filter("missionId", "missions.id", FilterKind.MEMBER)
        .searchable("name", "nationality", "selectionGroup", "summary", "birthPlace")
        .defaultSort("name")
        .build();
  }

  @Override
  protected Astronaut newEntity() {
    return new Astronaut();
  }

  @Override
  protected Long idOf(AstronautDto dto) {
    return dto.id();
  }

  @Override
  protected AstronautDto toDto(Astronaut a) {
    Picture p = a.getPicture() == null ? new Picture() : a.getPicture();
    return new AstronautDto(
        a.getId(),
        a.getName(),
        a.getNationality(),
        a.getGender(),
        a.getBirthDate(),
        a.getDeathDate(),
        a.getBirthPlace(),
        a.getSelectionGroup(),
        a.getSelectionYear(),
        a.getStatus(),
        a.getTimeInSpaceDays(),
        a.getEvaHours(),
        a.getSummary(),
        a.getBio(),
        a.getMissions().stream().map(Mission::getId).toList(),
        a.getMissions().stream().map(Mission::getName).toList(),
        a.getMissions().size(),
        a.getWikidataId(),
        wikidataUrl(a.getWikidataId()),
        urlOf(a.getId()),
        p.getImage(),
        p.getImageLarge(),
        p.getThumbnail(),
        p.getImageTitle(),
        p.getImageCredit(),
        p.getSourceUrl());
  }

  @Override
  protected void apply(AstronautDto dto, Astronaut a) {
    a.setName(dto.name());
    a.setNationality(dto.nationality());
    a.setGender(dto.gender());
    a.setBirthDate(dto.birthDate());
    a.setDeathDate(dto.deathDate());
    a.setBirthPlace(dto.birthPlace());
    a.setSelectionGroup(dto.selectionGroup());
    a.setSelectionYear(dto.selectionYear());
    a.setStatus(oneOf(dto.status(), "status", STATUSES));
    a.setTimeInSpaceDays(dto.timeInSpaceDays());
    a.setEvaHours(dto.evaHours());
    a.setSummary(dto.summary());
    a.setBio(dto.bio());
    a.setWikidataId(keep(dto.wikidataId(), a.getWikidataId()));
    if (DataUris.isDataUri(dto.image())) {
      // an uploaded picture: kept here, and the record points at where this service serves it
      var url = storePortrait(a, DataUris.decode(dto.image()));
      a.setPicture(Pictures.merge(a.getPicture(), url, url, url,
          dto.imageTitle(), dto.imageCredit(), dto.sourceUrl()));
      return;
    }
    a.setPicture(Pictures.merge(a.getPicture(), dto.image(), dto.imageLarge(), dto.thumbnail(),
        dto.imageTitle(), dto.imageCredit(), dto.sourceUrl()));
  }

  /** Saves the upload as this astronaut's portrait and answers the absolute URL that serves it. */
  private String storePortrait(Astronaut a, DataUris.Decoded image) {
    if (a.getId() == null) {
      // create assigns the id on save, after this: the portrait is uploaded on the record's edit
      a.setId(astronauts.saveAndFlush(a).getId());
    }
    var portrait = portraits.findById(a.getId()).orElseGet(Portrait::new);
    portrait.setAstronautId(a.getId());
    portrait.setContentType(image.contentType());
    portrait.setBytes(image.bytes());
    portrait.setVersion(version(image.bytes()));
    portraits.save(portrait);
    return baseUrl() + urlOf(a.getId()) + "/portrait?v=" + portrait.getVersion();
  }

  /**
   * Absolute, because the page showing it is on ANOTHER origin (the UI): a relative URL would be
   * read against the UI's host. `space.public-base-url` when set, else the origin this request
   * reached (behind the ingress, from its X-Forwarded-* headers).
   */
  private String baseUrl() {
    return publicBaseUrl.isEmpty()
        ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
        : publicBaseUrl;
  }

  private static String version(byte[] bytes) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
