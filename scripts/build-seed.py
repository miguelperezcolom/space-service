#!/usr/bin/env python3
"""
Builds the space-service seed (src/main/resources/seed/*.json) ONCE, from public sources:

  * Wikidata (CC0)                        — facts and relations: launch dates and sites, launch
                                            vehicles, crews, birth dates, time in space, parent
                                            bodies, radii, masses, orbital periods, coordinates…
  * NASA Image and Video Library          — the photographs (images-api.nasa.gov search, linked
                                            at images-assets.nasa.gov), with the credit taken
                                            from each asset's own metadata.
  * this file                             — the curated part: WHICH records exist, the editorial
                                            text (objectives), the fields neither source has in a
                                            usable shape (program, type, status, spacecraft
                                            components), and the SYNTHETIC collections (mission
                                            tasks, budget split), generated deterministically.

The service never calls any of these at runtime: it reads the JSON this writes. Run it again only to
refresh the seed; the responses are cached in scripts/.cache (git-ignored), so a re-run with the
cache is offline and reproduces the same files.

    python3 scripts/build-seed.py            # uses the cache when present
    python3 scripts/build-seed.py --refresh  # ignores the cache

Standard library only.
"""

import datetime as dt
import hashlib
import json
import random
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "src" / "main" / "resources" / "seed"
CACHE = Path(__file__).resolve().parent / ".cache"
UA = "space-service-seed/0.1 (https://github.com/miguelperezcolom; seed builder, run once)"
REFRESH = "--refresh" in sys.argv

# A fixed "today", so a re-run next year produces the same statuses and the same synthetic dates.
TODAY = dt.date(2026, 10, 31)

# ─────────────────────────────────────────────────────────────────────────────────────────────────
# HTTP with a cache
# ─────────────────────────────────────────────────────────────────────────────────────────────────


def get_json(url):
    CACHE.mkdir(exist_ok=True)
    key = CACHE / (hashlib.sha1(url.encode()).hexdigest() + ".json")
    if key.exists() and not REFRESH:
        return json.loads(key.read_text())
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.loads(r.read().decode("utf-8"))
            if isinstance(data, dict) and "error" in data:
                raise RuntimeError(json.dumps(data["error"])[:300])
            key.write_text(json.dumps(data))
            time.sleep(0.15)
            return data
        except Exception as e:  # noqa: BLE001 — retry anything, then give up loudly
            if attempt == 3:
                raise RuntimeError(f"GET {url} failed: {e}") from e
            time.sleep(2 * (attempt + 1))


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# Wikidata
# ─────────────────────────────────────────────────────────────────────────────────────────────────

WD_API = "https://www.wikidata.org/w/api.php"


def wd_by_titles(titles):
    """enwiki title → entity. Titles are resolved through the sitelink, which is unambiguous."""
    out = {}
    titles = list(dict.fromkeys(titles))
    for i in range(0, len(titles), 50):
        chunk = titles[i:i + 50]
        q = urllib.parse.urlencode({
            "action": "wbgetentities", "sites": "enwiki", "titles": "|".join(chunk),
            "props": "claims|labels|descriptions|sitelinks", "languages": "en|mul",
            "sitefilter": "enwiki", "format": "json"})
        data = get_json(f"{WD_API}?{q}")
        for qid, e in data.get("entities", {}).items():
            if qid.startswith("-") or "missing" in e:
                continue
            title = e.get("sitelinks", {}).get("enwiki", {}).get("title")
            if title:
                out[title] = e
    missing = [t for t in titles if t not in out and t.replace("_", " ") not in out]
    if missing:
        print("  ! no Wikidata entity for:", missing, file=sys.stderr)
    return out


def wd_by_ids(ids):
    out = {}
    ids = [i for i in dict.fromkeys(ids) if i]
    for i in range(0, len(ids), 50):
        q = urllib.parse.urlencode({
            "action": "wbgetentities", "ids": "|".join(ids[i:i + 50]),
            "props": "claims|labels|descriptions", "languages": "en|mul", "format": "json"})
        out.update(get_json(f"{WD_API}?{q}").get("entities", {}))
    return out


def claims(e, prop):
    return [c for c in e.get("claims", {}).get(prop, []) if c.get("rank") != "deprecated"]


def best(e, prop):
    cs = claims(e, prop)
    preferred = [c for c in cs if c.get("rank") == "preferred"]
    return (preferred or cs or [None])[0]


def value(c):
    if not c:
        return None
    return c.get("mainsnak", {}).get("datavalue", {}).get("value")


def item_ids(e, prop):
    return [v["id"] for v in (value(c) for c in claims(e, prop)) if isinstance(v, dict) and "id" in v]


def item_id(e, prop):
    ids = item_ids(e, prop)
    return ids[0] if ids else None


TIME_ITEMS = {}


def wd_time(e, prop):
    """(date, time-or-None) of a time claim; the time only when Wikidata knows it to the minute —
    either in the value itself or as a 'refine date' (P4241) qualifier naming the time of day."""
    c = best(e, prop)
    v = value(c)
    refine = None
    if c:
        q = c.get("qualifiers", {}).get("P4241", [{}])[0].get("datavalue", {}).get("value", {})
        refine = TIME_ITEMS.get(q.get("id")) if isinstance(q, dict) else None
    if not v or "time" not in v:
        return None, None
    raw, precision = v["time"], v.get("precision", 11)
    m = re.match(r"^\+(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})Z$", raw)
    if not m:
        return None, None
    y, mo, d, hh, mi, _ = (int(x) for x in m.groups())
    if precision < 11 or mo == 0 or d == 0:
        return None, None
    t = f"{hh:02d}:{mi:02d}" if precision >= 13 else (refine[:5] if refine else None)
    return dt.date(y, mo, d).isoformat(), t


def load_time_items(entities):
    """The 'refine date' qualifiers point at items labelled with a time of day ("22:35:12")."""
    ids = []
    for e in entities:
        for c in claims(e, "P619"):
            q = c.get("qualifiers", {}).get("P4241", [{}])[0].get("datavalue", {}).get("value", {})
            if isinstance(q, dict) and q.get("id"):
                ids.append(q["id"])
    for qid, e in wd_by_ids(ids).items():
        lab = label(e) or ""
        if re.match(r"^\d{2}:\d{2}(:\d{2})?$", lab):
            TIME_ITEMS[qid] = lab


UNIT = {
    "Q828224": ("km", 1.0), "Q11573": ("km", 0.001), "Q11570": ("kg", 1.0), "Q191118": ("kg", 1000.0),
    "Q573": ("d", 1.0), "Q577": ("d", 365.25), "Q1092296": ("d", 365.25), "Q25235": ("d", 1 / 24),
    "Q7727": ("min", 1.0), "Q11574": ("min", 1 / 60), "Q39369": ("m/s2", 1.0),
    "Q25236": ("W", 1000.0), "Q25250": ("W", 1.0), "Q11579": ("K", 1.0),
    "Q613726": ("kg", 1e21), "Q180892": ("kg", 1.98847e30), "Q1051665": ("m/s2", 1.0),
    "Q39699418": ("m/s2", 0.01),
}


def wd_quantity(e, prop, want):
    """A quantity claim in the unit `want` ('km', 'kg', 'd', 'min', 'm/s2', 'W'), or None."""
    for c in [best(e, prop)] + claims(e, prop):
        v = value(c)
        if not v or "amount" not in v:
            continue
        unit = v.get("unit", "1").rsplit("/", 1)[-1]
        if unit in UNIT and UNIT[unit][0] == want:
            return float(v["amount"]) * UNIT[unit][1]
    return None


def coords(e):
    v = value(best(e, "P625"))
    return (round(v["latitude"], 5), round(v["longitude"], 5)) if v else (None, None)


def label(e):
    labels = (e or {}).get("labels", {})
    return (labels.get("en") or labels.get("mul") or {}).get("value")


def description(e):
    return e.get("descriptions", {}).get("en", {}).get("value")


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# NASA Image and Video Library
# ─────────────────────────────────────────────────────────────────────────────────────────────────

NASA_API = "https://images-api.nasa.gov/search"
# A NASA insignia, patch or logo must not be used in a way that implies endorsement, so the seed
# takes photographs and leaves those out wherever they would be picked.
NO_LOGOS = re.compile(r"\b(patch|insignia|emblem|logo|meatball|seal|decal|sticker|graphic)\b", re.I)


