const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, '..', 'painel-admin', 'index.html');
const content = fs.readFileSync(filePath, 'utf8');

// Extract script
const scriptMatch = content.match(/<script>([\s\S]*?)<\/script>/);
if (!scriptMatch) {
    console.error('ERROR: <script> not found');
    process.exit(1);
}

try {
    new Function(scriptMatch[1]);
    console.log('JavaScript syntax is 100% VALID!');
} catch (e) {
    console.error('JavaScript SYNTAX ERROR:', e);
    process.exit(1);
}

// Check key IDs
const ids = [
    'dashboardScreen',
    'appSidebar',
    'sidebarBackdrop',
    'brandBadge',
    'panelTitle',
    'masterTabs',
    'tabBtnMain',
    'tabBtnVpn',
    'tabBtnResellers',
    'tabBtnLinks',
    'tabBtnMessages',
    'tabBtnParental',
    'tabBtnEpg',
    'sidebarHeadResellers',
    'sidebarHeadTools',
    'roleBadge',
    'lastSyncLabel',
    'pageActiveTitle',
    'headerSubTitle',
    'statTotal',
    'statOnline',
    'tabContentMain',
    'tabContentVpn',
    'tabContentResellers',
    'tabContentLinks',
    'tabContentMessages',
    'tabContentParental',
    'tabContentEpg'
];

let missing = [];
for (const id of ids) {
    if (!content.includes(`id="${id}"`)) {
        missing.push(id);
    }
}

if (missing.length > 0) {
    console.error('MISSING IDs:', missing);
    process.exit(1);
} else {
    console.log('All 28 critical IDs present and verified!');
}
