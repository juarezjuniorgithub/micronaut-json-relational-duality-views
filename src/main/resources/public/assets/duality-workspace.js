(() => {
    const apiBase = "/duality";
    const byId = (id) => document.querySelector(id);
    const output = byId("#response-output");
    const responseMeta = byId("#response-meta");
    const lastRequest = byId("#last-request");
    const orderId = byId("#order-id");
    const draftId = byId("#draft-id");
    const editor = byId("#document-editor");
    const orderList = byId("#order-list");
    const documentCount = byId("#document-count");

    function showResult(label, result, status = 200) {
        if (!output || !responseMeta) return;
        responseMeta.textContent = `${label} | HTTP ${status}`;
        output.textContent = typeof result === "string" ? result : JSON.stringify(result, null, 2);
        if (lastRequest) lastRequest.textContent = label;
        output.focus({ preventScroll: true });
    }

    async function request(label, path, options = {}) {
        const response = await fetch(`${apiBase}${path}`, {
            headers: { Accept: "application/json", ...(options.headers || {}) },
            ...options
        });
        const text = await response.text();
        let result = text;
        try { result = text ? JSON.parse(text) : { message: "No content" }; } catch (_) { /* The DDL endpoint returns SQL text. */ }
        showResult(label, result, response.status);
        if (!response.ok) {
            const message = typeof result === "object" && result.error ? result.error : `Request failed with HTTP ${response.status}`;
            throw new Error(message);
        }
        return result;
    }

    function requiredPositiveId(input, label) {
        const id = Number.parseInt(input?.value, 10);
        if (!Number.isInteger(id) || id < 1) throw new Error(`Enter a positive ${label}.`);
        return id;
    }

    function selectedOrderId() {
        return requiredPositiveId(orderId, "order ID");
    }

    function describeSelection(selectId, descriptionId) {
        const select = byId(selectId);
        const description = byId(descriptionId);
        if (!select || !description) return;
        const updateDescription = () => { description.textContent = select.selectedOptions[0].dataset.description; };
        select.addEventListener("change", updateDescription);
        updateDescription();
    }

    function renderOrders(orders) {
        if (documentCount) documentCount.textContent = orders.length;
        if (!orderList) return;
        orderList.replaceChildren();
        if (!orders.length) {
            orderList.innerHTML = '<p class="empty-state">No orders match this request.</p>';
            return;
        }
        orders.forEach((order) => {
            const row = document.createElement("button");
            row.type = "button";
            row.className = "record";
            row.innerHTML = '<span class="record-top"><span class="record-title"></span><span class="status-chip"></span></span><span class="record-detail"></span>';
            row.querySelector(".record-title").textContent = order.orderNumber;
            row.querySelector(".status-chip").textContent = order.status;
            row.querySelector(".record-detail").textContent = `${order.customer?.name ?? "No customer"} | ${order.lines?.length ?? 0} lines`;
            row.addEventListener("click", () => selectOrder(order));
            orderList.append(row);
        });
    }

    async function selectOrder(order) {
        if (!orderId) return;
        orderId.value = order._id;
        await loadOrder();
    }

    async function refreshOrders() {
        const status = byId("#status-filter")?.value;
        const orders = await request("Order documents", `/orders${status ? `?status=${encodeURIComponent(status)}` : ""}`);
        renderOrders(orders);
        return orders;
    }

    async function loadOrder() {
        const id = selectedOrderId();
        const order = await request("Mapped document", `/orders/${id}`);
        const selectedStatus = byId("#selected-status");
        if (selectedStatus) selectedStatus.textContent = order.status || "Loaded";
        return order;
    }

    async function searchDocuments() {
        const form = byId("#document-search-form");
        const parameters = new URLSearchParams();
        for (const [name, value] of new FormData(form).entries()) {
            const normalized = String(value).trim();
            if (normalized) parameters.set(name, normalized);
        }
        if (!parameters.size) throw new Error("Choose at least one JSON document search criterion.");
        const result = await request("Duality document search", `/search?${parameters.toString()}`);
        renderOrders(result.orders);
    }

    async function bootstrap() {
        const result = await request("Bootstrap schema", "/bootstrap?recreate=true", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: "{}"
        });
        const connectionValue = byId("#connection-value");
        if (connectionValue) connectionValue.textContent = result.databaseProduct || "Connected";
        if (documentCount) documentCount.textContent = result.rowCounts?.JDV_ORDER_DV ?? "2";
    }

    async function writeDocument() {
        let payload;
        try { payload = JSON.parse(editor.value); } catch (_) { throw new Error("The order document must contain valid JSON."); }
        const id = requiredPositiveId(draftId, "draft order ID");
        payload._id = id;
        const method = byId("#write-method").value;
        await request(method === "PUT" ? "Replace document" : "Create document", method === "PUT" ? `/orders/${id}` : "/orders", {
            method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
    }

    async function loadDraft() {
        const id = requiredPositiveId(draftId, "draft order ID");
        const document = await request("Load draft", `/orders/${id}`);
        editor.value = JSON.stringify(document, null, 2);
    }

    async function deleteDraft() {
        const id = requiredPositiveId(draftId, "draft order ID");
        await request("Delete document", `/orders/${id}`, { method: "DELETE" });
    }

    async function runAction(action) {
        switch (action) {
            case "overview": {
                const overview = await request("Connection Check", "");
                const connectionValue = byId("#connection-value");
                if (connectionValue) connectionValue.textContent = overview.database ? "Connected" : "Unavailable";
                break;
            }
            case "bootstrap": await bootstrap(); break;
            case "order": await loadOrder(); break;
            case "raw-json": await request("Raw Oracle JSON", `/orders/${selectedOrderId()}/json`); break;
            case "relational": await request("Relational snapshot", `/orders/${selectedOrderId()}/relational`); break;
            case "open-summaries": await request("Open summaries", "/summaries/open"); break;
            case "ddl": await request("Duality view DDL", "/ddl"); break;
            case "load-draft": await loadDraft(); break;
            case "write-document": await writeDocument(); break;
            case "delete": await deleteDraft(); break;
            case "etag-conflict": await request("ETAG conflict check", `/orders/${selectedOrderId()}/etag-conflict`, {
                method: "POST", headers: { "Content-Type": "application/json" }, body: "{}"
            }); break;
            case "clear-output": showResult("Ready", {}); break;
            default: break;
        }
    }

    document.addEventListener("click", (event) => {
        const trigger = event.target.closest("[data-action]");
        if (!trigger) return;
        runAction(trigger.dataset.action).catch((error) => showResult("Request error", { error: error.message }, 0));
    });

    const filterForm = byId("#filter-form");
    if (filterForm) filterForm.addEventListener("submit", (event) => {
        event.preventDefault();
        refreshOrders().catch((error) => showResult("Request error", { error: error.message }, 0));
    });

    const searchForm = byId("#document-search-form");
    if (searchForm) searchForm.addEventListener("submit", (event) => {
        event.preventDefault();
        searchDocuments().catch((error) => showResult("Request error", { error: error.message }, 0));
    });

    const statusForm = byId("#status-form");
    if (statusForm) statusForm.addEventListener("submit", (event) => {
        event.preventDefault();
        const status = byId("#status-value").value;
        request("Patch status", `/orders/${selectedOrderId()}/status`, {
            method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status })
        }).then((order) => {
            const selectedStatus = byId("#selected-status");
            if (selectedStatus) selectedStatus.textContent = order.status;
        }).catch((error) => showResult("Request error", { error: error.message }, 0));
    });

    describeSelection("#status-filter", "#filter-description");
    describeSelection("#status-value", "#status-description");
    describeSelection("#write-method", "#write-description");
})();
