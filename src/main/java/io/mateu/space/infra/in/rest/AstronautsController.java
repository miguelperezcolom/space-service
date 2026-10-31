package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.Astronaut;
import io.mateu.space.domain.Mission;
import io.mateu.space.domain.Picture;
import io.mateu.space.infra.in.rest.dto.AstronautDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.out.AstronautRepository;
import io.mateu.space.infra.out.SpaceRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/astronauts")
@Tag(name = "Astronauts", description = "The people who flew the crewed missions")
public class AstronautsController extends CrudController<Astronaut, AstronautDto> {

  static final List<String> STATUSES = List.of("ACTIVE", "RETIRED", "DECEASED");

  private final AstronautRepository astronauts;

  public AstronautsController(AstronautRepository astronauts) {
    this.astronauts = astronauts;
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
    a.setPicture(Pictures.merge(a.getPicture(), dto.image(), dto.imageLarge(), dto.thumbnail(),
        dto.imageTitle(), dto.imageCredit(), dto.sourceUrl()));
  }
}
