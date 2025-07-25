import React, { useEffect, useState } from 'react';
import { fetchAvailableExchanges, fetchAvailablePairs, fetchBestSpreads } from './api';

function App() {
    const formatVolume = (num) => {
        if (num === null || num === undefined) return '0.00';
        if (num >= 1_000_000_000) {
            return (num / 1_000_000_000).toFixed(2) + 'B';
        }
        if (num >= 1_000_000) {
            return (num / 1_000_000).toFixed(2) + 'M';
        }
        if (num >= 1_000) {
            return (num / 1_000).toFixed(2) + 'K';
        }
        return num.toFixed(2);
    };

    const [exchanges, setExchanges] = useState([]);
    const [pairs, setPairs] = useState([]);
    const [filteredPairs, setFilteredPairs] = useState([]);

    const [selectedExchanges, setSelectedExchanges] = useState([]);
    const [selectedPairs, setSelectedPairs] = useState([]);

    const [minProfit, setMinProfit] = useState(0);
    const [minVolume, setMinVolume] = useState(0);
    const [maxProfit, setMaxProfit] = useState(10);

    const [whitelist, setWhitelist] = useState('');
    const [blacklist, setBlacklist] = useState('');

    const [filterText, setFilterText] = useState('');

    const [spreads, setSpreads] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        fetchAvailableExchanges().then(setExchanges);
        fetchAvailablePairs().then(pairs => {
            setPairs(pairs);
            setFilteredPairs(pairs);
        });
    }, []);

    // Фильтрация пар по тексту
    useEffect(() => {
        if (!filterText.trim()) {
            setFilteredPairs(pairs);
        } else {
            const lower = filterText.toLowerCase();
            setFilteredPairs(pairs.filter(pair => pair.toLowerCase().includes(lower)));
        }
    }, [filterText, pairs]);

    const toggleSelection = (item, list, setList) => {
        if (list.includes(item)) {
            setList(list.filter(i => i !== item));
        } else {
            setList([...list, item]);
        }
    };

    const selectAllPairs = () => {
        setSelectedPairs(filteredPairs);
    };

    const handleFetchSpreads = () => {
        setLoading(true);
        fetchBestSpreads({
            exchanges: selectedExchanges.length ? selectedExchanges : null,
            pairs: selectedPairs.length ? selectedPairs : null,
            minProfitPercent: minProfit,
            maxProfitPercent: maxProfit,
            minVolume: minVolume,
            whitelist: whitelist.split(',').map(item => item.trim()).filter(item => item),
            blacklist: blacklist.split(',').map(item => item.trim()).filter(item => item),
        })
            .then(data => setSpreads(data))
            .finally(() => setLoading(false));
    };

    return (
        <div style={{ padding: 20, maxWidth: 800, margin: '0 auto' }}>
            <h1>Арбитражный сканер</h1>

            <h3>Выберите биржи:</h3>
            <div>
                {exchanges.map(exchange => (
                    <label key={exchange} style={{ marginRight: 10 }}>
                        <input
                            type="checkbox"
                            checked={selectedExchanges.includes(exchange)}
                            onChange={() => toggleSelection(exchange, selectedExchanges, setSelectedExchanges)}
                        />
                        {exchange}
                    </label>
                ))}
            </div>

            <h3>Выберите пары:</h3>
            <input
                type="text"
                placeholder="Фильтр по паре, например: usdt, usdc"
                value={filterText}
                onChange={e => setFilterText(e.target.value)}
                style={{ marginBottom: 10, width: '100%', padding: 5 }}
            />
            <button onClick={selectAllPairs} style={{ marginBottom: 10 }}>
                Выбрать все пары
            </button>

            <div style={{ maxHeight: 150, overflowY: 'auto', border: '1px solid #ccc', padding: 10 }}>
                {filteredPairs.map(pair => (
                    <label key={pair} style={{ display: 'block' }}>
                        <input
                            type="checkbox"
                            checked={selectedPairs.includes(pair)}
                            onChange={() => toggleSelection(pair, selectedPairs, setSelectedPairs)}
                        />
                        {pair}
                    </label>
                ))}
            </div>

            <div style={{ marginTop: 20 }}>
                <label>
                    Минимальный профит (%):
                    <input
                        type="number"
                        value={minProfit}
                        onChange={e => setMinProfit(Number(e.target.value))}
                        style={{ marginLeft: 10, width: 60 }}
                    />
                </label>

                <label style={{ marginLeft: 20 }}>
                    Минимальный объем:
                    <input
                        type="number"
                        value={minVolume}
                        onChange={e => setMinVolume(Number(e.target.value))}
                        style={{ marginLeft: 10, width: 80 }}
                    />
                </label>
            </div>

            <div style={{ marginTop: 10 }}>
                <label>
                    Максимальный профит (%):
                    <input
                        type="number"
                        value={maxProfit}
                        onChange={e => setMaxProfit(Number(e.target.value))}
                        style={{ marginLeft: 10, width: 60 }}
                    />
                </label>
            </div>

            <div style={{ marginTop: 10 }}>
                <label>
                    Белый список пар (через запятую):
                    <input
                        type="text"
                        value={whitelist}
                        onChange={e => setWhitelist(e.target.value)}
                        style={{ marginLeft: 10, width: '100%' }}
                    />
                </label>
            </div>

            <div style={{ marginTop: 10 }}>
                <label>
                    Черный список пар (через запятую):
                    <input
                        type="text"
                        value={blacklist}
                        onChange={e => setBlacklist(e.target.value)}
                        style={{ marginLeft: 10, width: '100%' }}
                    />
                </label>
            </div>

            <button
                onClick={handleFetchSpreads}
                disabled={loading}
                style={{ marginTop: 20, padding: '10px 20px' }}
            >
                {loading ? 'Загрузка...' : 'Показать лучшие спреды'}
            </button>

            <h3 style={{ marginTop: 40 }}>Результаты:</h3>
            {spreads.length === 0 && !loading && (
                <div style={{ padding: '10px', backgroundColor: '#ffdddd', color: '#cc0000', borderRadius: '4px' }}>
                    Нет данных по выбранным критериям
                </div>
            )}
            {spreads.length > 0 && (
                <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '20px' }}>
                    <thead>
                        <tr style={{ backgroundColor: '#f5f5f5' }}>
                            <th style={{ padding: '8px', border: '1px solid #ddd' }}>Инструмент</th>
                            <th style={{ padding: '8px', border: '1px solid #ddd' }}>Биржа покупки</th>
                            <th style={{ padding: '8px', border: '1px solid #ddd' }}>Биржа продажи</th>
                            <th style={{ padding: '8px', border: '1px solid #ddd' }}>Профит%</th>
                            <th style={{ padding: '8px', border: '1px solid #ddd' }}>Сети вывода</th>
                            <th style={{ padding: '8px', border: '1px solid #ddd' }}>Сети депозита</th>
                        </tr>
                    </thead>
                    <tbody>
                        {spreads.map((spread, index) => (
                            <tr key={`${spread.instrument}-${index}`} style={{ backgroundColor: index % 2 === 0 ? '#fff' : '#f9f9f9' }}>
                                <td style={{ padding: '8px', border: '1px solid #ddd' }}>{spread.instrument}</td>
                                <td style={{ padding: '8px', border: '1px solid #ddd' }}>
                                    <strong>{spread.buyExchange}</strong><br />
                                    Цена: ${spread.buyPrice.toFixed(6)}<br />
                                    Объем: {formatVolume(spread.buyVolume)}
                                </td>
                                <td style={{ padding: '8px', border: '1px solid #ddd' }}>
                                    <strong>{spread.sellExchange}</strong><br />
                                    Цена: ${spread.sellPrice.toFixed(6)}<br />
                                    Объем: {formatVolume(spread.sellVolume)}
                                </td>
                                <td style={{
                                    padding: '8px',
                                    border: '1px solid #ddd',
                                    fontWeight: 'bold',
                                    color: spread.spreadPercentage > 0 ? 'green' : 'red'
                                }}>
                                    {spread.spreadPercentage.toFixed(2)}%
                                </td>
                                <td style={{ padding: '8px', border: '1px solid #ddd' }}>
                                    {spread.buyTradingInfo?.networks?.map(network => (
                                        <span key={network.network} style={{ color: network.withdrawEnabled ? 'inherit' : 'red' }}>
                                            {network.network} ({network.withdrawFee === -1 ? 'N/A' : network.withdrawFee})<br />
                                        </span>
                                    )) ?? 'N/A'}
                                </td>
                                <td style={{ padding: '8px', border: '1px solid #ddd' }}>
                                    {spread.sellTradingInfo?.networks?.map(network => (
                                        <span key={network.network} style={{ color: network.depositEnabled ? 'inherit' : 'red' }}>
                                            {network.network} ({network.withdrawFee === -1 ? 'N/A' : network.withdrawFee})<br />
                                        </span>
                                    )) ?? 'N/A'}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}
        </div>
    );
}

export default App;
