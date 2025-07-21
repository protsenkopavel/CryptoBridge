package net.protsenko.cryptobridge.cryptobridge.controller;

import lombok.RequiredArgsConstructor;
import net.protsenko.cryptobridge.cryptobridge.service.ExchangeService;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController("/")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeService exchangeService;

    @GetMapping("test")
    public Map<String, List<Ticker>> getPublicMarketData() {
        return exchangeService.getAllMarketDataForAllExchanges();
    }

}
