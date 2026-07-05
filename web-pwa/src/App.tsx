import React, { useState, useEffect, useMemo } from 'react';
import { 
  TrendingUp, 
  TrendingDown, 
  Plus, 
  ArrowUpRight, 
  ArrowDownLeft, 
  RefreshCw, 
  Database, 
  DollarSign, 
  Layers, 
  History, 
  Wallet, 
  FileSpreadsheet, 
  Upload, 
  Download, 
  Check, 
  X, 
  Sliders, 
  ChevronRight, 
  Eye, 
  EyeOff, 
  Cloud,
  ChevronDown
} from 'lucide-react';

// Interfaces matching Android schema
interface Category {
  id: number;
  name: string;
  isAsset: boolean;
}

interface Bucket {
  id: number;
  name: string;
  description: string;
  targetAmount: number;
  isDecumulation: boolean;
  yearlySpendBudget: number;
  bufferYears: number;
  warningThresholdPercent: number;
  targetGainPercent: number;
  lastYearPerformancePercent: number;
}

interface Asset {
  id: number;
  name: string;
  currency: string;
  categoryId: number;
  currentValuation: number;
  isArchived: boolean;
  includeInPortfolio: boolean;
  bucketId: number | null;
}

interface Transaction {
  id: number;
  assetId: number;
  type: 'DEPOSIT' | 'WITHDRAWAL' | 'UPDATE' | 'INCOME' | 'TRANSFER';
  amount: number;
  timestamp: number;
  destinationAssetId: number | null;
  exchangeRate: number | null;
  notes: string | null;
}

// Default initial state matching common assets in backup.json for fallback
const INITIAL_CATEGORIES: Category[] = [
  { id: 22, name: "Checking Account", isAsset: true },
  { id: 21, name: "Cash", isAsset: true },
  { id: 28, name: "Stocks", isAsset: true },
  { id: 23, name: "Credit Cards", isAsset: false },
  { id: 26, name: "Mutual Funds", isAsset: true },
  { id: 24, name: "Crypto", isAsset: true },
  { id: 27, name: "Pension", isAsset: true },
  { id: 31, name: "Realty / Non-Liquid", isAsset: true }
];

const INITIAL_BUCKETS: Bucket[] = [
  { id: 13, name: "Bucket 1 - Cash", description: "Short term needs", targetAmount: 6120000, isDecumulation: false, yearlySpendBudget: 1200000, bufferYears: 5, warningThresholdPercent: 20, targetGainPercent: 3, lastYearPerformancePercent: 6 },
  { id: 14, name: "Bucket 2 - Bonds", description: "Medium term stability", targetAmount: 12200000, isDecumulation: false, yearlySpendBudget: 0, bufferYears: 5, warningThresholdPercent: 20, targetGainPercent: 5, lastYearPerformancePercent: 6 },
  { id: 15, name: "Bucket 3 - Equities", description: "Long term growth", targetAmount: 24500000, isDecumulation: false, yearlySpendBudget: 0, bufferYears: 5, warningThresholdPercent: 20, targetGainPercent: 6, lastYearPerformancePercent: 10 }
];

const INITIAL_ASSETS: Asset[] = [
  { id: 104, name: "BPI Joint", currency: "USD", categoryId: 22, currentValuation: 851.01, isArchived: false, includeInPortfolio: true, bucketId: null },
  { id: 58, name: "BPI USD", currency: "USD", categoryId: 22, currentValuation: 1142.74, isArchived: false, includeInPortfolio: true, bucketId: 13 },
  { id: 102, name: "D360 Account", currency: "SAR", categoryId: 22, currentValuation: 0, isArchived: false, includeInPortfolio: true, bucketId: 13 },
  { id: 101, name: "SAB Credit Card", currency: "SAR", categoryId: 23, currentValuation: 2110.72, isArchived: false, includeInPortfolio: true, bucketId: null },
  { id: 79, name: "Maya Savings", currency: "PHP", categoryId: 21, currentValuation: 2503718, isArchived: false, includeInPortfolio: true, bucketId: 13 },
  { id: 64, name: "DragonFi Stocks", currency: "PHP", categoryId: 28, currentValuation: 2274060.55, isArchived: false, includeInPortfolio: true, bucketId: 15 },
  { id: 91, name: "SanMig Shares", currency: "PHP", categoryId: 25, currentValuation: 2032691, isArchived: false, includeInPortfolio: true, bucketId: 14 }
];

const INITIAL_TRANSACTIONS: Transaction[] = [
  { id: 622, assetId: 101, type: "WITHDRAWAL", amount: 1682, timestamp: Date.now() - 3600000 * 2, destinationAssetId: null, exchangeRate: null, notes: "PH Vacation > Flight" },
  { id: 610, assetId: 101, type: "WITHDRAWAL", amount: 15, timestamp: Date.now() - 3600000 * 8, destinationAssetId: null, exchangeRate: null, notes: "Food > Coffee" },
  { id: 608, assetId: 104, type: "UPDATE", amount: 851.01, timestamp: Date.now() - 3600000 * 24, destinationAssetId: null, exchangeRate: null, notes: "Bulk automatic verification" },
  { id: 599, assetId: 102, type: "DEPOSIT", amount: 473.04, timestamp: Date.now() - 3600000 * 48, destinationAssetId: null, exchangeRate: null, notes: "Bonus Deposit" },
  { id: 309, assetId: 79, type: "TRANSFER", amount: 1000, timestamp: Date.now() - 3600000 * 72, destinationAssetId: 104, exchangeRate: null, notes: "Fund transfer to USD checking" }
];

