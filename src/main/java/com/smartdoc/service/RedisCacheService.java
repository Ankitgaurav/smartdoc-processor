package com.smartdoc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Handles all Redis cache operations for document extraction results.
 *
 * Cache key format: "doc:extract:{SHA256_HASH}"
 * Example: "doc:extract:00b32e01a1e93f9c24b6569fa87176483d2c6d..."
 *
 * Why use document hash as cache key?
 * Same file content = same hash = same extraction result.
 * If a user uploads the same invoice twice, the second upload
 * returns cached results instantly — no Groq API call needed.
 * This saves API quota and dramatically reduces response time.
 *
 * TTL: 24 hours — extraction results are unlikely to change.
 * After 24 hours Redis auto-evicts the entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "doc:extract:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /**
     * Retrieves cached extraction result for a given document hash.
     *
     * @param documentHash SHA-256 hash of the document content
     * @return Optional with cached JSON string, or empty if not cached
     */
    public Optional<String> getCachedExtraction(String documentHash) {
        String key = KEY_PREFIX + documentHash;
        String value = redisTemplate.opsForValue().get(key);

        if (value != null) {
            log.info("Cache HIT for hash: {}...", documentHash.substring(0, 16));
            return Optional.of(value);
        }

        log.info("Cache MISS for hash: {}...", documentHash.substring(0, 16));
        return Optional.empty();
    }

    /**
     * Saves extraction result to Redis cache with 24-hour TTL.
     *
     * @param documentHash  SHA-256 hash as cache key
     * @param extractedData JSON string of extracted data
     */
    public void cacheExtraction(String documentHash, String extractedData) {
        String key = KEY_PREFIX + documentHash;
        redisTemplate.opsForValue().set(key, extractedData, DEFAULT_TTL);
        log.info("Cached extraction for hash: {}...", documentHash.substring(0, 16));
    }

    /**
     * Removes a cached entry — useful if reprocessing is triggered.
     *
     * @param documentHash SHA-256 hash of the document
     */
    public void evictCache(String documentHash) {
        String key = KEY_PREFIX + documentHash;
        redisTemplate.delete(key);
        log.info("Evicted cache for hash: {}...", documentHash.substring(0, 16));
    }
}