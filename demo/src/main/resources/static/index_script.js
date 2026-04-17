// 1. 初始化 ECharts 实例
var myChart = echarts.init(document.getElementById('mainChart'));

// 显示高级的加载动画（适配暗黑主题）
myChart.showLoading({
    text: 'Syncing Market Data...',
    color: '#81c995',
    textColor: '#9aa0a6',
    maskColor: 'rgba(26, 26, 28, 0.8)'
});

// 2. 配置 ECharts 选项 (Candlestick 蜡烛图风格)
var option = {
    tooltip: {
        trigger: 'axis',
        axisPointer: {
            type: 'cross',
            label: { backgroundColor: '#5f6368' }
        },
        backgroundColor: '#202124',
        borderColor: '#3c4043',
        textStyle: { color: '#e8eaed' }
    },
    grid: {
        left: '2%',
        right: '5%',
        bottom: '5%',
        top: '5%',
        containLabel: true
    },
    xAxis: [
        {
            type: 'category',
            boundaryGap: false,
            data: [],
            axisLine: { lineStyle: { color: '#3c4043' } },
            axisLabel: { color: '#9aa0a6' },
            splitLine: { show: false }
        }
    ],
    yAxis: [
        {
            type: 'value',
            scale: true,
            position: 'right',
            axisLabel: { color: '#9aa0a6', formatter: '{value}' },
            axisLine: { show: false },
            splitLine: { lineStyle: { color: '#3c4043', type: 'dashed' } }
        }
    ],
    series: [
        {
            name: 'Price',
            type: 'candlestick',
            data: [],
            itemStyle: {
                color: '#81c995',
                color0: '#f28b82',
                borderColor: '#81c995',
                borderColor0: '#f28b82'
            }
        }
    ]
};

// 定义全局变量
let priceTimer = null;
let currentSymbol = 'TSLA';
let currentPrice = 0;

// 保存登录用户信息
function persistLoginUser(user) {
    localStorage.setItem('userId', user.id);
    localStorage.setItem('username', user.username || '');
    localStorage.setItem('role', user.role || 'USER');
}

// 加载股票数据
function loadStockData(symbol, companyName) {
    currentSymbol = symbol;
    console.log('当前选中的股票已切换为:', currentSymbol);

    myChart.showLoading({
        text: `Syncing ${symbol} Market Data...`,
        color: '#81c995',
        maskColor: 'rgba(26, 26, 28, 0.8)'
    });

    if (companyName) {
        document.querySelector('.stock-title').innerText = `${companyName} (${symbol})`;
    }

    if (priceTimer) {
        clearInterval(priceTimer);
        priceTimer = null;
    }

    fetch(`/api/market/kline/${symbol}`)
        .then(response => response.json())
        .then(data => {
            myChart.hideLoading();

            option.xAxis[0].data = data.dates;
            option.series[0].data = data.values;
            myChart.setOption(option, true);

            if (data.values && data.values.length > 0) {
                let initialLastCandle = data.values[data.values.length - 1];
                currentPrice = parseFloat(initialLastCandle[1]);
            }

            priceTimer = setInterval(() => {
                let lastCandle = data.values[data.values.length - 1];
                let currentOpen = parseFloat(lastCandle[0]);
                let currentClose = parseFloat(lastCandle[1]);

                let fluctuation = Math.random() * 6 - 3;
                let newClose = parseFloat((currentClose + fluctuation).toFixed(2));
                currentPrice = newClose;

                const priceMainDom = document.querySelector('.stock-price-main');
                if (priceMainDom) {
                    let changeAmount = newClose - currentOpen;
                    let percent = ((changeAmount / currentOpen) * 100).toFixed(2);
                    let colorCls = changeAmount >= 0 ? 'green' : 'red';
                    let sign = changeAmount >= 0 ? '+' : '';

                    priceMainDom.innerHTML = `
                        $${newClose.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                        <span class="change ${colorCls}">${sign}${percent}% (${sign}${changeAmount.toFixed(2)}) Today</span>
                    `;
                }
            }, 2000);
        })
        .catch(err => {
            console.error(`加载 ${symbol} 数据失败:`, err);
            myChart.hideLoading();
        });

    fetchAndRenderNews(symbol);
}

