package com.smartdoc.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for computing SHA-256 hash of file content.
 *
 * Why hash the file?
 * When the same document is uploaded twice, the hash will be identical.
 * We use this hash as a Redis cache key — if a document with the same
 * hash was already processed, we return the cached AI result immediately
 * without calling OpenAI again. This saves API costs significantly.
 *
 * SHA-256 produces a 64-character hexadecimal string.
 * Example: "a3f1bc9d..." (64 chars)
 */
@Slf4j
public class FileHashUtil {

    private FileHashUtil() {}

    /**
     * Computes SHA-256 hash of the file content.
     *
     * @param inputStream file content stream
     * @return 64-character hex string representing the file hash
     */
    public static String computeSha256(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];    // read 8KB at a time
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            // Convert byte array to hex string
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to compute SHA-256 hash: {}", e.getMessage());
            throw new RuntimeException("Failed to compute file hash", e);
        }
    }
}