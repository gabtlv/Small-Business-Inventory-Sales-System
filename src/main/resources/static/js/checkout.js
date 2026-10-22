// Checkout page (story 5): pick products and quantities, then POST /api/sales.
// The server re-checks everything and calculates the real total; the numbers here are a preview.
//
// The form is locked (fieldset disabled) whenever:
//  - a request is in flight: submitting a sale, then refreshing stock afterwards (busy)
//  - we couldn't confirm whether the last sale was saved (pendingSale), until it's resolved
//  - stock levels couldn't be loaded, so the rows on screen may be out of date (!stockLoaded)

const REQUEST_TIMEOUT_MS = 20000;

let products = new Map();   // id (string) -> { id, name, sku, price, quantity }
let lines = [];             // what the cashier has entered; always changed through setLines()
let busy = false;
let pendingSale = null;     // { key, items } of a sale whose outcome is unknown
let stockLoaded = false;
let loadError = null;

const linesEl = document.getElementById("lines");
const completeBtn = document.getElementById("complete-sale");
const formEl = document.getElementById("sale-form");
const messageEl = document.getElementById("message");
const uncertainEl = document.getElementById("uncertain-actions");
const checkBtn = document.getElementById("check-sale");
const discardBtn = document.getElementById("discard-sale");
const reloadEl = document.getElementById("reload-actions");
const reloadBtn = document.getElementById("reload-products");

function newLine() {
    return { productId: "", quantity: "1" };
}

/** Money is handled in whole cents so 0.10 + 0.20 shows as 0.30. */
function toCents(price) {
    return Math.round(Number(price) * 100);
}

function formatCents(cents) {
    return "$" + (cents / 100).toFixed(2);
}

function showMessage(text, type) {
    messageEl.textContent = text;
    messageEl.className = "alert " + type;
    messageEl.hidden = false;
}

/** A fresh random key per sale attempt; retrying with the same key can't record the sale twice. */
function newSaleKey() {
    if (crypto.randomUUID) return crypto.randomUUID();
    const bytes = crypto.getRandomValues(new Uint8Array(16));   // randomUUID needs https or localhost
    return Array.from(bytes, b => b.toString(16).padStart(2, "0")).join("");
}

function timeoutSignal() {
    return AbortSignal.timeout ? AbortSignal.timeout(REQUEST_TIMEOUT_MS) : undefined;
}

function isLocked() {
    return busy || pendingSale !== null || !stockLoaded;
}

/** Shows/hides the lock and recovery buttons to match the current state. */
function updateLocks() {
    formEl.disabled = isLocked();
    uncertainEl.hidden = pendingSale === null;
    checkBtn.disabled = discardBtn.disabled = busy;
    reloadEl.hidden = loadError === null || pendingSale !== null;
    document.getElementById("reload-message").textContent = loadError || "";
    reloadBtn.disabled = busy;
    refresh();
}

/** Replaces the cart and redraws it, so the rows on screen always match `lines`. */
function setLines(newLines) {
    lines = newLines.length ? newLines : [newLine()];
    renderLines();
}

/** Loads products and stock. Doesn't change `busy`; callers lock around it. */
async function loadProducts() {
    try {
        const list = await api("/api/sales/products", { signal: timeoutSignal() });
        products = new Map(list.map(p => [String(p.id), p]));
        stockLoaded = true;
        loadError = null;
    } catch (e) {
        // Keep showing the old rows, but locked: their stock numbers may be out of date
        stockLoaded = false;
        loadError = "Couldn't load current stock levels (" + e.message + "). Checkout is paused until they load.";
    }
    document.getElementById("loading").hidden = true;
    document.getElementById("no-products").hidden = !stockLoaded || products.size > 0;
    formEl.hidden = products.size === 0;
    renderLines();
}

async function reloadProducts() {
    if (busy) return;
    busy = true;
    updateLocks();
    await loadProducts();
    busy = false;
    updateLocks();
}

/** Rebuilds the table rows from `lines` and `products`. */
function renderLines() {
    linesEl.replaceChildren(...lines.map(buildRow));
    updateLocks();
}

