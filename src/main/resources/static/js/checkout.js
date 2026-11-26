// Checkout page (story 5): add products to the sale (search or click a product card), adjust
// quantities, then POST /api/sales.
// The server re-checks everything and calculates the real total; the numbers here are a preview.
//
// The form is locked (fieldset disabled) whenever:
//  - a request is in flight: submitting a sale, then refreshing stock afterwards (busy)
//  - we couldn't confirm whether the last sale was saved (pendingSale), until it's resolved
//  - stock levels couldn't be loaded, so the rows on screen may be out of date (!stockLoaded)

const REQUEST_TIMEOUT_MS = 20000;

let products = new Map();   // id (string) -> { id, name, sku, price, quantity }
let lines = [];             // { productId, quantity } per product in the sale; always changed through setLines()
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
    // Ready for the next item: put the cursor in the search box unless the cashier is using something else
    if (!isLocked() && !formEl.hidden && (document.activeElement === document.body || document.activeElement === null)) {
        document.getElementById("quick-search").focus();
    }
}

/** Replaces the cart and redraws it, so the rows on screen always match `lines`. */
function setLines(newLines) {
    lines = newLines;
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

/** Rebuilds the cart rows from `lines` and `products`. */
function renderLines() {
    linesEl.replaceChildren(...lines.map(buildRow));
    document.getElementById("cart-empty").hidden = lines.length > 0;
    updateLocks();
}

function iconButton(label, text, className) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = className;
    btn.setAttribute("aria-label", label);
    btn.textContent = text;
    return btn;
}

/** One product in the sale: name, price each, − quantity +, line total, remove. */
function buildRow(line, index) {
    const p = products.get(line.productId);
    const name = p ? p.name : "Unknown product";
    const li = document.createElement("li");

    const info = document.createElement("span");
    info.className = "cart-item";
    const title = document.createElement("strong");
    title.textContent = name;
    const detail = document.createElement("span");
    detail.className = "muted small";
    detail.textContent = p ? (p.sku ? p.sku + " · " : "") + formatCents(toCents(p.price)) + " each" : "";
    const problem = document.createElement("small");
    problem.className = "field-error";
    info.append(title, detail, problem);

    const qty = document.createElement("input");
    qty.type = "number";
    qty.min = "1";
    qty.step = "1";
    qty.inputMode = "numeric";
    qty.className = "qty";
    qty.setAttribute("aria-label", "Quantity of " + name);
    qty.value = line.quantity;
    qty.addEventListener("input", () => { line.quantity = qty.value; refresh(); });

    // − and + change the quantity in place (no redraw), so keyboard focus stays on the button
    const step = delta => {
        const current = isWholeNumber(line.quantity) ? Number(line.quantity) : 1;
        line.quantity = String(Math.max(1, current + delta));
        qty.value = line.quantity;
        refresh();
    };
    const minus = iconButton("One less " + name, "−", "step");
    minus.addEventListener("click", () => step(-1));
    const plus = iconButton("One more " + name, "+", "step");
    plus.addEventListener("click", () => step(1));
    const stepper = document.createElement("span");
    stepper.className = "stepper";
    stepper.append(minus, qty, plus);

    const total = document.createElement("strong");
    total.className = "cart-line-total";

    const remove = iconButton("Remove " + name, "✕", "link danger cart-remove");
    remove.addEventListener("click", () => setLines(lines.filter((_, i) => i !== index)));

    li.append(info, stepper, total, remove);
    line.cells = { total, problem, minus, plus };
    return li;
}

/**
 * Updates line totals, the sale total, per-line problems and the product cards.
 * Returns true if the sale looks valid and can be submitted.
 */
function refresh() {
    // How many of each product are in the sale (to check against stock)
    const wanted = new Map();
    for (const line of lines) {
        if (line.productId && isWholeNumber(line.quantity)) {
            wanted.set(line.productId, (wanted.get(line.productId) || 0) + Number(line.quantity));
        }
    }

    let totalCents = 0;
    let units = 0;
    let valid = lines.length > 0;
    for (const line of lines) {
        const p = products.get(line.productId);
        let problem = "";
        if (!p) {
            problem = "This product no longer exists. Remove it.";
        } else if (!isWholeNumber(line.quantity)) {
            problem = "Enter a whole number of at least 1.";
        } else if (wanted.get(line.productId) > p.quantity) {
            problem = `Only ${p.quantity} in stock.`;
        }

        if (p && isWholeNumber(line.quantity)) {
            const lineCents = toCents(p.price) * Number(line.quantity);
            line.cells.total.textContent = formatCents(lineCents);
            totalCents += lineCents;
            units += Number(line.quantity);
        } else {
            line.cells.total.textContent = "—";
        }
        line.cells.problem.textContent = problem;
        line.cells.minus.disabled = !isWholeNumber(line.quantity) || Number(line.quantity) <= 1;
        line.cells.plus.disabled = !p || (wanted.get(line.productId) || 0) >= p.quantity;
        if (problem) valid = false;
    }

    document.getElementById("total").textContent = formatCents(totalCents);
    document.getElementById("cart-count").textContent = lines.length
        ? `${lines.length} item${lines.length === 1 ? "" : "s"} · ${units} unit${units === 1 ? "" : "s"}`
        : "";
    completeBtn.disabled = isLocked() || !valid;
    renderProductGrid(wanted);
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
        setLines([]);   // show the cleared cart right away (still locked)
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
    setLines([]);
    showMessage("Sale discarded. Check the stock levels below before entering it again.", "warning");
    await reloadProducts();
}

