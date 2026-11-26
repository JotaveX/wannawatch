package br.com.jotave.wannawatch.recommendation.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Ruído determinístico em [0, 1): o mesmo par (seed, id) sempre dá o mesmo valor. Base: md5("seed:id"). */
public final class SeededNoise {

    private SeededNoise() {}

    public static double of(String seed, long id) {
        try {
            byte[] h = MessageDigest.getInstance("MD5").digest((seed + ":" + id).getBytes(StandardCharsets.UTF_8));
            long v = ((h[0] & 0xFFL) << 24) | ((h[1] & 0xFFL) << 16) | ((h[2] & 0xFFL) << 8) | (h[3] & 0xFFL);
            return v / (double) (1L << 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