function buildRow(line, index) {
    const tr = document.createElement("tr");

    const select = document.createElement("select");
    select.setAttribute("aria-label", "Product");
    select.append(new Option("Choose a product…", ""));
    for (const p of products.values()) {
        const option = new Option(p.sku ? `${p.name} (${p.sku})` : p.name, String(p.id));
        if (p.quantity <= 0) {
            option.text += " (out of stock)";
            option.disabled = true;
        }
        select.append(option);
    }
    select.value = products.has(line.productId) ? line.productId : "";
    line.productId = select.value;
    select.addEventListener("change", () => { line.productId = select.value; refresh(); });

    const qty = document.createElement("input");
    qty.type = "number";
    qty.min = "1";
    qty.step = "1";
    qty.inputMode = "numeric";
    qty.className = "qty";
    qty.setAttribute("aria-label", "Quantity");
    qty.value = line.quantity;
    qty.addEventListener("input", () => { line.quantity = qty.value; refresh(); });

    const problem = document.createElement("small");
    problem.className = "field-error";

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "link";
    remove.textContent = "Remove";
    remove.addEventListener("click", () => setLines(lines.filter((_, i) => i !== index)));

    const price = document.createElement("td");
    const stock = document.createElement("td");
    const total = document.createElement("td");
    price.className = stock.className = total.className = "num";

    const productCell = document.createElement("td");
    productCell.append(select);
    const qtyCell = document.createElement("td");
    qtyCell.append(qty, problem);
    const removeCell = document.createElement("td");
    removeCell.append(remove);

    tr.append(productCell, price, stock, qtyCell, total, removeCell);
    line.cells = { price, stock, total, problem };
    return tr;
}

/**
 * Updates prices, line totals, the sale total and per-row problems.
 * Returns true if the sale looks valid and can be submitted.
 */
function refresh() {
    // Same product on several rows: check the combined quantity against stock
    const wanted = new Map();
    for (const line of lines) {
        if (line.productId && isWholeNumber(line.quantity)) {
            wanted.set(line.productId, (wanted.get(line.productId) || 0) + Number(line.quantity));
        }
    }

    let totalCents = 0;
    let valid = true;
    for (const line of lines) {
        const p = products.get(line.productId);
        let problem = "";
        if (!p) {
            problem = "Choose a product.";
        } else if (!isWholeNumber(line.quantity)) {
            problem = "Enter a whole number of at least 1.";
        } else if (wanted.get(line.productId) > p.quantity) {
            problem = `Only ${p.quantity} in stock.`;
        }

        line.cells.price.textContent = p ? formatCents(toCents(p.price)) : "—";
        line.cells.stock.textContent = p ? p.quantity : "—";
        if (p && isWholeNumber(line.quantity)) {
            const lineCents = toCents(p.price) * Number(line.quantity);
            line.cells.total.textContent = formatCents(lineCents);
            totalCents += lineCents;
        } else {
            line.cells.total.textContent = "—";
        }
        line.cells.problem.textContent = problem;
        if (problem) valid = false;
    }

    document.getElementById("total").textContent = formatCents(totalCents);
    completeBtn.disabled = isLocked() || !valid;
    return valid;
}

function isWholeNumber(value) {
    return /^\s*[1-9]\d*\s*$/.test(value) && Number(value) <= 1000000;
}

/**
 * True only when the server definitely answered "no" (a 4xx response), so nothing was saved.
 * Lost connections, timeouts and server errors are uncertain: the sale may have been saved
 * before the response went missing.
 */
function isConfirmedRejection(e) {
    const inProgress = e.body && e.body.code === "SALE_IN_PROGRESS";
    return e.status >= 400 && e.status < 500 && !inProgress;
}

function uncertainMessage(e) {
    if (e.body && e.body.code === "SALE_IN_PROGRESS") {
        return "This sale is still being saved. Press “Check again” in a moment. It will not be recorded twice.";
    }
    let reason = "connection problem";
    if (e.name === "TimeoutError" || e.name === "AbortError") reason = "the server took too long to answer";
    else if (e.status >= 500) reason = "server error";
    return "We couldn't confirm whether this sale was recorded (" + reason + "). It may already be saved. "
        + "Don't enter it again: press “Check again” to finish it safely. It will not be recorded twice.";
}

/** Sends a sale and keeps the page locked until the result is shown and stock is refreshed. */
async function sendSale(sale) {
    busy = true;
    completeBtn.textContent = "Saving…";
    messageEl.hidden = true;
    updateLocks();

    try {
        const saved = await api("/api/sales", {
            method: "POST",
            headers: { "Idempotency-Key": sale.key },
            body: { items: sale.items },
            signal: timeoutSignal(),
        });
        pendingSale = null;
        setLines([newLine()]);   // show the cleared cart right away (still locked)
        const total = formatCents(toCents(saved.total));
        showMessage(saved.alreadyRecorded
            ? `Sale #${saved.id} had already been recorded, so it was not recorded again. Total ${total}.`
            : `Sale #${saved.id} recorded. Total ${total}. Stock has been updated.`, "success");
    } catch (e) {
        if (isConfirmedRejection(e)) {
            pendingSale = null;   // keep the rows so the cashier can fix them and try again
            showMessage("Sale not recorded: " + e.message, "error");
        } else {
            pendingSale = sale;   // same key on "Check again", so it can't be recorded twice
            showMessage(uncertainMessage(e), "warning");
        }
    }
    completeBtn.textContent = "Complete sale";
    updateLocks();   // show the outcome's buttons now; still locked because busy

    // Show the new stock levels (they may also have changed because of other cashiers).
    // Skipped while the outcome is unknown: the cart stays locked until "Check again" resolves it.
    if (pendingSale === null) {
        await loadProducts();
    }
    busy = false;
    updateLocks();
}