# A NASA-published asset whose credit names a third party may carry that party's terms (a company's
# photo, a processed image under CC BY, ESA's or ISRO's data): only NASA's own material is taken.
THIRD_PARTY = re.compile(r"spacex|axiom|blue origin|boeing|ula\b|©|cc by|all right|gill|\besa\b|isro|getty|reuters|\bap\b", re.I)


def nasa_search(query=None, nasa_id=None, pages=1):
    items = []
    for page in range(1, pages + 1):
        params = {"media_type": "image", "page": page}
        if nasa_id:
            params["nasa_id"] = nasa_id
        else:
            params["q"] = query
        data = get_json(f"{NASA_API}?{urllib.parse.urlencode(params)}")
        items += data.get("collection", {}).get("items", [])
        if nasa_id:
            break
    return items


def short(text, limit=200):
    """A NASA title is sometimes a whole caption; a caption is cut to its first sentence or so."""
    if not text:
        return text
    text = re.sub(r"\s+", " ", text).strip()
    return text if len(text) <= limit else text[:limit - 1].rsplit(" ", 1)[0] + "…"


def to_image(item):
    d = item["data"][0]
    links = {}
    for link in item.get("links", []):
        href = link.get("href", "")
        m = re.search(r"~(thumb|small|medium|large|orig)\.\w+$", href)
        if m:
            links[m.group(1)] = href
    if not links:
        return None
    nasa_id = d["nasa_id"]
    credit = (d.get("secondary_creator") or "").strip()
    photographer = (d.get("photographer") or "").strip()
    if not credit:
        credit = "NASA" + (f" / {photographer}" if photographer and "nasa" not in photographer.lower()
                           else (f" ({d['center']})" if d.get("center") else ""))
    elif "nasa" not in credit.lower():
        credit = f"NASA / {credit}"
    credit = credit.replace("NASA / N/A", "NASA")
    return {
        "nasaId": nasa_id,
        "title": short(d.get("title")),
        "thumbnail": links.get("thumb") or links.get("small"),
        "image": links.get("medium") or links.get("small") or links.get("thumb"),
        "imageLarge": links.get("large") or links.get("orig") or links.get("medium"),
        "imageCredit": credit[:250],
        "sourceUrl": f"https://images.nasa.gov/details/{urllib.parse.quote(nasa_id)}",
        "dateCreated": (d.get("date_created") or "")[:10] or None,
    }


def pick_images(query, must=(), prefer=(), avoid=(), count=1, pages=1, pinned=None):
    """The best `count` photographs for a query, scored by what their title says."""
    if pinned == "":  # no photograph of it exists (an asteroid seen only in artists' concepts)
        return []
    if pinned:
        found = [to_image(i) for i in nasa_search(nasa_id=pinned)]
        found = [f for f in found if f and not THIRD_PARTY.search(f["imageCredit"] or "")]
        if found:
            return found[:1] + (pick_images(query, must, prefer, avoid, count - 1, pages) if count > 1 and query else [])
        if not query:
            return []
    scored = []
    for rank, item in enumerate(nasa_search(query, pages=pages)):
        d = item["data"][0]
        title = d.get("title") or ""
        text = " ".join([title, " ".join(d.get("keywords") or [])])
        if NO_LOGOS.search(title) or NO_LOGOS.search(" ".join(d.get("keywords") or [])):
            continue
        low = title.lower()
        if must and not all(m.lower() in text.lower() for m in must):
            continue
        if any(a.lower() in low for a in avoid):
            continue
        score = -rank * 0.01
        score += sum(2 for p in prefer if p.lower() in text.lower())
        img = to_image(item)
        if img and not THIRD_PARTY.search(img["imageCredit"] or ""):
            scored.append((score, img))
    scored.sort(key=lambda s: -s[0])
    out, seen = [], set()
    for _, img in scored:
        if img["nasaId"] not in seen:
            seen.add(img["nasaId"])
            out.append(img)
        if len(out) >= count:
            break
    return out


def no_image():
    return {"image": None, "imageLarge": None, "thumbnail": None, "imageTitle": None, "imageCredit": None, "sourceUrl": None}


def image_fields(images):
    if not images:
        return no_image()
    i = images[0]
    return {"image": i["image"], "imageLarge": i["imageLarge"], "thumbnail": i["thumbnail"],
            "imageTitle": i["title"], "imageCredit": i["imageCredit"], "sourceUrl": i["sourceUrl"]}


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# The curated part
# ─────────────────────────────────────────────────────────────────────────────────────────────────

# id, enwiki title, type, image query, pinned NASA id (optional)
BODIES = [
    (1, "Sun", "STAR", "Sun Solar Dynamics Observatory", None),
    (2, "Mercury (planet)", "PLANET", "Mercury MESSENGER global", "PIA12397"),
    (3, "Venus", "PLANET", "Venus Magellan", None),
    (4, "Earth", "PLANET", "Blue Marble Earth", None),
    (5, "Moon", "MOON", "full Moon", "as11-44-6667"),
    (6, "Mars", "PLANET", "Mars Viking global mosaic", "PIA00003"),
    (7, "Phobos (moon)", "MOON", "Phobos", None),
    (8, "Deimos (moon)", "MOON", "Deimos", None),
    (9, "Ceres (dwarf planet)", "DWARF_PLANET", "Ceres Dawn", None),
    (10, "Jupiter", "PLANET", "Jupiter Cassini", None),
    (11, "Io (moon)", "MOON", "Io Galileo", None),
    (12, "Europa (moon)", "MOON", "Europa Galileo", None),
    (13, "Ganymede (moon)", "MOON", "Ganymede", None),
    (14, "Callisto (moon)", "MOON", "Callisto", None),
    (15, "Saturn", "PLANET", "Saturn Cassini", None),
    (16, "Titan (moon)", "MOON", "Titan Cassini", None),
    (17, "Enceladus", "MOON", "Enceladus", "PIA06443"),
    (18, "Mimas", "MOON", "Mimas", None),
    (19, "Uranus", "PLANET", "Uranus Voyager 2", None),
    (20, "Miranda (moon)", "MOON", "Miranda Voyager", None),
    (21, "Titania (moon)", "MOON", "Titania Voyager", None),
    (22, "Neptune", "PLANET", "Neptune Voyager 2", None),
    (23, "Triton (moon)", "MOON", "Triton Voyager", None),
    (24, "Pluto", "DWARF_PLANET", "Pluto New Horizons", None),
    (25, "Charon (moon)", "MOON", "Charon New Horizons", None),
    (26, "486958 Arrokoth", "KUIPER_BELT_OBJECT", "Arrokoth", ""),
    (27, "101955 Bennu", "ASTEROID", "Bennu OSIRIS-REx", "Bennu_Sample2"),
    (28, "65803 Didymos", "ASTEROID", "Didymos DART", None),
    (29, "Dimorphos", "MOON", "Dimorphos DART", ""),
    (30, "16 Psyche", "ASTEROID", "Psyche asteroid", ""),
    (31, "Eris (dwarf planet)", "DWARF_PLANET", "Eris dwarf planet", ""),
]

