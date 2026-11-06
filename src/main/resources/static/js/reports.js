// Sales report page (story 7). Asks the server for totals grouped by day/week/month and shows them.

const formEl = document.getElementById("report-form");
const messageEl = document.getElementById("message");
const money = v => "$" + Number(v).toLocaleString("en-CA", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const HEADINGS = { daily: "Day", weekly: "Week", monthly: "Month" };

function cell(content, className) {
    const td = document.createElement("td");
    td.append(content);
    if (className) td.className = className;
    return td;
}

/** The period and date range currently chosen in the form (used by both the report and the CSV export). */
function reportParams() {
    const params = new URLSearchParams({ period: document.getElementById("period").value });
    const from = document.getElementById("from").value;
    const to = document.getElementById("to").value;
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    return params;
}

async function loadReport() {
    const params = reportParams();

    messageEl.hidden = true;
    formEl.querySelector("button").disabled = true;
    try {
        render(await api("/api/reports/sales?" + params));
    } catch (e) {
        messageEl.textContent = "Couldn't load the report: " + e.message;
        messageEl.hidden = false;
    } finally {
        formEl.querySelector("button").disabled = false;
    }
}

function render(r) {
    document.getElementById("revenue").textContent = money(r.totalRevenue);
    document.getElementById("sale-count").textContent = r.saleCount;
    document.getElementById("items").textContent = r.itemsSold;
    document.getElementById("average").textContent = money(r.averageSale);
    document.getElementById("range").textContent = `${r.from} to ${r.to}`;
    document.getElementById("period-heading").textContent = HEADINGS[r.period];

    // Fill the date boxes with what the server actually used (shows the defaults)
    document.getElementById("from").value = r.from;
    document.getElementById("to").value = r.to;

    const max = Math.max(0, ...r.rows.map(row => Number(row.revenue)));
    document.getElementById("rows").replaceChildren(...r.rows.map(row => {
        const tr = document.createElement("tr");
        if (row.saleCount === 0) tr.className = "muted";
        const bar = document.createElement("span");
        bar.className = "bar";
        bar.style.width = max > 0 ? (Number(row.revenue) / max * 100) + "%" : "0";
        tr.append(cell(row.label), cell(String(row.saleCount), "num"), cell(String(row.itemsSold), "num"),
            cell(money(row.revenue), "num"), cell(bar, "bar-col"));
        return tr;
    }));

    document.getElementById("no-top").hidden = r.topProducts.length > 0;
    document.getElementById("top-table").hidden = r.topProducts.length === 0;
    document.getElementById("top-rows").replaceChildren(...r.topProducts.map(p => {
        const tr = document.createElement("tr");
        tr.append(cell(p.name), cell(String(p.quantitySold), "num"), cell(money(p.revenue), "num"));
        return tr;
    }));

    document.getElementById("report").hidden = false;
}

/** Story 10: download the report the form is set to as a CSV file. */
async function exportCsv() {
    const button = document.getElementById("export-csv");
    messageEl.hidden = true;
    button.disabled = true;
    try {
        const res = await fetch("/api/reports/sales/export?" + reportParams());
        if (res.status === 401) {
            location.href = "/login.html";
            return;
        }
        if (!res.ok) {
            const body = await res.json().catch(() => null);
            throw new Error((body && body.error) || `${res.status} ${res.statusText}`);
        }
        const match = /filename="([^"]+)"/.exec(res.headers.get("Content-Disposition") || "");
        const link = document.createElement("a");
        link.href = URL.createObjectURL(await res.blob());
        link.download = match ? match[1] : "sales-report.csv";
        document.body.append(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(link.href);
    } catch (e) {
        messageEl.textContent = "Couldn't export the report: " + e.message;
        messageEl.hidden = false;
    } finally {
        button.disabled = false;
    }
}

formEl.addEventListener("submit", e => { e.preventDefault(); loadReport(); });
document.getElementById("export-csv").addEventListener("click", exportCsv);
// Changing the grouping resets to that grouping's default range
document.getElementById("period").addEventListener("change", () => {
    document.getElementById("from").value = "";
    document.getElementById("to").value = "";
    loadReport();
});

loadCurrentUser().then(me => {
    document.getElementById("who").textContent = me.username + " (" + me.role + ")";
    loadReport();
});
