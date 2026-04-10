// 1. 左侧菜单切换逻辑 (完全保留)
const menuItems = document.querySelectorAll('.menu-item[data-target]');
const panels = document.querySelectorAll('.content-section');

menuItems.forEach(item => {
    item.addEventListener('click', () => {
        // 移除所有激活状态
        menuItems.forEach(btn => btn.classList.remove('active'));
        panels.forEach(panel => panel.classList.remove('active'));

        // 激活当前点击的项和对应的面板
        item.classList.add('active');
        const targetId = item.getAttribute('data-target');
        document.getElementById(targetId).classList.add('active');
    });
});

// 2. 退出与切换账号逻辑 (整合 V1 的提示语与 V2 的 role 清除及跳转逻辑)
function handleLogout() {
    if (confirm("确定要退出当前账号吗？")) {
        // 抹除浏览器的记忆 (超度幽灵缓存)
        localStorage.removeItem('userId');
        localStorage.removeItem('username');
        localStorage.removeItem('role'); // 引入 V2 的 role 清除

        alert("已成功退出登录，即将返回首页");
        window.location.href = "index.html";
    }
}

function handleSwitchAccount() {
    // 使用 V2 补充完整的账号切换清除与跳转逻辑
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    localStorage.removeItem('role');

    alert("请重新登录其他账号");
    window.location.href = "index.html";
}

// 3. 动态生成盈亏日历数据 (完全保留)
const calendarGrid = document.getElementById('calendarGrid');
const daysInMonth = 31;
const startDayOfWeek = 0; // 0代表周日开始

// 填充开头的空白天数
for(let i = 0; i < startDayOfWeek; i++) {
    calendarGrid.innerHTML += `<div class="day-cell empty"></div>`;
}

// 填充实际天数
for(let day = 1; day <= daysInMonth; day++) {
    let currentDayOfWeek = (startDayOfWeek + day - 1) % 7;
    let isWeekend = (currentDayOfWeek === 0 || currentDayOfWeek === 6);

    let html = '';
    if (isWeekend) {
        html = `<div class="day-cell"><div class="date">${day}</div><div class="pnl flat">休市</div></div>`;
    } else {
        // 生成随机数模拟盈亏 -1000 到 +1500
        let pnlValue = Math.floor(Math.random() * 2500) - 1000;
        let pnlClass = pnlValue > 0 ? 'profit' : (pnlValue < 0 ? 'loss' : 'flat');
        let sign = pnlValue > 0 ? '+' : '';
        let pnlText = pnlValue === 0 ? '0.00' : `${sign}$${pnlValue.toFixed(2)}`;

        html = `<div class="day-cell ${pnlClass}">
                    <div class="date">${day}</div>
                    <div class="pnl">${pnlText}</div>
                </div>`;
    }
    calendarGrid.innerHTML += html;
}

