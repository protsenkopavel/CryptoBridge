import axios from 'axios';

const api = axios.create({
    baseURL: '/api/v1',
    timeout: 12000000,
});

export const fetchAvailableExchanges = () => api.get('/scanner/available-exchanges').then(res => res.data);

export const fetchAvailablePairs = () => api.get('/scanner/available-pairs').then(res => res.data);

export const fetchBestSpreads = (filters) =>
    api.post('/spreads/best-spreads', filters).then(res => res.data.map(spread => ({
        ...spread,
        buyPrice: Number(spread.buyPrice),
        sellPrice: Number(spread.sellPrice),
        buyVolume: Number(spread.buyVolume),
        sellVolume: Number(spread.sellVolume),
        spreadPercentage: Number(spread.spreadPercentage),
        buyTradingInfo: spread.buyTradingInfo,
        sellTradingInfo: spread.sellTradingInfo
    })));
