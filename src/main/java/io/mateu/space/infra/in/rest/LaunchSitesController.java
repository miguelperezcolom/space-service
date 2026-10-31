package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.LaunchSite;
import io.mateu.space.domain.Picture;
import io.mateu.space.infra.in.rest.dto.LaunchSiteDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.out.LaunchRepository;
import io.mateu.space.infra.out.LaunchSiteRepository;
import io.mateu.space.infra.out.SpaceRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/launch-sites")
@Tag(name = "Launch sites", description = "Launch pads, with their coordinates")
public class LaunchSitesController extends CrudController<LaunchSite, LaunchSiteDto> {

  private final LaunchSiteRepository sites;
  private final LaunchRepository launches;

  public LaunchSitesController(LaunchSiteRepository sites, LaunchRepository launches) {
    this.sites = sites;
    this.launches = launches;
  }

  @Override
  protected String collection() {
    return "launch-sites";
  }

  @Override
  protected SpaceRepository<LaunchSite> repository() {
    return sites;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("name", FilterKind.TEXT)
        .filter("country", FilterKind.EXACT)
        .filter("region", FilterKind.TEXT)
        .filter("operator", FilterKind.TEXT)
        .filter("active", FilterKind.BOOLEAN)
        .filter("latitude", FilterKind.NUMBER)
        .filter("longitude", FilterKind.NUMBER)
        .searchable("name", "country", "region", "operator")
        .defaultSort("name")
        .build();
  }

  @Override
  protected LaunchSite newEntity() {
    return new LaunchSite();
  }

  @Override
  protected Long idOf(LaunchSiteDto dto) {
    return dto.id();
  }

  @Override
  protected LaunchSiteDto toDto(LaunchSite s) {
    Picture p = s.getPicture() == null ? new Picture() : s.getPicture();
    return new LaunchSiteDto(
        s.getId(),
        s.getName(),
        s.getCountry(),
        s.getRegion(),
        s.getOperator(),
        s.getLatitude(),
        s.getLongitude(),
        s.getActive(),
        s.getDescription(),
        (int) launches.count((root, q, cb) -> cb.equal(root.get("site").get("id"), s.getId())),
        s.getWikidataId(),
        wikidataUrl(s.getWikidataId()),
        urlOf(s.getId()),
        p.getImage(),
        p.getImageLarge(),
        p.getThumbnail(),
        p.getImageTitle(),
        p.getImageCredit(),
        p.getSourceUrl());
  }

  @Override
  protected void apply(LaunchSiteDto dto, LaunchSite s) {
    s.setName(dto.name());
    s.setCountry(dto.country());
    s.setRegion(dto.region());
    s.setOperator(dto.operator());
    s.setLatitude(dto.latitude());
    s.setLongitude(dto.longitude());
    s.setActive(dto.active() != null && dto.active());
    s.setDescription(dto.description());
    s.setWikidataId(keep(dto.wikidataId(), s.getWikidataId()));
    s.setPicture(Pictures.merge(s.getPicture(), dto.image(), dto.imageLarge(), dto.thumbnail(),
        dto.imageTitle(), dto.imageCredit(), dto.sourceUrl()));
  }
}
