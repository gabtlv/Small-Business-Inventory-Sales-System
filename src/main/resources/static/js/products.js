// Products page: list (stories 2-3), search by name/SKU (9), category filter + category admin (8),
// low-stock badges (6). Admin-only controls are marked data-admin-only and removed for Staff by app.js.

let isAdmin = false;
let categories = [];
let searchTimer = null;
let requestNo = 0;

const rowsEl = document.getElementById("rows");
const searchEl = document.getElementById("search");
const filterEl = document.getElementById("category-filter");
const messageEl = document.getElementById("message");

function showMessage(text, type) {
    messageEl.textContent = text;
    messageEl.className = "alert " + type;
    messageEl.hidden = !text;
}

function money(value) {
    return "$" + Number(value).toFixed(2);
}

function cell(text, className) {
    const td = document.createElement("td");
    td.textContent = text;
    if (className) td.className = className;
    return td;
}

async function loadCategories() {
    categories = await api("/api/categories");
    const current = filterEl.value;
    filterEl.replaceChildren(new Option("All categories", ""),
        ...categories.map(c => new Option(c.name, String(c.id))));
    filterEl.value = categories.some(c => String(c.id) === current) ? current : "";
    renderCategoryList();
}

async function loadProducts() {
    const params = new URLSearchParams();
    if (searchEl.value.trim()) params.set("q", searchEl.value.trim());
    if (filterEl.value) params.set("categoryId", filterEl.value);
    const mine = ++requestNo;
    try {
        const list = await api("/api/products?" + params);
        if (mine !== requestNo) return;   // a newer search already started; ignore this one
        render(list);
    } catch (e) {
        if (mine === requestNo) showMessage("Couldn't load products: " + e.message, "error");
    }
}

function render(list) {
    rowsEl.replaceChildren(...list.map(buildRow));
    const filtered = searchEl.value.trim() || filterEl.value;
    document.getElementById("result-count").textContent =
        filtered ? `${list.length} matching product${list.length === 1 ? "" : "s"}` : "";
    const empty = document.getElementById("empty");
    empty.hidden = list.length > 0;
    empty.textContent = filtered ? "No products match your search." : "No products yet.";
}

function buildRow(p) {
    const tr = document.createElement("tr");
    if (p.lowStock) tr.className = "low-stock";
    tr.append(cell(p.name), cell(p.sku || "—"), cell(p.categoryName || "—"), cell(money(p.price), "num"));

    const qty = cell(String(p.quantity), "num");
    if (p.lowStock) {
        const badge = document.createElement("span");
        badge.className = "badge warning";
        badge.textContent = p.quantity === 0 ? "Out of stock" : "Low stock";
        badge.title = `Reorder at ${p.lowStockThreshold} or fewer`;
        qty.prepend(badge, " ");
    }
    tr.append(qty);

    if (isAdmin) {
        const actions = document.createElement("td");
        actions.className = "row-actions";
        const edit = document.createElement("a");
        edit.href = "/product-form.html?id=" + p.id;
        edit.textContent = "Edit";
        const del = document.createElement("button");
        del.type = "button";
        del.className = "link danger";
        del.textContent = "Delete";
        del.addEventListener("click", () => deleteProduct(p));
        actions.append(edit, del);
        tr.append(actions);
    }
    return tr;
}

async function deleteProduct(p) {
    if (!confirm(`Delete "${p.name}"? This can't be undone.`)) return;
    try {
        await api("/api/products/" + p.id, { method: "DELETE" });
        showMessage(`Deleted ${p.name}.`, "success");
        await Promise.all([loadProducts(), loadCategories()]);
    } catch (e) {
        showMessage(e.message, "error");
    }
}

function renderCategoryList() {
    const list = document.getElementById("category-list");
    if (!list) return;   // Staff: section removed
    if (categories.length === 0) {
        const li = document.createElement("li");
        li.className = "muted";
        li.textContent = "No categories yet.";
        list.replaceChildren(li);
        return;
    }
    list.replaceChildren(...categories.map(c => {
        const li = document.createElement("li");
        li.className = "chip";
        li.append(`${c.name} (${c.productCount})`);
        const del = document.createElement("button");
        del.type = "button";
        del.className = "link danger";
        del.textContent = "×";
        del.setAttribute("aria-label", "Delete category " + c.name);
        del.addEventListener("click", () => deleteCategory(c));
        li.append(del);
        return li;
    }));
}

async function deleteCategory(c) {
    const note = c.productCount ? ` Its ${c.productCount} product(s) will become uncategorized.` : "";
    if (!confirm(`Delete category "${c.name}"?${note}`)) return;
    try {
        await api("/api/categories/" + c.id, { method: "DELETE" });
        showMessage(`Deleted category ${c.name}.`, "success");
        await loadCategories();
        await loadProducts();
    } catch (e) {
        showMessage(e.message, "error");
    }
}

searchEl.addEventListener("input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(loadProducts, 250);   // wait for a pause in typing
});
filterEl.addEventListener("change", loadProducts);

loadCurrentUser().then(async me => {
    document.getElementById("who").textContent = me.username + " (" + me.role + ")";
    isAdmin = me.role === "ADMIN";

    const form = document.getElementById("category-form");
    if (form) {
        form.addEventListener("submit", async e => {
            e.preventDefault();
            const input = document.getElementById("category-name");
            try {
                await api("/api/categories", { method: "POST", body: { name: input.value } });
                showMessage(`Added category ${input.value.trim()}.`, "success");
                input.value = "";
                await loadCategories();
            } catch (err) {
                showMessage(err.message, "error");
            }
        });
    }

    const params = new URLSearchParams(location.search);
    if (params.get("saved")) showMessage("Product saved.", "success");
    if (params.get("q")) searchEl.value = params.get("q");

    try {
        await loadCategories();
    } catch (e) {
        showMessage("Couldn't load categories: " + e.message, "error");
    }
    await loadProducts();
});