// 4. 核心数据请求与渲染
window.addEventListener('DOMContentLoaded', () => {

    const userId = localStorage.getItem('userId');
    const role = localStorage.getItem('role'); // 引入 V2 的权限获取

    if (!userId) {
        alert("请先登录系统！");
        window.location.href = "index.html";
        return;
    }

    // 🌟 引入 V2 的管理员拦截逻辑
    if (role === 'ADMIN') {
        window.location.href = 'admin-user-manage.html';
        return;
    }

    // 🌟 保留 V1 的精髓：前端状态账本（完美解决余额与市值请求的“赛跑”问题）
    const accountState = {
        balance: 0,
        marketValue: 0,
        isBalanceReady: false,
        isMarketReady: false,

        renderTotalEquity: function() {
            // 只有两个数据都到位了，才进行计算渲染
            if (this.isBalanceReady && this.isMarketReady) {
                const totalEquity = this.balance + this.marketValue;
                const totalEquityElem = document.getElementById('totalEquityDisplay');
                if(totalEquityElem) {
                    totalEquityElem.innerText = '$' + totalEquity.toLocaleString('en-US', {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2
                    });
                }
            }
        }
    };

    // ---------------------------------------------------------
    // 🚚 一号车：获取基础信息和余额
    // ---------------------------------------------------------
    fetch(`/api/users/${userId}`)
        .then(response => {
            // 引入 V2 的请求状态校验
            if (!response.ok) {
                throw new Error('网络请求失败');
            }
            return response.json();
        })
        .then(data => {
            document.getElementById('userNameDisplay').innerText = data.username;
            document.getElementById('userAvatar').innerText = data.username.charAt(0).toUpperCase();
            document.getElementById('userIdDisplay').innerText = 'UID: ' + data.id;

            // 存入账本并标记（引入 V2 的 Number 强转，防止字符串拼接错误）
            accountState.balance = Number(data.balance);
            accountState.isBalanceReady = true;

            // 渲染可用资金 UI
            document.getElementById('userBalanceDisplay').innerText =
                '$' + accountState.balance.toLocaleString('en-US', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                });

            // 呼叫账本计算总资产
            accountState.renderTotalEquity();
        })
        .catch(err => {
            console.error('获取用户信息出错:', err);
            // 引入 V2 的 UI 容错提示
            document.getElementById('userNameDisplay').innerText = '服务连接失败';
        });

    // ---------------------------------------------------------
    // 🚚 二号车：获取持仓市值
    // ---------------------------------------------------------
    fetch(`/api/users/userdashboards/${userId}`)
        .then(res => res.json())
        .then(data => {
            // 引入 V2 的 Number 强转
            const totalValue = Number(data.totalMarketValue);

            // 存入账本并标记
            accountState.marketValue = totalValue;
            accountState.isMarketReady = true;

            // 渲染持仓市值 UI
            const portfolioElem = document.getElementById('portfolioValueDisplay');
            if(portfolioElem) {
                portfolioElem.innerText = '$' + totalValue.toLocaleString('en-US', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                });
            }

            // 呼叫账本计算总资产
            accountState.renderTotalEquity();
        })
        .catch(err => console.error("获取持仓失败:", err));

    // ---------------------------------------------------------
    // 🚚 三号车：获取订单流水
    // ---------------------------------------------------------
    fetch(`/api/orders/user/${userId}`)
        .then(res => res.json())
        .then(result => {
            const tbody = document.getElementById('orderTableBody');

            // 💡 兼容处理：V1 预期是带 code 的响应体，V2 预期是直接返回数组
            // 这里做了一个自动兼容，不管你们后端最终返回哪种格式，都能正常渲染
            let orders = [];
            if (result.code === 200 && result.data) {
                orders = result.data; // 兼容 V1 格式 (Result 对象)
            } else if (Array.isArray(result)) {
                orders = result;      // 兼容 V2 格式 (纯数组)
            } else if (result.code && result.code !== 200) {
                tbody.innerHTML = `<tr><td colspan="5" style="padding: 30px; text-align: center; color: #f28b82;">获取失败: ${result.msg}</td></tr>`;
                return;
            }

            // 如果没有数据，显示无记录
            if (!orders || orders.length === 0) {
                tbody.innerHTML = `<tr><td colspan="5" style="padding: 30px; text-align: center; color: #5f6368;">暂无交易记录</td></tr>`;
                return;
            }

            tbody.innerHTML = '';

            orders.forEach(order => {
                const dateObj = new Date(order.tradeTime);
                const timeStr = dateObj.toLocaleString('zh-CN', {
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit'
                });

                const isBuy = order.direction.toUpperCase() === 'BUY';
                const directionHtml = isBuy
                    ? `<span style="color: #81c995; background: rgba(129,201,149,0.1); padding: 2px 6px; border-radius: 4px; font-size: 12px;">买入 BUY</span>`
                    : `<span style="color: #f28b82; background: rgba(242,139,130,0.1); padding: 2px 6px; border-radius: 4px; font-size: 12px;">卖出 SELL</span>`;

                // 引入 V2 的 Number 转换，防止数据类型导致 toLocaleString 报错
                const priceStr = '$' + Number(order.price).toLocaleString('en-US', { minimumFractionDigits: 2 });
                const amountStr = '$' + Number(order.totalAmount).toLocaleString('en-US', { minimumFractionDigits: 2 });

                const tr = document.createElement('tr');
                tr.style.borderBottom = "1px solid #3c4043";
                tr.innerHTML = `
                    <td style="padding: 12px 15px; color: #9aa0a6;">${timeStr}</td>
                    <td style="padding: 12px 15px; font-weight: 500; color: #fff;">${order.ticker}</td>
                    <td style="padding: 12px 15px;">${directionHtml}</td>
                    <td style="padding: 12px 15px;">${priceStr}</td>
                    <td style="padding: 12px 15px;">${amountStr}</td>
                `;
                tbody.appendChild(tr);
            });
        })
        .catch(err => {
            console.error("获取流水失败:", err);
            document.getElementById('orderTableBody').innerHTML = `<tr><td colspan="5" style="padding: 30px; text-align: center; color: #f28b82;">获取数据失败</td></tr>`;
        });
});