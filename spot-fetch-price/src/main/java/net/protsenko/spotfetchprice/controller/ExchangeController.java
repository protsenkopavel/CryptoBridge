package net.protsenko.spotfetchprice.controller;

import lombok.RequiredArgsConstructor;
import net.protsenko.spotfetchprice.dto.ExchangeTickersDTO;
import net.protsenko.spotfetchprice.service.ExchangeService;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/scanner")
public class ExchangeController {

    private final ExchangeService exchangeService;

    @GetMapping("test")
    public List<ExchangeTickersDTO> getPublicMarketData() {
        return exchangeService.getAllMarketDataForAllExchanges(
                List.of(ExchangeType.BYBIT, ExchangeType.BITGET, ExchangeType.KUCOIN),
                List.of(CurrencyPair.BTC_USDT, CurrencyPair.ETH_USDT)
        );
    }

    @GetMapping("/available-exchanges")
    public List<ExchangeType> getAvailableExchanges() {
        return exchangeService.getAvailableExchanges();
    }

    @GetMapping("/available-pairs")
    public List<CurrencyPair> getAvailablePairs() {
        return exchangeService.getAvailableCurrencyPairs(List.of(ExchangeType.BYBIT));
    }

}
