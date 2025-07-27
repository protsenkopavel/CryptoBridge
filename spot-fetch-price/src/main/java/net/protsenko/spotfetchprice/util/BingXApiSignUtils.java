package net.protsenko.spotfetchprice.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class BingXApiSignUtils {

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private BingXApiSignUtils() {
    }

    public static String getMessageToDigest(TreeMap<String, String> parameters) {
        boolean first = true;
        StringBuilder valueToDigest = new StringBuilder();
        for (Map.Entry<String, String> e : parameters.entrySet()) {
            if (!first) {
                valueToDigest.append("&");
            }
            first = false;
            valueToDigest.append(e.getKey()).append("=").append(e.getValue());
        }
        return valueToDigest.toString();
    }

    public static String generateHmac256(String message, String secret) {
        try {
            byte[] bytes = hmac(secret.getBytes(StandardCharsets.UTF_8), message.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (Exception e) {
            log.error("generateHmac256 exception: {}", e.toString());
        }
        return "";
    }

    private static byte[] hmac(byte[] key, byte[] message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message);
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

}
