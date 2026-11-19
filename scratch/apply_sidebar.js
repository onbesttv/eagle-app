const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, '..', 'painel-admin', 'index.html');
let raw = fs.readFileSync(filePath, 'utf8');
const isCrlf = raw.includes('\r\n');
let content = raw.replace(/\r\n/g, '\n');

console.log('Original length (normalized):', content.length, 'isCrlf:', isCrlf);

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

// 1. CSS REPLACEMENT
const cssTarget = `        #dashboardScreen {
            display: none;
            max-width: 1320px;
            margin: 0 auto;
            padding: 24px 20px 60px;
        }
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
        .brand {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .brand-badge {
            background: linear-gradient(135deg, #3b82f6, #1d4ed8);
            width: 40px;
            height: 40px;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            font-size: 18px;
        }
        .brand h1 {
            font-size: 20px;
            font-weight: 700;
        }
        .brand span {
            font-size: 12px;
            color: var(--text-muted);
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
        .tab-btn {
            background: var(--bg-card);
            border: 1px solid var(--border);
            color: var(--text-muted);
            padding: 10px 18px;
            border-radius: 10px;
            font-weight: 700;
            font-size: 13px;
            cursor: pointer;
            transition: all 0.15s;
        }
        .tab-btn.active {
            background: var(--accent);
            border-color: var(--accent);
            color: #fff;
        }`;

