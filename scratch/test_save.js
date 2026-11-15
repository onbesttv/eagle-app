const https = require("https");
// Vamos verificar o que acontece se chamarmos panel_save_own_config para suporte24 com a password
// Primeiro vamos buscar a password do suporte24 chamando panel_login_dashboard
const payload = JSON.stringify({
  p_device_id: "test",
  p_username: "",
  p_service: "BEST",
  p_device_name: "PC",
  p_app_version: "8.0",
  p_reseller_id: "suporte24"
});
const req = https.request({
  hostname: "myreymyqsnikjdhnstjn.supabase.co",
  path: "/rest/v1/rpc/app_sync",
  method: "POST",
  headers: {
    "apikey": "sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM",
    "Authorization": "Bearer sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM",
    "Content-Type": "application/json"
  }
}, res => {
  let d = "";
  res.on("data", c => d += c);
  res.on("end", () => console.log("SYNC:", d));
});
req.write(payload);
req.end();
