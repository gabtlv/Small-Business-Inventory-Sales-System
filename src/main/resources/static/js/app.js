// Shared helpers for every page. Include with: <script src="/js/app.js"></script>

/**
 * Adds the left sidebar (main menu) to every page that has the top bar, so no page's HTML has to
 * repeat it. Runs as soon as this file loads (it's included at the end of <body>).
 * Admin-only links carry data-admin-only, so loadCurrentUser() below removes them for Staff
 * (hiding is just for looks; SecurityConfig.java still blocks those pages).
 */
(function addSidebar() {
    if (!document.querySelector("header.topbar")) return;   // e.g. the login page

    const icon = paths =>
        `<svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" ` +
        `stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;
    const icons = {
        logo: '<path d="M21 8l-9-5-9 5 9 5 9-5z"/><path d="M3 8v8l9 5 9-5V8"/>',
        home: '<path d="M3 11l9-7 9 7"/><path d="M5 10v10h14V10"/>',
        products: '<path d="M21 8l-9-5-9 5 9 5 9-5z"/><path d="M3 8v8l9 5 9-5V8"/><path d="M12 13v8"/>',
        checkout: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2.5 4h2.3l2.3 11.2a1 1 0 0 0 1 .8h9.4a1 1 0 0 0 1-.8L20.5 8H6"/>',
        report: '<path d="M3 20h18"/><path d="M6 20v-7"/><path d="M11 20V5"/><path d="M16 20v-10"/>',
        dashboard: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
        staff: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8"/><path d="M21.5 20a6.5 6.5 0 0 0-4-6"/>',
        logout: '<path d="M15 4h4v16h-4"/><path d="M10 8l-4 4 4 4"/><path d="M6 12h10"/>'
    };
    // [page, label, icon, Admin only?, other pages that count as this one]
    const links = [
        ["/index.html", "Home", "home", false, ["/"]],
        ["/products.html", "Products", "products", false, ["/product-form.html"]],
        ["/checkout.html", "Checkout", "checkout", false, []],
        ["/reports.html", "Sales report", "report", true, []],
        ["/dashboard.html", "Dashboard", "dashboard", true, []],
        ["/staff.html", "Staff", "staff", true, []]
    ];
    const here = location.pathname;

    const nav = document.createElement("nav");
    nav.className = "sidebar";
    nav.setAttribute("aria-label", "Main menu");
    nav.innerHTML =
        `<a class="sidebar-brand" href="/index.html"><span class="sidebar-logo">${icon(icons.logo)}</span><span>Inventory System</span></a>` +
        `<span class="sidebar-heading">Main menu</span>` +
        links.map(([href, label, name, adminOnly, alsoActive]) => {
            const current = here === href || alsoActive.includes(here) ? ' aria-current="page"' : "";
            return `<a class="sidebar-link" href="${href}"${current}${adminOnly ? " data-admin-only" : ""}>${icon(icons[name])}<span>${label}</span></a>`;
        }).join("") +
        `<span class="sidebar-spacer"></span>` +
        `<form method="post" action="/logout"><button type="submit">${icon(icons.logout)}<span>Log out</span></button></form>`;

    document.body.prepend(nav);
    document.body.classList.add("has-sidebar");
})();

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
        // Use the server's {"error": "..."} message if it sent one
        const body = await res.json().catch(() => null);
        const err = new Error((body && body.error) || `${res.status} ${res.statusText}`);
        err.status = res.status;
        err.body = body;
        throw err;
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