const cssReplacement = `        #dashboardScreen {
            display: none;
            min-height: 100vh;
            width: 100%;
        }
        .app-layout {
            display: flex;
            min-height: 100vh;
            width: 100%;
            background: var(--bg);
        }

        /* SIDEBAR LATERAL (ESTILO OTTRUN / MODERNO) */
        .sidebar {
            width: 260px;
            min-width: 260px;
            max-width: 260px;
            background: #0b1325;
            border-right: 1px solid var(--border);
            display: flex;
            flex-direction: column;
            position: sticky;
            top: 0;
            height: 100vh;
            overflow-y: auto;
            z-index: 100;
            transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .sidebar::-webkit-scrollbar {
            width: 5px;
        }
        .sidebar::-webkit-scrollbar-thumb {
            background: rgba(255, 255, 255, 0.1);
            border-radius: 4px;
        }

        .sidebar-header {
            padding: 20px 18px 18px;
            border-bottom: 1px solid var(--border);
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
        }
        .sidebar-brand {
            display: flex;
            align-items: center;
            gap: 12px;
            min-width: 0;
            text-decoration: none;
        }
        .brand-badge {
            background: linear-gradient(135deg, #3b82f6, #1d4ed8);
            width: 38px;
            height: 38px;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            font-size: 17px;
            color: #fff;
            flex-shrink: 0;
            box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
        }
        .sidebar-brand-info {
            min-width: 0;
        }
        .sidebar-brand-info h1 {
            font-size: 15px;
            font-weight: 700;
            color: #f8fafc;
            line-height: 1.2;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            margin: 0;
        }
        .sidebar-status-tag {
            display: inline-flex;
            align-items: center;
            gap: 5px;
            font-size: 11px;
            color: #10b981;
            font-weight: 600;
            margin-top: 3px;
        }
        .sidebar-status-dot {
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background: #10b981;
            box-shadow: 0 0 8px #10b981;
        }
        .sidebar-close-btn {
            display: none;
            background: transparent;
            border: 1px solid var(--border);
            color: var(--text-muted);
            border-radius: 8px;
            width: 32px;
            height: 32px;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            font-size: 14px;
        }

        .sidebar-nav {
            padding: 16px 12px;
            display: flex;
            flex-direction: column;
            gap: 4px;
            flex: 1;
        }
        .sidebar-section-title {
            font-size: 10px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.8px;
            color: #64748b;
            padding: 14px 10px 6px;
        }
        .sidebar-nav .tab-btn {
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
        }
        .sidebar-nav .tab-btn:hover {
            background: rgba(59, 130, 246, 0.08);
            border-color: rgba(59, 130, 246, 0.2);
            color: #f1f5f9;
        }
        .sidebar-nav .tab-btn.active {
            background: linear-gradient(135deg, rgba(37, 99, 235, 0.25), rgba(59, 130, 246, 0.16));
            border-color: rgba(59, 130, 246, 0.45);
            color: #60a5fa;
            font-weight: 700;
            box-shadow: 0 2px 8px rgba(37, 99, 235, 0.18);
        }
        .sidebar-nav .tab-btn .tab-icon {
            font-size: 16px;
            line-height: 1;
            flex-shrink: 0;
            width: 20px;
            text-align: center;
        }
        .sidebar-nav .tab-btn .tab-text {
            flex: 1;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .sidebar-footer {
            padding: 14px 16px;
            border-top: 1px solid var(--border);
            background: rgba(7, 12, 23, 0.95);
            display: flex;
            flex-direction: column;
            gap: 12px;
        }
        .sidebar-user-card {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 8px 10px;
            background: rgba(255, 255, 255, 0.03);
            border: 1px solid rgba(255, 255, 255, 0.06);
            border-radius: 10px;
        }
        .sidebar-user-avatar {
            width: 32px;
            height: 32px;
            border-radius: 8px;
            background: #1e293b;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 15px;
            flex-shrink: 0;
        }
        .sidebar-user-info {
            min-width: 0;
            flex: 1;
        }
        .sidebar-user-info .badge {
            display: inline-block;
            font-size: 10px;
            padding: 3px 7px;
            font-weight: 700;
            letter-spacing: 0.3px;
            max-width: 100%;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .sidebar-sync-text {
            font-size: 10px;
            color: var(--text-muted);
            margin-top: 2px;
            display: block;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .sidebar-logout-btn {
            width: 100%;
            justify-content: center;
            gap: 8px;
            font-size: 12px;
            padding: 8px 12px;
        }

        /* CONTEÚDO PRINCIPAL (MAIN CONTENT) */
        .main-content {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
            min-height: 100vh;
        }
        .content-header {
            padding: 16px 28px;
            border-bottom: 1px solid var(--border);
            background: rgba(15, 23, 42, 0.7);
            backdrop-filter: blur(10px);
            position: sticky;
            top: 0;
            z-index: 40;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 16px;
        }
        .header-left {
            display: flex;
            align-items: center;
            gap: 14px;
            min-width: 0;
        }
        .btn-menu-toggle {
            display: none;
            background: var(--bg-card);
            border: 1px solid var(--border);
            color: var(--text);
            width: 38px;
            height: 38px;
            border-radius: 9px;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            flex-shrink: 0;
        }
        .header-titles {
            min-width: 0;
        }
        .header-titles h2 {
            font-size: 18px;
            font-weight: 700;
            color: #f8fafc;
            line-height: 1.2;
            margin: 0;
        }
        .header-titles .header-sub {
            font-size: 12px;
            color: var(--text-muted);
            margin-top: 2px;
            display: block;
        }
        .header-actions {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-shrink: 0;
        }
        .content-body {
            padding: 24px 28px 60px;
            flex: 1;
            max-width: 1440px;
            width: 100%;
            margin: 0 auto;
        }

        .sidebar-backdrop {
            display: none;
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.7);
            backdrop-filter: blur(3px);
            z-index: 90;
        }`;

replaceExact('CSS', cssTarget, cssReplacement);

