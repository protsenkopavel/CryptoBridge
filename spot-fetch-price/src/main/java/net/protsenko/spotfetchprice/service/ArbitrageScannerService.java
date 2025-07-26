package net.protsenko.spotfetchprice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.ArbitrageOpportunityFoundEvent;
import net.protsenko.spotfetchprice.mapper.ServiceMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrageScannerService {

    private final ExchangeService exchangeService;
    private final PriceSpreadService priceSpreadService;
    private final ArbitrageScannerConfigService arbitrageScannerConfigService;
    private final ArbitrageScannerConfig config;
    private final ApplicationEventPublisher eventPublisher;
    private final ServiceMapper serviceMapper;

    public void scanBestSpreads() {
        log.info("Starting arbitrage scan");

        var pairs = config.getPairsToScan() != null
                ? config.getPairsToScan()
                : exchangeService.getAvailableCurrencyPairs(config.getExchangesToScan());

        var whitelist = arbitrageScannerConfigService.getWhitelist();
        var blacklist = arbitrageScannerConfigService.getBlacklist();

        pairs = pairs.stream()
                .filter(pair -> {
                    String counter = pair.getCounter().toString();

                    boolean allowed =
                            (whitelist == null || whitelist.isEmpty()) || whitelist.contains(counter);

                    boolean forbidden = (blacklist) != null && blacklist.contains(counter);

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

                eventPublisher.publishEvent(new ArbitrageOpportunityFoundEvent(serviceMapper.toDto(spread)));
            });
        }

        log.info("Arbitrage scan completed");
    }

}