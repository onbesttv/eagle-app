const fs = require('fs');

async function cleanAnnouncement() {
    const html = fs.readFileSync('painel-admin/index.html', 'utf8');
    const url = html.match(/const SUPABASE_URL = "([^"]+)"/)[1];
    const key = html.match(/const SUPABASE_KEY = "([^"]+)"/)[1];

    // Read reseller dream via rpc panel_login_dashboard
    const res = await fetch(`${url}/rest/v1/rpc/panel_login_dashboard`, {
        method: "POST",
        headers: {
            "apikey": key,
            "Authorization": `Bearer ${key}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            p_reseller_id: "dream",
            p_password: "dream"
        })
    });
    const data = await res.json();
    console.log("Current dream announcement:", data.announcement);

    // Save with clean announcement
    const rawAnn = String(data.announcement || "");
    // Extract activations and vpn parts
    const actMatch = rawAnn.match(/\|\|ACTIVATIONS:[^|]+(\|[^|]+)*/);
    const actPart = actMatch ? actMatch[0] : "";
    const vpnMatch = rawAnn.match(/\|\|VPN:[^$]+/);
    const vpnPart = vpnMatch ? vpnMatch[0] : "";
    
    // There should be NO global text message! Just actPart and vpnPart
    const newAnnouncement = `${actPart} ${vpnPart}`.trim();
    console.log("New clean announcement:", newAnnouncement);

    const saveRes = await fetch(`${url}/rest/v1/rpc/panel_save_own_config`, {
        method: "POST",
        headers: {
            "apikey": key,
            "Authorization": `Bearer ${key}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            p_reseller_id: "dream",
            p_password: "dream",
            p_best_url: data.best_url || "https://topnextplay.online:443",
            p_best2_url: data.best2_url || "https://topnextplay.online:443",
            p_announcement: newAnnouncement,
            p_maintenance_mode: !!data.maintenance_mode,
            p_new_password: ""
        })
    });
    console.log("Save status:", saveRes.status);
}

cleanAnnouncement().catch(console.error);