// 2. MEDIA QUERIES
const mediaTarget = `        @media (max-width: 992px) {
            #dashboardScreen {
                padding: 16px 14px 50px;
            }
            .grid-2col {
                grid-template-columns: 1fr;
                gap: 16px;
            }
        }

        @media (max-width: 768px) {
            html, body {
                overflow-x: hidden;
                width: 100%;
            }
            #dashboardScreen {
                padding: 12px 10px 40px;
            }

            /* Topbar mobile */
            .topbar {
                flex-direction: column;
                align-items: stretch;
                gap: 12px;
                margin-bottom: 14px;
                padding-bottom: 12px;
            }
            .brand {
                width: 100%;
                gap: 10px;
            }
            .brand-badge {
                width: 36px;
                height: 36px;
                font-size: 16px;
                flex-shrink: 0;
            }
            .brand h1 {
                font-size: 16px;
                line-height: 1.3;
                word-break: break-word;
            }
            .brand span {
                font-size: 11px;
            }
            .topbar-actions {
                width: 100%;
                justify-content: space-between;
            }
            .topbar-actions .badge {
                font-size: 11px;
                padding: 6px 10px;
            }
            .topbar-actions button {
                padding: 7px 16px;
                font-size: 12px;
            }

            /* Abas com scroll táctil suave */
            .nav-tabs {
                display: flex;
                flex-wrap: nowrap;
                overflow-x: auto;
                -webkit-overflow-scrolling: touch;
                gap: 8px;
                padding-bottom: 8px;
                margin-bottom: 16px;
                scrollbar-width: none;
            }
            .nav-tabs::-webkit-scrollbar {
                display: none;
            }
            .tab-btn {
                flex-shrink: 0;
                padding: 8px 14px;
                font-size: 12px;
                border-radius: 8px;
            }`;