# id, name, type, manufacturer, mass kg, power W, power source, image query, enwiki title (for mass/manufacturer)
SPACECRAFT = [
    (1, "Freedom 7 (Mercury capsule)", "CAPSULE", "McDonnell Aircraft", 1295, None, "BATTERY", "Freedom 7 spacecraft", None),
    (2, "Friendship 7 (Mercury capsule)", "CAPSULE", "McDonnell Aircraft", 1355, None, "BATTERY", "Friendship 7 spacecraft", None),
    (3, "Gemini spacecraft", "CAPSULE", "McDonnell Aircraft", 3790, None, "FUEL_CELL", "Gemini spacecraft", None),
    (4, "Apollo Command and Service Module", "CAPSULE", "North American Aviation", 28800, 2300, "FUEL_CELL", "Apollo Command and Service Module", None),
    (5, "Apollo Lunar Module", "LANDER", "Grumman", 15200, None, "BATTERY", "Apollo Lunar Module", None),
    (6, "Skylab", "STATION", "McDonnell Douglas", 77088, 11000, "SOLAR", "Skylab space station", None),
    (7, "Space Shuttle Columbia", "ORBITER", "Rockwell International", 81700, 21000, "FUEL_CELL", "Space Shuttle Columbia", "Space Shuttle Columbia"),
    (8, "Space Shuttle Challenger", "ORBITER", "Rockwell International", 79900, 21000, "FUEL_CELL", "Space Shuttle Challenger", "Space Shuttle Challenger"),
    (9, "Space Shuttle Discovery", "ORBITER", "Rockwell International", 78000, 21000, "FUEL_CELL", "Space Shuttle Discovery", "Space Shuttle Discovery"),
    (10, "Space Shuttle Endeavour", "ORBITER", "Rockwell International", 78000, 21000, "FUEL_CELL", "Space Shuttle Endeavour", "Space Shuttle Endeavour"),
    (11, "Space Shuttle Atlantis", "ORBITER", "Rockwell International", 77700, 21000, "FUEL_CELL", "Space Shuttle Atlantis", "Space Shuttle Atlantis"),
    (12, "International Space Station", "STATION", "Boeing, Energia and partners", 450000, 120000, "SOLAR", "International Space Station", "International Space Station"),
    (13, "Crew Dragon", "CAPSULE", "SpaceX", 12519, 4000, "SOLAR", "Crew Dragon spacecraft", None),
    (14, "Orion", "CAPSULE", "Lockheed Martin and Airbus", 26520, 11000, "SOLAR", "Orion spacecraft", None),
    (15, "Pioneer 10", "PROBE", "TRW", 258, 155, "RTG", "Pioneer 10", None),
    (16, "Viking 1 Lander", "LANDER", "Martin Marietta", 572, 70, "RTG", "Viking lander", None),
    (17, "Voyager 1", "PROBE", "Jet Propulsion Laboratory", 825, 470, "RTG", "Voyager spacecraft", None),
    (18, "Voyager 2", "PROBE", "Jet Propulsion Laboratory", 825, 470, "RTG", "Voyager 2 spacecraft", None),
    (19, "Galileo", "ORBITER", "Jet Propulsion Laboratory", 2562, 570, "RTG", "Galileo spacecraft", None),
    (20, "Sojourner", "ROVER", "Jet Propulsion Laboratory", 11.5, 16, "SOLAR", "Sojourner rover", None),
    (21, "Cassini", "ORBITER", "Jet Propulsion Laboratory", 2125, 885, "RTG", "Cassini spacecraft Saturn", None),
    (22, "Spirit", "ROVER", "Jet Propulsion Laboratory", 185, 140, "SOLAR", "Spirit rover", None),
    (23, "Opportunity", "ROVER", "Jet Propulsion Laboratory", 185, 140, "SOLAR", "Opportunity rover", None),
    (24, "New Horizons", "PROBE", "Johns Hopkins APL", 478, 245, "RTG", "New Horizons spacecraft", None),
    (25, "Kepler", "TELESCOPE", "Ball Aerospace", 1052, 1100, "SOLAR", "Kepler spacecraft", None),
    (26, "Curiosity", "ROVER", "Jet Propulsion Laboratory", 899, 110, "RTG", "Curiosity rover selfie", None),
    (27, "Juno", "ORBITER", "Lockheed Martin", 3625, 500, "SOLAR", "Juno spacecraft", None),
    (28, "OSIRIS-REx", "PROBE", "Lockheed Martin", 2110, 3000, "SOLAR", "OSIRIS-REx spacecraft", None),
    (29, "InSight", "LANDER", "Lockheed Martin", 358, 600, "SOLAR", "InSight lander", None),
    (30, "Parker Solar Probe", "PROBE", "Johns Hopkins APL", 685, 388, "SOLAR", "Parker Solar Probe", None),
    (31, "TESS", "TELESCOPE", "Orbital ATK", 362, 433, "SOLAR", "TESS spacecraft", None),
    (32, "Perseverance", "ROVER", "Jet Propulsion Laboratory", 1025, 110, "RTG", "Perseverance rover", None),
    (33, "Ingenuity", "HELICOPTER", "Jet Propulsion Laboratory", 1.8, 350, "SOLAR", "Ingenuity helicopter", None),
    (34, "DART", "PROBE", "Johns Hopkins APL", 610, 6600, "SOLAR", "DART spacecraft", None),
    (35, "Lucy", "PROBE", "Lockheed Martin", 1550, 500, "SOLAR", "Lucy spacecraft", None),
    (36, "Psyche", "PROBE", "Maxar Technologies", 2608, 4500, "SOLAR", "Psyche spacecraft", None),
    (37, "Europa Clipper", "ORBITER", "Jet Propulsion Laboratory", 6065, 600, "SOLAR", "Europa Clipper spacecraft", None),
    (38, "Hubble Space Telescope", "TELESCOPE", "Lockheed Martin", 11110, 2800, "SOLAR", "Hubble Space Telescope", None),
    (39, "James Webb Space Telescope", "TELESCOPE", "Northrop Grumman", 6161, 2000, "SOLAR", "James Webb Space Telescope mirror", None),
    (40, "Lunar Reconnaissance Orbiter", "ORBITER", "Goddard Space Flight Center", 1916, 1850, "SOLAR", "Lunar Reconnaissance Orbiter", None),
    (41, "Dragonfly", "HELICOPTER", "Johns Hopkins APL", 875, 70, "RTG", "Dragonfly Titan", None),
    (42, "Nancy Grace Roman Space Telescope", "TELESCOPE", "Goddard Space Flight Center", 4166, None, "SOLAR", "Roman Space Telescope", None),
    (43, "MESSENGER", "ORBITER", "Johns Hopkins APL", 1107, 450, "SOLAR", "MESSENGER spacecraft", None),
]

# Photographs picked by hand where the search's best guess was not the spacecraft itself.
SPACECRAFT_PINS = {1: "S61-02547", 5: "as11-44-6642", 12: "s132e013215", 16: "PIA00565", 21: "PIA04603",
                   27: "PIA13928", 38: "s125e011763", 41: "GSFC_20261020_DRAGONFLY_044216", 43: "KSC-04pd0431",
                   24: "KSC-05pd2273"}
SITE_PINS = {"Gagarin's Start": "201305260002HQ", "Cape Canaveral Space Launch Complex 40": "KSC-20181205-PH_KLS01_0093",
             "Vandenberg Space Launch Complex 3 East": "NHQ202109270013", "Cape Canaveral Space Launch Complex 14": "KSC-62C-0363"}