async function completeSale() {
    if (isLocked() || !refresh()) return;
    await sendSale({
        key: newSaleKey(),
        items: lines.map(l => ({ productId: Number(l.productId), quantity: Number(l.quantity) })),
    });
}

async function checkAgain() {
    if (busy || pendingSale === null) return;
    await sendSale(pendingSale);
}

async function discardPendingSale() {
    if (busy || pendingSale === null) return;
    const sure = confirm("Only discard this sale if you're sure it was NOT recorded (for example, the customer left). "
        + "If it was recorded, its stock has already been taken out. Discard it?");
    if (!sure) return;
    pendingSale = null;
    setLines([newLine()]);
    showMessage("Sale discarded. Check the stock levels below before entering it again.", "warning");
    await reloadProducts();
}

// ---- Story 9: quick search by name or SKU ----

const quickInput = document.getElementById("quick-search");
const quickResults = document.getElementById("quick-results");
const MAX_MATCHES = 6;

/** Products whose name or SKU contains the text; exact SKU matches first. */
function findProducts(text) {
    const q = text.trim().toLowerCase();
    if (!q) return [];
    const matches = [...products.values()].filter(p =>
        p.name.toLowerCase().includes(q) || (p.sku && p.sku.toLowerCase().includes(q)));
    matches.sort((a, b) => {
        const exactA = a.sku && a.sku.toLowerCase() === q ? 0 : 1;
        const exactB = b.sku && b.sku.toLowerCase() === q ? 0 : 1;
        return exactA - exactB || a.name.localeCompare(b.name);
    });
    return matches;
}

/** Adds one of the product: bumps its existing row, fills an empty row, or adds a new row. */
function addProductToSale(productId) {
    const id = String(productId);
    const existing = lines.find(l => l.productId === id);
    if (existing) {
        existing.quantity = String((isWholeNumber(existing.quantity) ? Number(existing.quantity) : 0) + 1);
    } else {
        const empty = lines.find(l => !l.productId);
        if (empty) {
            empty.productId = id;
        } else {
            lines.push({ productId: id, quantity: "1" });
        }
    }
    setLines([...lines]);
    quickInput.value = "";
    renderQuickResults();
    quickInput.focus();
}

function renderQuickResults() {
    const matches = findProducts(quickInput.value);
    if (quickInput.value.trim() && matches.length === 0) {
        const li = document.createElement("li");
        li.className = "muted";
        li.style.padding = "8px 12px";
        li.textContent = "No products match.";
        quickResults.replaceChildren(li);
        return;
    }
    quickResults.replaceChildren(...matches.slice(0, MAX_MATCHES).map(p => {
        const li = document.createElement("li");
        const btn = document.createElement("button");
        btn.type = "button";
        const left = document.createElement("span");
        left.textContent = p.sku ? `${p.name} (${p.sku})` : p.name;
        const right = document.createElement("span");
        right.className = "muted";
        right.textContent = p.quantity > 0 ? `${formatCents(toCents(p.price))} · ${p.quantity} in stock` : "out of stock";
        btn.append(left, right);
        btn.disabled = p.quantity <= 0;
        btn.addEventListener("click", () => addProductToSale(p.id));
        li.append(btn);
        return li;
    }));
}

quickInput.addEventListener("input", renderQuickResults);
quickInput.addEventListener("keydown", e => {
    if (e.key === "Escape") { quickInput.value = ""; renderQuickResults(); return; }
    if (e.key !== "Enter") return;
    e.preventDefault();
    const matches = findProducts(quickInput.value).filter(p => p.quantity > 0);
    const q = quickInput.value.trim().toLowerCase();
    const exact = matches.find(p => p.sku && p.sku.toLowerCase() === q);
    if (exact) addProductToSale(exact.id);
    else if (matches.length === 1) addProductToSale(matches[0].id);
    // Several matches: leave the list open so the cashier can pick one
});

document.getElementById("add-line").addEventListener("click", () => setLines([...lines, newLine()]));
completeBtn.addEventListener("click", completeSale);
checkBtn.addEventListener("click", checkAgain);
discardBtn.addEventListener("click", discardPendingSale);
reloadBtn.addEventListener("click", reloadProducts);

loadCurrentUser().then(me => {
    document.getElementById("who").textContent = me.username + " (" + me.role + ")";
});
setLines([newLine()]);
reloadProducts();
