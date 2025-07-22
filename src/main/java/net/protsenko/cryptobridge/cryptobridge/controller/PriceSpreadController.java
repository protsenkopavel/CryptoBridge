package net.protsenko.cryptobridge.cryptobridge.controller;

import lombok.RequiredArgsConstructor;
import net.protsenko.cryptobridge.cryptobridge.dto.PriceSpreadResult;
import net.protsenko.cryptobridge.cryptobridge.service.ExchangeType;
import net.protsenko.cryptobridge.cryptobridge.service.PriceSpreadService;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spreads")
public class PriceSpreadController {

    private final PriceSpreadService priceSpreadService;

    @GetMapping("/test")
    public PriceSpreadResult getSpreadsByExchangerAndTicker() {
        return priceSpreadService.findMaxArbitrageSpreadForPair(
                        CurrencyPair.BTC_USDT,
                        List.of(ExchangeType.BYBIT, ExchangeType.BITGET),
                        0.0,
                        1.0

                )
                .orElse(null);
    }

}
