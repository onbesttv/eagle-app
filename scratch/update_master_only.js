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

// 1. ADD CSS FOR RESELLER VS MASTER LAYOUT
const cssLayoutTarget = `        .app-layout {
            display: flex;
            min-height: 100vh;
            width: 100%;
            background: var(--bg);
        }`;

const cssLayoutReplacement = `        .app-layout {
            display: flex;
            min-height: 100vh;
            width: 100%;
            background: var(--bg);
        }

        /* LAYOUT CLÁSSICO PARA REVENDEDORES (SEM BARRA LATERAL) */
        .app-layout.is-reseller {
            display: block !important;
            max-width: 1320px;
            margin: 0 auto;
            padding: 24px 20px 60px;
            background: transparent;
        }
        .app-layout.is-reseller .sidebar {
            display: none !important;
        }
        .app-layout.is-reseller .content-header {
            display: none !important;
        }
        .app-layout.is-reseller .sidebar-backdrop {
            display: none !important;
        }
        .app-layout.is-reseller .main-content {
            padding: 0 !important;
            max-width: 100% !important;
            background: transparent !important;
        }
        .app-layout.is-reseller .content-body {
            padding: 0 !important;
            max-width: 100% !important;
        }

        /* Topbar e Tabs horizontais clássicas para revendedores */
        .topbar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 20px;
            padding-bottom: 16px;
            border-bottom: 1px solid var(--border);
            flex-wrap: wrap;
            gap: 12px;
        }
        .topbar-actions {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;
        }
        .nav-tabs {
            display: flex;
            gap: 10px;
            margin-bottom: 24px;
            border-bottom: 1px solid var(--border);
            padding-bottom: 12px;
        }
        .nav-tabs .tab-btn {
            background: var(--bg-card);
            border: 1px solid var(--border);
            color: var(--text-muted);
            padding: 10px 18px;
            border-radius: 10px;
            font-weight: 700;
            font-size: 13px;
            cursor: pointer;
            transition: all 0.15s;
            display: inline-block !important;
            width: auto !important;
        }
        .nav-tabs .tab-btn.active {
            background: var(--accent);
            border-color: var(--accent);
            color: #fff;
        }`;

replaceExact('CSS Layout for Resellers', cssLayoutTarget, cssLayoutReplacement);

// 2. MEDIA QUERIES FOR RESELLERS
const mediaTarget = `        @media (max-width: 768px) {
            html, body {
                overflow-x: hidden;
                width: 100%;
            }
            .content-header {
                padding: 12px 14px;
            }
            .header-titles h2 {
                font-size: 16px;
            }
            .header-titles .header-sub {
                display: none;
            }
            .content-body {
                padding: 14px 10px 35px;
            }`;

const mediaReplacement = `        @media (max-width: 768px) {
            html, body {
                overflow-x: hidden;
                width: 100%;
            }
            .content-header {
                padding: 12px 14px;
            }
            .header-titles h2 {
                font-size: 16px;
            }
            .header-titles .header-sub {
                display: none;
            }
            .content-body {
                padding: 14px 10px 35px;
            }

            /* Adaptação móvel exclusiva do painel de revendedor */
            .app-layout.is-reseller {
                padding: 12px 10px 40px !important;
            }
            .app-layout.is-reseller .topbar {
                flex-direction: column;
                align-items: stretch;
                gap: 12px;
                margin-bottom: 14px;
                padding-bottom: 12px;
            }
            .app-layout.is-reseller .nav-tabs {
                display: flex;
                flex-wrap: nowrap;
                overflow-x: auto;
                -webkit-overflow-scrolling: touch;
                gap: 8px;
                padding-bottom: 8px;
                margin-bottom: 16px;
                scrollbar-width: none;
            }
            .app-layout.is-reseller .nav-tabs::-webkit-scrollbar {
                display: none;
            }
            .app-layout.is-reseller .tab-btn {
                flex-shrink: 0;
                padding: 8px 14px;
                font-size: 12px;
                border-radius: 8px;
            }`;

replaceExact('Media queries for Resellers', mediaTarget, mediaReplacement);

// 3. HTML: ADD appLayout ID, contentHeader ID, AND RESELLER TOPBAR / NAV-TABS
const htmlContentTarget = `    <div class="app-layout">
        <!-- SIDEBAR LATERAL (NAVEGAÇÃO COMPLETA) -->
        <aside class="sidebar" id="appSidebar">`;

const htmlContentReplacement = `    <div class="app-layout" id="appLayout">
        <!-- SIDEBAR LATERAL (EXCLUSIVA DO PAINEL MASTER) -->
        <aside class="sidebar" id="appSidebar">`;

replaceExact('appLayout ID', htmlContentTarget, htmlContentReplacement);

const htmlHeaderTarget = `        <!-- ÁREA DE CONTEÚDO PRINCIPAL -->
        <main class="main-content">
            <header class="content-header">
                <div class="header-left">
                    <button type="button" class="btn-menu-toggle" onclick="toggleSidebar(true)" aria-label="Abrir Menu">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
                    </button>
                    <div class="header-titles">
                        <h2 id="pageActiveTitle">📺 DNS &amp; Dispositivos</h2>
                        <span id="headerSubTitle" class="header-sub">Gestão central de DNS, boxes e conexões ativas</span>
                    </div>
                </div>
                <div class="header-actions">
                    <button type="button" class="btn btn-outline btn-sm" onclick="loadDashboard(false)">🔄 Atualizar</button>
                </div>
            </header>

            <div class="content-body">
                <!-- CARTÕES DE ESTATÍSTICAS -->`;