# id, enwiki title, display name, program, agency, type, spacecraft id, destination body id, status,
# image query, extra words the title must contain (optional), objectives
M = "NASA"
MISSIONS = [
    (1, "Mercury-Redstone 3", "Freedom 7", "Mercury", M, "CREWED", 1, 4, "COMPLETED", "Freedom 7", (), ["Fly the first American into space on a suborbital trajectory.", "Test manual attitude control of the capsule by the pilot."]),
    (2, "Mercury-Atlas 6", "Friendship 7", "Mercury", M, "CREWED", 2, 4, "COMPLETED", "Friendship 7 Glenn", (), ["Put an American astronaut into Earth orbit.", "Observe the pilot's reactions to three orbits of weightlessness."]),
    (3, "Gemini 4", "Gemini 4", "Gemini", M, "CREWED", 3, 4, "COMPLETED", "Gemini 4", (), ["Perform the first American extravehicular activity.", "Fly a four-day mission to study long-duration effects."]),
    (4, "Gemini 8", "Gemini 8", "Gemini", M, "CREWED", 3, 4, "PARTIAL_SUCCESS", "Gemini 8", (), ["Perform the first docking of two spacecraft in orbit, with an Agena target vehicle.", "Recover from a stuck thruster — the mission ended early after the docking."]),
    (5, "Apollo 8", "Apollo 8", "Apollo", M, "CREWED", 4, 5, "COMPLETED", "Apollo 8", (), ["Carry the first crew around the Moon and back.", "Photograph candidate landing sites; return the Earthrise photograph."]),
    (6, "Apollo 11", "Apollo 11", "Apollo", M, "CREWED", 5, 5, "COMPLETED", "Apollo 11", (), ["Land two astronauts on the Moon and return them safely to Earth.", "Collect lunar samples and deploy the Early Apollo Scientific Experiments Package."]),
    (7, "Apollo 13", "Apollo 13", "Apollo", M, "CREWED", 4, 5, "FAILED", "Apollo 13", (), ["Land at Fra Mauro — abandoned after an oxygen tank exploded on the way out.", "Return the crew home safely using the Lunar Module as a lifeboat."]),
    (8, "Apollo 15", "Apollo 15", "Apollo", M, "CREWED", 5, 5, "COMPLETED", "Apollo 15 rover", (), ["First of the J missions: longer stays on the surface.", "First use of the Lunar Roving Vehicle, at Hadley–Apennine."]),
    (9, "Apollo 17", "Apollo 17", "Apollo", M, "CREWED", 5, 5, "COMPLETED", "Apollo 17", (), ["Last crewed landing on the Moon, in the Taurus–Littrow valley.", "Field geology by the first scientist on the Moon."]),
    (10, "Skylab 2", "Skylab 2", "Skylab", M, "CREWED", 6, 4, "COMPLETED", "Skylab 2", (), ["Repair the launch-damaged Skylab station.", "Run 28 days of solar and medical experiments."]),
    (11, "STS-1", "STS-1", "Space Shuttle", M, "CREWED", 7, 4, "COMPLETED", "STS-1 Columbia launch", (), ["First orbital flight of the Space Shuttle.", "Demonstrate a safe launch, orbit and runway landing."]),
    (12, "STS-7", "STS-7", "Space Shuttle", M, "CREWED", 8, 4, "COMPLETED", "STS-7", (), ["Deploy two communications satellites.", "First American woman in space."]),
    (13, "STS-41-B", "STS-41-B", "Space Shuttle", M, "CREWED", 8, 4, "COMPLETED", "41-B McCandless", (), ["First untethered spacewalk, with the Manned Maneuvering Unit.", "First landing at the Kennedy Space Center."]),
    (14, "STS-51-L", "STS-51-L", "Space Shuttle", M, "CREWED", 8, 4, "FAILED", "STS-51-L crew", (), ["Deploy a tracking and data relay satellite and fly the Teacher in Space.", "Lost 73 seconds after launch; the crew of seven died."]),
    (15, "STS-31", "STS-31", "Space Shuttle", M, "CREWED", 9, 4, "COMPLETED", "STS-31 Hubble deploy", (), ["Deploy the Hubble Space Telescope into low Earth orbit."]),
    (16, "STS-61", "STS-61", "Space Shuttle", M, "CREWED", 10, 4, "COMPLETED", "STS-61 Hubble servicing", (), ["First Hubble servicing mission: correct the telescope's optics.", "Five spacewalks to install WFPC2 and COSTAR."]),
    (17, "STS-95", "STS-95", "Space Shuttle", M, "CREWED", 9, 4, "COMPLETED", "STS-95 Glenn", (), ["Study ageing in space with John Glenn aboard, 36 years after his first flight."]),
    (18, "STS-107", "STS-107", "Space Shuttle", M, "CREWED", 7, 4, "FAILED", "STS-107 crew", (), ["Sixteen days of microgravity research in the Spacehab module.", "Columbia broke up on re-entry; the crew of seven died."]),
    (19, "STS-135", "STS-135", "Space Shuttle", M, "CREWED", 11, 4, "COMPLETED", "STS-135 Atlantis", (), ["Final flight of the Space Shuttle programme.", "Resupply the International Space Station."]),
    (20, "Expedition 1", "Expedition 1", "International Space Station", "NASA / Roscosmos", "CREWED", 12, 4, "COMPLETED", "Expedition 1 crew", (), ["First long-duration crew aboard the International Space Station."]),
    (21, "Crew Dragon Demo-2", "Crew Dragon Demo-2", "Commercial Crew", M, "CREWED", 13, 4, "COMPLETED", "Demo-2 Crew Dragon", (), ["First crewed orbital flight of a commercial spacecraft.", "Return human launches to American soil after the Shuttle's retirement."]),
    (22, "SpaceX Crew-1", "Crew-1", "Commercial Crew", M, "CREWED", 13, 4, "COMPLETED", "SpaceX Crew-1", (), ["First operational Commercial Crew rotation to the ISS."]),
    (23, "Artemis I", "Artemis I", "Artemis", M, "LUNAR_FLYBY", 14, 5, "COMPLETED", "Artemis I Orion", (), ["Uncrewed test of SLS and Orion in a distant retrograde lunar orbit.", "Validate Orion's heat shield at lunar-return speed."]),
    (24, "Artemis II", "Artemis II", "Artemis", M, "CREWED", 14, 5, None, "Artemis II crew", (), ["First crewed flight of SLS and Orion, on a lunar free-return trajectory.", "Test life support and crew operations in deep space."]),
    (25, "Artemis III", "Artemis III", "Artemis", M, "CREWED", 14, 5, "PLANNED", "Artemis III", (), ["Return astronauts to the lunar surface, near the south pole.", "Use a commercial Human Landing System."]),
    (26, "Pioneer 10", "Pioneer 10", "Pioneer", M, "FLYBY", 15, 10, "COMPLETED", "Pioneer 10", (), ["First spacecraft through the asteroid belt and past Jupiter."]),
    (27, "Viking 1", "Viking 1", "Viking", M, "LANDER", 16, 6, "COMPLETED", "Viking 1 lander Mars", (), ["First successful landing on Mars by a NASA spacecraft.", "Search for signs of life in the soil."]),
    (28, "Voyager 1", "Voyager 1", "Voyager", M, "FLYBY", 17, 10, "ACTIVE", "Voyager 1", (), ["Fly by Jupiter and Saturn, and Titan up close.", "Reach interstellar space (2012) — still returning data."]),
    (29, "Voyager 2", "Voyager 2", "Voyager", M, "FLYBY", 18, 22, "ACTIVE", "Voyager 2", (), ["The Grand Tour: the only spacecraft to visit Uranus and Neptune.", "Entered interstellar space in 2018."]),
    (30, "Galileo (spacecraft)", "Galileo", "Galileo", M, "ORBITER", 19, 10, "COMPLETED", "Galileo spacecraft Jupiter", (), ["First spacecraft to orbit Jupiter; drop a probe into its atmosphere.", "Find evidence of a subsurface ocean on Europa."]),
    (31, "Mars Pathfinder", "Mars Pathfinder", "Discovery", M, "ROVER", 20, 6, "COMPLETED", "Mars Pathfinder Sojourner", (), ["Demonstrate airbag landing and the first Mars rover, Sojourner."]),
    (32, "Cassini–Huygens", "Cassini–Huygens", "Flagship", "NASA / ESA / ASI", "ORBITER", 21, 15, "COMPLETED", "Cassini Saturn", (), ["Thirteen years orbiting Saturn; land the Huygens probe on Titan.", "Discover the plumes of Enceladus; end with the Grand Finale dive."]),
    (33, "Spirit (rover)", "Spirit", "Mars Exploration Rover", M, "ROVER", 22, 6, "COMPLETED", "Spirit rover Mars", (), ["Search Gusev crater for evidence of past water.", "Planned for 90 sols; drove for six years."]),
    (34, "Opportunity (rover)", "Opportunity", "Mars Exploration Rover", M, "ROVER", 23, 6, "COMPLETED", "Opportunity rover Mars", (), ["Find evidence of past water at Meridiani Planum.", "Drove a record 45 km over almost fifteen years."]),
    (35, "New Horizons", "New Horizons", "New Frontiers", M, "FLYBY", 24, 24, "ACTIVE", "New Horizons Pluto", (), ["First flyby of Pluto and Charon (2015).", "Flyby of the Kuiper belt object Arrokoth (2019)."]),
    (36, "Kepler space telescope", "Kepler", "Discovery", M, "TELESCOPE", 25, None, "COMPLETED", "Kepler telescope", (), ["Count Earth-size planets in habitable zones by watching stars dim.", "Found more than 2,600 confirmed exoplanets."]),
    (37, "Mars Science Laboratory", "Mars Science Laboratory", "Mars Exploration Program", M, "ROVER", 26, 6, "ACTIVE", "Curiosity rover Mars", (), ["Assess whether Gale crater ever offered conditions for microbial life.", "Climb Mount Sharp, reading its layers."]),
    (38, "Juno (spacecraft)", "Juno", "New Frontiers", M, "ORBITER", 27, 10, "ACTIVE", "Juno Jupiter", (), ["Map Jupiter's gravity and magnetic fields from a polar orbit.", "Probe the deep atmosphere and the origin of the planet."]),
    (39, "OSIRIS-REx", "OSIRIS-REx", "New Frontiers", M, "SAMPLE_RETURN", 28, 27, "COMPLETED", "OSIRIS-REx Bennu", (), ["Collect a sample of the asteroid Bennu and return it to Earth (2023)."]),
    (40, "InSight", "InSight", "Discovery", M, "LANDER", 29, 6, "COMPLETED", "InSight Mars lander", (), ["Listen for marsquakes and measure the planet's interior."]),
    (41, "Parker Solar Probe", "Parker Solar Probe", "Living With a Star", M, "PROBE", 30, 1, "ACTIVE", "Parker Solar Probe", (), ["Fly through the Sun's corona, closer than any spacecraft before.", "Trace how the solar wind is heated and accelerated."]),
    (42, "Transiting Exoplanet Survey Satellite", "TESS", "Explorer", M, "TELESCOPE", 31, None, "ACTIVE", "TESS", (), ["Survey the brightest nearby stars for transiting exoplanets."]),
    (43, "Mars 2020", "Mars 2020", "Mars Exploration Program", M, "ROVER", 32, 6, "ACTIVE", "Perseverance rover Mars", (), ["Seek signs of ancient life in Jezero crater.", "Cache rock samples for a future return to Earth."]),
    (44, "Ingenuity (helicopter)", "Ingenuity", "Mars Exploration Program", M, "HELICOPTER", 33, 6, "COMPLETED", "Ingenuity helicopter Mars", (), ["First powered, controlled flight on another planet (2021).", "Flew 72 times before a rotor was damaged in 2024."]),
    (45, "Double Asteroid Redirection Test", "DART", "Planetary Defense", M, "IMPACTOR", 34, 29, "COMPLETED", "DART Dimorphos", (), ["Change an asteroid's orbit by hitting it: the first planetary defence test."]),
    (46, "Lucy (spacecraft)", "Lucy", "Discovery", M, "FLYBY", 35, 10, "ACTIVE", "Lucy spacecraft", (), ["Visit the Trojan asteroids that share Jupiter's orbit."]),
    (47, "Psyche (spacecraft)", "Psyche", "Discovery", M, "ORBITER", 36, 30, "ACTIVE", "Psyche spacecraft", (), ["Orbit the metal-rich asteroid 16 Psyche."]),
    (48, "Europa Clipper", "Europa Clipper", "Flagship", M, "ORBITER", 37, 12, "ACTIVE", "Europa Clipper", (), ["Study whether Europa's ocean could support life, in dozens of flybys."]),
    (49, "Hubble Space Telescope", "Hubble Space Telescope", "Great Observatories", "NASA / ESA", "TELESCOPE", 38, None, "ACTIVE", "Hubble Space Telescope", (), ["Observe the universe in visible, ultraviolet and near-infrared light from above the atmosphere."]),
    (50, "James Webb Space Telescope", "James Webb Space Telescope", "Flagship", "NASA / ESA / CSA", "TELESCOPE", 39, None, "ACTIVE", "Webb telescope", (), ["See the first galaxies, in the infrared, from the Sun–Earth L2 point."]),
    (51, "Lunar Reconnaissance Orbiter", "Lunar Reconnaissance Orbiter", "Lunar Precursor Robotic Program", M, "ORBITER", 40, 5, "ACTIVE", "Lunar Reconnaissance Orbiter", (), ["Map the Moon in high resolution to prepare future landings."]),
    (52, "Dragonfly (Titan space probe)", "Dragonfly", "New Frontiers", M, "HELICOPTER", 41, 16, "PLANNED", "Dragonfly rotorcraft", (), ["Fly a rotorcraft lander between sites on Titan's surface."]),
    (53, "Nancy Grace Roman Space Telescope", "Nancy Grace Roman Space Telescope", "Astrophysics", M, "TELESCOPE", 42, None, None, "Roman Space Telescope", (), ["Wide-field infrared survey of dark energy and exoplanets."]),
    (54, "MESSENGER", "MESSENGER", "Discovery", M, "ORBITER", 43, 2, "COMPLETED", "MESSENGER Mercury", (), ["First spacecraft to orbit Mercury."]),
]

