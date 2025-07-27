package net.protsenko.spotfetchprice.util;

import java.util.HashMap;
import java.util.Map;

public class NetworkNormalizer {

    private static final Map<String, String> NETWORK_MAP = new HashMap<>();

    static {
        // Ethereum ERC20
        NETWORK_MAP.put("ETH", "ERC20");
        NETWORK_MAP.put("ERC20", "ERC20");
        NETWORK_MAP.put("ETHEREUM", "ERC20");
        NETWORK_MAP.put("ETHEREUM(ERC20)", "ERC20");
        NETWORK_MAP.put("ETHEREUM ERC20", "ERC20");
        NETWORK_MAP.put("ETH-ERC20", "ERC20");
        NETWORK_MAP.put("ERC-20", "ERC20");

        // Binance Smart Chain BEP20
        NETWORK_MAP.put("BNB", "BEP20");
        NETWORK_MAP.put("BSC", "BEP20");
        NETWORK_MAP.put("BEP20", "BEP20");
        NETWORK_MAP.put("BNB SMART CHAIN", "BEP20");
        NETWORK_MAP.put("BINANCE SMART CHAIN", "BEP20");
        NETWORK_MAP.put("BSC(BEP20)", "BEP20");
        NETWORK_MAP.put("BNB SMART CHAIN (BEP20)", "BEP20");

        // Polygon/Matic
        NETWORK_MAP.put("MATIC", "POLYGON");
        NETWORK_MAP.put("POLYGON", "POLYGON");
        NETWORK_MAP.put("POLYGON(MATIC)", "POLYGON");

        // Tron
        NETWORK_MAP.put("TRC20", "TRC20");
        NETWORK_MAP.put("TRON", "TRC20");
        NETWORK_MAP.put("TRON(TRC20)", "TRC20");

        // Solana
        NETWORK_MAP.put("SOL", "SOL");
        NETWORK_MAP.put("SOLANA", "SOL");
        NETWORK_MAP.put("SOLANA(SOL)", "SOL");

        // Arbitrum
        NETWORK_MAP.put("ARB", "ARBITRUM");
        NETWORK_MAP.put("ARBITRUM", "ARBITRUM");
        NETWORK_MAP.put("ARBITRUM ONE(ARB)", "ARBITRUM");
        NETWORK_MAP.put("ARBITRUM ONE", "ARBITRUM");

        // Base
        NETWORK_MAP.put("BASE", "BASE");
        NETWORK_MAP.put("BASE MAINNET", "BASE");

        // OP Mainnet / Optimism
        NETWORK_MAP.put("OPTIMISM", "OPTIMISM");
        NETWORK_MAP.put("OP", "OPTIMISM");
        NETWORK_MAP.put("OP MAINNET", "OPTIMISM");

        // ZKSync
        NETWORK_MAP.put("ZKSYNC LITE", "ZKSYNC");
        NETWORK_MAP.put("ZKSYNC ERA", "ZKSYNC");
        NETWORK_MAP.put("ZKSYNCERA", "ZKSYNC");

        // Mantle
        NETWORK_MAP.put("MANTLE NETWORK", "MANTLE");

        // Starknet
        NETWORK_MAP.put("STARKNET", "STARKNET");

        // Metal
        NETWORK_MAP.put("MTL", "MTL");

        // TON
        NETWORK_MAP.put("TON", "TON");

        // Terra Classic
        NETWORK_MAP.put("TERRA CLASSIC", "TERRA");

        // XRP
        NETWORK_MAP.put("XRP", "XRP");

        // DOGE
        NETWORK_MAP.put("DOGE", "DOGE");
        NETWORK_MAP.put("DOGECOIN", "DOGE");

        // HBAR / Hedera
        NETWORK_MAP.put("HBAR", "HBAR");
        NETWORK_MAP.put("HEDERA", "HBAR");

        // FLARE
        NETWORK_MAP.put("FLR", "FLARE");

        // HyperEVM
        NETWORK_MAP.put("HYPEREVM", "HYPEREVM");

        // Bitcoin Cash
        NETWORK_MAP.put("BITCOIN CASH", "BCH");
        NETWORK_MAP.put("BCH", "BCH");

        // Chiliz Chain
        NETWORK_MAP.put("CHILIZ CHAIN", "CHILIZ");

        // Sahara
        NETWORK_MAP.put("SAHARA", "SAHARA");

        // ZirCuit
        NETWORK_MAP.put("ZIRCUIT", "ZIRCUIT");

        // Eclipse
        NETWORK_MAP.put("ECLIPSE", "ECLIPSE");

        // SUI
        NETWORK_MAP.put("SUI", "SUI");

        // OTHERS
        NETWORK_MAP.put("FXEVM", "FXEVM");
        NETWORK_MAP.put("EGLD", "EGLD");
        NETWORK_MAP.put("ELROND", "EGLD");
    }

    public static String normalize(String rawNetwork) {
        if (rawNetwork == null) return null;
        String cleaned = rawNetwork.trim().toUpperCase();

        if (cleaned.contains("(")) cleaned = cleaned.replaceAll("\\(.*?\\)", "").trim();

        if (NETWORK_MAP.containsKey(cleaned)) return NETWORK_MAP.get(cleaned);

        if (cleaned.contains("ERC20")) return "ERC20";
        if (cleaned.contains("BEP20")) return "BEP20";
        if (cleaned.contains("TRC20")) return "TRC20";
        if (cleaned.contains("SOL")) return "SOL";
        if (cleaned.contains("MATIC") || cleaned.contains("POLYGON")) return "POLYGON";
        if (cleaned.contains("ARB")) return "ARBITRUM";
        if (cleaned.contains("BASE")) return "BASE";
        if (cleaned.contains("OPTI") || cleaned.contains("OP MAINNET")) return "OPTIMISM";
        if (cleaned.contains("ZKSYNC")) return "ZKSYNC";
        if (cleaned.contains("MANTLE")) return "MANTLE";
        if (cleaned.contains("STARKNET")) return "STARKNET";
        if (cleaned.contains("MTL")) return "MTL";
        if (cleaned.contains("TON")) return "TON";
        if (cleaned.contains("TERRA")) return "TERRA";
        if (cleaned.contains("XRP")) return "XRP";
        if (cleaned.contains("DOGE")) return "DOGE";
        if (cleaned.contains("HBAR") || cleaned.contains("HEDERA")) return "HBAR";
        if (cleaned.contains("FLR")) return "FLARE";
        if (cleaned.contains("HYPEREVM")) return "HYPEREVM";
        if (cleaned.contains("BITCOIN CASH") || cleaned.contains("BCH")) return "BCH";
        if (cleaned.contains("CHILIZ")) return "CHILIZ";
        if (cleaned.contains("SAHARA")) return "SAHARA";
        if (cleaned.contains("ZIRCUIT")) return "ZIRCUIT";
        if (cleaned.contains("ECLIPSE")) return "ECLIPSE";
        if (cleaned.contains("SUI")) return "SUI";
        if (cleaned.contains("FXEVM")) return "FXEVM";
        if (cleaned.contains("EGLD") || cleaned.contains("ELROND")) return "EGLD";

        return cleaned;
    }
}
