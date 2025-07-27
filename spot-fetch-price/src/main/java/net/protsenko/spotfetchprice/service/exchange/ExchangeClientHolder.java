package net.protsenko.spotfetchprice.service.exchange;

import lombok.Data;

@Data
public class ExchangeClientHolder {

    private final ExchangeClient client;
    private final Exception initException;
    private final boolean disabled;

    public ExchangeClientHolder(ExchangeClient client) {
        this.client = client;
        this.initException = null;
        this.disabled = false;
    }

    public ExchangeClientHolder(Exception ex) {
        this.client = null;
        this.initException = ex;
        this.disabled = true;
    }

}