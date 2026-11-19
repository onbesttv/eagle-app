const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, '..', 'painel-admin', 'index.html');
let raw = fs.readFileSync(filePath, 'utf8');
const isCrlf = raw.includes('\r\n');
let content = raw.replace(/\r\n/g, '\n');

console.log('Original length (normalized):', content.length);

function replaceExact(name, target, replacement) {
    const t = target.replace(/\r\n/g, '\n');
    const r = replacement.replace(/\r\n/g, '\n');
    if (!content.includes(t)) {
        console.error(`ERROR: ${name} not found!`);
        process.exit(1);
    }
    content = content.replace(t, r);
    console.log(`Success: ${name}`);
}

// 1. FIX CSS SPECIFICITY AND ADD MASTER-ONLY-ITEM RULES
const cssSidebarNavTarget = `        .sidebar-nav .tab-btn {
            width: 100%;
            display: flex !important;
            align-items: center;
            gap: 12px;
            padding: 11px 14px;
            border-radius: 10px;
            font-size: 13px;
            font-weight: 600;
            text-align: left;
            background: transparent;
            border: 1px solid transparent;
            color: #94a3b8;
            cursor: pointer;
            transition: all 0.15s ease;
        }`;

const cssSidebarNavReplacement = `        .sidebar-nav .tab-btn {
            width: 100%;
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 11px 14px;
            border-radius: 10px;
            font-size: 13px;
            font-weight: 600;
            text-align: left;
            background: transparent;
            border: 1px solid transparent;
            color: #94a3b8;
            cursor: pointer;
            transition: all 0.15s ease;
        }

        /* REGRAS ESTRITAS DE ISOLAMENTO MASTER */
        .master-only-item {
            display: none !important;
        }
        body.is-master .master-only-item.tab-btn {
            display: flex !important;
        }
        body.is-master .master-only-item.sidebar-section-title {
            display: block !important;
        }
        body.is-master #masterAnalyticsSection.master-only-item {
            display: block !important;
        }
        body:not(.is-master) .master-only-item,
        body.is-reseller .master-only-item,
        body.is-reseller .sidebar,
        body.is-reseller .sidebar-backdrop,
        body.is-reseller .content-header,
        body:not(.is-master) .sidebar,
        body:not(.is-master) .sidebar-backdrop,
        body:not(.is-master) .content-header {
            display: none !important;
        }`;

replaceExact('CSS Sidebar Nav & Master-only items', cssSidebarNavTarget, cssSidebarNavReplacement);

// 2. HTML: ADD master-only-item CLASS TO ALL MASTER ITEMS IN SIDEBAR
const htmlSidebarTarget = `                <div class="sidebar-section-title" id="sidebarHeadResellers" style="display: none;">REVENDAS</div>
                <button type="button" class="tab-btn" id="tabBtnResellers" style="display: none;" onclick="switchTab('resellers')">
                    <span class="tab-icon">🤝</span>
                    <span class="tab-text">Revendedores Apps</span>
                </button>
                <button type="button" class="tab-btn" id="tabBtnLinks" style="display: none;" onclick="switchTab('links')">
                    <span class="tab-icon">🔗</span>
                    <span class="tab-text">Links Revendas</span>
                </button>

                <div class="sidebar-section-title" id="sidebarHeadTools" style="display: none;">FERRAMENTAS TV</div>
                <button type="button" class="tab-btn" id="tabBtnMessages" style="display: none;" onclick="switchTab('messages')">
                    <span class="tab-icon">💬</span>
                    <span class="tab-text">Mensagens TV</span>
                </button>
                <button type="button" class="tab-btn" id="tabBtnParental" style="display: none;" onclick="switchTab('parental')">
                    <span class="tab-icon">🔒</span>
                    <span class="tab-text">PIN Parental</span>
                </button>
                <button type="button" class="tab-btn" id="tabBtnEpg" style="display: none;" onclick="switchTab('epg')">
                    <span class="tab-icon">📡</span>
                    <span class="tab-text">Validador EPG</span>
                </button>`;

