# space-service — a writable space-exploration API

A REST API over NASA's missions, the people who flew them, the spacecraft (down to their
instruments), the launch sites, the launches, the bodies of the solar system, and — synthetic —
the work left on the missions in preparation and the budget split by program. It exists so a
[Mateu](https://github.com/miguelperezcolom/mateu) app can demonstrate everything a business UI
does — search, filters, ranges, paging, writes, references, trees, a map, a calendar, a board and
charts — against a real backend with good, legitimately usable pictures.

It is modelled on [swapi-service](https://github.com/miguelperezcolom/swapi-service) (same generic
`CrudController`, same query contract, same write key), which it replaces as the Mateu demo backend:
the Star Wars photographs could not be used under a free licence; NASA's can.

Spring Boot 4, Java 21, H2 in memory, seeded at boot from `src/main/resources/seed/*.json`. Port
**8122**. The service never calls NASA or Wikidata at runtime: the seed is a snapshot, built once by
`scripts/build-seed.py`.

## The data model

```
CelestialBody ──parent──▶ CelestialBody         (a TREE: Sun → Jupiter → Europa)
      ▲
      │ destination
Mission ──launchSite──▶ LaunchSite (lat/lon)    Launch ──mission──▶ Mission
   │    ──spacecraft──▶ Spacecraft                     ──site─────▶ LaunchSite
   │                        ▲
   └──crew (M:N)──▶ Astronaut    SpacecraftComponent ──spacecraft, parent──▶ (a TREE:
                                                       spacecraft → subsystem → instrument)
MissionTask ──mission──▶ Mission        BudgetLine (fiscal year × program)
```

| collection | rows | owns | real or synthetic |
|---|---|---|---|
| `/api/missions` | 54 | `destinationId`, `launchSiteId`, `spacecraftId`, `crewIds`, `gallery` | real (curated list; facts from Wikidata) |
| `/api/astronauts` | 77 | — (their missions are the inverse of a crew) | real (Wikidata) |
| `/api/spacecraft` | 43 | — | real (curated; mass from Wikidata where it has it) |
| `/api/components` | 138 | `spacecraftId`, `parentId` | real instrument names; the grouping into subsystems is ours |
| `/api/bodies` | 31 | `parentId` | real (Wikidata) |
| `/api/launch-sites` | 15 | — | real (Wikidata, with coordinates) |
| `/api/launches` | 50 | `missionId`, `siteId` | real (Wikidata) |
| `/api/tasks` | 53 | `missionId` | **synthetic** |
| `/api/budgets` | 135 | — | yearly totals from Wikidata (interpolated); **split by program synthetic** |

An association is written on the side that **owns** it: a mission's crew on the mission. The other
side is read-only — an astronaut lists `missionIds`, but joins a crew through the mission.

## Reading

```bash
GET /api/missions?search=apollo                       # free text over the collection's text fields
GET /api/missions?status=ACTIVE                       # a declared filter (EXACT: a pick, not a prefix)
GET /api/missions?status=ACTIVE,PLANNED               # comma-joined or repeated = OR (a multi-select)
GET /api/missions?launchDate_from=2000-01-01&launchDate_to=2009-12-31   # inclusive ranges
GET /api/missions?costUsd_from=1000000000&sort=costUsd,desc
GET /api/missions?destinationId=6&type=ROVER          # by reference
GET /api/missions?crewId=5                            # by member of a to-many (Armstrong's flights)
GET /api/missions?featured=true                       # boolean
GET /api/missions?sort=destinationName                # a table sorts by the column it shows
GET /api/astronauts?page=2&size=20
```

The response is an envelope, because a bare array cannot say how many rows matched:

```json
{ "content": [ … ], "totalElements": 54, "totalPages": 3, "page": 0, "size": 20 }
```

A **filter the collection does not declare is a 400** naming what is supported, never a silently
unfiltered page. Each collection's filters are in its controller's `querySpec()` and in the OpenAPI
(`/swagger-ui.html`, `/v3/api-docs`).

References come back as ids plus the display name for the single-valued ones
(`destinationId` + `destinationName`), and as ids plus names for the crew (`crewIds`, `crewNames`),
so a table renders without a request per row.

### Beyond the collections

| endpoint | what for |
|---|---|
| `GET /api/bodies/tree[?search=]` | the solar system as a tree — each node's `children` hold what orbits it; same envelope, unpaged; with `search`, the branches that lead to a match |
| `GET /api/spacecraft/tree[?search=]` | spacecraft → subsystem → instrument, for every spacecraft with a breakdown |
| `GET /api/spacecraft/{id}/components/tree` | one spacecraft's breakdown |
| `GET /api/search?search=mars[&kind=MISSION,BODY]` | one search over missions, astronauts, spacecraft, bodies and launch sites; each hit carries the `route` that opens it (`bodies/6`) |
| `GET /api/stats` | counts and chart series: launches per decade and year, missions by status/type/program/destination, budget per year and program, tasks by status |
| `POST /api/tasks/{id}/move?status=DONE` | move a card to another column (sets the progress a status implies) |
| `GET /api/credits` | every photograph, with its credit and source page |

## Writing

Reads are open. **Writes need the `X-Api-Key` header** (`space.write-key`, env `WRITE_KEY`). Without
a key configured every write is refused with 401 — a deployment that forgot one is inert, not open.

```bash
curl -X POST localhost:8122/api/missions -H 'Content-Type: application/json' -H 'X-Api-Key: localdev' \
  -d '{"name":"Mars Sample Return","type":"SAMPLE_RETURN","status":"PLANNED","destinationId":6}'
```

`POST` → 201 with a `Location`; `PUT /{id}` replaces; `DELETE /{id}` → 204. A write pointing at a
row that does not exist (`destinationId: 9999`) is a **400**; a value outside a closed set
(`status: "bogus"`) is a 400 naming the accepted ones; deleting a row others still refer to (a
mission with launches) is a **409** and removes nothing; a collection at its row ceiling
(`space.max-rows-per-collection`, 2000) answers **507**.

Three rules make the API writable **from a declarative UI**, which builds its body by interpolating
a JSON template (`{"costUsd":"${state.costUsd}"}`):

- an **empty string is null** for every type — numbers and dates included;
- an **id list** is read from an array, `"1,2"` or `"[1, 2]"`;
- on a `PUT`, the **picture fields, the gallery and the crew are kept when the body leaves them
  out**: a form that edits a mission's facts does not wipe its photographs. Given (even empty), they
  are replaced.

**The database is in memory and re-seeded on every restart**, so anything written is demo data with
a short life.

## Where the data comes from

`scripts/build-seed.py` (standard-library Python) builds the seed once and is committed with it. Its
HTTP responses are cached in `scripts/.cache/` (git-ignored); `--refresh` ignores the cache.

- **Wikidata** ([CC0](https://www.wikidata.org/wiki/Wikidata:Licensing)) — facts and relations:
  launch dates (and times, where Wikidata refines them), launch sites with their coordinates,
  launch vehicles, crews, end dates of crewed flights, astronauts' birth and death dates,
  nationality, astronaut group and time in space, the bodies' parents, radii, masses, orbital
  periods and gravity, some costs and masses, the short descriptions (`summary`), and NASA's
  total budget for 2011, 2021 and 2023.
- **NASA Image and Video Library** (`images-api.nasa.gov`) — every photograph, linked at
  `images-assets.nasa.gov` in three sizes (`thumbnail`, `image`, `imageLarge`), each with its
  `imageTitle`, `imageCredit` (from the asset's own metadata: secondary creator, photographer or
  NASA centre) and `sourceUrl` (its page on images.nasa.gov). Picked by search and scoring, with a
  hand-picked asset where the search's first guess was poor.
- **Curated in the script** — which missions, spacecraft and bodies exist; mission program, type,
  status and spacecraft; the objectives (short original text, rich text); spacecraft type,
  manufacturer, power; the robotic missions' end dates (last contact); approximate EVA hours for a
  few astronauts whose totals are well documented (null elsewhere); which astronauts are still
  active; the instrument lists of nine spacecraft (real instrument names; the subsystem grouping is
  ours). The short astronaut biographies are generated from the facts above.
- **Synthetic, generated deterministically** (fixed seeds, fixed "today" 2026-10-31) — the 53 mission
  tasks (teams, not people, as owners) and the budget's split by program around Wikidata's totals.
  Every budget line says so (`synthetic: true`, `basis`).

Not used: Launch Library / The Space Devs (its terms ask for attribution and restrict use) and
Wikipedia prose (CC BY-SA). Launch outcomes are SUCCESS except STS-51-L (Challenger).

## Media usage

NASA content is generally **not subject to copyright in the United States** and may be used for
educational or informational purposes, with NASA acknowledged as the source — see NASA's
[Images and Media guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/). Two
caveats the seed respects:

- **The NASA insignia ("meatball"), logotype and seal are not in the public domain** and must not
  be used to imply endorsement. The seed takes photographs and drops assets titled or tagged as a
  patch, insignia, emblem, logo or seal; a photograph may still show a mark on a rocket or a suit,
  as a photograph of it. Do not use these pictures to suggest that NASA endorses a product.
- **NASA sometimes publishes third parties' material with permission.** The seed leaves out assets
  whose credit names a company or another agency (SpaceX, Boeing, ESA, ISRO…) or a CC-licensed
  processing. Credits that name a NASA centre, JPL-Caltech or a university team are NASA mission
  data, used as NASA asks: with the credit shown.

Every record with a picture carries its credit and source; [CREDITS.md](CREDITS.md) lists them all,
as does `/api/credits`. Credit "NASA" (or the line given) wherever a picture is shown.

## Running it locally

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run -Dspring-boot.run.arguments=--space.write-key=localdev
# → http://localhost:8122/swagger-ui.html
```

```bash
mvn verify      # 21 integration tests over the real seeded dataset
```

CORS answers any origin (writes included, with the key header allowed in the preflight), so a
browser page with no backend of its own can call it directly.
