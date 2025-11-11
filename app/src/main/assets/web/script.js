// API Base URL
const API_BASE = '/api';

// DOM Elements
const configForm = document.getElementById('configForm');
const loadBtn = document.getElementById('loadBtn');
const refreshInfoBtn = document.getElementById('refreshInfoBtn');
const notification = document.getElementById('notification');

// Show notification
function showNotification(message, type = 'info') {
    notification.textContent = message;
    notification.className = `notification ${type}`;
    notification.classList.remove('hidden');
    
    // Auto-hide after 5 seconds
    setTimeout(() => {
        notification.classList.add('hidden');
    }, 5000);
}

// Show loading state on button
function setButtonLoading(button, loading) {
    if (loading) {
        button.classList.add('loading');
        button.disabled = true;
    } else {
        button.classList.remove('loading');
        button.disabled = false;
    }
}

// Make API request
async function apiRequest(endpoint, method = 'GET', body = null) {
    try {
        const options = {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            }
        };
        
        if (body) {
            options.body = JSON.stringify(body);
        }
        
        const response = await fetch(API_BASE + endpoint, options);
        const data = await response.json();
        
        if (!response.ok) {
            throw new Error(data.message || 'Request failed');
        }
        
        return data;
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

// Load configuration
async function loadConfiguration() {
    setButtonLoading(loadBtn, true);
    
    try {
        const config = await apiRequest('/config');
        
        // Populate form fields
        document.getElementById('botToken').value = config.botToken || '';
        document.getElementById('chatId').value = config.chatId || '';
        document.getElementById('trustedNumber').value = config.trustedNumber || '';
        
        // Set checkboxes
        document.getElementById('chatCommand').checked = config.chatCommand || false;
        document.getElementById('batteryMonitoring').checked = config.batteryMonitoring || false;
        document.getElementById('chargerStatus').checked = config.chargerStatus || false;
        document.getElementById('fallbackSms').checked = config.fallbackSms || false;
        document.getElementById('verificationCode').checked = config.verificationCode || false;
        document.getElementById('privacyMode').checked = config.privacyMode || false;
        document.getElementById('dohSwitch').checked = config.dohSwitch || false;
        
        showNotification('✅ Configuration loaded successfully', 'success');
    } catch (error) {
        showNotification('❌ Failed to load configuration: ' + error.message, 'error');
    } finally {
        setButtonLoading(loadBtn, false);
    }
}

// Save configuration
async function saveConfiguration(event) {
    event.preventDefault();
    
    const submitBtn = event.target.querySelector('button[type="submit"]');
    setButtonLoading(submitBtn, true);
    
    try {
        const config = {
            botToken: document.getElementById('botToken').value.trim(),
            chatId: document.getElementById('chatId').value.trim(),
            trustedNumber: document.getElementById('trustedNumber').value.trim(),
            chatCommand: document.getElementById('chatCommand').checked,
            batteryMonitoring: document.getElementById('batteryMonitoring').checked,
            chargerStatus: document.getElementById('chargerStatus').checked,
            fallbackSms: document.getElementById('fallbackSms').checked,
            verificationCode: document.getElementById('verificationCode').checked,
            privacyMode: document.getElementById('privacyMode').checked,
            dohSwitch: document.getElementById('dohSwitch').checked
        };
        
        // Validate
        if (!config.botToken || !config.chatId) {
            throw new Error('Bot Token and Chat ID are required');
        }
        
        if (config.fallbackSms && !config.trustedNumber) {
            throw new Error('Trusted phone number is required when fallback SMS is enabled');
        }
        
        const result = await apiRequest('/config', 'POST', config);
        
        showNotification('✅ ' + result.message, 'success');
        
        // Refresh system info after save
        setTimeout(() => loadSystemInfo(), 1000);
    } catch (error) {
        showNotification('❌ Failed to save configuration: ' + error.message, 'error');
    } finally {
        setButtonLoading(submitBtn, false);
    }
}

// Load system information
async function loadSystemInfo() {
    try {
        const info = await apiRequest('/info');
        
        document.getElementById('serviceStatus').textContent = info.serviceRunning ? 
            '✅ Running' : '❌ Stopped';
        document.getElementById('serviceStatus').style.color = info.serviceRunning ? 
            'var(--success-color)' : 'var(--error-color)';
        
        document.getElementById('androidVersion').textContent = info.androidVersion || '-';
        document.getElementById('appVersion').textContent = info.appVersion || '-';
        document.getElementById('batteryLevel').textContent = info.batteryLevel ? 
            info.batteryLevel + '%' : '-';
    } catch (error) {
        console.error('Failed to load system info:', error);
        document.getElementById('serviceStatus').textContent = '❌ Error loading info';
        document.getElementById('serviceStatus').style.color = 'var(--error-color)';
    }
}

// Test connection to Telegram
async function testConnection() {
    try {
        const result = await apiRequest('/test');
        showNotification('✅ ' + result.message, 'success');
        return true;
    } catch (error) {
        showNotification('❌ Connection test failed: ' + error.message, 'error');
        return false;
    }
}

// Event listeners
configForm.addEventListener('submit', saveConfiguration);
loadBtn.addEventListener('click', loadConfiguration);
refreshInfoBtn.addEventListener('click', () => {
    setButtonLoading(refreshInfoBtn, true);
    loadSystemInfo().finally(() => {
        setButtonLoading(refreshInfoBtn, false);
    });
});

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    loadConfiguration();
    loadSystemInfo();
    
    // Auto-refresh system info every 30 seconds
    setInterval(loadSystemInfo, 30000);
});

// Handle battery monitoring toggle
document.getElementById('batteryMonitoring').addEventListener('change', function() {
    const chargerStatus = document.getElementById('chargerStatus');
    if (!this.checked) {
        chargerStatus.checked = false;
    }
});

// Handle fallback SMS toggle
document.getElementById('fallbackSms').addEventListener('change', function() {
    const trustedNumber = document.getElementById('trustedNumber');
    if (this.checked && !trustedNumber.value) {
        showNotification('⚠️ Please enter a trusted phone number for fallback SMS', 'warning');
        trustedNumber.focus();
    }
});