const mediaReplacement = `        @media (max-width: 992px) {
            .sidebar {
                position: fixed;
                left: 0;
                top: 0;
                bottom: 0;
                transform: translateX(-100%);
                box-shadow: 10px 0 40px rgba(0,0,0,0.8);
            }
            .sidebar.open {
                transform: translateX(0);
            }
            .sidebar-backdrop.active {
                display: block;
            }
            .sidebar-close-btn {
                display: flex;
            }
            .btn-menu-toggle {
                display: flex;
            }
            .content-header {
                padding: 14px 18px;
            }
            .content-body {
                padding: 18px 14px 45px;
            }
            .grid-2col {
                grid-template-columns: 1fr;
                gap: 16px;
            }
        }

        @media (max-width: 768px) {
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

replaceExact('Media Queries', mediaTarget, mediaReplacement);

// 3. HTML HEADER & SIDEBAR
const htmlTarget = `<!-- ECRÃ PRINCIPAL DO PAINEL -->
<div id="dashboardScreen">
    <div class="topbar">
        <div class="brand">
            <div class="brand-badge" id="brandBadge">E</div>
            <div>
                <h1 id="panelTitle">Eagle TV — Painel de Controlo</h1>
                <span id="lastSyncLabel">Ligado ao Supabase em tempo real</span>
            </div>
        </div>
        <div class="topbar-actions">
            <span id="roleBadge" class="badge badge-best" style="padding: 7px 12px; font-size: 12px;">MASTER</span>
            <button class="btn btn-outline" onclick="logout()">Sair</button>
        </div>
    </div>

    <!-- ABAS DE NAVEGAÇÃO (Visível para Master e Revendedores) -->
    <div class="nav-tabs" id="masterTabs">
        <button class="tab-btn active" id="tabBtnMain" onclick="switchTab('main')">📺 DNS & Dispositivos</button>
        <button class="tab-btn" id="tabBtnVpn" onclick="switchTab('vpn')">🛡️ Gerir VPN</button>
        <button class="tab-btn" id="tabBtnResellers" style="display: none;" onclick="switchTab('resellers')">🤝 Gerir Revendedores (Apps White-Label)</button>
        <button class="tab-btn" id="tabBtnLinks" style="display: none;" onclick="switchTab('links')">🔗 Links Revendas</button>
        <button class="tab-btn" id="tabBtnMessages" style="display: none;" onclick="switchTab('messages')">💬 Mensagens TV</button>
        <button class="tab-btn" id="tabBtnParental" style="display: none;" onclick="switchTab('parental')">🔒 PIN Parental</button>
        <button class="tab-btn" id="tabBtnEpg" style="display: none;" onclick="switchTab('epg')">📡 Validador EPG</button>
    </div>

    <!-- CARTÕES DE ESTATÍSTICAS -->`;

const htmlReplacement = `<!-- ECRÃ PRINCIPAL DO PAINEL -->
<div id="dashboardScreen">
    <!-- Backdrop overlay para telemóveis / tablets -->
    <div class="sidebar-backdrop" id="sidebarBackdrop" onclick="toggleSidebar(false)"></div>

    <div class="app-layout">
        <!-- SIDEBAR LATERAL (NAVEGAÇÃO COMPLETA) -->
        <aside class="sidebar" id="appSidebar">
            <div class="sidebar-header">
                <div class="sidebar-brand">
                    <div class="brand-badge" id="brandBadge">E</div>
                    <div class="sidebar-brand-info">
                        <h1 id="panelTitle">Eagle TV</h1>
                        <span class="sidebar-status-tag"><span class="sidebar-status-dot"></span> Online</span>
                    </div>
                </div>
                <button type="button" class="sidebar-close-btn" onclick="toggleSidebar(false)" title="Fechar Menu">✕</button>
            </div>

            <!-- MENU LATERAL COM CATEGORIAS AGRUPADAS -->
            <nav class="sidebar-nav" id="masterTabs">
                <div class="sidebar-section-title">PRINCIPAL</div>
                <button type="button" class="tab-btn active" id="tabBtnMain" onclick="switchTab('main')">
                    <span class="tab-icon">📺</span>
                    <span class="tab-text">DNS &amp; Dispositivos</span>
                </button>
                <button type="button" class="tab-btn" id="tabBtnVpn" onclick="switchTab('vpn')">
                    <span class="tab-icon">🛡️</span>
                    <span class="tab-text">Gerir VPN</span>
                </button>

                <div class="sidebar-section-title" id="sidebarHeadResellers" style="display: none;">REVENDAS</div>
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
                </button>
            </nav>

            <div class="sidebar-footer">
                <div class="sidebar-user-card">
                    <div class="sidebar-user-avatar">👤</div>
                    <div class="sidebar-user-info">
                        <span id="roleBadge" class="badge badge-best">MASTER</span>
                        <span id="lastSyncLabel" class="sidebar-sync-text">Supabase Conectado</span>
                    </div>
                </div>
                <button type="button" class="btn btn-outline btn-sm sidebar-logout-btn" onclick="logout()">
                    <span>Sair do Painel</span> 🚪
                </button>
            </div>
        </aside>

        <!-- ÁREA DE CONTEÚDO PRINCIPAL -->
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

replaceExact('HTML Header & Sidebar', htmlTarget, htmlReplacement);

// 4. CLOSING TAGS BEFORE MODALS
const closingTarget = `                <div id="epgDiagnosticsNotes" style="font-size: 13px; color: var(--text-muted); line-height: 1.5;"></div>
            </div>
        </div>
    </div>

<!-- MODAL ATIVAR DISPOSITIVO (RedOn / Device Key) -->`;

const closingReplacement = `                <div id="epgDiagnosticsNotes" style="font-size: 13px; color: var(--text-muted); line-height: 1.5;"></div>
            </div>
        </div>
    </div>

            </div> <!-- Fecha content-body -->
        </main> <!-- Fecha main-content -->
    </div> <!-- Fecha app-layout -->
</div> <!-- Fecha dashboardScreen -->

<!-- MODAL ATIVAR DISPOSITIVO (RedOn / Device Key) -->`;

replaceExact('Closing tags', closingTarget, closingReplacement);