const htmlHeaderReplacement = `        <!-- ÁREA DE CONTEÚDO PRINCIPAL -->
        <main class="main-content">
            <!-- CABEÇALHO SUPERIOR (APENAS PAINEL MASTER COM SIDEBAR) -->
            <header class="content-header" id="contentHeader" style="display: none;">
                <div class="header-left">
                    <button type="button" class="btn-menu-toggle" onclick="toggleSidebar(true)" aria-label="Abrir Menu">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
                    </button>
                    <div class="header-titles">
                        <h2 id="pageActiveTitle">📺 DNS &amp; Dispositivos</h2>
                        <span id="headerSubTitle" class="header-sub">Gestão central de DNS, boxes e conexões ativas</span>
                    </div>
                </div>
                <div class="header-actions">
                    <button type="button" class="btn btn-outline btn-sm" onclick="loadDashboard(false)">🔄 Atualizar</button>
                </div>
            </header>

            <div class="content-body">
                <!-- TOPBAR CLÁSSICA (EXCLUSIVA DE REVENDEDORES) -->
                <div class="topbar" id="resellerTopbar" style="display: none;">
                    <div class="brand">
                        <div class="brand-badge" id="resellerBrandBadge">P</div>
                        <div>
                            <h1 id="resellerPanelTitle">Painel de Controlo</h1>
                            <span id="resellerLastSyncLabel">Ligado ao Supabase em tempo real</span>
                        </div>
                    </div>
                    <div class="topbar-actions">
                        <span id="resellerRoleBadge" class="badge badge-best" style="padding: 7px 12px; font-size: 12px;">REVENDA</span>
                        <button class="btn btn-outline" onclick="logout()">Sair</button>
                    </div>
                </div>

                <!-- ABAS HORIZONTAIS CLÁSSICAS (EXCLUSIVAS DE REVENDEDORES) -->
                <div class="nav-tabs" id="resellerNavTabs" style="display: none;">
                    <button type="button" class="tab-btn active" id="resellerTabBtnMain" onclick="switchTab('main')">📺 DNS &amp; Dispositivos</button>
                    <button type="button" class="tab-btn" id="resellerTabBtnVpn" onclick="switchTab('vpn')">🛡️ Gerir VPN</button>
                </div>

                <!-- CARTÕES DE ESTATÍSTICAS -->`;

replaceExact('Reseller Topbar & Tabs HTML', htmlHeaderTarget, htmlHeaderReplacement);

// 4. JAVASCRIPT: switchTab UPDATE FOR RESELLER BUTTONS
const jsSwitchTabTarget = `    function switchTab(tab) {
        document.getElementById("tabBtnMain").classList.toggle("active", tab === "main");
        document.getElementById("tabBtnVpn").classList.toggle("active", tab === "vpn");`;

const jsSwitchTabReplacement = `    function switchTab(tab) {
        document.getElementById("tabBtnMain").classList.toggle("active", tab === "main");
        document.getElementById("tabBtnVpn").classList.toggle("active", tab === "vpn");
        const rBtnMain = document.getElementById("resellerTabBtnMain");
        if (rBtnMain) rBtnMain.classList.toggle("active", tab === "main");
        const rBtnVpn = document.getElementById("resellerTabBtnVpn");
        if (rBtnVpn) rBtnVpn.classList.toggle("active", tab === "vpn");`;

replaceExact('JavaScript switchTab reseller buttons', jsSwitchTabTarget, jsSwitchTabReplacement);

// 5. JAVASCRIPT: loadDashboard LAYOUT TOGGLE
const jsLoadTarget = `            isMasterRole = data.role === "master";
            const brandTitle = \`\${data.brand_name || "Painel"} — Painel de Controlo\`;
            document.getElementById("panelTitle").textContent = brandTitle;
            document.title = brandTitle;
            document.getElementById("brandBadge").textContent = (data.brand_name || "P").trim().charAt(0).toUpperCase();
            document.getElementById("roleBadge").textContent = isMasterRole ? "👑 SUPER ADMIN (MASTER)" : \`🤝 REVENDA: \${currentResellerId.toUpperCase()}\`;
            const isJoker = (currentResellerId === "joker" || currentResellerId === "tv_joker");
            const hasVpnInApp = currentResellerId !== "pereira_tv";
            const hasBest2InApp = !isJoker && currentResellerId !== "pereira_tv" && currentResellerId !== "godinho_tv" && currentResellerId !== "dream" && currentResellerId !== "suporte24";
            document.getElementById("masterTabs").style.display = "flex";
            document.getElementById("tabBtnVpn").style.display = hasVpnInApp ? "inline-block" : "none";`;

const jsLoadReplacement = `            isMasterRole = data.role === "master";
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

replaceExact('JavaScript loadDashboard Layout check', jsLoadTarget, jsLoadReplacement);

// 6. SYNC LAST SYNC LABEL IN BOTH MASTER AND RESELLER
const jsSyncTarget = `            document.getElementById("lastSyncLabel").textContent = "Atualizado às " + new Date().toLocaleTimeString("pt-PT");`;

const jsSyncReplacement = `            const syncLabelText = "Atualizado às " + new Date().toLocaleTimeString("pt-PT");
            const lbl1 = document.getElementById("lastSyncLabel");
            if (lbl1) lbl1.textContent = syncLabelText;
            const lbl2 = document.getElementById("resellerLastSyncLabel");
            if (lbl2) lbl2.textContent = syncLabelText;`;

replaceExact('JavaScript lastSyncLabel sync', jsSyncTarget, jsSyncReplacement);

// Restore original line ending style
const output = isCrlf ? content.replace(/\n/g, '\r\n') : content;
fs.writeFileSync(filePath, output, 'utf8');
console.log('Successfully written to', filePath, 'size:', output.length);
