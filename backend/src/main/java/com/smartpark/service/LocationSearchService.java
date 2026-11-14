package com.smartpark.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class LocationSearchService {

    private static final String SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    private static final int MAX_CACHE_ENTRIES = 250;
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final long MIN_REQUEST_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    private final String contact;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final Map<String, CachedSearch> cache = new LinkedHashMap<>(16, 0.75f, true);
    private long nextRequestAtNanos;

    public LocationSearchService(@Value("${smartpark.geocoding.contact:}") String contact) {
        this.contact = contact == null ? "" : contact.strip();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
        this.jsonMapper = JsonMapper.builder().build();
    }

    public List<LocationResult> search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip().replaceAll("\\s+", " ");
        if (query.length() < 3 || query.length() > 120) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Location search must be between 3 and 120 characters"
            );
        }
        String key = query.toLowerCase(java.util.Locale.ROOT);
        List<LocationResult> cached = getCached(key);
        if (cached != null) return cached;

        if (contact.isBlank() || contact.length() > 254 || !contact.contains("@")) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Address search is not configured; set SMARTPARK_GEOCODER_CONTACT"
            );
        }
        reserveProviderRequest();

        URI uri = URI.create(SEARCH_URL + "?format=jsonv2&limit=5&q="
            + URLEncoder.encode(query, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(6))
            .header("Accept", "application/json")
            .header("User-Agent", "SmartPark/1.0 (" + contact + ")")
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() == 429) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenStreetMap address search is rate-limited; try again shortly"
                );
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "OpenStreetMap address search returned HTTP " + response.statusCode()
                );
            }
            List<LocationResult> results = parseResults(response.body());
            putCached(key, results);
            return results;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "OpenStreetMap address search is unavailable", exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Address search was interrupted", exception
            );
        }
    }

    private List<LocationResult> parseResults(String body) {
        try {
            JsonNode root = jsonMapper.readTree(body);
            if (!root.isArray()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "OpenStreetMap returned an invalid address-search response"
                );
            }
            List<LocationResult> results = new ArrayList<>();
            for (JsonNode item : root) {
                String label = item.path("display_name").asText("");
                BigDecimal latitude = decimal(item.path("lat").asText(""));
                BigDecimal longitude = decimal(item.path("lon").asText(""));
                if (!label.isBlank() && latitude != null && longitude != null
                        && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                        && latitude.compareTo(BigDecimal.valueOf(90)) <= 0
                        && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                        && longitude.compareTo(BigDecimal.valueOf(180)) <= 0) {
                    results.add(new LocationResult(label, latitude, longitude));
                }
            }
            return List.copyOf(results);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "OpenStreetMap returned an invalid address-search response", exception
            );
        }
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private synchronized List<LocationResult> getCached(String key) {
        CachedSearch cached = cache.get(key);
        if (cached == null) return null;
        if (cached.expiresAt().isBefore(Instant.now())) {
            cache.remove(key);
            return null;
        }
        return cached.results();
    }

    private synchronized void putCached(String key, List<LocationResult> results) {
        cache.put(key, new CachedSearch(results, Instant.now().plus(CACHE_TTL)));
        while (cache.size() > MAX_CACHE_ENTRIES) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    private synchronized void reserveProviderRequest() {
        long now = System.nanoTime();
        if (now < nextRequestAtNanos) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Please wait before searching for another address"
            );
        }
        nextRequestAtNanos = now + MIN_REQUEST_INTERVAL_NANOS;
    }

    private record CachedSearch(List<LocationResult> results, Instant expiresAt) { }

    public record LocationResult(String label, BigDecimal latitude, BigDecimal longitude) { }
}
