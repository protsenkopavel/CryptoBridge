package net.protsenko.spotfetchprice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

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

        pairs = pairs.stream()
                .filter(pair -> {
                    String counter = pair.getCounter().toString();

                    boolean allowed =
                            (config.getWhitelist() == null ||
                            config.getWhitelist().isEmpty()) ||
                            config.getWhitelist().contains(counter);

                    boolean forbidden = (config.getBlacklist() != null && config.getBlacklist().contains(counter));

                    return allowed && !forbidden;
                })
                .toList();

        var exchanges = config.getExchangesToScan() != null
                ? config.getExchangesToScan()
                : exchangeService.getAvailableExchanges();

        for (var pair : pairs) {
            priceSpreadService.findMaxArbitrageSpreadForPair(
                    pair, exchanges, config.getMinVolume(), config.getMinProfitPercent(), config.getMaxProfitPercent()
            ).ifPresent(spread -> {
                String buyNetworks = spread.buyTradingInfo().networks().stream()
                        .map(n -> String.format("%s: withdrawFee=%.4f (deposit: %s, withdraw: %s)",
                                n.network(), n.withdrawFee(), n.depositEnabled(), n.withdrawEnabled()))
                        .collect(Collectors.joining("; "));
                String sellNetworks = spread.sellTradingInfo().networks().stream()
                        .map(n -> String.format("%s: withdrawFee=%.4f (deposit: %s, withdraw: %s)",
                                n.network(), n.withdrawFee(), n.depositEnabled(), n.withdrawEnabled()))
                        .collect(Collectors.joining("; "));

                log.info(
                        "Best arbitrage for pair {}: Buy on {} at {} [{}], " +
                                "sell on {} at {} [{}], spread = ({}%)",
                        spread.instrument(),
                        spread.buyExchange(),
                        spread.buyPrice(),
                        buyNetworks,
                        spread.sellExchange(),
                        spread.sellPrice(),
                        sellNetworks,
                        spread.profitPercent()
                );
            });
        }

        log.info("Arbitrage scan completed");
    }

}