// AI 聊天
async function sendMessage() {
    const input = document.getElementById('userInput');
    const messageText = input.value.trim();

    if (!messageText) {
        return;
    }

    appendMessage('user', messageText);
    input.value = '';

    const tempId = 'ai-msg-' + Date.now();
    appendMessage('ai', '思考中...', tempId);

    try {
        const response = await fetch('/api/ai/analyze', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                symbol: currentSymbol,
                message: messageText
            })
        });

        const reply = await response.text();
        document.getElementById(tempId).innerText = reply;
    } catch (error) {
        document.getElementById(tempId).innerText = 'AI 暂时掉线了...';
    }
}

function appendMessage(role, text, id = null) {
    const chatMessages = document.getElementById('chatMessages');
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${role}-message`;
    msgDiv.innerText = text;
    if (id) {
        msgDiv.id = id;
    }
    chatMessages.appendChild(msgDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// 绑定回车键发送
document.getElementById('userInput').addEventListener('keypress', function (e) {
    if (e.key === 'Enter') {
        sendMessage();
    }
});

// 页面加载后默认显示 TSLA
document.addEventListener('DOMContentLoaded', () => {
    loadStockData('TSLA', 'Tesla Inc.');
});

// 点击左侧股票时，加载对应股票数据
document.querySelector('.left-panel').addEventListener('click', event => {
    const stockItem = event.target.closest('.stock-item');
    if (stockItem) {
        const symbol = stockItem.dataset.symbol;
        const companyName = stockItem.dataset.name;
        console.log(`请求 ${symbol} 数据`);
        loadStockData(symbol, companyName);
    }
});

// 监听窗口大小变化自动调整图表
window.addEventListener('resize', function () {
    myChart.resize();
});

// 页面加载时检查是否已登录
window.onload = function () {
    const storedUserId = localStorage.getItem('userId');
    const storedUsername = localStorage.getItem('username');
    const avatar = document.getElementById('navAvatar');

    if (!avatar) {
        return;
    }

    if (storedUserId && storedUsername) {
        avatar.innerText = storedUsername.charAt(0).toUpperCase();
    } else {
        avatar.innerText = '?';
    }
};

// 点击头像时的拦截逻辑
function handleAvatarClick(event) {
    event.preventDefault();

    const storedUserId = localStorage.getItem('userId');
    const role = localStorage.getItem('role');

    if (storedUserId) {
        window.location.href = role === 'ADMIN' ? 'admin-user-manage.html' : 'profile.html';
    } else {
        document.getElementById('loginModal').classList.add('active');
    }
}

// 关闭弹窗
function closeLogginModal() {
    document.getElementById('loginModal').classList.remove('active');
    document.getElementById('loginError').innerText = '';
}

function closeRegisterModal() {
    document.getElementById('RegisterModal').classList.remove('active');
    document.getElementById('RegisterError').innerText = '';
}

// 执行登录
function executeLogin() {
    const uName = document.getElementById('usernameInput').value.trim();
    const pWord = document.getElementById('passwordInput').value.trim();
    const errorDiv = document.getElementById('loginError');

    if (!uName || !pWord) {
        errorDiv.innerText = '用户名和密码不能为空！';
        return;
    }

    errorDiv.innerText = '登录中...';

    const formData = new URLSearchParams();
    formData.append('username', uName);
    formData.append('password', pWord);

    fetch('/api/login', {
        method: 'POST',
        body: formData,
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    })
        .then(async response => {
            if (!response.ok) {
                throw new Error(await response.text() || '账号或密码错误');
            }
            return response.json();
        })
        .then(user => {
            persistLoginUser(user);
            document.getElementById('navAvatar').innerText = user.username.charAt(0).toUpperCase();
            closeLogginModal();

            if ((user.role || 'USER') === 'ADMIN') {
                window.location.href = 'admin-user-manage.html';
                return;
            }

            alert('登录成功！欢迎回来，' + user.username);
        })
        .catch(error => {
            errorDiv.innerText = error.message;
        });
}

function executeRegister() {
    document.getElementById('RegisterModal').classList.add('active');
}

//这是用户在弹窗里填完信息，点击“确认注册”时触发的方法
function submitRegister() {
    // 从输入框拿到账号密码
    const usernameInput = document.getElementById('regUsername').value.trim();
    const passwordInput = document.getElementById('regPassword').value.trim();

    if (!usernameInput || !passwordInput) {
        alert("账号和密码都必须填写！");
        return;
    }

    // 准备要发给后端的 JSON 数据盒子
    const registerData = {
        username: usernameInput,
        password: passwordInput
    };

    // 发起网络请求 (POST)
    fetch('/api/register', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(registerData)
    })
        .then(res => res.json())
        .then(result => {
            // 拆披萨盒，看看状态码
            if (result.code === 200) {
                alert("✅ " + result.msg);

                // 注册成功后，关闭弹窗，清空输入框
                document.getElementById('RegisterModal').classList.remove('active');
                document.getElementById('regUsername').value = '';
                document.getElementById('regPassword').value = '';

                // 可选：把刚才注册的账号直接塞进登录框里，省得用户再敲一遍
                document.getElementById('loginUsername').value = usernameInput;

            } else {
                // 比如账号已存在，弹出红色的警告
                alert("❌ 注册失败: " + result.msg);
            }
        })
        .catch(err => {
            console.error("网络异常:", err);
            alert("服务器开小差了，请稍后再试");
        });
}

// 新闻渲染
async function fetchAndRenderNews(symbol) {
    const newsContainer = document.getElementById('newsContainer');
    newsContainer.innerHTML = '<div class="loading-text">📡 正在获取 ' + symbol + ' 的最新新闻...</div>';

    try {
        console.log(`🚀 准备向 Python(5000) 请求 ${symbol} 的新闻...`);
        const response = await fetch(`http://localhost:5000/api/news/${symbol}`);
        console.log('📥 收到 Python 响应状态码:', response.status);

        const result = await response.json();
        console.log('📦 解析出来的 Python 数据长这样:', result);

        if (result.status === 'success' && Array.isArray(result.data) && result.data.length > 0) {
            newsContainer.innerHTML = '';

            result.data.forEach(news => {
                const safeLink = news.link ? news.link : '#';
                const safeTitle = news.title ? news.title : '无标题';
                const safeSummary = news.summary ? news.summary : '';
                const bulletText = safeSummary.length > 40 ? safeSummary.substring(0, 60) + '...' : safeSummary;

                const newsHTML = `
                    <a href="${safeLink}" target="_blank" class="news-item">
                        <div class="news-header">
                            <div class="company-logo">${symbol}</div>
                            <div class="news-title-wrapper">
                                <div class="news-title">${safeTitle}</div>
                            </div>
                        </div>
                
                        <div class="news-summary">${safeSummary}</div>
                
                        <div class="news-bullet">
                            - 核心看点: ${bulletText}
                        </div>
                
                        <div class="news-footer">
                            <div class="news-source">Yahoo Finance</div>
                            <div class="news-stats">
                                <span>T+1 +2.22%</span>
                                <span>T+5 +1.60%</span>
                            </div>
                            <div class="news-action">阅读原文</div>
                        </div>
                    </a>
                `;
                newsContainer.innerHTML += newsHTML;
            });
        } else {
            console.warn('⚠️ 数据格式不对，或者 data 是空的！');
            newsContainer.innerHTML = '<div class="loading-text" style="color:#f28b82;">暂无该股票的情报。</div>';
        }
    } catch (error) {
        console.error('❌ 新闻拉取彻底失败, 错误详情:', error);
        newsContainer.innerHTML = '<div class="loading-text" style="color:#f28b82;">❌ 无法连接到情报网络(请按 F12 检查跨域)</div>';
    }
}

