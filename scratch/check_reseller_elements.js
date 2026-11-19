const fs = require('fs');
const html = fs.readFileSync('painel-admin/index.html', 'utf8');

console.log('Has resellerTopbar:', html.includes('id="resellerTopbar"'));
console.log('Has resellerNavTabs:', html.includes('id="resellerNavTabs"'));
console.log('Has resellerTabBtnMain:', html.includes('id="resellerTabBtnMain"'));
console.log('Has resellerTabBtnVpn:', html.includes('id="resellerTabBtnVpn"'));
console.log('Has appLayout.is-reseller CSS:', html.includes('.app-layout.is-reseller'));