const htmlSidebarReplacement = `                <div class="sidebar-section-title master-only-item" id="sidebarHeadResellers" style="display: none;">REVENDAS</div>
                <button type="button" class="tab-btn master-only-item" id="tabBtnResellers" style="display: none;" onclick="switchTab('resellers')">
                    <span class="tab-icon">🤝</span>
                    <span class="tab-text">Revendedores Apps</span>
                </button>
                <button type="button" class="tab-btn master-only-item" id="tabBtnLinks" style="display: none;" onclick="switchTab('links')">
                    <span class="tab-icon">🔗</span>
                    <span class="tab-text">Links Revendas</span>
                </button>

                <div class="sidebar-section-title master-only-item" id="sidebarHeadTools" style="display: none;">FERRAMENTAS TV</div>
                <button type="button" class="tab-btn master-only-item" id="tabBtnMessages" style="display: none;" onclick="switchTab('messages')">
                    <span class="tab-icon">💬</span>
                    <span class="tab-text">Mensagens TV</span>
                </button>
                <button type="button" class="tab-btn master-only-item" id="tabBtnParental" style="display: none;" onclick="switchTab('parental')">
                    <span class="tab-icon">🔒</span>
                    <span class="tab-text">PIN Parental</span>
                </button>
                <button type="button" class="tab-btn master-only-item" id="tabBtnEpg" style="display: none;" onclick="switchTab('epg')">
                    <span class="tab-icon">📡</span>
                    <span class="tab-text">Validador EPG</span>
                </button>`;

replaceExact('HTML Sidebar master-only-item classes', htmlSidebarTarget, htmlSidebarReplacement);

// 3. HTML: ADD master-only-item TO ANALYTICS AND MASTER TAB CONTENTS
const htmlAnalyticsTarget = `        <div id="masterAnalyticsSection" style="grid-column: 1 / -1; display: none; margin-bottom: 24px;">`;
const htmlAnalyticsReplacement = `        <div id="masterAnalyticsSection" class="master-only-item" style="grid-column: 1 / -1; display: none; margin-bottom: 24px;">`;
replaceExact('masterAnalyticsSection class', htmlAnalyticsTarget, htmlAnalyticsReplacement);

const htmlTabResTarget = `    <div id="tabContentResellers" style="display: none;">`;
const htmlTabResReplacement = `    <div id="tabContentResellers" class="master-only-item" style="display: none;">`;
replaceExact('tabContentResellers class', htmlTabResTarget, htmlTabResReplacement);

const htmlTabLinksTarget = `    <div id="tabContentLinks" style="display: none;">`;
const htmlTabLinksReplacement = `    <div id="tabContentLinks" class="master-only-item" style="display: none;">`;
replaceExact('tabContentLinks class', htmlTabLinksTarget, htmlTabLinksReplacement);

const htmlTabMsgTarget = `    <div id="tabContentMessages" style="display: none;">`;
const htmlTabMsgReplacement = `    <div id="tabContentMessages" class="master-only-item" style="display: none;">`;
replaceExact('tabContentMessages class', htmlTabMsgTarget, htmlTabMsgReplacement);

const htmlTabParTarget = `    <div id="tabContentParental" style="display: none;">`;
const htmlTabParReplacement = `    <div id="tabContentParental" class="master-only-item" style="display: none;">`;
replaceExact('tabContentParental class', htmlTabParTarget, htmlTabParReplacement);

const htmlTabEpgTarget = `    <div id="tabContentEpg" style="display: none;">`;
const htmlTabEpgReplacement = `    <div id="tabContentEpg" class="master-only-item" style="display: none;">`;
replaceExact('tabContentEpg class', htmlTabEpgTarget, htmlTabEpgReplacement);