// ---- Story 9: find products by name or SKU; product cards ----

const quickInput = document.getElementById("quick-search");
const gridEl = document.getElementById("product-grid");

/** Products whose name or SKU contains the text (all products when it's empty); exact SKU matches first. */
function findProducts(text) {
    const q = text.trim().toLowerCase();
    const matches = [...products.values()].filter(p =>
        !q || p.name.toLowerCase().includes(q) || (p.sku && p.sku.toLowerCase().includes(q)));
    matches.sort((a, b) => {
        const exactA = q && a.sku && a.sku.toLowerCase() === q ? 0 : 1;
        const exactB = q && b.sku && b.sku.toLowerCase() === q ? 0 : 1;
        return exactA - exactB || a.name.localeCompare(b.name);
    });
    return matches;
}

/** How many more of this product can still go in the sale. */
function stockLeft(p, wanted) {
    return p.quantity - (wanted.get(String(p.id)) || 0);
}

/** Adds one of the product: bumps its line if it's already in the sale, otherwise adds a line. */
function addProductToSale(productId) {
    const id = String(productId);
    const existing = lines.find(l => l.productId === id);
    if (existing) {
        existing.quantity = String((isWholeNumber(existing.quantity) ? Number(existing.quantity) : 0) + 1);
    } else {
        lines.push({ productId: id, quantity: "1" });
    }
    quickInput.value = "";
    setLines([...lines]);
    quickInput.focus();
}

/** One card per matching product: name, SKU, price and stock. Sold-out cards can't be clicked. */
function renderProductGrid(wanted) {
    const matches = findProducts(quickInput.value);
    document.getElementById("no-matches").hidden = matches.length > 0 || products.size === 0;
    gridEl.replaceChildren(...matches.map(p => {
        const left = stockLeft(p, wanted);
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "product-card";
        btn.disabled = left <= 0;

        const name = document.createElement("strong");
        name.textContent = p.name;
        const sku = document.createElement("span");
        sku.className = "muted small";
        sku.textContent = p.sku || "No SKU";
        const bottom = document.createElement("span");
        bottom.className = "product-card-bottom";
        const price = document.createElement("strong");
        price.textContent = formatCents(toCents(p.price));
        const stock = document.createElement("span");
        if (p.quantity <= 0) {
            stock.className = "badge danger";
            stock.textContent = "Out of stock";
        } else if (left <= 0) {
            stock.className = "badge warning";
            stock.textContent = "All in sale";
        } else {
            stock.className = "muted small";
            stock.textContent = `${left} in stock`;
        }
        bottom.append(price, stock);
        btn.append(name, sku, bottom);
        btn.setAttribute("aria-label", `Add ${p.name}${p.sku ? " (" + p.sku + ")" : ""}, ${formatCents(toCents(p.price))}, ` +
            (p.quantity <= 0 ? "out of stock" : `${left} in stock`));
        btn.addEventListener("click", () => addProductToSale(p.id));
        return btn;
    }));
}

quickInput.addEventListener("input", () => refresh());
quickInput.addEventListener("keydown", e => {
    if (e.key === "Escape") { quickInput.value = ""; refresh(); return; }
    if (e.key !== "Enter") return;
    e.preventDefault();
    const wanted = new Map();
    for (const l of lines) {
        if (isWholeNumber(l.quantity)) wanted.set(l.productId, (wanted.get(l.productId) || 0) + Number(l.quantity));
    }
    const q = quickInput.value.trim().toLowerCase();
    if (!q) return;
    const matches = findProducts(q).filter(p => stockLeft(p, wanted) > 0);
    const exact = matches.find(p => p.sku && p.sku.toLowerCase() === q);
    if (exact) addProductToSale(exact.id);
    else if (matches.length === 1) addProductToSale(matches[0].id);
    // Several matches: the cards show them, so the cashier can click the right one
});

document.getElementById("clear-sale").addEventListener("click", () => {
    if (lines.length && !confirm("Remove every item from this sale?")) return;
    setLines([]);
    quickInput.focus();
});
completeBtn.addEventListener("click", completeSale);
checkBtn.addEventListener("click", checkAgain);
discardBtn.addEventListener("click", discardPendingSale);
reloadBtn.addEventListener("click", reloadProducts);

loadCurrentUser().then(me => {
    document.getElementById("who").textContent = me.username + " (" + me.role + ")";
});
setLines([]);
reloadProducts();