// 5. JAVASCRIPT: switchTab and toggleSidebar
const jsTabTarget = `    function switchTab(tab) {
        document.getElementById("tabBtnMain").classList.toggle("active", tab === "main");
        document.getElementById("tabBtnVpn").classList.toggle("active", tab === "vpn");
        document.getElementById("tabBtnResellers").classList.toggle("active", tab === "resellers");
        document.getElementById("tabBtnLinks").classList.toggle("active", tab === "links");
        const btnMsg = document.getElementById("tabBtnMessages");
        if (btnMsg) btnMsg.classList.toggle("active", tab === "messages");
        const btnPar = document.getElementById("tabBtnParental");
        if (btnPar) btnPar.classList.toggle("active", tab === "parental");
        const btnEpg = document.getElementById("tabBtnEpg");
        if (btnEpg) btnEpg.classList.toggle("active", tab === "epg");

        document.getElementById("tabContentMain").style.display = tab === "main" ? "grid" : "none";
        document.getElementById("tabContentVpn").style.display = tab === "vpn" ? "block" : "none";
        document.getElementById("tabContentResellers").style.display = tab === "resellers" ? "block" : "none";
        document.getElementById("tabContentLinks").style.display = tab === "links" ? "block" : "none";
        const cMsg = document.getElementById("tabContentMessages");
        if (cMsg) cMsg.style.display = tab === "messages" ? "block" : "none";
        const cPar = document.getElementById("tabContentParental");
        if (cPar) cPar.style.display = tab === "parental" ? "block" : "none";
        const cEpg = document.getElementById("tabContentEpg");
        if (cEpg) cEpg.style.display = tab === "epg" ? "block" : "none";

        if (tab === "links") {
            renderResellerLinks();
        }
        if (tab === "messages" || tab === "parental") {
            populateMasterToolsDropdowns();
        }
    }`;

const jsTabReplacement = `    function toggleSidebar(open) {
        const sidebar = document.getElementById("appSidebar");
        const backdrop = document.getElementById("sidebarBackdrop");
        if (!sidebar) return;
        const shouldOpen = (open !== undefined) ? !!open : !sidebar.classList.contains("open");
        sidebar.classList.toggle("open", shouldOpen);
        if (backdrop) backdrop.classList.toggle("active", shouldOpen);
        if (window.innerWidth <= 992) {
            document.body.style.overflow = shouldOpen ? "hidden" : "";
        }
    }

    function switchTab(tab) {
        document.getElementById("tabBtnMain").classList.toggle("active", tab === "main");
        document.getElementById("tabBtnVpn").classList.toggle("active", tab === "vpn");
        document.getElementById("tabBtnResellers").classList.toggle("active", tab === "resellers");
        document.getElementById("tabBtnLinks").classList.toggle("active", tab === "links");
        const btnMsg = document.getElementById("tabBtnMessages");
        if (btnMsg) btnMsg.classList.toggle("active", tab === "messages");
        const btnPar = document.getElementById("tabBtnParental");
        if (btnPar) btnPar.classList.toggle("active", tab === "parental");
        const btnEpg = document.getElementById("tabBtnEpg");
        if (btnEpg) btnEpg.classList.toggle("active", tab === "epg");

        document.getElementById("tabContentMain").style.display = tab === "main" ? "grid" : "none";
        document.getElementById("tabContentVpn").style.display = tab === "vpn" ? "block" : "none";
        document.getElementById("tabContentResellers").style.display = tab === "resellers" ? "block" : "none";
        document.getElementById("tabContentLinks").style.display = tab === "links" ? "block" : "none";
        const cMsg = document.getElementById("tabContentMessages");
        if (cMsg) cMsg.style.display = tab === "messages" ? "block" : "none";
        const cPar = document.getElementById("tabContentParental");
        if (cPar) cPar.style.display = tab === "parental" ? "block" : "none";
        const cEpg = document.getElementById("tabContentEpg");
        if (cEpg) cEpg.style.display = tab === "epg" ? "block" : "none";

        if (tab === "links") {
            renderResellerLinks();
        }
        if (tab === "messages" || tab === "parental") {
            populateMasterToolsDropdowns();
        }

        // Fecha a gaveta mobile automaticamente ao selecionar
        if (typeof toggleSidebar === "function") {
            toggleSidebar(false);
        }

        const tabTitles = {
            main: { title: "📺 DNS & Dispositivos", sub: "Gestão central de DNS, boxes e conexões ativas" },
            vpn: { title: "🛡️ Gerir VPN", sub: "Configuração de países, chaves e credenciais de VPN" },
            resellers: { title: "🤝 Revendedores (Apps White-Label)", sub: "Gestão de revendas, limites e marcas personalizadas" },
            links: { title: "🔗 Links & Portais de Revendas", sub: "Painéis e downloads dedicados para cada revendedor" },
            messages: { title: "💬 Mensagens TV Pop-up", sub: "Envio de notificações e avisos diretamente ao ecrã da TV" },
            parental: { title: "🔒 PIN Parental Remoto", sub: "Redefinição e desbloqueio do código de controlo parental da app" },
            epg: { title: "📡 Validador de EPG", sub: "Diagnóstico e emparelhamento de canais com guia de programação" }
        };
        const meta = tabTitles[tab];
        if (meta) {
            const hTitle = document.getElementById("pageActiveTitle");
            const hSub = document.getElementById("headerSubTitle");
            if (hTitle) hTitle.textContent = meta.title;
            if (hSub) hSub.textContent = meta.sub;
        }
    }`;