// 4. JAVASCRIPT: UPDATE isMasterRole LOGIC AND STRICT ENFORCEMENT
const jsLoginLogicTarget = `            isMasterRole = data.role === "master";
            const brandTitle = \`\${data.brand_name || "Painel"} — Painel de Controlo\`;
            document.title = brandTitle;
            const isJoker = (currentResellerId === "joker" || currentResellerId === "tv_joker");
            const hasVpnInApp = currentResellerId !== "pereira_tv";
            const hasBest2InApp = !isJoker && currentResellerId !== "pereira_tv" && currentResellerId !== "godinho_tv" && currentResellerId !== "dream" && currentResellerId !== "suporte24";

            const appLayout = document.getElementById("appLayout");
            const appSidebar = document.getElementById("appSidebar");
            const contentHeader = document.getElementById("contentHeader");
            const resellerTopbar = document.getElementById("resellerTopbar");
            const resellerNavTabs = document.getElementById("resellerNavTabs");

            if (isMasterRole) {
                // APENAS NO PAINEL MASTER: BARRA LATERAL (SIDEBAR) E FERRAMENTAS AVANÇADAS
                if (appLayout) {
                    appLayout.classList.add("is-master");
                    appLayout.classList.remove("is-reseller");
                }
                if (appSidebar) appSidebar.style.display = "flex";
                if (contentHeader) contentHeader.style.display = "flex";
                if (resellerTopbar) resellerTopbar.style.display = "none";
                if (resellerNavTabs) resellerNavTabs.style.display = "none";

                document.getElementById("panelTitle").textContent = brandTitle;
                document.getElementById("brandBadge").textContent = (data.brand_name || "E").trim().charAt(0).toUpperCase();
                document.getElementById("roleBadge").textContent = "👑 SUPER ADMIN (MASTER)";
                document.getElementById("masterTabs").style.display = "flex";
                document.getElementById("tabBtnVpn").style.display = hasVpnInApp ? "flex" : "none";
            } else {
                // NOS OUTROS TODOS (REVENDEDORES): PAINEL TRADICIONAL CLÁSSICO SEM SIDEBAR
                if (appLayout) {
                    appLayout.classList.add("is-reseller");
                    appLayout.classList.remove("is-master");
                }
                if (appSidebar) appSidebar.style.display = "none";
                if (contentHeader) contentHeader.style.display = "none";
                const sBackdrop = document.getElementById("sidebarBackdrop");
                if (sBackdrop) sBackdrop.style.display = "none";
                if (resellerTopbar) resellerTopbar.style.display = "flex";
                if (resellerNavTabs) resellerNavTabs.style.display = "flex";

                const rBadge = document.getElementById("resellerBrandBadge");
                if (rBadge) rBadge.textContent = (data.brand_name || "P").trim().charAt(0).toUpperCase();
                const rTitle = document.getElementById("resellerPanelTitle");
                if (rTitle) rTitle.textContent = brandTitle;
                const rRole = document.getElementById("resellerRoleBadge");
                if (rRole) rRole.textContent = \`🤝 REVENDA: \${currentResellerId.toUpperCase()}\`;
                const rTabVpn = document.getElementById("resellerTabBtnVpn");
                if (rTabVpn) rTabVpn.style.display = hasVpnInApp ? "inline-block" : "none";
            }`;

const jsLoginLogicReplacement = `            // STRICT MASTER CHECK: Apenas a conta master tem acesso
            isMasterRole = (currentResellerId === "master") && (data.role === "master");
            const brandTitle = \`\${data.brand_name || "Painel"} — Painel de Controlo\`;
            document.title = brandTitle;
            const isJoker = (currentResellerId === "joker" || currentResellerId === "tv_joker");
            const hasVpnInApp = currentResellerId !== "pereira_tv";
            const hasBest2InApp = !isJoker && currentResellerId !== "pereira_tv" && currentResellerId !== "godinho_tv" && currentResellerId !== "dream" && currentResellerId !== "suporte24";

            const appLayout = document.getElementById("appLayout");
            const appSidebar = document.getElementById("appSidebar");
            const contentHeader = document.getElementById("contentHeader");
            const resellerTopbar = document.getElementById("resellerTopbar");
            const resellerNavTabs = document.getElementById("resellerNavTabs");

            if (isMasterRole) {
                // APENAS NO TEU PAINEL MASTER: BARRA LATERAL E FERRAMENTAS AVANÇADAS
                document.body.classList.add("is-master");
                document.body.classList.remove("is-reseller");
                if (appLayout) {
                    appLayout.classList.add("is-master");
                    appLayout.classList.remove("is-reseller");
                }
                if (appSidebar) appSidebar.style.setProperty("display", "flex", "important");
                if (contentHeader) contentHeader.style.setProperty("display", "flex", "important");
                if (resellerTopbar) resellerTopbar.style.setProperty("display", "none", "important");
                if (resellerNavTabs) resellerNavTabs.style.setProperty("display", "none", "important");

                document.getElementById("panelTitle").textContent = brandTitle;
                document.getElementById("brandBadge").textContent = (data.brand_name || "E").trim().charAt(0).toUpperCase();
                document.getElementById("roleBadge").textContent = "👑 SUPER ADMIN (MASTER)";
                document.getElementById("masterTabs").style.display = "flex";
                document.getElementById("tabBtnVpn").style.display = hasVpnInApp ? "flex" : "none";

                document.querySelectorAll(".master-only-item").forEach(el => {
                    if (el.tagName === "BUTTON") {
                        el.style.setProperty("display", "flex", "important");
                    } else {
                        el.style.setProperty("display", "block", "important");
                    }
                });
            } else {
                // NOS OUTROS TODOS (REVENDEDORES): TOTALMENTE LIVRE DE FERRAMENTAS E ABAS MASTER
                document.body.classList.add("is-reseller");
                document.body.classList.remove("is-master");
                if (appLayout) {
                    appLayout.classList.add("is-reseller");
                    appLayout.classList.remove("is-master");
                }
                if (appSidebar) appSidebar.style.setProperty("display", "none", "important");
                if (contentHeader) contentHeader.style.setProperty("display", "none", "important");
                const sBackdrop = document.getElementById("sidebarBackdrop");
                if (sBackdrop) sBackdrop.style.setProperty("display", "none", "important");
                if (resellerTopbar) resellerTopbar.style.setProperty("display", "flex", "important");
                if (resellerNavTabs) resellerNavTabs.style.setProperty("display", "flex", "important");

                // Ocultar totalmente todos os itens e ferramentas exclusivas de Master
                document.querySelectorAll(".master-only-item").forEach(el => {
                    el.style.setProperty("display", "none", "important");
                });

                const rBadge = document.getElementById("resellerBrandBadge");
                if (rBadge) rBadge.textContent = (data.brand_name || "P").trim().charAt(0).toUpperCase();
                const rTitle = document.getElementById("resellerPanelTitle");
                if (rTitle) rTitle.textContent = brandTitle;
                const rRole = document.getElementById("resellerRoleBadge");
                if (rRole) rRole.textContent = \`🤝 REVENDA: \${currentResellerId.toUpperCase()}\`;
                const rTabVpn = document.getElementById("resellerTabBtnVpn");
                if (rTabVpn) rTabVpn.style.display = hasVpnInApp ? "inline-block" : "none";

                // Forçar revendedor a ficar exclusivamente na aba principal
                switchTab("main");
            }`;

