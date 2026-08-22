---
gap_id: G-02-2
phase: 02-exports-completion-search
status: root_cause_found
found: 2026-08-22
---

# Debug: Mapbox map snapshot missing after route optimization

## Symptom (from UAT test 2)

> it passes when i export a pdf with a unoptimized route but after i optimize a route it does not show the map

Expected: a rendered, non-garbled map image with the correct route/pins in the exported PDF.
Actual: PDF exports fine, but no map image at all, only for optimized (route-geometry) trips.

## Evidence

User-provided backend log at the moment of the failing export:

```
WARN c.t.backend.client.mapbox.MapboxClient : Mapbox call failed: 422 Unprocessable Content: "{"message":"Invalid GeoJSON"}"
WARN c.t.backend.service.PdfExportService   : Mapbox snapshot unavailable for trip 12, exporting PDF without it
```

This rules out:
- The double-encoding bug fixed earlier this session (WR-02) — that would produce a malformed
  request URI, not a clean HTTP round-trip ending in a real 422 from Mapbox's own server. The
  `staticSnapshot_withRouteGeometry_requestUriIsSinglyEncodedNotDoubleEncoded` test independently
  confirms the request URI Spring sends matches exactly what the client constructs.
- The URL-length fallback (`Mapbox snapshot request too long...`) — that WARN line is absent;
  the request reached Mapbox and was rejected on content, not truncated/skipped on length.
- An uncaught exception failing the whole export — the export succeeded, just without a map,
  which is the designed fail-open behavior for a caught `MapboxClientException`.

## Investigation

`Trip.routeGeometry` is persisted by `RouteOptimizationService.persistRouteGeometry` as a BARE
GeoJSON Geometry object — `objectMapper.writeValueAsString(geometry)` where `geometry` is
`OrsDirectionsResponse.Feature.geometry()`, i.e. just `{"type":"LineString","coordinates":[...]}`,
never wrapped in a Feature. This is intentional (RESEARCH.md's `geojson()` overlay plan reuses
`Trip.routeGeometry` directly).

`MapboxClient.requestPath` hardcodes `/auto/{width}x{height}` positioning on every request —
there is no explicit center/zoom anywhere in this client.

Checked Mapbox's own Static Images API docs (https://docs.mapbox.com/api/maps/static-images/):
- One documented 422 case: *"Auto extent cannot be determined when GeoJSON has no features"*.
- The docs' own bare-Geometry `geojson()` example (a Point) is paired with an **explicit**
  center/zoom in the URL, never with `auto` positioning.
- The FeatureCollection example (which DOES work with implied auto-extent in Mapbox's own use)
  wraps every geometry in a `Feature` with a `properties` object.

**Root cause:** Mapbox's `auto` position/zoom calculates its bounding box from the overlay's
`features`. A bare Geometry object has no `features` key, so Mapbox's auto-extent resolver can't
process it and rejects the whole overlay — manifesting as the generic "Invalid GeoJSON" 422
rather than the more specific auto-extent error message. The marker overlay (`pin-s+...`) also
uses `/auto/` and works fine because Mapbox computes marker auto-extent through an entirely
different code path (marker syntax parsing, not GeoJSON `features` inspection) — which is exactly
why "unoptimized route" (markers) succeeds and "optimized route" (geojson) fails.

## Fix

`MapboxClient.geojsonOverlay` now wraps the raw geometry JSON string in a minimal GeoJSON
`Feature` before encoding:

```java
private static String geojsonOverlay(String routeGeometryJson) {
    String feature = "{\"type\":\"Feature\",\"properties\":{},\"geometry\":" + routeGeometryJson + "}";
    return "geojson(" + URLEncoder.encode(feature, StandardCharsets.UTF_8) + ")";
}
```

Regression test added: `MapboxClientTest#staticSnapshot_withRouteGeometry_overlayWrapsBareGeometryInAFeature`
asserts the outgoing request URI contains `"type":"Feature"` and `"geometry":{...the original geometry...}`.
The existing `staticSnapshot_withRouteGeometry_requestUriIsSinglyEncodedNotDoubleEncoded` test was
updated to expect the Feature-wrapped payload.

**Not independently verified against the live Mapbox API** — no Mapbox token or network access in
this session. Diagnosis is backed by: the user's own production 422 response, Mapbox's documented
auto-extent-requires-features error case, and Mapbox's own example usage pattern (bare Geometry
paired only with explicit center/zoom, never `auto`). High confidence, not a live-traffic proof.
Re-run UAT test 2 against a real optimized trip once this ships to confirm end-to-end.

## Files Changed

- `backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClient.java`
- `backend/src/test/java/com/tripflow/backend/client/mapbox/MapboxClientTest.java`
