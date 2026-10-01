// Shared helpers for every page. Include with: <script src="/js/app.js"></script>

/**
 * Call the backend and get JSON back.
 * If the session expired (401), send the user to the login page.
 * Usage: const products = await api("/api/products");
 *        await api("/api/products", { method: "POST", body: { name: "Shirt", price: 20 } });
 */
async function api(url, options = {}) {
    const opts = { ...options, headers: { ...(options.headers || {}) } };
    if (opts.body && typeof opts.body !== "string") {
        opts.body = JSON.stringify(opts.body);
        opts.headers["Content-Type"] = "application/json";
    }
    const res = await fetch(url, opts);
    if (res.status === 401) {
        location.href = "/login.html";
        throw new Error("Not logged in");
    }
    if (!res.ok) {
        throw new Error(`${res.status} ${res.statusText}`);
    }
    return res.status === 204 ? null : res.json();
}

/**
 * Get the logged-in user ({ username, role }) and hide Admin-only elements for Staff.
 * Mark any Admin-only element with the attribute data-admin-only.
 * (Hiding is just for looks; the real protection is in SecurityConfig.java.)
 */
async function loadCurrentUser() {
    const me = await api("/api/me");
    if (me.role !== "ADMIN") {
        document.querySelectorAll("[data-admin-only]").forEach(el => el.remove());
    }
    return me;
}
