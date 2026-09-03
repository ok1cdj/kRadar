#!/usr/bin/env python3
"""Convert MeteoPlaneRadar's baked-in C map data into JSON assets for kRadar.

Reads (from the sibling MeteoPlaneRadar project):
  - EuMapData.h    : EU_BORDER_PTS[][2] (fixed-point uint16), EU_RING_OFFSETS[],
                     EU_CITIES[] (struct EuCity)
  - CzCitiesData.h : CZ_CITIES[] (same struct) + CZ bounding box

Writes (into app/src/main/assets/):
  - borders.json : [[[lat,lon],[lat,lon], ...], ...]   one array per ring/polyline
  - cities.json  : [{"name","lat","lon","tier"}, ...]

Border coordinates are fixed-point uint16 decoded with:
    lon = EU_LON_ORIGIN + v[0] * EU_COORD_SCALE
    lat = EU_LAT_ORIGIN + v[1] * EU_COORD_SCALE

Data sources (see MeteoPlaneRadar README): borders = Natural Earth (public
domain), cities = GeoNames (CC BY 4.0).
"""

import json
import os
import re
import sys

# --- paths ----------------------------------------------------------------
HERE = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.environ.get(
    "METEOPLANE_SRC",
    os.path.normpath(os.path.join(HERE, "..", "..", "MeteoPlaneRadar", "src")),
)
OUT_DIR = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets"))

EU_MAP = os.path.join(SRC_DIR, "EuMapData.h")
CZ_MAP = os.path.join(SRC_DIR, "CzCitiesData.h")

# --- fixed-point decode constants (must match EuMapData.h) -----------------
COORD_SCALE = 0.0011
LON_ORIGIN = -32.0
LAT_ORIGIN = 34.0


