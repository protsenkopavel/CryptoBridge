package net.protsenko.spotfetchprice.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class OKXApiSignUtils {

    private OKXApiSignUtils() {
    }

    public static String sign(String preHash, String secret) {
        byte[] hash = null;
        try {
            Mac sha256Mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Mac.init(secretKey);
            hash = sha256Mac.doFinal(preHash.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        return Base64.getEncoder().encodeToString(hash);
    }

}