# The headline photograph of a mission, picked by hand where the search's first guess was weak.
MISSION_PINS = {
    2: "s62-00302", 3: "S65-29639", 6: "as11-40-5903", 8: "as15-85-11437", 10: "sl2-07-651",
    11: "S81-30460", 12: "s07-31-1603", 13: "S84-27031", 15: "s31-10-019", 16: "sts061-73-040",
    19: "201107080002HQ", 27: "PIA00565", 28: "PIA17464", 31: "PIA00621", 35: "PIA20202",
    38: "PIA21033", 40: "PIA22228", 41: "NHQ201808110001", 43: "PIA23824", 49: "s125e011763",
    52: "GSFC_20261020_DRAGONFLY_044216",
}
# A second search per mission, so a gallery is not six shots of the same press event.
EXTRA_QUERIES = {
    1: "Mercury-Redstone 3 launch", 3: "Ed White spacewalk", 5: "Earthrise Apollo 8", 6: "Apollo 11 lunar surface",
    7: "Apollo 13 service module damage", 8: "Apollo 15 Lunar Roving Vehicle", 9: "Apollo 17 Blue Marble",
    14: "51-L crew portrait", 17: "STS-95 launch", 18: "STS-107 crew portrait", 20: "Expedition One crew",
    21: "Demo-2 launch", 22: "Crew-1 launch", 24: "Artemis II launch", 30: "Galileo STS-34",
    45: "DART launch", 48: "Europa Clipper launch", 50: "Webb first images", 36: "Kepler spacecraft",
    37: "Curiosity selfie Mars", 43: "Perseverance selfie", 39: "Bennu asteroid", 32: "Cassini Grand Finale",
}

# Highlighted on the home page.
FEATURED = {6, 28, 37, 43, 49, 50, 24, 32, 35, 21}

# Cost in US dollars, when Wikidata has none (approximate, as published by NASA; null elsewhere).
COSTS = {
    6: 355_000_000, 7: 375_000_000, 9: 450_000_000, 28: 865_000_000, 29: 865_000_000,
    31: 265_000_000, 32: 3_260_000_000, 35: 700_000_000, 37: 2_500_000_000, 38: 1_100_000_000,
    39: 1_160_000_000, 43: 2_700_000_000, 44: 85_000_000, 45: 324_000_000, 48: 5_200_000_000,
    49: 4_700_000_000, 50: 10_000_000_000, 52: 3_350_000_000, 53: 4_000_000_000, 23: 4_100_000_000,
}

# The year NASA selected each astronaut group.
GROUP_YEARS = {"Mercury Seven": 1959, **{f"NASA Astronaut Group {n}": y for n, y in [
    (2, 1962), (3, 1963), (4, 1965), (5, 1966), (6, 1967), (7, 1969), (8, 1978), (9, 1980), (10, 1984),
    (11, 1985), (12, 1987), (13, 1990), (14, 1992), (15, 1994), (16, 1996), (17, 1998), (18, 2000),
    (19, 2004), (20, 2009), (21, 2013), (22, 2017), (23, 2021)]}}
# Still in the corps as of TODAY: the crew of the last flight in the seed (Artemis II).
ACTIVE_ASTRONAUTS = {"Reid Wiseman", "Victor Glover", "Christina Koch", "Jeremy Hansen"}

# End of mission for the robotic ones, where Wikidata only has the landing (last contact, as NASA
# announced it).
END_DATES = {
    26: "2003-01-23", 27: "1982-11-13", 30: "2003-09-21", 31: "1997-09-27", 32: "2017-09-15",
    33: "2010-03-22", 34: "2018-06-10", 36: "2018-10-30", 40: "2022-12-15", 44: "2024-01-18",
    45: "2022-09-26", 39: "2023-09-24", 54: "2015-04-30",
}
CREWED = {"CREWED"}
# Launches Wikidata does not date as a launch: the expedition's start is its docking.
LAUNCH_FIXES = {20: ("2000-10-31", "07:52", "Soyuz-U", "Gagarin's Start")}
# Missions that rode another mission's launch: one launch, filed under the first.
SHARED_LAUNCH = {44: 43, 49: 15}

