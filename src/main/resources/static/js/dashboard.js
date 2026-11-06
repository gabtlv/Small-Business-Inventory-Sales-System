// Dashboard page (story 12). Shows total sales, top products and the low-stock count for the last 30 days.

const messageEl = document.getElementById("message");
const money = v => "$" + Number(v).toLocaleString("en-CA", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

function cell(text, className) {
    const td = document.createElement("td");
    td.textContent = text;
    if (className) td.className = className;
    return td;
}

async function loadDashboard() {
    try {
        render(await api("/api/dashboard/summary"));
    } catch (e) {
        messageEl.textContent = "Couldn't load the dashboard: " + e.message;
        messageEl.hidden = false;
    }
}

function render(d) {
    document.getElementById("range").textContent = `Last 30 days (${d.from} to ${d.to})`;
    document.getElementById("total-sales").textContent = money(d.totalSales);
    document.getElementById("sale-count").textContent = d.saleCount;
    document.getElementById("low-stock").textContent = d.lowStockCount;

    document.getElementById("no-top").hidden = d.topProducts.length > 0;
    document.getElementById("top-table").hidden = d.topProducts.length === 0;
    document.getElementById("top-rows").replaceChildren(...d.topProducts.map(p => {
        const tr = document.createElement("tr");
        tr.append(cell(p.name), cell(String(p.quantitySold), "num"), cell(money(p.revenue), "num"));
        return tr;
    }));

    document.getElementById("dashboard").hidden = false;
}

loadCurrentUser().then(me => {
    document.getElementById("who").textContent = me.username + " (" + me.role + ")";
    loadDashboard();
});
