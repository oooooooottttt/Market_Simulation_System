const feedbackBar = document.getElementById('feedbackBar');
const userTableBody = document.getElementById('userTableBody');
const statusModal = document.getElementById('statusModal');
const reasonInput = document.getElementById('reasonInput');

let currentAction = null;

window.addEventListener('DOMContentLoaded', () => {
    const role = localStorage.getItem('role');
    const username = localStorage.getItem('username');

    if (role !== 'ADMIN') {
        alert('当前账号不是管理员，无法进入用户管理页面');
        window.location.href = 'index.html';
        return;
    }

    document.getElementById('adminWelcome').innerText = username
        ? `管理员：${username}`
        : '管理员控制台';

    loadUserList();
});

async function loadUserList() {
    userTableBody.innerHTML = '<tr><td colspan="6" class="empty-row">正在加载用户列表...</td></tr>';
    clearFeedback();

    try {
        const response = await fetch('/api/admin/users');
        if (!response.ok) {
            throw new Error(await response.text() || '用户列表加载失败');
        }

        const users = await response.json();
        renderStats(users);
        renderUserRows(users);
    } catch (error) {
        userTableBody.innerHTML = `<tr><td colspan="6" class="empty-row">${error.message}</td></tr>`;
        showFeedback(error.message, 'error');
    }
}

function renderStats(users) {
    const total = users.length;
    const normal = users.filter(user => user.status === 1).length;
    const frozen = users.filter(user => user.status === 0).length;

    document.getElementById('totalUsers').innerText = total;
    document.getElementById('normalUsers').innerText = normal;
    document.getElementById('frozenUsers').innerText = frozen;
}

function renderUserRows(users) {
    if (!users || users.length === 0) {
        userTableBody.innerHTML = '<tr><td colspan="6" class="empty-row">暂无用户数据</td></tr>';
        return;
    }

    userTableBody.innerHTML = '';

    users.forEach(user => {
        const tr = document.createElement('tr');
        const createdAt = formatDateTime(user.createdAt);
        const statusHtml = user.status === 1
            ? '<span class="status-pill normal">正常</span>'
            : '<span class="status-pill frozen">已冻结</span>';
        const roleHtml = `<span class="role-pill">${user.role || 'USER'}</span>`;

        let actionHtml = '<span style="color:#9ba3ad;">管理员账号</span>';
        if (user.role !== 'ADMIN') {
            if (user.status === 1) {
                actionHtml = `<button class="action-btn freeze" onclick="openStatusModal(${user.id}, 0, '${escapeText(user.username)}')">冻结</button>`;
            } else {
                actionHtml = `<button class="action-btn unfreeze" onclick="openStatusModal(${user.id}, 1, '${escapeText(user.username)}')">解冻</button>`;
            }
        }

        tr.innerHTML = `
            <td>${user.id}</td>
            <td>${user.username || '-'}</td>
            <td>${createdAt}</td>
            <td>${roleHtml}</td>
            <td>${statusHtml}</td>
            <td><div class="action-group">${actionHtml}</div></td>
        `;
        userTableBody.appendChild(tr);
    });
}

function openStatusModal(userId, status, username) {
    currentAction = { userId, status, username };
    document.getElementById('modalTitle').innerText = status === 0 ? '冻结用户' : '解冻用户';
    document.getElementById('modalDesc').innerText = `请填写对用户 ${username} 的操作原因，提交后会写入日志。`;
    reasonInput.value = '';
    statusModal.classList.add('active');
}

function closeStatusModal() {
    statusModal.classList.remove('active');
    currentAction = null;
    reasonInput.value = '';
}

async function submitStatusChange() {
    if (!currentAction) {
        return;
    }

    const reason = reasonInput.value.trim();
    if (!reason) {
        showFeedback('请输入操作原因', 'error');
        return;
    }

    const adminUserId = Number(localStorage.getItem('userId'));
    if (!adminUserId) {
        showFeedback('管理员登录信息已失效，请重新登录', 'error');
        return;
    }

    try {
        const response = await fetch('/api/admin/users/status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                adminUserId,
                userId: currentAction.userId,
                status: currentAction.status,
                reason: reason
            })
        });

        if (!response.ok) {
            throw new Error(await response.text() || '状态更新失败');
        }

        const successMessage = currentAction.status === 0 ? '冻结成功' : '解冻成功';
        closeStatusModal();
        await loadUserList();
        showFeedback(successMessage, 'success');
    } catch (error) {
        showFeedback(error.message, 'error');
    }
}

function showFeedback(message, type) {
    feedbackBar.className = `feedback-bar ${type}`;
    feedbackBar.innerText = message;
}

function clearFeedback() {
    feedbackBar.className = 'feedback-bar';
    feedbackBar.innerText = '';
}

function formatDateTime(value) {
    if (!value) {
        return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function escapeText(text) {
    return String(text || '').replace(/'/g, "\\'");
}

function logoutAdmin() {
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
    window.location.href = 'index.html';
}

function goBackMarket() {
    window.location.href = 'index.html';
}

statusModal.addEventListener('click', event => {
    if (event.target === statusModal) {
        closeStatusModal();
    }
});