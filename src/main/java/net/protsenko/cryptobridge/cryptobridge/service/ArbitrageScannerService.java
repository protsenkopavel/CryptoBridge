package net.protsenko.cryptobridge.cryptobridge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrageScannerService {

    private final ExchangeService exchangeService;
    private final PriceSpreadService priceSpreadService;
    private final ArbitrageScannerConfig config;

    public void scanBestSpreads() {
        log.info("Starting arbitrage scan");

        var pairs = config.getPairsToScan() != null
                ? config.getPairsToScan()
                : exchangeService.getAvailableCurrencyPairs(config.getExchangesToScan());

        var exchanges = config.getExchangesToScan() != null
                ? config.getExchangesToScan()
                : exchangeService.getAvailableExchanges();

        for (var pair : pairs) {
            priceSpreadService.findMaxArbitrageSpreadForPair(pair, exchanges, config.getMinVolume())
                    .ifPresent(spread -> {
                        log.info("Best arbitrage for pair {}: Buy on {} at {}, sell on {} at {}, spread = ({}%)",
                                spread.instrument(),
                                spread.buyExchange(),
                                spread.buyPrice(),
                                spread.sellExchange(),
                                spread.sellPrice(),
                                String.format("%.2f", spread.spreadPercent()));
                    });
        }

        log.info("Arbitrage scan completed");
    }

}