// Staff management page (Sprint 3): list, add and remove Staff accounts. Admin only.

const formEl = document.getElementById("staff-form");
const messageEl = document.getElementById("message");
const usernameEl = document.getElementById("new-username");
const passwordEl = document.getElementById("new-password");

function showMessage(text, type) {
    messageEl.textContent = text;
    messageEl.className = "alert " + type;
    messageEl.hidden = false;
}

async function loadStaff() {
    try {
        render(await api("/api/users"));
    } catch (e) {
        showMessage("Couldn't load staff: " + e.message, "error");
    } finally {
        document.getElementById("loading").hidden = true;
    }
}

function render(list) {
    document.getElementById("no-staff").hidden = list.length > 0;
    document.getElementById("staff-table").hidden = list.length === 0;
    document.getElementById("staff-rows").replaceChildren(...list.map(buildRow));
}

function buildRow(s) {
    const tr = document.createElement("tr");
    const name = document.createElement("td");
    name.textContent = s.username;

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "link danger";
    remove.textContent = "Remove";
    remove.addEventListener("click", () => removeStaff(s, remove));
    const actions = document.createElement("td");
    actions.className = "row-actions";
    actions.append(remove);

    tr.append(name, actions);
    return tr;
}

async function removeStaff(s, button) {
    if (!confirm(`Remove staff account "${s.username}"? They will be signed out and can't log in again. Their past sales are kept.`)) return;
    button.disabled = true;
    try {
        await api("/api/users/" + s.id, { method: "DELETE" });
        showMessage(`Removed ${s.username}.`, "success");
    } catch (e) {
        showMessage(e.message, "error");
    }
    await loadStaff();
}

formEl.addEventListener("submit", async event => {
    event.preventDefault();
    const button = formEl.querySelector("button");
    if (button.disabled) return;
    button.disabled = true;
    try {
        const created = await api("/api/users", {
            method: "POST",
            body: { username: usernameEl.value.trim(), password: passwordEl.value },
        });
        showMessage(`Added staff account ${created.username}.`, "success");
        formEl.reset();
        await loadStaff();
    } catch (e) {
        showMessage(e.message, "error");
    } finally {
        button.disabled = false;
    }
});

loadCurrentUser().then(me => {
    document.getElementById("who").textContent = me.username + " (" + me.role + ")";
});
loadStaff();
