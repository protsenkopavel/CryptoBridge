import React, { useEffect, useState, useCallback } from 'react';
import { fetchAvailableExchanges, fetchAvailablePairs, fetchBestSpreads } from './api';
import { FixedSizeList as List } from 'react-window';
import './App.css';

function PairCheckboxList({ pairs, selectedPairs, onToggle }) {
    // Сюда можно добавить поиск, если хочется
    return (
        <List
            height={150}
            itemCount={pairs.length}
            itemSize={32}
            width={'100%'}
            style={{ border: '1px solid #e5e7eb', borderRadius: 8, overflow: 'auto' }}
        >
            {({ index, style }) => {
                const pair = pairs[index];
                return (
                    <div style={style} key={pair}>
                        <label style={{ display: 'block', paddingLeft: 5 }}>
                            <input
                                type="checkbox"
                                checked={selectedPairs.includes(pair)}
                                onChange={() => onToggle(pair)}
                            />
                            {pair}
                        </label>
                    </div>
                );
            }}
        </List>
    );
}

function App() {
    const formatVolume = (num) => {
        if (num === null || num === undefined) return '0.00';
        if (num >= 1_000_000_000) return (num / 1_000_000_000).toFixed(2) + 'B';
        if (num >= 1_000_000) return (num / 1_000_000).toFixed(2) + 'M';
        if (num >= 1_000) return (num / 1_000).toFixed(2) + 'K';
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

    useEffect(() => {
        if (!filterText.trim()) setFilteredPairs(pairs);
        else {
            const lower = filterText.toLowerCase();
            setFilteredPairs(pairs.filter(pair => pair.toLowerCase().includes(lower)));
        }
    }, [filterText, pairs]);

    const toggleSelection = useCallback((item, list, setList) => {
        if (list.includes(item)) setList(list.filter(i => i !== item));
        else setList([...list, item]);
    }, []);

    const selectAllPairs = () => setSelectedPairs(filteredPairs);

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
        <div className="app-container">
            <h1>Арбитражный сканер</h1>

            <div className="section">
                <h3>Выберите биржи:</h3>
                <div className="select-group">
                    {exchanges.map(exchange => (
                        <label key={exchange}>
                            <input
                                type="checkbox"
                                checked={selectedExchanges.includes(exchange)}
                                onChange={() => toggleSelection(exchange, selectedExchanges, setSelectedExchanges)}
                            />
                            {exchange}
                        </label>
                    ))}
                </div>
            </div>

            <div className="section">
                <h3>Выберите пары:</h3>
                <input
                    type="text"
                    placeholder="Фильтр по паре, например: usdt, usdc"
                    value={filterText}
                    onChange={e => setFilterText(e.target.value)}
                    style={{ marginBottom: 10, width: '100%', padding: 7 }}
                />
                <button onClick={selectAllPairs} style={{ marginBottom: 10 }}>
                    Выбрать все пары
                </button>
                <PairCheckboxList
                    pairs={filteredPairs}
                    selectedPairs={selectedPairs}
                    onToggle={pair => toggleSelection(pair, selectedPairs, setSelectedPairs)}
                />
            </div>

            <div className="section input-row">
                <label>
                    Минимальный профит (%):
                    <input
                        type="number"
                        value={minProfit}
                        min={0}
                        step={0.01}
                        onChange={e => setMinProfit(Number(e.target.value))}
                        style={{ marginLeft: 10, width: 80 }}
                    />
                </label>
                <label>
                    Минимальный объем:
                    <input
                        type="number"
                        value={minVolume}
                        min={0}
                        step={0.01}
                        onChange={e => setMinVolume(Number(e.target.value))}
                        style={{ marginLeft: 10, width: 80 }}
                    />
                </label>
                <label>
                    Максимальный профит (%):
                    <input
                        type="number"
                        value={maxProfit}
                        min={0}
                        step={0.01}
                        onChange={e => setMaxProfit(Number(e.target.value))}
                        style={{ marginLeft: 10, width: 80 }}
                    />
                </label>
            </div>

            <div className="section input-row">
                <label>
                    Белый список пар:
                    <input
                        type="text"
                        value={whitelist}
                        onChange={e => setWhitelist(e.target.value)}
                        placeholder="через запятую"
                        style={{ marginLeft: 10, width: 220 }}
                    />
                </label>
                <label>
                    Черный список пар:
                    <input
                        type="text"
                        value={blacklist}
                        onChange={e => setBlacklist(e.target.value)}
                        placeholder="через запятую"
                        style={{ marginLeft: 10, width: 220 }}
                    />
                </label>
            </div>

            <button onClick={handleFetchSpreads} disabled={loading}>
                {loading ? 'Загрузка...' : 'Показать лучшие спреды'}
            </button>

            <div className="section table-container">
                <h3>Результаты:</h3>
                {spreads.length === 0 && !loading && (
                    <div style={{
                        padding: '13px 12px', background: '#fee2e2',
                        color: '#b91c1c', borderRadius: '8px'
                    }}>
                        Нет данных по выбранным критериям
                    </div>
                )}
                {spreads.length > 0 && (
                    <table>
                        <thead>
                        <tr>
                            <th>Инструмент</th>
                            <th>Биржа покупки</th>
                            <th>Биржа продажи</th>
                            <th>Профит%</th>
                            <th>Сети вывода</th>
                            <th>Сети депозита</th>
                        </tr>
                        </thead>
                        <tbody>
                        {spreads.map((spread, index) => (
                            <tr key={`${spread.instrument}-${index}`}>
                                <td>{spread.instrument}</td>
                                <td>
                                    <strong>{spread.buyExchange}</strong><br />
                                    Цена: ${spread.buyPrice.toFixed(6)}<br />
                                    Объем: {formatVolume(spread.buyVolume)}
                                </td>
                                <td>
                                    <strong>{spread.sellExchange}</strong><br />
                                    Цена: ${spread.sellPrice.toFixed(6)}<br />
                                    Объем: {formatVolume(spread.sellVolume)}
                                </td>
                                <td style={{
                                    fontWeight: 'bold',
                                    color: spread.spreadPercentage > 0 ? '#059669' : '#dc2626'
                                }}>
                                    {spread.spreadPercentage.toFixed(2)}%
                                </td>
                                <td>
                                    {spread.buyTradingInfo?.networks?.length
                                        ? spread.buyTradingInfo.networks.map(network => (
                                            <span key={network.network}
                                                  style={{ color: network.withdrawEnabled ? 'inherit' : '#ef4444' }}>
                          {network.network} ({network.withdrawFee === -1 ? 'N/A' : network.withdrawFee})<br />
                        </span>
                                        ))
                                        : 'N/A'}
                                </td>
                                <td>
                                    {spread.sellTradingInfo?.networks?.length
                                        ? spread.sellTradingInfo.networks.map(network => (
                                            <span key={network.network}
                                                  style={{ color: network.depositEnabled ? 'inherit' : '#ef4444' }}>
                          {network.network} ({network.withdrawFee === -1 ? 'N/A' : network.withdrawFee})<br />
                        </span>
                                        ))
                                        : 'N/A'}
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>
        </div>
    );
}

export default App;