replaceExact('JavaScript switchTab', jsTabTarget, jsTabReplacement);

// 6. JAVASCRIPT: SECTION HEADERS IN loadDashboard
const jsHeadersTarget = `            document.getElementById("tabBtnResellers").style.display = isMasterRole ? "inline-block" : "none";
            document.getElementById("tabBtnLinks").style.display = isMasterRole ? "inline-block" : "none";
            const btnMsgTab = document.getElementById("tabBtnMessages");
            if (btnMsgTab) btnMsgTab.style.display = isMasterRole ? "inline-block" : "none";
            const btnParTab = document.getElementById("tabBtnParental");
            if (btnParTab) btnParTab.style.display = isMasterRole ? "inline-block" : "none";
            const btnEpgTab = document.getElementById("tabBtnEpg");
            if (btnEpgTab) btnEpgTab.style.display = isMasterRole ? "inline-block" : "none";`;

const jsHeadersReplacement = `            document.getElementById("tabBtnResellers").style.display = isMasterRole ? "flex" : "none";
            document.getElementById("tabBtnLinks").style.display = isMasterRole ? "flex" : "none";
            const btnMsgTab = document.getElementById("tabBtnMessages");
            if (btnMsgTab) btnMsgTab.style.display = isMasterRole ? "flex" : "none";
            const btnParTab = document.getElementById("tabBtnParental");
            if (btnParTab) btnParTab.style.display = isMasterRole ? "flex" : "none";
            const btnEpgTab = document.getElementById("tabBtnEpg");
            if (btnEpgTab) btnEpgTab.style.display = isMasterRole ? "flex" : "none";
            const sHeadRes = document.getElementById("sidebarHeadResellers");
            if (sHeadRes) sHeadRes.style.display = isMasterRole ? "block" : "none";
            const sHeadTools = document.getElementById("sidebarHeadTools");
            if (sHeadTools) sHeadTools.style.display = isMasterRole ? "block" : "none";`;

replaceExact('JavaScript Headers', jsHeadersTarget, jsHeadersReplacement);

// Restore original line ending style
const output = isCrlf ? content.replace(/\n/g, '\r\n') : content;
fs.writeFileSync(filePath, output, 'utf8');
console.log('Successfully written to', filePath, 'size:', output.length);