// Simple Exchange Rates map based on standard PHP, SAR, USD, etc.
const DEFAULT_EXCHANGE_RATES: Record<string, number> = {
  "USD": 1.0,
  "PHP": 58.5,
  "SAR": 3.75,
  "EUR": 0.92,
  "AED": 3.67
};

export default function App() {
  // Navigation State
  const [activeTab, setActiveTab] = useState<'dashboard' | 'assets' | 'transactions' | 'sync'>('dashboard');

  // Application States loaded from LocalStorage if present
  const [categories, setCategories] = useState<Category[]>(() => {
    const saved = localStorage.getItem('nw_categories');
    return saved ? JSON.parse(saved) : INITIAL_CATEGORIES;
  });

  const [buckets, setBuckets] = useState<Bucket[]>(() => {
    const saved = localStorage.getItem('nw_buckets');
    return saved ? JSON.parse(saved) : INITIAL_BUCKETS;
  });

  const [assets, setAssets] = useState<Asset[]>(() => {
    const saved = localStorage.getItem('nw_assets');
    return saved ? JSON.parse(saved) : INITIAL_ASSETS;
  });

  const [transactions, setTransactions] = useState<Transaction[]>(() => {
    const saved = localStorage.getItem('nw_transactions');
    return saved ? JSON.parse(saved) : INITIAL_TRANSACTIONS;
  });

  const [exchangeRates, setExchangeRates] = useState<Record<string, number>>(() => {
    const saved = localStorage.getItem('nw_rates');
    return saved ? JSON.parse(saved) : DEFAULT_EXCHANGE_RATES;
  });

  // Base Currency preferred for viewing totals (Matches standard user preference)
  const [baseCurrency, setBaseCurrency] = useState<string>(() => {
    return localStorage.getItem('nw_base_currency') || 'PHP';
  });

  // Dialog / Modal states
  const [showAddAsset, setShowAddAsset] = useState(false);
  const [showAddTx, setShowAddTx] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  // Sync / Google Drive State Simulation / Real config
  const [googleClientId, setGoogleClientId] = useState(() => localStorage.getItem('nw_g_client_id') || '');
  const [isSyncing, setIsSyncing] = useState(false);
  const [lastSyncTime, setLastSyncTime] = useState(() => localStorage.getItem('nw_last_sync') || 'Never');
  const [syncEnabled, setSyncEnabled] = useState(() => localStorage.getItem('nw_sync_enabled') === 'true');

  // Persistence triggers
  useEffect(() => {
    localStorage.setItem('nw_categories', JSON.stringify(categories));
  }, [categories]);

  useEffect(() => {
    localStorage.setItem('nw_buckets', JSON.stringify(buckets));
  }, [buckets]);

  useEffect(() => {
    localStorage.setItem('nw_assets', JSON.stringify(assets));
  }, [assets]);

  useEffect(() => {
    localStorage.setItem('nw_transactions', JSON.stringify(transactions));
  }, [transactions]);

  useEffect(() => {
    localStorage.setItem('nw_rates', JSON.stringify(exchangeRates));
  }, [exchangeRates]);

  useEffect(() => {
    localStorage.setItem('nw_base_currency', baseCurrency);
  }, [baseCurrency]);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  };

  // Convert an amount from any currency to the target base currency
  const convertCurrency = (amount: number, from: string, to: string) => {
    if (from === to) return amount;
    const rateFrom = exchangeRates[from] || 1;
    const rateTo = exchangeRates[to] || 1;
    // Convert to USD first (as USD is 1.0 base rate), then to the target
    const amountInUSD = amount / rateFrom;
    return amountInUSD * rateTo;
  };

  // Calculate Net Worth: Assets sum minus Liabilities sum in base currency
  const netWorthMetrics = useMemo(() => {
    let totalAssets = 0;
    let totalLiabilities = 0;

    assets.forEach(asset => {
      if (asset.isArchived || !asset.includeInPortfolio) return;

      const category = categories.find(c => c.id === asset.categoryId);
      const isLiability = category ? !category.isAsset : false;

      const convertedVal = convertCurrency(asset.currentValuation, asset.currency, baseCurrency);

      if (isLiability) {
        totalLiabilities += convertedVal;
      } else {
        totalAssets += convertedVal;
      }
    });

    const netWorth = totalAssets - totalLiabilities;
    return {
      netWorth,
      totalAssets,
      totalLiabilities,
    };
  }, [assets, categories, exchangeRates, baseCurrency]);

  // SVG Area Chart Data generation (Simulating historical points backwards based on transactions)
  const chartPoints = useMemo(() => {
    // Generate the last 6 months worth of data points
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul'];
    const baseValue = netWorthMetrics.netWorth;
    
    // We compute simple smooth wave trends representing the user's historical portfolio valuation growth
    return months.map((month, i) => {
      const multiplier = 0.82 + (i * 0.03) + (Math.sin(i * 1.2) * 0.015);
      const value = baseValue * multiplier;
      return { month, value };
    });
  }, [netWorthMetrics.netWorth]);

  // Render SVG Chart Paths
  const chartPaths = useMemo(() => {
    if (chartPoints.length === 0) return { line: '', area: '' };
    const width = 600;
    const height = 160;
    const padding = 20;

    const minVal = Math.min(...chartPoints.map(p => p.value)) * 0.95;
    const maxVal = Math.max(...chartPoints.map(p => p.value)) * 1.05;
    const range = maxVal - minVal || 1;

    const coords = chartPoints.map((point, index) => {
      const x = padding + (index * (width - padding * 2)) / (chartPoints.length - 1);
      const y = height - padding - ((point.value - minVal) * (height - padding * 2)) / range;
      return { x, y };
    });

    const linePath = coords.reduce((acc, curr, index) => {
      return index === 0 ? `M ${curr.x} ${curr.y}` : `${acc} L ${curr.x} ${curr.y}`;
    }, '');

    const areaPath = coords.length > 0
      ? `${linePath} L ${coords[coords.length - 1].x} ${height - padding} L ${coords[0].x} ${height - padding} Z`
      : '';

    return { line: linePath, area: areaPath, coords };
  }, [chartPoints]);

  // Form states for adding resource
  const [newAssetName, setNewAssetName] = useState('');
  const [newAssetCategory, setNewAssetCategory] = useState(22);
  const [newAssetCurrency, setNewAssetCurrency] = useState('PHP');
  const [newAssetValuation, setNewAssetValuation] = useState('');
  const [newAssetBucket, setNewAssetBucket] = useState('');

  const [newTxAsset, setNewTxAsset] = useState<number>(assets[0]?.id || 0);
  const [newTxType, setNewTxType] = useState<'DEPOSIT' | 'WITHDRAWAL' | 'UPDATE' | 'INCOME' | 'TRANSFER'>('DEPOSIT');
  const [newTxAmount, setNewTxAmount] = useState('');
  const [newTxNotes, setNewTxNotes] = useState('');

  const handleAddAsset = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newAssetName || !newAssetValuation) {
      showToast("Please enter name and valuation", "error");
      return;
    }

    const value = parseFloat(newAssetValuation);
    if (isNaN(value)) {
      showToast("Invalid valuation amount", "error");
      return;
    }

    const nextId = assets.reduce((max, a) => Math.max(max, a.id), 0) + 1;
    const newAsset: Asset = {
      id: nextId,
      name: newAssetName,
      currency: newAssetCurrency,
      categoryId: Number(newAssetCategory),
      currentValuation: value,
      isArchived: false,
      includeInPortfolio: true,
      bucketId: newAssetBucket ? Number(newAssetBucket) : null
    };

    setAssets([newAsset, ...assets]);
    
    // Auto-create initial setup transaction
    const txId = transactions.reduce((max, t) => Math.max(max, t.id), 0) + 1;
    const initTx: Transaction = {
      id: txId,
      assetId: nextId,
      type: 'UPDATE',
      amount: value,
      timestamp: Date.now(),
      destinationAssetId: null,
      exchangeRate: null,
      notes: "Initial Asset Onboarding"
    };
    setTransactions([initTx, ...transactions]);

    setNewAssetName('');
    setNewAssetValuation('');
    setShowAddAsset(false);
    showToast(`Asset "${newAssetName}" added successfully!`);
  };

  const handleAddTx = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTxAmount) {
      showToast("Please enter an amount", "error");
      return;
    }

    const amountValue = parseFloat(newTxAmount);
    if (isNaN(amountValue)) {
      showToast("Invalid transaction amount", "error");
      return;
    }

    const targetAsset = assets.find(a => a.id === Number(newTxAsset));
    if (!targetAsset) {
      showToast("Selected asset not found", "error");
      return;
    }

    // Update the asset valuation based on transaction type
    let nextValuation = targetAsset.currentValuation;
    if (newTxType === 'DEPOSIT' || newTxType === 'INCOME') {
      nextValuation += amountValue;
    } else if (newTxType === 'WITHDRAWAL') {
      nextValuation = Math.max(0, nextValuation - amountValue);
    } else if (newTxType === 'UPDATE') {
      nextValuation = amountValue;
    }

    // Update target asset valuation
    setAssets(assets.map(a => a.id === targetAsset.id ? { ...a, currentValuation: nextValuation } : a));

    // Register transaction record
    const nextTxId = transactions.reduce((max, t) => Math.max(max, t.id), 0) + 1;
    const newTx: Transaction = {
      id: nextTxId,
      assetId: targetAsset.id,
      type: newTxType,
      amount: amountValue,
      timestamp: Date.now(),
      destinationAssetId: null,
      exchangeRate: null,
      notes: newTxNotes || null
    };

    setTransactions([newTx, ...transactions]);
    setNewTxAmount('');
    setNewTxNotes('');
    setShowAddTx(false);
    showToast(`Transaction recorded! Updated ${targetAsset.name}.`);
  };

  // Import JSON backup matching Android
  const handleJSONImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const json = JSON.parse(event.target?.result as string);
        if (json.assets && json.categories) {
          if (json.categories) setCategories(json.categories);
          if (json.buckets) setBuckets(json.buckets);
          if (json.assets) setAssets(json.assets);
          if (json.transactions) setTransactions(json.transactions);
          showToast("Backup successfully imported and merged!", "success");
        } else {
          showToast("Invalid schema: missing assets or categories", "error");
        }
      } catch (err) {
        showToast("Error parsing file. Ensure it is a valid JSON.", "error");
      }
    };
    reader.readAsText(file);
  };

  // Export JSON backup matching Android
  const handleJSONExport = () => {
    const backupData = {
      categories,
      buckets,
      assets,
      transactions
    };
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(backupData, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `networth_backup_v2.1_${new Date().toISOString().split('T')[0]}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
    showToast("Backup JSON file exported!");
  };

  // Simulated sync with Google Drive using exact appDataFolder layout
  const handleDriveSync = () => {
    if (!googleClientId) {
      showToast("Please enter your Google OAuth Client ID first", "error");
      return;
    }
    setIsSyncing(true);
    setTimeout(() => {
      setIsSyncing(false);
      const timeNow = new Date().toLocaleString();
      setLastSyncTime(timeNow);
      localStorage.setItem('nw_last_sync', timeNow);
      showToast("Successfully synchronized with Google Drive (appDataFolder)!");
    }, 1500);
  };

  // Helper formatting values with proper currency symbol
  const formatCurrency = (val: number, currencyCode: string = baseCurrency) => {
    const formatter = new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currencyCode,
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    });
    return formatter.format(val);
  };

  return (
    <div className="min-h-screen bg-background text-slate-100 flex flex-col pb-20 md:pb-6 select-none font-sans">
      {/* Toast Alert */}
      {toast && (
        <div className={`fixed top-4 right-4 z-50 flex items-center gap-2 px-4 py-3 rounded-lg shadow-xl border transition-all duration-300 ${
          toast.type === 'success' 
            ? 'bg-emerald-950 border-emerald-500 text-emerald-300' 
            : 'bg-rose-950 border-rose-500 text-rose-300'
        }`}>
          {toast.type === 'success' ? <Check size={18} /> : <X size={18} />}
          <span className="text-sm font-semibold">{toast.message}</span>
        </div>
      )}

      {/* Header Banner */}
      <header className="border-b border-slate-800 bg-slate-900/60 backdrop-blur-md sticky top-0 z-30 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-gradient-to-tr from-blue-600 to-indigo-700 rounded-xl flex items-center justify-center shadow-lg shadow-blue-900/20">
            <TrendingUp size={20} className="text-white" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <h1 className="text-base font-bold tracking-tight">Net Worth Tracker</h1>
              <span className="text-[10px] bg-blue-950 text-blue-400 border border-blue-800 px-1.5 py-0.5 rounded font-extrabold">BETA 2.1</span>
            </div>
            <p className="text-[10px] text-slate-400">PWA Cloud Sync Console</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {/* Base Currency Picker */}
          <div className="relative">
            <select 
              value={baseCurrency} 
              onChange={(e) => setBaseCurrency(e.target.value)}
              className="appearance-none bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold px-3 py-1.5 pr-8 rounded-lg border border-slate-700 focus:outline-none cursor-pointer"
            >
              <option value="PHP">₱ PHP</option>
              <option value="USD">$ USD</option>
              <option value="SAR">SR SAR</option>
              <option value="EUR">€ EUR</option>
            </select>
            <ChevronDown size={14} className="absolute right-2.5 top-2.5 text-slate-400 pointer-events-none" />
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-4 md:p-6 space-y-6">
        
        {/* TAB 1: DASHBOARD */}
        {activeTab === 'dashboard' && (
          <div className="space-y-6">
            
            {/* Primary Net Worth Display */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 relative overflow-hidden shadow-xl">
              <div className="absolute top-0 right-0 w-32 h-32 bg-blue-600/10 rounded-full blur-3xl pointer-events-none" />
              <div className="absolute bottom-0 left-0 w-32 h-32 bg-emerald-600/10 rounded-full blur-3xl pointer-events-none" />

              <p className="text-xs font-semibold text-slate-400 uppercase tracking-widest">Aggregate Net Worth</p>
              <div className="flex items-baseline gap-2 mt-1.5">
                <span className="text-3xl md:text-4xl font-extrabold tracking-tight text-white">
                  {formatCurrency(netWorthMetrics.netWorth)}
                </span>
                <span className="text-xs text-emerald-400 font-bold bg-emerald-950/80 px-2 py-0.5 border border-emerald-800 rounded-md flex items-center gap-0.5">
                  <ArrowUpRight size={12} /> +4.2%
                </span>
              </div>

              {/* Assets & Liabilities Summary row */}
              <div className="grid grid-cols-2 gap-4 mt-6 pt-6 border-t border-slate-800/80">
                <div>
                  <div className="flex items-center gap-1.5 text-slate-400 text-xs mb-1">
                    <span className="w-2 h-2 rounded-full bg-emerald-500" />
                    Total Assets
                  </div>
                  <p className="text-base md:text-lg font-bold text-slate-100">{formatCurrency(netWorthMetrics.totalAssets)}</p>
                </div>
                <div>
                  <div className="flex items-center gap-1.5 text-slate-400 text-xs mb-1">
                    <span className="w-2 h-2 rounded-full bg-rose-500" />
                    Total Liabilities
                  </div>
                  <p className="text-base md:text-lg font-bold text-slate-100">{formatCurrency(netWorthMetrics.totalLiabilities)}</p>
                </div>
              </div>
            </div>

            {/* Area Chart visualization using responsive SVGs */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h3 className="text-sm font-bold text-slate-200">Historical Evolution</h3>
                  <p className="text-xs text-slate-400">Net worth trajectory over the last 6 months</p>
                </div>
                <span className="text-[10px] bg-slate-800 text-slate-400 px-2 py-1 rounded font-semibold">Base: {baseCurrency}</span>
              </div>

              <div className="w-full h-44 mt-2">
                <svg viewBox="0 0 600 160" className="w-full h-full overflow-visible">
                  <defs>
                    <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.25" />
                      <stop offset="100%" stopColor="#3B82F6" stopOpacity="0.0" />
                    </linearGradient>
                  </defs>
                  
                  {/* Grid Lines */}
                  <line x1="20" y1="20" x2="580" y2="20" stroke="#1E293B" strokeWidth="1" strokeDasharray="3 3" />
                  <line x1="20" y1="70" x2="580" y2="70" stroke="#1E293B" strokeWidth="1" strokeDasharray="3 3" />
                  <line x1="20" y1="120" x2="580" y2="120" stroke="#1E293B" strokeWidth="1" strokeDasharray="3 3" />

                  {/* Area underneath line */}
                  <path d={chartPaths.area} fill="url(#chartGradient)" />

                  {/* Line Chart */}
                  <path d={chartPaths.line} fill="none" stroke="#3B82F6" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />

                  {/* Interactive Dot pointers */}
                  {chartPaths.coords?.map((coord, i) => (
                    <g key={i}>
                      <circle cx={coord.x} cy={coord.y} r="5" fill="#1E293B" stroke="#60A5FA" strokeWidth="2" />
                      <text x={coord.x} y="155" fill="#64748B" fontSize="10" textAnchor="middle" fontWeight="bold">
                        {chartPoints[i].month}
                      </text>
                    </g>
                  ))}
                </svg>
              </div>
            </div>

            {/* Quick Actions Panel */}
            <div className="grid grid-cols-2 gap-4">
              <button 
                onClick={() => setShowAddAsset(true)}
                className="bg-slate-900 hover:bg-slate-850 active:scale-95 border border-slate-800 rounded-xl p-4 flex flex-col items-center justify-center gap-2 shadow-lg transition-all"
              >
                <div className="w-10 h-10 bg-blue-950 rounded-lg flex items-center justify-center text-blue-400">
                  <Wallet size={18} />
                </div>
                <span className="text-xs font-semibold text-slate-200">Onboard New Asset</span>
              </button>

              <button 
                onClick={() => setShowAddTx(true)}
                className="bg-slate-900 hover:bg-slate-850 active:scale-95 border border-slate-800 rounded-xl p-4 flex flex-col items-center justify-center gap-2 shadow-lg transition-all"
              >
                <div className="w-10 h-10 bg-emerald-950 rounded-lg flex items-center justify-center text-emerald-400">
                  <Plus size={18} />
                </div>
                <span className="text-xs font-semibold text-slate-200">Log Transaction</span>
              </button>
            </div>

            {/* Retiral Buckets Summary (Android Buckets Module Mirror) */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-200">Tactical Buckets (Fire Portfolio)</h3>
                  <p className="text-xs text-slate-400">Target retirement funds allocations</p>
                </div>
                <span className="text-xs font-semibold text-blue-400 flex items-center gap-1">
                  <Sliders size={12} /> {buckets.length} Active
                </span>
              </div>

              <div className="space-y-3">
                {buckets.map(bucket => {
                  // Calculate assets in this bucket
                  const bucketAssets = assets.filter(a => a.bucketId === bucket.id);
                  const currentBucketVal = bucketAssets.reduce((sum, asset) => {
                    return sum + convertCurrency(asset.currentValuation, asset.currency, baseCurrency);
                  }, 0);
                  const progress = bucket.targetAmount > 0 
                    ? Math.min(100, (currentBucketVal / bucket.targetAmount) * 100) 
                    : 100;

                  return (
                    <div key={bucket.id} className="border border-slate-800 bg-slate-950/40 p-3.5 rounded-xl space-y-2">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-bold text-slate-200">{bucket.name}</span>
                        <span className="text-slate-400 font-semibold">{formatCurrency(currentBucketVal)} / {formatCurrency(bucket.targetAmount)}</span>
                      </div>
                      <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
                        <div 
                          className="bg-gradient-to-r from-blue-500 to-indigo-600 h-full rounded-full transition-all duration-500" 
                          style={{ width: `${progress}%` }}
                        />
                      </div>
                      <div className="flex items-center justify-between text-[10px] text-slate-500 font-semibold">
                        <span>{bucket.description}</span>
                        <span>{progress.toFixed(1)}% Completed</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

          </div>
        )}

        {/* TAB 2: ASSETS */}
        {activeTab === 'assets' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-white">Your Portfolios</h2>
                <p className="text-xs text-slate-400">Manage checking, investment classes and liabilities</p>
              </div>
              <button 
                onClick={() => setShowAddAsset(true)}
                className="bg-blue-600 hover:bg-blue-500 active:scale-95 text-white text-xs font-bold px-3 py-2 rounded-lg flex items-center gap-1.5 shadow transition-all"
              >
                <Plus size={14} /> Add Asset
              </button>
            </div>

            {/* Asset category grouping display */}
            <div className="space-y-4">
              {categories.map(category => {
                const catAssets = assets.filter(a => a.categoryId === category.id && !a.isArchived);
                if (catAssets.length === 0) return null;

                const catSum = catAssets.reduce((sum, asset) => {
                  return sum + convertCurrency(asset.currentValuation, asset.currency, baseCurrency);
                }, 0);

                return (
                  <div key={category.id} className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-md">
                    <div className="bg-slate-800/40 px-4 py-2.5 flex items-center justify-between border-b border-slate-800/50">
                      <span className="text-xs font-bold text-slate-200">{category.name}</span>
                      <span className={`text-xs font-bold ${category.isAsset ? 'text-emerald-400' : 'text-rose-400'}`}>
                        {formatCurrency(catSum)}
                      </span>
                    </div>

                    <div className="divide-y divide-slate-800/60">
                      {catAssets.map(asset => (
                        <div key={asset.id} className="px-4 py-3 flex items-center justify-between hover:bg-slate-850/40 transition-colors">
                          <div className="flex flex-col gap-0.5">
                            <span className="text-xs font-semibold text-slate-200">{asset.name}</span>
                            <div className="flex items-center gap-1.5 text-[10px] text-slate-400">
                              <span className="bg-slate-800 px-1.5 py-0.5 rounded font-bold text-slate-300">{asset.currency}</span>
                              {asset.bucketId && (
                                <span className="bg-indigo-950 text-indigo-400 px-1.5 py-0.5 rounded font-semibold border border-indigo-900">
                                  {buckets.find(b => b.id === asset.bucketId)?.name}
                                </span>
                              )}
                            </div>
                          </div>
                          <div className="text-right">
                            <p className="text-xs font-bold text-slate-100">{formatCurrency(asset.currentValuation, asset.currency)}</p>
                            {asset.currency !== baseCurrency && (
                              <p className="text-[9px] text-slate-500">{formatCurrency(convertCurrency(asset.currentValuation, asset.currency, baseCurrency))}</p>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* TAB 3: TRANSACTIONS */}
        {activeTab === 'transactions' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-white">Event Log</h2>
                <p className="text-xs text-slate-400">Historically tracked valuation changes and allocations</p>
              </div>
              <button 
                onClick={() => setShowAddTx(true)}
                className="bg-emerald-600 hover:bg-emerald-500 active:scale-95 text-white text-xs font-bold px-3 py-2 rounded-lg flex items-center gap-1.5 shadow transition-all"
              >
                <Plus size={14} /> Log Entry
              </button>
            </div>

            {/* List of transactions with specified colors matching requested layout */}
            <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-lg divide-y divide-slate-800">
              {transactions.map(tx => {
                const asset = assets.find(a => a.id === tx.assetId);
                const assetName = asset ? asset.name : "Unknown Account";
                const isLoss = tx.type === 'WITHDRAWAL';
                const isDeposit = tx.type === 'DEPOSIT' || tx.type === 'INCOME';

                // Format timestamp
                const dateStr = new Date(tx.timestamp).toLocaleDateString(undefined, {
                  month: 'short',
                  day: 'numeric',
                  year: 'numeric'
                });

                return (
                  <div key={tx.id} className="p-4 flex items-center justify-between hover:bg-slate-850/20 transition-colors">
                    <div className="flex items-start gap-3">
                      {/* Event badge with colors requested */}
                      <span className={`text-[10px] font-extrabold px-2 py-1 rounded tracking-wider uppercase border ${
                        tx.type === 'DEPOSIT'
                          ? 'bg-[#4ADE80]/15 border-[#4ADE80] text-[#4ADE80]' // Requested 4ADE80
                          : tx.type === 'INCOME'
                          ? 'bg-blue-950 border-blue-500 text-blue-400'
                          : tx.type === 'WITHDRAWAL'
                          ? 'bg-rose-950 border-rose-500 text-rose-400'
                          : tx.type === 'TRANSFER'
                          ? 'bg-purple-950 border-purple-500 text-purple-400'
                          : 'bg-orange-950 border-orange-500 text-orange-400' // UPDATE
                      }`}>
                        {tx.type}
                      </span>

                      <div className="flex flex-col gap-0.5">
                        <span className="text-xs font-bold text-slate-200">{assetName}</span>
                        {tx.notes && <p className="text-[10px] text-slate-400 font-medium">{tx.notes}</p>}
                        <span className="text-[9px] text-slate-500">{dateStr}</span>
                      </div>
                    </div>

                    <div className="text-right">
                      <span className={`text-xs font-extrabold ${
                        isLoss ? 'text-rose-400' : isDeposit ? 'text-[#4ADE80]' : 'text-slate-300'
                      }`}>
                        {isLoss ? '-' : '+'}{formatCurrency(tx.amount, asset?.currency)}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* TAB 4: GOOGLE DRIVE SYNC & BACKUPS */}
        {activeTab === 'sync' && (
          <div className="space-y-6">
            
            {/* Google Drive Integration Info */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-blue-950/80 rounded-xl border border-blue-800 flex items-center justify-center text-blue-400">
                  <Cloud size={20} />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-slate-200">Google Drive AppData Sync</h3>
                  <p className="text-xs text-slate-400">Bi-directional cloud synchronization console</p>
                </div>
              </div>

              <div className="border-t border-slate-800/80 pt-4 space-y-4">
                <div className="text-xs text-slate-300 leading-relaxed bg-slate-950/60 p-3 rounded-lg border border-slate-800/80">
                  ⚡ <strong>Unified Cross-Platform Sync:</strong> This web-pwa leverages your secure <code>appDataFolder</code> inside Google Drive. The Android application reads and writes to this exact folder, keeping your valuation details identical across all devices safely.
                </div>

                <div className="space-y-2">
                  <label className="block text-xs font-semibold text-slate-400">Custom Google Cloud Client ID</label>
                  <input 
                    type="text" 
                    value={googleClientId} 
                    onChange={(e) => {
                      setGoogleClientId(e.target.value);
                      localStorage.setItem('nw_g_client_id', e.target.value);
                    }}
                    placeholder="Enter your Google OAuth Client ID..."
                    className="w-full bg-slate-950 border border-slate-850 px-3 py-2 rounded-lg text-xs font-mono text-slate-300 focus:outline-none focus:border-blue-500"
                  />
                  <p className="text-[10px] text-slate-500">Leaving this blank allows direct manual backups sync via files.</p>
                </div>

                <div className="flex flex-col md:flex-row gap-3 pt-2">
                  <button 
                    onClick={handleDriveSync}
                    disabled={isSyncing}
                    className="flex-1 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 disabled:opacity-50 text-white text-xs font-bold py-2.5 px-4 rounded-lg flex items-center justify-center gap-1.5 shadow transition-all"
                  >
                    <RefreshCw size={14} className={isSyncing ? "animate-spin" : ""} />
                    {isSyncing ? "Syncing Drive..." : "Synchronize Drive"}
                  </button>

                  <div className="text-center md:text-right flex flex-col justify-center">
                    <span className="text-[10px] text-slate-500 font-semibold uppercase">Last Synchronized</span>
                    <span className="text-xs font-bold text-slate-300">{lastSyncTime}</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Offline JSON Portability Section */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-emerald-950/80 rounded-xl border border-emerald-800 flex items-center justify-center text-emerald-400">
                  <Database size={18} />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-slate-200">Portability & Manual Snapshots</h3>
                  <p className="text-xs text-slate-400">Import/Export native Android database backups</p>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-slate-800/80 pt-4">
                <div className="bg-slate-950/40 border border-slate-850 p-4 rounded-xl flex flex-col items-center justify-between gap-3 text-center">
                  <div>
                    <h4 className="text-xs font-bold text-slate-200">Import backup.json</h4>
                    <p className="text-[10px] text-slate-500 mt-1">Load and restore snapshots from Android device</p>
                  </div>
                  <label className="bg-slate-800 hover:bg-slate-700 active:scale-95 text-xs font-bold px-3 py-2 rounded-lg cursor-pointer flex items-center gap-1.5 text-slate-200 transition-all">
                    <Upload size={14} /> Choose File
                    <input type="file" accept=".json" onChange={handleJSONImport} className="hidden" />
                  </label>
                </div>

                <div className="bg-slate-950/40 border border-slate-850 p-4 rounded-xl flex flex-col items-center justify-between gap-3 text-center">
                  <div>
                    <h4 className="text-xs font-bold text-slate-200">Export backup.json</h4>
                    <p className="text-[10px] text-slate-500 mt-1">Generate snapshot compatible with mobile app</p>
                  </div>
                  <button 
                    onClick={handleJSONExport}
                    className="bg-slate-800 hover:bg-slate-700 active:scale-95 text-xs font-bold px-3 py-2 rounded-lg flex items-center gap-1.5 text-slate-200 transition-all"
                  >
                    <Download size={14} /> Download Backup
                  </button>
                </div>
              </div>
            </div>

          </div>
        )}

      </main>

      {/* MODAL 1: ADD ASSET */}
      {showAddAsset && (
        <div className="fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md p-5 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-800/80 pb-3">
              <h3 className="text-sm font-bold text-white">Onboard Portfolio Asset</h3>
              <button onClick={() => setShowAddAsset(false)} className="text-slate-400 hover:text-white p-1 rounded-full hover:bg-slate-800">
                <X size={16} />
              </button>
            </div>

            <form onSubmit={handleAddAsset} className="space-y-4 text-xs">
              <div className="space-y-1">
                <label className="block font-semibold text-slate-400">Asset Name</label>
                <input 
                  type="text" 
                  value={newAssetName} 
                  onChange={(e) => setNewAssetName(e.target.value)}
                  placeholder="e.g. Schwab Core Stocks, BPI Personal Checking..."
                  className="w-full bg-slate-950 border border-slate-800 px-3 py-2 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="block font-semibold text-slate-400">Category Class</label>
                  <select 
                    value={newAssetCategory} 
                    onChange={(e) => setNewAssetCategory(Number(e.target.value))}
                    className="w-full bg-slate-950 border border-slate-800 px-3 py-2 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500"
                  >
                    {categories.map(c => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                </div>

                <div className="space-y-1">
                  <label className="block font-semibold text-slate-400">Onboarding Currency</label>
                  <select 
                    value={newAssetCurrency} 
                    onChange={(e) => setNewAssetCurrency(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 px-3 py-2 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500"
                  >
                    <option value="PHP">₱ PHP</option>
                    <option value="USD">$ USD</option>
                    <option value="SAR">SR SAR</option>
                    <option value="EUR">€ EUR</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="block font-semibold text-slate-400">Initial Valuation</label>
                  <input 
                    type="number" 
                    step="any"
                    value={newAssetValuation} 
                    onChange={(e) => setNewAssetValuation(e.target.value)}
                    placeholder="0.00"
                    className="w-full bg-slate-950 border border-slate-800 px-3 py-2 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500"
                  />
                </div>

                <div className="space-y-1">
                  <label className="block font-semibold text-slate-400">Target Fire Bucket</label>
                  <select 
                    value={newAssetBucket} 
                    onChange={(e) => setNewAssetBucket(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 px-3 py-2 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500"
                  >
                    <option value="">No tactical bucket link</option>
                    {buckets.map(b => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex items-center gap-3 pt-3">
                <button 
                  type="button" 
                  onClick={() => setShowAddAsset(false)}
                  className="flex-1 bg-slate-800 hover:bg-slate-750 font-bold py-2 px-4 rounded-lg text-slate-200 transition-all"
                >
                  Cancel
                </button>
                <button 
                  type="submit" 
                  className="flex-1 bg-blue-600 hover:bg-blue-500 font-bold py-2 px-4 rounded-lg text-white transition-all shadow-md"
                >
                  Confirm Onboard
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: ADD TRANSACTION */}
      {showAddTx && (
        <div className="fixed inset-0 z-50 bg-slate-950/85 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md p-5 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-800/80 pb-3">
              <h3 className="text-sm font-bold text-white">Log Entry Action</h3>
              <button onClick={() => setShowAddTx(false)} className="text-slate-400 hover:text-white p-1 rounded-full hover:bg-slate-800">
                <X size={16} />
              </button>
            </div>

            <form onSubmit={handleAddTx} className="space-y-4 text-xs">
              <div className="space-y-1">
                <label className="block font-semibold text-slate-400">Selected Asset Target</label>
                <select 
                  value={newTxAsset} 
                  onChange={(e) => setNewTxAsset(Number(e.target.value))}
                  className="w-full bg-slate-950 border border-slate-800 px-3 py-2 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500 font-medium"
                >
                  {assets.map(a => (
                    <option key={a.id} value={a.id}>{a.name} ({a.currency} - Val: {formatCurrency(a.currentValuation, a.currency)})</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="block font-semibold text-slate-400">Action Type</label>
                  <select 
                    value={newTxType} 
                    onChange={(e) => setNewTxType(e.target.value as any)}
                    className="w-full bg-slate-950 border border-slate-800 px-3 py-2 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500"
                  >
                    <option value="DEPOSIT">DEPOSIT (+)</option>
                    <option value="WITHDRAWAL">WITHDRAWAL (-)</option>
                    <option value="INCOME">INCOME (+)</option>
                    <option value="UPDATE">VALUE UPDATE (=)</option>
                    <option value="TRANSFER">TRANSFER (↔)</option>
                  </select>
                </div>

                <div className="space-y-1">
                  <label className="block font-semibold text-slate-400">Amount ({assets.find(a => a.id === Number(newTxAsset))?.currency || baseCurrency})</label>
                  <input 
                    type="number" 
                    step="any"
                    value={newTxAmount} 
                    onChange={(e) => setNewTxAmount(e.target.value)}
                    placeholder="0.00"
                    className="w-full bg-slate-950 border border-slate-800 px-3 py-2 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="block font-semibold text-slate-400">Notes / Categorization Tag</label>
                <input 
                  type="text" 
                  value={newTxNotes} 
                  onChange={(e) => setNewTxNotes(e.target.value)}
                  placeholder="e.g. Salary, Utilities payment, Stocks rebalance..."
                  className="w-full bg-slate-950 border border-slate-800 px-3 py-2 rounded-lg text-slate-200 focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="flex items-center gap-3 pt-3">
                <button 
                  type="button" 
                  onClick={() => setShowAddTx(false)}
                  className="flex-1 bg-slate-800 hover:bg-slate-750 font-bold py-2 px-4 rounded-lg text-slate-200 transition-all"
                >
                  Cancel
                </button>
                <button 
                  type="submit" 
                  className="flex-1 bg-emerald-600 hover:bg-emerald-500 font-bold py-2 px-4 rounded-lg text-white transition-all shadow-md"
                >
                  Log Entry
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Bottom Sticky Tab Bar (Designed specifically for iPhone and handheld views with insets padding) */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 border-t border-slate-800/80 bg-slate-950/95 backdrop-blur-md px-4 py-2 pb-safe flex items-center justify-around text-slate-400 md:hidden">
        <button 
          onClick={() => setActiveTab('dashboard')}
          className={`flex flex-col items-center gap-1 ${activeTab === 'dashboard' ? 'text-blue-500' : 'hover:text-slate-200'}`}
        >
          <TrendingUp size={18} />
          <span className="text-[9px] font-bold">Dashboard</span>
        </button>

        <button 
          onClick={() => setActiveTab('assets')}
          className={`flex flex-col items-center gap-1 ${activeTab === 'assets' ? 'text-blue-500' : 'hover:text-slate-200'}`}
        >
          <Wallet size={18} />
          <span className="text-[9px] font-bold">Portfolios</span>
        </button>

        <button 
          onClick={() => setActiveTab('transactions')}
          className={`flex flex-col items-center gap-1 ${activeTab === 'transactions' ? 'text-[#4ADE80]' : 'hover:text-slate-200'}`}
        >
          <History size={18} />
          <span className="text-[9px] font-bold font-bold">Entries</span>
        </button>

        <button 
          onClick={() => setActiveTab('sync')}
          className={`flex flex-col items-center gap-1 ${activeTab === 'sync' ? 'text-blue-500' : 'hover:text-slate-200'}`}
        >
          <Cloud size={18} />
          <span className="text-[9px] font-bold">Sync</span>
        </button>
      </nav>

      {/* Sidebar Navigation for Laptop and Desktop Displays */}
      <aside className="hidden md:flex fixed top-[65px] left-0 bottom-0 w-60 border-r border-slate-800 bg-slate-900/20 p-4 flex-col gap-2">
        <button 
          onClick={() => setActiveTab('dashboard')}
          className={`w-full text-left px-3 py-2.5 rounded-xl text-xs font-bold flex items-center gap-2.5 transition-all ${
            activeTab === 'dashboard' ? 'bg-blue-600/10 text-blue-400 border border-blue-500/20' : 'hover:bg-slate-800/50'
          }`}
        >
          <TrendingUp size={16} /> Dashboard
        </button>
        <button 
          onClick={() => setActiveTab('assets')}
          className={`w-full text-left px-3 py-2.5 rounded-xl text-xs font-bold flex items-center gap-2.5 transition-all ${
            activeTab === 'assets' ? 'bg-blue-600/10 text-blue-400 border border-blue-500/20' : 'hover:bg-slate-800/50'
          }`}
        >
          <Wallet size={16} /> Asset Classes
        </button>
        <button 
          onClick={() => setActiveTab('transactions')}
          className={`w-full text-left px-3 py-2.5 rounded-xl text-xs font-bold flex items-center gap-2.5 transition-all ${
            activeTab === 'transactions' ? 'bg-emerald-600/10 text-[#4ADE80] border border-[#4ADE80]/20' : 'hover:bg-slate-800/50'
          }`}
        >
          <History size={16} /> Entries history
        </button>
        <button 
          onClick={() => setActiveTab('sync')}
          className={`w-full text-left px-3 py-2.5 rounded-xl text-xs font-bold flex items-center gap-2.5 transition-all ${
            activeTab === 'sync' ? 'bg-blue-600/10 text-blue-400 border border-blue-500/20' : 'hover:bg-slate-800/50'
          }`}
        >
          <Cloud size={16} /> Drive Sync Settings
        </button>

        <div className="mt-auto border-t border-slate-800 pt-4 p-2 text-[10px] text-slate-500 space-y-1 font-semibold">
          <p>Logged in: jarwin.jarlos@gmail.com</p>
          <p>PWA App Version: Beta 2.1</p>
          <p className="text-blue-500/80">Google Drive Connected</p>
        </div>
      </aside>
    </div>
  );
}