# EVA hours: approximate totals, only for astronauts whose total is well documented; null elsewhere.
EVA_HOURS = {
    "Neil Armstrong": 2.5, "Buzz Aldrin": 7.9, "Ed White": 0.4, "Eugene Cernan": 24.2,
    "Harrison Schmitt": 22.1, "Bruce McCandless II": 12.2, "Story Musgrave": 26.3,
    "Robert L. Behnken": 61.2, "Douglas G. Hurley": 0.0, "Michael Collins": 1.5, "David Scott": 19.6,
    "James Irwin": 18.6, "Pete Conrad": 12.7, "Joseph P. Kerwin": 3.4, "Jeffrey A. Hoffman": 25.0,
    "Kathryn D. Sullivan": 3.5, "William Shepherd": 0.0, "Christina Koch": 42.2, "Victor Glover": 26.5,
}

# Spacecraft → subsystem → instrument. Real instrument names; the subsystem grouping is ours.
COMPONENTS = {
    26: [("Science payload", ["ChemCam", "Mastcam", "MAHLI", "APXS", "CheMin", "SAM", "RAD", "DAN", "REMS", "MARDI"]),
         ("Mobility", ["Rocker-bogie suspension", "Six aluminium wheels"]),
         ("Power", ["MMRTG"]),
         ("Communications", ["High-gain antenna", "UHF antenna"]),
         ("Engineering cameras", ["Hazcams", "Navcams"])],
    32: [("Science payload", ["Mastcam-Z", "SuperCam", "PIXL", "SHERLOC", "WATSON", "MOXIE", "MEDA", "RIMFAX"]),
         ("Sample handling", ["Robotic arm", "Coring drill", "Sample caching system"]),
         ("Power", ["MMRTG"]),
         ("Communications", ["High-gain antenna", "UHF antenna"])],
    39: [("Optical Telescope Element", ["Primary mirror (18 segments)", "Secondary mirror"]),
         ("Integrated Science Instrument Module", ["NIRCam", "NIRSpec", "MIRI", "FGS/NIRISS"]),
         ("Spacecraft element", ["Sunshield (5 layers)", "Spacecraft bus"])],
    38: [("Science instruments", ["Wide Field Camera 3", "Advanced Camera for Surveys", "Cosmic Origins Spectrograph", "STIS", "NICMOS", "Fine Guidance Sensors"]),
         ("Optical Telescope Assembly", ["Primary mirror (2.4 m)", "Secondary mirror"]),
         ("Power", ["Solar arrays", "Nickel-hydrogen batteries"])],
    21: [("Optical remote sensing", ["ISS (Imaging Science Subsystem)", "VIMS", "CIRS", "UVIS"]),
         ("Fields, particles and waves", ["CAPS", "MAG", "CDA", "INMS", "RPWS", "MIMI"]),
         ("Microwave remote sensing", ["RADAR", "RSS"]),
         ("Power", ["Three RTGs"]),
         ("Huygens probe", ["DISR", "HASI", "GCMS", "ACP", "DWE", "SSP"])],
    17: [("Science instruments", ["Imaging Science System", "IRIS", "UVS", "PPS", "CRS", "LECP", "MAG", "PLS", "PRA", "PWS"]),
         ("Communications", ["High-gain antenna (3.7 m)", "Golden Record"]),
         ("Power", ["Three RTGs"])],
    24: [("Science payload", ["Ralph", "Alice", "LORRI", "SWAP", "PEPSSI", "Student Dust Counter", "REX"]),
         ("Power", ["RTG"]),
         ("Communications", ["High-gain antenna (2.1 m)"])],
    27: [("Science payload", ["JunoCam", "JIRAM", "MWR", "UVS", "JADE", "JEDI", "MAG", "Gravity Science", "Waves"]),
         ("Power", ["Three solar arrays"]),
         ("Radiation vault", ["Titanium electronics vault"])],
    4: [("Command Module", ["Guidance computer", "Heat shield", "Docking probe"]),
        ("Service Module", ["Service Propulsion System", "Fuel cells", "Reaction control system"])],
}

# Mission tasks (SYNTHETIC): what is left to do on the missions still in preparation.
TASK_MISSIONS = [25, 52, 53, 24, 48]
TEAMS = ["Systems engineering", "GNC team", "Thermal team", "Payload team", "Avionics team",
         "Ground systems", "Launch operations", "Flight software", "Mission assurance"]
TASK_TEMPLATES = {
    "DESIGN": ["Close the {sc} critical design review actions", "Update the mass budget", "Freeze the interface control documents"],
    "BUILD": ["Finish the flight harness", "Deliver the flight computer", "Build the structural test model"],
    "INTEGRATION": ["Integrate {inst}", "Install the high-gain antenna", "Mate the propulsion module"],
    "TEST": ["Thermal vacuum test", "Vibration and acoustic test", "End-to-end communications test with the DSN"],
    "LAUNCH_OPS": ["Ship {sc} to the launch site", "Fuel the spacecraft", "Launch readiness review"],
}


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# Build
# ─────────────────────────────────────────────────────────────────────────────────────────────────