def read(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def slice_array(text, decl):
    """Return the text between the '{' after `decl` and its matching outer '}'."""
    start = text.index(decl)
    brace = text.index("{", start)
    depth = 0
    for i in range(brace, len(text)):
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return text[brace + 1 : i]
    raise ValueError(f"unterminated array for {decl!r}")


def parse_borders(text):
    """EU_BORDER_PTS[][2] + EU_RING_OFFSETS[] -> list of rings of [lat,lon]."""
    pts_body = slice_array(text, "EU_BORDER_PTS")
    # every {a,b} pair
    pairs = re.findall(r"\{\s*(\d+)\s*,\s*(\d+)\s*\}", pts_body)
    pts = [(int(a), int(b)) for a, b in pairs]

    off_body = slice_array(text, "EU_RING_OFFSETS")
    offsets = [int(n) for n in re.findall(r"\d+", off_body)]

    rings = []
    for i in range(len(offsets) - 1):
        s, e = offsets[i], offsets[i + 1]
        ring = []
        for (u_lon, u_lat) in pts[s:e]:
            lon = LON_ORIGIN + u_lon * COORD_SCALE
            lat = LAT_ORIGIN + u_lat * COORD_SCALE
            ring.append([round(lat, 4), round(lon, 4)])
        if len(ring) >= 2:
            rings.append(ring)
    return rings, len(pts)


# struct EuCity { const char* name; const char* abbr; float lon, lat; uint8_t tier; };
CITY_RE = re.compile(
    r'\{\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,'
    r"\s*(-?\d+(?:\.\d+)?)f?\s*,\s*(-?\d+(?:\.\d+)?)f?\s*,\s*(\d+)\s*\}"
)


def parse_cities(body):
    out = []
    for m in CITY_RE.finditer(body):
        name, abbr, lon, lat, tier = m.groups()
        out.append(
            {
                "name": name,
                "abbr": abbr,
                "lat": round(float(lat), 4),
                "lon": round(float(lon), 4),
                "tier": int(tier),
            }
        )
    return out


# Hand-curated abbreviations for well-known European cities. Names match the
# ASCII spelling in EuMapData.h. Everything not listed falls back to abbr_from
# below. (Czech cities keep MeteoPlaneRadar's own curated abbreviations.)
CURATED_ABBR = {
    # Capitals / very large cities
    "London": "LON", "Berlin": "BER", "Madrid": "MAD", "Roma": "ROM",
    "Paris": "PAR", "Bucuresti": "BUC", "Budapest": "BUD", "Warszawa": "WAW",
    "Wien": "WIE", "Barcelona": "BCN", "Stockholm": "STO", "Milano": "MIL",
    "Munchen": "MUC", "Kobenhavn": "CPH", "Sofia": "SOF", "Hamburg": "HAM",
    "Amsterdam": "AMS", "Dublin": "DUB", "Lisboa": "LIS", "Athina": "ATH",
    "Bruxelles": "BRU", "Helsinki": "HEL", "Oslo": "OSL", "Zagreb": "ZAG",
    "Beograd": "BEG", "Bratislava": "BTS", "Ljubljana": "LJU", "Riga": "RIG",
    "Vilnius": "VNO", "Tallinn": "TLL", "Luxembourg": "LUX", "Zurich": "ZUR",
    "Geneve": "GVA", "Bern": "BRN",
    # Germany
    "Koln": "CGN", "Frankfurt": "FRA", "Stuttgart": "STR", "Dusseldorf": "DUS",
    "Dortmund": "DTM", "Essen": "ESS", "Bremen": "BRE", "Dresden": "DRS",
    "Hannover": "HAN", "Nurnberg": "NUE", "Leipzig": "LEJ", "Duisburg": "DUI",
    "Bochum": "BOC", "Wuppertal": "WUP", "Bonn": "BON", "Mannheim": "MAN",
    "Karlsruhe": "KAR", "Wiesbaden": "WIB", "Munster": "MUN", "Augsburg": "AUG",
    "Aachen": "AAC", "Braunschweig": "BRA", "Kiel": "KIE", "Magdeburg": "MAG",
    "Freiburg": "FRB", "Mainz": "MAI", "Lubeck": "LUB", "Erfurt": "ERF",
    "Kassel": "KAS", "Rostock": "ROS",
    # UK & Ireland
    "Birmingham": "BIR", "Manchester": "MAN", "Liverpool": "LIV", "Leeds": "LDS",
    "Sheffield": "SHF", "Bristol": "BRS", "Glasgow": "GLA", "Edinburgh": "EDI",
    "Cardiff": "CDF", "Belfast": "BEL", "Leicester": "LEI", "Nottingham": "NOT",
    "Newcastle": "NCL", "Southampton": "SOU", "Portsmouth": "POR", "Cork": "COR",
    # France
    "Marseille": "MRS", "Lyon": "LYO", "Toulouse": "TLS", "Nice": "NCE",
    "Nantes": "NAN", "Strasbourg": "STG", "Bordeaux": "BOR", "Lille": "LIL",
    "Rennes": "REN", "Montpellier": "MPL", "Rouen": "ROU",
    # Spain / Portugal
    "Valencia": "VLC", "Sevilla": "SEV", "Zaragoza": "ZAZ", "Malaga": "AGP",
    "Bilbao": "BIO", "Porto": "OPO", "Granada": "GRA", "Vigo": "VGO",
    "Gijon": "GIJ", "Palma": "PMI",
    # Italy
    "Napoli": "NAP", "Torino": "TRN", "Palermo": "PMO", "Genova": "GOA",
    "Bologna": "BLQ", "Firenze": "FLR", "Bari": "BRI", "Catania": "CTA",
    "Venezia": "VCE", "Verona": "VRN", "Trieste": "TRS", "Padova": "PAD",
    # Poland
    "Krakow": "KRK", "Lodz": "LOD", "Wroclaw": "WRO", "Poznan": "POZ",
    "Gdansk": "GDN", "Szczecin": "SZZ", "Bydgoszcz": "BYD", "Lublin": "LUB",
    "Katowice": "KAT", "Bialystok": "BIA",
    # Nordics / Baltics / NL / BE
    "Goteborg": "GOT", "Malmo": "MAL", "Bergen": "BGO", "Trondheim": "TRD",
    "Tampere": "TMP", "Espoo": "ESP", "Rotterdam": "RTM", "DenHaag": "HAG",
    "Utrecht": "UTR", "Eindhoven": "EIN", "Antwerpen": "ANR", "Gent": "GEN",
    "Kaunas": "KAU",
    # SE / SEE
    "Thessaloniki": "SKG", "Cluj-Napoca": "CLJ", "Timisoara": "TSR",
    "Brasov": "BRV", "Craiova": "CRA", "Kosice": "KSC",
}


def abbr_from(name):
    """Readable 3-4 letter fallback: drop parentheticals, prefer the main word."""
    n = re.sub(r"\(.*?\)", "", name).strip()
    parts = [re.sub(r"[^A-Za-z]", "", p) for p in re.split(r"[ \-/]+", n)]
    parts = [p for p in parts if p]
    if not parts:
        return re.sub(r"[^A-Za-z]", "", name)[:4].upper() or "?"
    base = parts[0]
    if len(base) < 3 and len(parts) > 1:      # "A Coruna" -> "ACOR"
        base = base + parts[1]
    return base[:4].upper()


def curate_abbr(cities):
    """Override EU abbreviations with curated/generated readable forms."""
    for c in cities:
        c["abbr"] = CURATED_ABBR.get(c["name"], abbr_from(c["name"]))
    return cities


def parse_cz_box(text):
    def val(key):
        return float(re.search(rf"#define\s+{key}\s+(-?\d+(?:\.\d+)?)f?", text).group(1))
    return {
        "lat0": val("CZ_BOX_LAT0"),
        "lat1": val("CZ_BOX_LAT1"),
        "lon0": val("CZ_BOX_LON0"),
        "lon1": val("CZ_BOX_LON1"),
    }


def main():
    eu_text = read(EU_MAP)
    cz_text = read(CZ_MAP)

    rings, n_pts = parse_borders(eu_text)

    # EU cities get curated/generated abbreviations; CZ cities keep their own
    # hand-curated abbreviations from CzCitiesData.h.
    eu_cities = curate_abbr(parse_cities(slice_array(eu_text, "EU_CITIES")))
    cz_cities = parse_cities(slice_array(cz_text, "CZ_CITIES"))
    box = parse_cz_box(cz_text)

    # Czech list overrides EU cities inside the CZ bounding box (matches
    # MeteoPlaneRadar behaviour: local names/abbreviations win at home).
    def in_box(c):
        return box["lat0"] <= c["lat"] <= box["lat1"] and box["lon0"] <= c["lon"] <= box["lon1"]

    cities = [c for c in eu_cities if not in_box(c)] + cz_cities

    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, "borders.json"), "w", encoding="utf-8") as f:
        json.dump(rings, f, separators=(",", ":"))
    with open(os.path.join(OUT_DIR, "cities.json"), "w", encoding="utf-8") as f:
        json.dump(cities, f, ensure_ascii=False, separators=(",", ":"))

    print(f"borders.json: {len(rings)} rings, {n_pts} source points")
    print(f"cities.json : {len(cities)} cities "
          f"(EU {len(eu_cities)} - inside-box + CZ {len(cz_cities)})")
    print(f"written to {OUT_DIR}")


if __name__ == "__main__":
    try:
        main()
    except FileNotFoundError as e:
        sys.exit(f"missing source file: {e}. Set METEOPLANE_SRC to MeteoPlaneRadar/src.")