// 切换买卖方向
let currentTradeAction = 'buy';

function switchTradeType(type) {
    currentTradeAction = type;

    const buyTab = document.querySelector('.trade-tab.buy');
    const sellTab = document.querySelector('.trade-tab.sell');
    const executeBtn = document.getElementById('executeBtn');

    if (type === 'buy') {
        buyTab.classList.add('active');
        sellTab.classList.remove('active');
        executeBtn.className = 'execute-btn buy-btn';
        executeBtn.innerText = '提交买单 (Buy)';
    } else {
        sellTab.classList.add('active');
        buyTab.classList.remove('active');
        executeBtn.className = 'execute-btn sell-btn';
        executeBtn.innerText = '提交卖单 (Sell)';
    }
}

// 执行交易
async function executeTrade() {
    if (!localStorage.getItem('userId')) {
        alert('请先登录系统');
        window.location.href = 'index.html';
        return;
    }

    const quantity = Number(document.getElementById('tradeShares').value);

    const orderData = {
        userId: localStorage.getItem('userId'),
        ticker: currentSymbol,
        direction: currentTradeAction.toUpperCase(),
        price: currentPrice,
        quantity: quantity,
        fee: 5.00,
        totalAmount: (currentPrice * quantity + 5.00).toFixed(2),
        tradeTime: new Date().toISOString()
    };

    console.log('🚀 正在打包订单发送至后端...', orderData);

    const response = await fetch('/api/trade/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderData)
    });

    const result = await response.json();
    if (result.code === 200) {
        alert('订单成交！');
    }
}