def build():
    SEED.mkdir(parents=True, exist_ok=True)

    print("· celestial bodies")
    body_wd = wd_by_titles([b[1] for b in BODIES])
    qid_to_body = {}
    bodies = []
    for bid, title, kind, query, pin in BODIES:
        e = body_wd.get(title) or {}
        qid_to_body[e.get("id")] = bid
        name = label(e) or title.split(" (")[0]
        imgs = pick_images(query, must=(name.split()[-1],), prefer=("PIA", "global", "full"), avoid=("artist", "illustration", "concept"), pinned=pin)
        gravity = wd_quantity(e, "P7015", "m/s2")
        radius = wd_quantity(e, "P2120", "km")
        if radius is None and wd_quantity(e, "P2386", "km"):
            radius = wd_quantity(e, "P2386", "km") / 2
        bodies.append({
            "id": bid, "name": name, "type": kind, "parentQid": item_id(e, "P397"),
            "radiusKm": round(radius, 1) if radius else None,
            "massKg": float(f"{wd_quantity(e, 'P2067', 'kg'):.4g}") if wd_quantity(e, "P2067", "kg") else None,
            "orbitalPeriodDays": round(abs(wd_quantity(e, "P2146", "d")), 3) if wd_quantity(e, "P2146", "d") else None,
            # a large moon cannot have a gravity under 0.1 m/s²: such a value was entered in m/s² but
            # labelled cm/s² (Io, in 2026), so the label is what is wrong
            "surfaceGravity": round(gravity * (100 if gravity < 0.1 and (radius or 0) > 1000 else 1), 3) if gravity else None,
            "description": description(e),
            "wikidataId": e.get("id"),
            **image_fields(imgs),
        })
    for b in bodies:
        b["parentId"] = qid_to_body.get(b.pop("parentQid"))
        if b["type"] == "STAR":
            b["parentId"] = None

    print("· missions (Wikidata)")
    mission_wd = wd_by_titles([m[1] for m in MISSIONS])
    load_time_items(mission_wd.values())
    fix_sites = wd_by_titles([f[3] for f in LAUNCH_FIXES.values()])

    print("· astronauts")
    crew_qids = []
    for m in MISSIONS:
        e = mission_wd.get(m[1]) or {}
        crew_qids += item_ids(e, "P1029")
    crew_wd = wd_by_ids(crew_qids)
    # the referenced labels: citizenships, astronaut groups, birthplaces, sites, vehicles…
    ref_ids = []
    for e in crew_wd.values():
        ref_ids += item_ids(e, "P27") + item_ids(e, "P463") + item_ids(e, "P361") + item_ids(e, "P19") + item_ids(e, "P21")
    site_qids = []
    for m in MISSIONS:
        e = mission_wd.get(m[1]) or {}
        site_qids += (item_ids(e, "P448") + item_ids(e, "P1427"))[:1]
        ref_ids += item_ids(e, "P375")
    site_qids += [e["id"] for e in fix_sites.values()]
    refs = wd_by_ids(ref_ids)

    astronauts, qid_to_astronaut = [], {}
    for qid in dict.fromkeys(crew_qids):
        e = crew_wd.get(qid)
        if not e or "Q5" not in item_ids(e, "P31"):  # people only (a crew list can name a dummy)
            continue
        aid = len(astronauts) + 1
        qid_to_astronaut[qid] = aid
        name = re.sub(r" \(astronaut\)$", "", label(e))
        born, _ = wd_time(e, "P569")
        died, _ = wd_time(e, "P570")
        group_ids = item_ids(e, "P361") + item_ids(e, "P463")
        groups = [label(refs.get(g, {})) or "" for g in group_ids]
        is_group = lambda g: any(w in g.lower() for w in ("astronaut group", "cosmonaut", "astronaut corps", "mercury seven", "astronaut class", "group of astronauts"))
        group = next((g for g in groups if is_group(g)), None)
        sel_year = None
        if group:
            gm = re.search(r"\b(19|20)\d{2}\b", group)
            ge = refs.get(group_ids[groups.index(group)], {})
            started = wd_time(ge, "P571")[0] or wd_time(ge, "P580")[0] or wd_time(ge, "P585")[0]
            sel_year = GROUP_YEARS.get(group) or (int(gm.group(0)) if gm else (int(started[:4]) if started else None))
        nationality = label(refs.get(item_id(e, "P27"), {}))
        minutes = wd_quantity(e, "P2873", "min")
        if minutes is None:
            days = wd_quantity(e, "P2873", "d")
            minutes = days * 1440 if days else None
        surname = name.split()[-1] if name.split()[-1] not in ("Jr.", "II", "III") else name.split()[-2]
        imgs = pick_images(f"{name} portrait", must=(surname,), prefer=("portrait", "official", "astronaut"), avoid=("training", "suit up", "crew portrait"))
        if not imgs:
            imgs = pick_images(f"astronaut {name}", must=(surname,), prefer=("portrait",))
        status = "DECEASED" if died else ("ACTIVE" if name in ACTIVE_ASTRONAUTS else "RETIRED")
        astronauts.append({
            "id": aid, "name": name, "nationality": nationality,
            "gender": (label(refs.get(item_id(e, "P21"), {})) or "").replace("trans", "") or None,
            "birthDate": born, "deathDate": died,
            "birthPlace": label(refs.get(item_id(e, "P19"), {})),
            "selectionGroup": group, "selectionYear": sel_year,
            "status": status,
            "timeInSpaceDays": round(minutes / 1440, 2) if minutes else None,
            "evaHours": EVA_HOURS.get(name),
            "summary": description(e),
            "wikidataId": qid,
            **image_fields(imgs),
        })

    print("· launch sites")
    site_wd = wd_by_ids(site_qids)
    site_refs = wd_by_ids([item_id(e, "P17") for e in site_wd.values()] + [item_id(e, "P131") for e in site_wd.values()] + [item_id(e, "P137") for e in site_wd.values()])
    sites, qid_to_site = [], {}
    for qid in dict.fromkeys(site_qids):
        e = site_wd.get(qid)
        if not e:
            continue
        sid = len(sites) + 1
        qid_to_site[qid] = sid
        lat, lon = coords(e)
        name = label(e)
        query = re.sub(r"Cape Canaveral (Space Force|Air Force) Station ", "", name)
        imgs = pick_images(query, prefer=("launch pad", "aerial", "pad"), avoid=("artist",), pinned=SITE_PINS.get(name))
        if not imgs:
            imgs = pick_images(name.split(" Launch")[0] + " launch pad", prefer=("aerial",))
        opened, _ = wd_time(e, "P571")
        sites.append({
            "id": sid, "name": name,
            "country": label(site_refs.get(item_id(e, "P17"), {})),
            "region": label(site_refs.get(item_id(e, "P131"), {})),
            "operator": label(site_refs.get(item_id(e, "P137"), {})),
            "latitude": lat, "longitude": lon,
            "firstLaunchYear": int(opened[:4]) if opened else None,
            "description": description(e),
            "wikidataId": qid,
            **image_fields(imgs),
        })

    print("· spacecraft")
    sc_wd = wd_by_titles([s[8] for s in SPACECRAFT if s[8]])
    spacecraft = []
    for sid, name, kind, maker, mass, power, source, query, title in SPACECRAFT:
        e = sc_wd.get(title) if title else None
        wd_mass = wd_quantity(e, "P2067", "kg") if e else None
        imgs = pick_images(query, prefer=(name.split(" (")[0],), avoid=("artist", "illustration", "concept", "model", "rendering"), pinned=SPACECRAFT_PINS.get(sid))
        if not imgs:
            imgs = pick_images(query)
        spacecraft.append({
            "id": sid, "name": name, "type": kind, "manufacturer": maker,
            "massKg": round(wd_mass) if wd_mass else mass, "powerWatts": power, "powerSource": source,
            "description": description(e) if e else None,
            "wikidataId": e.get("id") if e else None,
            **image_fields(imgs),
        })

    components, cid = [], 0
    for sc, groups in COMPONENTS.items():
        for group, instruments in groups:
            cid += 1
            parent = cid
            components.append({"id": cid, "spacecraftId": sc, "parentId": None, "name": group, "kind": "SUBSYSTEM"})
            for inst in instruments:
                cid += 1
                components.append({"id": cid, "spacecraftId": sc, "parentId": parent, "name": inst,
                                   "kind": "INSTRUMENT" if "payload" in group.lower() or "instrument" in group.lower()
                                   or "sensing" in group.lower() or "particles" in group.lower() or "probe" in group.lower()
                                   or "Science" in group else "COMPONENT"})

    print("· missions (images and links)")
    missions, launches = [], []
    for (mid, title, name, program, agency, kind, sc, dest, status, query, must, objectives) in MISSIONS:
        e = mission_wd.get(title) or {}
        launch_date, launch_time = wd_time(e, "P619")
        if kind in CREWED:
            end = wd_time(e, "P582")[0] or wd_time(e, "P620")[0]
        else:
            end = END_DATES.get(mid) or wd_time(e, "P582")[0] or wd_time(e, "P2669")[0]
        if status is None:  # decided by what Wikidata knows on TODAY
            status = "PLANNED" if not launch_date or launch_date > TODAY.isoformat() else ("COMPLETED" if end else "ACTIVE")
        if launch_date and launch_date > TODAY.isoformat():
            status = "PLANNED"
        cost = None
        v = value(best(e, "P2130"))
        if v and v.get("unit", "").endswith("Q4917"):
            cost = float(v["amount"])
        crew = [qid_to_astronaut[q] for q in item_ids(e, "P1029") if q in qid_to_astronaut]
        avoid = ("artist", "illustration", "concept", "rendering", "memorial", "baseball")
        gallery = []
        if mid in MISSION_PINS:
            gallery += pick_images(None, pinned=MISSION_PINS[mid])
        if mid in EXTRA_QUERIES:
            gallery += pick_images(EXTRA_QUERIES[mid], prefer=(name,), avoid=avoid, count=4)
        gallery += pick_images(query, must=must, prefer=(name,), avoid=avoid, count=12, pages=2)
        seen_ids, seen_titles, unique = set(), set(), []
        for g in gallery:
            t = re.sub(r"[^a-z]", "", (g["title"] or "").lower())[:40]
            if g["nasaId"] in seen_ids or (t and t in seen_titles and len(unique) >= 1):
                continue
            seen_ids.add(g["nasaId"])
            seen_titles.add(t)
            unique.append(g)
        gallery = unique[:6]
        site = qid_to_site.get(next(iter(item_ids(e, "P448") + item_ids(e, "P1427")), None))
        vehicle = label(refs.get(item_id(e, "P375"), {}))
        if mid in LAUNCH_FIXES:
            launch_date, launch_time, vehicle, site_title = LAUNCH_FIXES[mid]
            site = qid_to_site.get((fix_sites.get(site_title) or {}).get("id"))
        objectives_html = "<ul>" + "".join(f"<li>{o}</li>" for o in objectives) + "</ul>"
        missions.append({
            "id": mid, "name": name, "program": program, "agency": agency, "type": kind, "status": status,
            "launchDate": launch_date, "endDate": end, "destinationId": dest, "launchSiteId": site,
            "spacecraftId": sc, "crewIds": crew,
            "costUsd": cost if cost else COSTS.get(mid),
            "summary": description(e), "objectives": objectives_html, "featured": mid in FEATURED,
            "wikidataId": e.get("id"),
            **image_fields(gallery),
            "gallery": [{k: g[k] for k in ("title", "image", "imageLarge", "thumbnail", "imageCredit", "sourceUrl", "dateCreated")} for g in gallery],
        })
        if not vehicle and sc in (7, 8, 9, 10, 11):
            vehicle = "Space Shuttle"
        if launch_date and mid in SHARED_LAUNCH:
            host = next((l for l in launches if l["missionId"] == SHARED_LAUNCH[mid]), None)
            if host and not host["launchTimeUtc"]:
                host["launchTimeUtc"] = launch_time
            launch_date = None if host else launch_date
        if launch_date:
            outcome = "FAILURE" if mid == 14 else ("SCHEDULED" if launch_date > TODAY.isoformat() else "SUCCESS")
            launches.append({
                "missionId": mid, "launchDate": launch_date, "launchTimeUtc": launch_time,
                "vehicle": vehicle, "siteId": site, "outcome": outcome,
                "notes": "Lost 73 seconds after liftoff" if mid == 14 else None,
            })
    launches.sort(key=lambda l: (l["launchDate"], l["launchTimeUtc"] or ""))
    recent = (TODAY - dt.timedelta(days=3653)).isoformat()
    for site in sites:
        site["active"] = any(l["siteId"] == site["id"] and l["launchDate"] >= recent for l in launches)
    # a short biography, written from the facts above (no third-party prose)
    by_astronaut = {}
    for m in missions:
        for a in m["crewIds"]:
            by_astronaut.setdefault(a, []).append(m)
    for a in astronauts:
        flown = sorted(by_astronaut.get(a["id"], []), key=lambda m: m["launchDate"] or "9999")
        parts = []
        if a["summary"]:
            parts.append(f"<p>{a['summary'][0].upper()}{a['summary'][1:]}.</p>")
        facts = []
        if a["selectionGroup"]:
            facts.append(f"Selected with {a['selectionGroup']}" + (f" in {a['selectionYear']}" if a["selectionYear"] else "") + ".")
        if flown:
            names = [m["name"] + (f" ({m['launchDate'][:4]})" if m["launchDate"] else "") for m in flown]
            facts.append("In this dataset: " + ", ".join(names) + ".")
        if a["timeInSpaceDays"]:
            facts.append(f"About {a['timeInSpaceDays']:g} days in space in all.")
        if facts:
            parts.append("<p>" + " ".join(facts) + "</p>")
        a["bio"] = "".join(parts) or None
    for i, l in enumerate(launches, 1):
        l["id"] = i

    print("· mission tasks (synthetic)")
    rnd = random.Random(20261031)
    tasks = []
    phases = list(TASK_TEMPLATES)
    statuses = ["BACKLOG", "IN_PROGRESS", "TESTING", "DONE"]
    for mid in TASK_MISSIONS:
        m = next(x for x in missions if x["id"] == mid)
        sc = next(s for s in spacecraft if s["id"] == m["spacecraftId"])
        instruments = [c["name"] for c in components if c["spacecraftId"] == sc["id"] and c["kind"] == "INSTRUMENT"] or ["the science payload"]
        for phase in phases:
            for tpl in TASK_TEMPLATES[phase]:
                if rnd.random() < 0.35:
                    continue
                pi = phases.index(phase)
                status = statuses[min(3, max(0, 3 - pi + rnd.choice([-1, 0, 0, 1])))] if m["status"] != "PLANNED" else statuses[max(0, min(3, 2 - pi + rnd.choice([-1, 0, 1])))]
                due = TODAY + dt.timedelta(days=(pi - 1) * 60 + rnd.randint(-30, 45))
                tasks.append({
                    "id": len(tasks) + 1, "missionId": mid,
                    "title": tpl.format(sc=sc["name"], inst=rnd.choice(instruments)),
                    "phase": phase, "status": status,
                    "owner": rnd.choice(TEAMS),
                    "priority": rnd.choice(["LOW", "MEDIUM", "MEDIUM", "HIGH", "HIGH", "CRITICAL"]),
                    "dueDate": due.isoformat(),
                    "estimateDays": rnd.choice([3, 5, 8, 13, 21]),
                    "progress": {"BACKLOG": 0, "IN_PROGRESS": rnd.choice([20, 40, 60]), "TESTING": 80, "DONE": 100}[status],
                })

    print("· budget (synthetic split of Wikidata's totals)")
    nasa = wd_by_titles(["NASA"]).get("NASA") or {}
    anchors = {}
    for c in claims(nasa, "P2769"):
        v = value(c)
        t = c.get("qualifiers", {}).get("P585", [{}])[0].get("datavalue", {}).get("value", {}).get("time")
        if v and t:
            anchors[int(t[1:5])] = float(v["amount"]) / 1e6
    years = list(range(2011, 2026))
    known = sorted(anchors)
    shares = {"Science": 0.29, "Exploration": 0.24, "Space Operations": 0.20, "Safety, Security and Mission Services": 0.13,
              "Space Technology": 0.05, "Aeronautics": 0.035, "Construction and Environmental Compliance": 0.02,
              "STEM Engagement": 0.005, "Inspector General": 0.0019}
    category = {"Science": "SCIENCE", "Exploration": "EXPLORATION", "Space Operations": "OPERATIONS",
                "Safety, Security and Mission Services": "SUPPORT", "Space Technology": "TECHNOLOGY", "Aeronautics": "AERONAUTICS",
                "Construction and Environmental Compliance": "SUPPORT", "STEM Engagement": "EDUCATION", "Inspector General": "SUPPORT"}
    budgets = []
    brnd = random.Random(1958)
    for y in years:
        if y in anchors:
            total = anchors[y]
        else:
            lo = max([k for k in known if k < y], default=None)
            hi = min([k for k in known if k > y], default=None)
            if lo and hi:
                total = anchors[lo] + (anchors[hi] - anchors[lo]) * (y - lo) / (hi - lo)
            else:
                ref = lo or hi
                total = anchors[ref] * (1 + 0.01 * (y - ref))
        for program, share in shares.items():
            amount = total * share * (1 + brnd.uniform(-0.04, 0.04))
            budgets.append({"id": len(budgets) + 1, "year": y, "program": program, "category": category[program],
                            "amountMillionsUsd": round(amount, 1), "synthetic": True,
                            "basis": ("Wikidata NASA budget for %d, split by program synthetically" % y) if y in anchors
                            else "NASA budget interpolated between Wikidata's figures, split by program synthetically"})

    write_credits([("missions", missions), ("astronauts", astronauts), ("spacecraft", spacecraft),
                   ("bodies", bodies), ("launch-sites", sites)])
    write("bodies", bodies)
    write("launch-sites", sites)
    write("spacecraft", spacecraft)
    write("components", components)
    write("astronauts", astronauts)
    write("missions", missions)
    write("launches", launches)
    write("tasks", tasks)
    write("budgets", budgets)
    print({k: len(v) for k, v in [("bodies", bodies), ("sites", sites), ("spacecraft", spacecraft), ("components", components),
                                  ("astronauts", astronauts), ("missions", missions), ("launches", launches),
                                  ("tasks", tasks), ("budgets", budgets)]})


