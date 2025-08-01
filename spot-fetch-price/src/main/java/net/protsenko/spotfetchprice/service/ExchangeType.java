package net.protsenko.spotfetchprice.service;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitget.BitgetExchange;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.coinex.CoinexExchange;
import org.knowm.xchange.gateio.GateioExchange;
import org.knowm.xchange.huobi.HuobiExchange;
import org.knowm.xchange.kucoin.KucoinExchange;

public enum ExchangeType {
    BYBIT(BybitExchange.class),
    MEXC(null),
    GATEIO(GateioExchange.class),
    KUCOIN(KucoinExchange.class),
    BITGET(BitgetExchange.class),
    COINEX(CoinexExchange.class),
    HUOBI(HuobiExchange.class),
    BITFINEX(BitfinexExchange.class),
    OKX(null),
    BINGX(null);

    private final Class<? extends Exchange> exchangeClass;

    ExchangeType(Class<? extends Exchange> exchangeClass) {
        this.exchangeClass = exchangeClass;
    }

    public Exchange createExchange() {
        return ExchangeFactory.INSTANCE.createExchange(exchangeClass);
    }

}