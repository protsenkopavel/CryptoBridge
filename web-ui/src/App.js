import React, { useEffect, useState } from 'react';
import { fetchAvailableExchanges, fetchAvailablePairs, fetchBestSpreads } from './api';

function App() {
    const [exchanges, setExchanges] = useState([]);
    const [pairs, setPairs] = useState([]);
    const [filteredPairs, setFilteredPairs] = useState([]);

    const [selectedExchanges, setSelectedExchanges] = useState([]);
    const [selectedPairs, setSelectedPairs] = useState([]);

    const [minProfit, setMinProfit] = useState(0);
    const [minVolume, setMinVolume] = useState(0);

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
            minVolume: minVolume,
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

            <button
                onClick={handleFetchSpreads}
                disabled={loading}
                style={{ marginTop: 20, padding: '10px 20px' }}
            >
                {loading ? 'Загрузка...' : 'Показать лучшие спреды'}
            </button>

            <h3 style={{ marginTop: 40 }}>Результаты:</h3>
            <ul>
                {spreads.length === 0 && !loading && <li>Нет результатов</li>}
                {spreads.map(spread => (
                    <li key={`${spread.instrument}-${spread.buyExchange}-${spread.sellExchange}`}>
                        {spread.instrument} — Купить на {spread.buyExchange} за {spread.buyPrice.toFixed(6)}, продать на {spread.sellExchange} за {spread.sellPrice.toFixed(6)} — спред {spread.spreadPercentage.toFixed(2)}%
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default App;