def write_credits(collections):
    """CREDITS.md: every photograph the seed links, with whom to credit and where it is published."""
    lines = [
        "# Photo credits", "",
        "Every photograph in the seed is an asset of the [NASA Image and Video Library](https://images.nasa.gov),",
        "linked where NASA publishes it (`images-assets.nasa.gov`) — this repository holds the links, not",
        "the files. NASA material is generally not subject to copyright in the United States; NASA's",
        "[media usage guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/) ask that it",
        "be credited, and that the NASA insignia, logotype and seal not be used to imply endorsement. The",
        "seed therefore carries photographs and leaves patches, insignia and logos out; a picture may still",
        "show a NASA mark on a spacecraft or a suit, as a photograph of it. Some assets are credited to NASA",
        "together with a partner (ESA, JPL-Caltech, a university…), whose credit is part of the line below.",
        "", "The same list is served at `/api/credits`, and each record carries its `imageCredit` and",
        "`sourceUrl`.", "",
        "| Record | Photograph | Credit |", "|---|---|---|",
    ]
    def cell(t):
        return (t or "").replace("|", "\\|")
    for kind, rows in collections:
        for r in rows:
            pics = ([r] if r.get("image") else []) + [g for g in r.get("gallery", []) if g["image"] != r.get("image")]
            for p in pics:
                lines.append(f"| {kind}/{r['id']} {cell(r['name'])} | [{cell(p.get('imageTitle') or p.get('title'))}]({p['sourceUrl']}) | {cell(p['imageCredit'])} |")
    (ROOT / "CREDITS.md").write_text("\n".join(lines) + "\n")


def write(name, rows):
    (SEED / f"{name}.json").write_text(json.dumps(rows, indent=1, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    build()