replaceExact('JavaScript Login Logic', jsLoginLogicTarget, jsLoginLogicReplacement);

// 5. JAVASCRIPT: switchTab SECURITY CHECK
const jsSwitchTabTarget = `    function switchTab(tab) {
        document.getElementById("tabBtnMain").classList.toggle("active", tab === "main");`;

const jsSwitchTabReplacement = `    function switchTab(tab) {
        // Bloqueio rigoroso: se não for Master, só pode aceder a 'main' ou 'vpn'
        if (!isMasterRole && (tab === "resellers" || tab === "links" || tab === "messages" || tab === "parental" || tab === "epg")) {
            tab = "main";
        }
        document.getElementById("tabBtnMain").classList.toggle("active", tab === "main");`;

replaceExact('JavaScript switchTab security check', jsSwitchTabTarget, jsSwitchTabReplacement);

// 6. JAVASCRIPT: PROTECT renderResellers AND renderResellerLinks
const jsResellersTarget = `    function renderResellers() {
        const tbody = document.getElementById("resellersTableBody");
        if (!multiResellerEnabled) {`;

const jsResellersReplacement = `    function renderResellers() {
        const tbody = document.getElementById("resellersTableBody");
        if (!isMasterRole) {
            if (tbody) tbody.innerHTML = "";
            return;
        }
        if (!multiResellerEnabled) {`;

replaceExact('JavaScript renderResellers protection', jsResellersTarget, jsResellersReplacement);

const jsLinksTarget = `    function renderResellerLinks() {
        const grid = document.getElementById("resellerLinksGrid");
        if (!grid) return;

        let apps = [...KNOWN_RESELLER_APPS];`;

const jsLinksReplacement = `    function renderResellerLinks() {
        const grid = document.getElementById("resellerLinksGrid");
        if (!grid) return;
        if (!isMasterRole) {
            grid.innerHTML = "";
            return;
        }

        let apps = [...KNOWN_RESELLER_APPS];`;

replaceExact('JavaScript renderResellerLinks protection', jsLinksTarget, jsLinksReplacement);

// 7. JAVASCRIPT: CLEANUP IN logout()
const jsLogoutTarget = `    function logout() {
        sessionStorage.removeItem("eagle_reseller_id");
        sessionStorage.removeItem("eagle_admin_pass");
        adminPassword = "";
        document.getElementById("dashboardScreen").style.display = "none";
        document.getElementById("loginScreen").style.display = "flex";
        document.getElementById("loginPassword").value = "";
    }`;

const jsLogoutReplacement = `    function logout() {
        sessionStorage.removeItem("eagle_reseller_id");
        sessionStorage.removeItem("eagle_admin_pass");
        adminPassword = "";
        currentResellerId = "";
        isMasterRole = false;
        document.body.classList.remove("is-master", "is-reseller");
        document.querySelectorAll(".master-only-item").forEach(el => {
            el.style.setProperty("display", "none", "important");
        });
        document.getElementById("dashboardScreen").style.display = "none";
        document.getElementById("loginScreen").style.display = "flex";
        document.getElementById("loginPassword").value = "";
        document.getElementById("loginResellerId").value = "";
    }`;

replaceExact('JavaScript logout cleanup', jsLogoutTarget, jsLogoutReplacement);

// Restore original line ending style
const output = isCrlf ? content.replace(/\n/g, '\r\n') : content;
fs.writeFileSync(filePath, output, 'utf8');
console.log('Successfully written to', filePath, 'size:', output.length);
