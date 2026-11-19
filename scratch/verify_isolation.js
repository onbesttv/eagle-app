const fs = require('fs');
const html = fs.readFileSync('painel-admin/index.html', 'utf8');

console.log('1. Checking master-only-item in CSS:');
console.log(html.includes('.master-only-item {'));
console.log(html.includes('display: none !important;'));

console.log('2. Checking master-only-item classes on HTML elements:');
console.log('tabBtnResellers has master-only-item:', html.includes('id="tabBtnResellers" style="display: none;" onclick="switchTab(\'resellers\')" class="tab-btn master-only-item"') || html.includes('class="tab-btn master-only-item" id="tabBtnResellers"'));
console.log('tabBtnLinks has master-only-item:', html.includes('class="tab-btn master-only-item" id="tabBtnLinks"'));
console.log('tabBtnMessages has master-only-item:', html.includes('class="tab-btn master-only-item" id="tabBtnMessages"'));
console.log('tabBtnParental has master-only-item:', html.includes('class="tab-btn master-only-item" id="tabBtnParental"'));
console.log('tabBtnEpg has master-only-item:', html.includes('class="tab-btn master-only-item" id="tabBtnEpg"'));

console.log('3. Checking tabContent containers:');
console.log('tabContentResellers has master-only-item:', html.includes('id="tabContentResellers" class="master-only-item"'));
console.log('tabContentLinks has master-only-item:', html.includes('id="tabContentLinks" class="master-only-item"'));
console.log('tabContentMessages has master-only-item:', html.includes('id="tabContentMessages" class="master-only-item"'));
console.log('tabContentParental has master-only-item:', html.includes('id="tabContentParental" class="master-only-item"'));
console.log('tabContentEpg has master-only-item:', html.includes('id="tabContentEpg" class="master-only-item"'));

console.log('4. Checking isMasterRole strict logic:');
console.log(html.includes('isMasterRole = (currentResellerId === "master") && (data.role === "master");'));

console.log('5. Checking switchTab security check:');
console.log(html.includes('if (!isMasterRole && (tab === "resellers" || tab === "links" || tab === "messages" || tab === "parental" || tab === "epg"))'));
