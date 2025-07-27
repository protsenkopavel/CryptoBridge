package net.protsenko.spotfetchprice.service.exchange;

public class ExchangeClientHolder {
    private static final long COOLDOWN_MS = 60_000;
    private final ExchangeClient client;
    private final Exception initException;
    private final long errorTimestamp;

    public ExchangeClientHolder(ExchangeClient client) {
        this.client = client;
        this.initException = null;
        this.errorTimestamp = 0;
    }

    public ExchangeClientHolder(Exception e) {
        this.client = null;
        this.initException = e;
        this.errorTimestamp = System.currentTimeMillis();
    }

    public boolean isDisabled() {
        return client == null;
    }

    public ExchangeClient getClient() throws Exception {
        if (client != null) return client;
        throw initException;
    }

    public boolean canRetry() {
        return isDisabled() && (System.currentTimeMillis() - errorTimestamp) > COOLDOWN_MS;
    }
}