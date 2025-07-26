package net.protsenko.spotfetchprice.util;

import java.util.HashMap;
import java.util.Map;

public class NetworkNormalizer {

    private static final Map<String, String> NETWORK_MAP = new HashMap<>();

    static {
        // Ethereum ERC20
        NETWORK_MAP.put("ETH", "ERC20");
        NETWORK_MAP.put("ERC20", "ERC20");
        NETWORK_MAP.put("Ethereum", "ERC20");
        NETWORK_MAP.put("Ethereum(ERC20)", "ERC20");
        NETWORK_MAP.put("Ethereum ERC20", "ERC20");
        NETWORK_MAP.put("ETH-ERC20", "ERC20");
        NETWORK_MAP.put("ERC-20", "ERC20");

        // Binance Smart Chain BEP20
        NETWORK_MAP.put("BNB", "BEP20");
        NETWORK_MAP.put("BSC", "BEP20");
        NETWORK_MAP.put("BEP20", "BEP20");
        NETWORK_MAP.put("BNB Smart Chain(BEP20)", "BEP20");
        NETWORK_MAP.put("Binance Smart Chain", "BEP20");
        NETWORK_MAP.put("BSC(BEP20)", "BEP20");

        // Polygon
        NETWORK_MAP.put("MATIC", "POLYGON");
        NETWORK_MAP.put("Polygon", "POLYGON");
        NETWORK_MAP.put("Polygon(MATIC)", "POLYGON");

        // Tron
        NETWORK_MAP.put("TRC20", "TRC20");
        NETWORK_MAP.put("TRON", "TRC20");
        NETWORK_MAP.put("TRON(TRC20)", "TRC20");

        // Solana
        NETWORK_MAP.put("SOL", "SOL");
        NETWORK_MAP.put("Solana", "SOL");
        NETWORK_MAP.put("Solana(SOL)", "SOL");

        // Arbitrum
        NETWORK_MAP.put("ARB", "ARBITRUM");
        NETWORK_MAP.put("Arbitrum", "ARBITRUM");
        NETWORK_MAP.put("Arbitrum One(ARB)", "ARBITRUM");

        // BASE
        NETWORK_MAP.put("BASE", "BASE");

        // OTHERS
        NETWORK_MAP.put("FXEVM", "FXEVM");
        NETWORK_MAP.put("EGLD", "EGLD");
        NETWORK_MAP.put("Elrond", "EGLD");
    }

    public static String normalize(String rawNetwork) {
        if (rawNetwork == null) return null;
        String cleaned = rawNetwork.trim().toUpperCase();
        if (NETWORK_MAP.containsKey(cleaned)) return NETWORK_MAP.get(cleaned);

        if (cleaned.contains("ERC20")) return "ERC20";
        if (cleaned.contains("BEP20")) return "BEP20";
        if (cleaned.contains("TRC20")) return "TRC20";
        if (cleaned.contains("SOL")) return "SOL";
        if (cleaned.contains("MATIC") || cleaned.contains("POLYGON")) return "POLYGON";
        if (cleaned.contains("ARB")) return "ARBITRUM";

        return cleaned;
    }
}
