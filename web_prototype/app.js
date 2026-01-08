/**
 * MqttPanelCraft Web Prototype
 * Implements "ProjectViewActivity" logic in JS.
 * Update: Grid Units & Type-Based IDs
 */

const GRID_SIZE = 20; // 1 Grid Unit = 20px

// --- History System (Undo) ---
const historyStack = [];
const MAX_HISTORY = 20;

function saveSnapshot() {
    // Deep copy components array
    const snapshot = JSON.parse(JSON.stringify(state.components));
    historyStack.push(snapshot);
    if (historyStack.length > MAX_HISTORY) historyStack.shift();
    console.log("Snapshot saved. Stack:", historyStack.length);
}

function performUndo() {
    if (historyStack.length === 0) return;
    const snapshot = historyStack.pop();
    state.components = snapshot;
    state.selectedId = null;

    // Clear Canvas
    canvas.innerHTML = '';
    // Re-add Overlay
    const overlay = document.createElement('div');
    overlay.id = 'guideOverlay';
    overlay.className = 'guide-overlay';
    overlay.innerHTML = `
        <div id="lineH" class="align-line horizontal hidden"></div>
        <div id="lineV" class="align-line vertical hidden"></div>
    `;
    canvas.appendChild(overlay);
    // Re-add Trash
    const trash = document.createElement('div');
    trash.id = 'trashBin';
    trash.className = 'trash-bin hidden';
    trash.innerHTML = '<div class="trash-icon">🗑️</div>';
    canvas.appendChild(trash);

    // Re-render all
    state.components.forEach(c => renderComponent(c));
    console.log("Undo performed.");
}

// --- State ---
const state = {
    components: [], // Array of { id, type, x, y, width, height, label, topic, props, bgColor }
    // x, y, width, height are now in GRID UNITS
    selectedId: null,
    isEditMode: true,
    showGrid: true,
    project: {
        id: "web_proto_001",
        name: "Web Prototype",
        broker: "broker.emqx.io",
        port: 8083, // WebSocket Port
        baseTopic: "test/mqttpanelcraft"
    },
    mqttClient: null
};

// --- DOM Elements ---
const canvas = document.getElementById('editorCanvas');
const propertiesPanel = document.getElementById('propertiesPanel');
const trashBin = document.getElementById('trashBin');
const lineH = document.getElementById('lineH');
const lineV = document.getElementById('lineV');

const inputs = {
    id: document.getElementById('propId'),
    label: document.getElementById('propLabel'),
    topic: document.getElementById('propTopic'),
    color: document.getElementById('propColor')
};

// --- Initialization ---
function init() {
    setupSidebarDrag();
    setupCanvasDrop();
    setupCanvasInteractions(); // Moving & Resizing items
    setupPropertiesPanel();
    setupToolbar();

    // Auto Connect MQTT
    connectMqtt();
}

// --- Smart ID Logic (Per Type) ---
function findNextId(type) {
    // Filter by type first
    const existing = state.components
        .filter(c => c.type === type)
        .map(c => c.typeId) // specific type ID
        .sort((a, b) => a - b);

    let next = 1;
    for (const id of existing) {
        if (id === next) next++;
        else if (id > next) return next; // Found gap
    }
    return next;
}

// --- MQTT System ---
function connectMqtt() {
    const clientId = "web_client_" + Math.random().toString(16).substr(2, 8);
    const client = new Paho.MQTT.Client(state.project.broker, state.project.port, clientId);

    client.onConnectionLost = onConnectionLost;
    client.onMessageArrived = onMessageArrived;

    const options = {
        useSSL: false,
        onSuccess: onConnect,
        onFailure: (e) => {
            console.log("MQTT Failed", e);
            updateStatus("Failed: " + e.errorMessage, false);
        }
    };

    console.log("Connecting to MQTT...", state.project.broker);
    client.connect(options);
    state.mqttClient = client;
}

function onConnect() {
    console.log("MQTT Connected");
    updateStatus("Connected", true);
    const topic = `${state.project.baseTopic}/#`;
    state.mqttClient.subscribe(topic);
}

function onConnectionLost(responseObject) {
    if (responseObject.errorCode !== 0) {
        updateStatus("Disconnected", false);
    }
}

function onMessageArrived(message) {
    state.components.forEach(comp => {
        if (message.destinationName.includes(comp.topic)) {
            updateComponentVisual(comp.id, message.payloadString);
        }
    });
}

function publish(topicSignal, value) {
    if (!state.mqttClient || !state.mqttClient.isConnected()) return;
    const message = new Paho.MQTT.Message(String(value));
    message.destinationName = topicSignal;
    state.mqttClient.send(message);
}

function updateStatus(msg, isConnected) {
    const el = document.getElementById('mqttStatus');
    el.innerText = msg;
    el.className = isConnected ? "connected" : "disconnected";
}

// --- Component System ---

function createComponent(type, px, py) {
    saveSnapshot(); // History Point

    // Convert Pixel drop input to Closest Grid Unit
    const gridX = Math.max(0, Math.round(px / GRID_SIZE));
    const gridY = Math.max(0, Math.round(py / GRID_SIZE));

    const typeId = findNextId(type); // Smart ID per type

    const newComp = {
        id: Date.now(), // Unique system ID
        typeId: typeId, // User visible ID (Switch 1, Switch 2)
        type: type,
        x: gridX,
        y: gridY,
        width: 5,  // Defaults in Units (5 * 20 = 100px)
        height: 5,
        label: `${type} ${typeId}`, // Auto Label
        topic: `${state.project.baseTopic}/${type.toLowerCase()}/${typeId}`,
        props: { color: "#ffffff" }
    };

    if (type === "BUTTON") { newComp.width = 6; newComp.height = 3; } // 120x60
    if (type === "SWITCH") { newComp.width = 5; newComp.height = 5; } // 100x100
    if (type === "TEXT") { newComp.width = 8; newComp.height = 3; } // 160x60

    state.components.push(newComp);
    renderComponent(newComp);
    selectComponent(newComp.id);
}

function renderComponent(comp) {
    let el = document.getElementById('comp_' + comp.id);
    if (!el) {
        el = document.createElement('div');
        el.id = 'comp_' + comp.id;
        el.className = `component comp-${comp.type.toLowerCase()}`;
        el.dataset.id = comp.id;
        // Styles are inline for position
        el.innerHTML = `
            <header>${comp.label}</header>
            <div class="content"></div>
            <div class="resize-handle"></div>
        `;
        canvas.appendChild(el);

        const content = el.querySelector('.content');

        // Type Specific Content
        if (comp.type === "SWITCH") {
            const btn = document.createElement('button');
            btn.innerText = "OFF";
            btn.style.width = "80%";
            btn.style.height = "60%";
            btn.onclick = (e) => {
                if (state.isEditMode) return;
                const next = btn.innerText === "OFF" ? "ON" : "OFF";
                publish(comp.topic, next);
                btn.innerText = next;
                e.stopPropagation();
            };
            content.appendChild(btn);
        } else if (comp.type === "BUTTON") {
            const btn = document.createElement('button');
            btn.innerText = "PUSH";
            btn.style.width = "90%";
            btn.style.height = "80%";
            btn.onmousedown = (e) => { if (!state.isEditMode) publish(comp.topic, "1"); e.stopPropagation(); };
            btn.onmouseup = (e) => { if (!state.isEditMode) publish(comp.topic, "0"); e.stopPropagation(); };
            content.appendChild(btn);
        } else if (comp.type === "TEXT") {
            const span = document.createElement('span');
            span.innerText = "Value: -";
            content.appendChild(span);
        }
    }

    // Update Position & Style based on GRID UNITS
    el.style.left = (comp.x * GRID_SIZE) + 'px';
    el.style.top = (comp.y * GRID_SIZE) + 'px';
    el.style.width = (comp.width * GRID_SIZE) + 'px';
    el.style.height = (comp.height * GRID_SIZE) + 'px';

    el.querySelector('header').innerText = comp.label; // Update Label
    el.style.backgroundColor = comp.props.color || "#FFF";

    // Update Selection
    if (state.selectedId === comp.id) el.classList.add('selected');
    else el.classList.remove('selected');
}

function updateComponentVisual(id, payload) {
    const el = document.getElementById('comp_' + id);
    if (!el) return;
    const content = el.querySelector('.content');

    const comp = state.components.find(c => c.id == id);
    if (comp.type === "SWITCH") {
        const btn = content.querySelector('button');
        if (btn) btn.innerText = (payload == "1" || payload == "ON") ? "ON" : "OFF";
    } else if (comp.type === "TEXT") {
        const span = content.querySelector('span');
        if (span) span.innerText = payload;
    }
}

function selectComponent(id) {
    state.selectedId = id;
    state.components.forEach(c => renderComponent(c));

    const comp = state.components.find(c => c.id == id);
    if (comp) {
        propertiesPanel.classList.remove('hidden');
        inputs.id.value = comp.type + " " + comp.typeId; // Show Friendly ID
        inputs.label.value = comp.label;
        inputs.topic.value = comp.topic;
        inputs.color.value = comp.props.color || "#ffffff";
    } else {
        propertiesPanel.classList.add('hidden');
    }
}

// --- Interactions (Drag, Align, Resize, Trash) ---
function setupSidebarDrag() {
    const items = document.querySelectorAll('.draggable-item');
    items.forEach(item => {
        item.addEventListener('dragstart', (e) => {
            e.dataTransfer.setData("type", item.dataset.type);
        });
    });
}

function setupCanvasDrop() {
    canvas.addEventListener('dragover', (e) => {
        e.preventDefault();
        if (state.isEditMode) e.dataTransfer.dropEffect = "copy";
    });

    canvas.addEventListener('drop', (e) => {
        e.preventDefault();
        if (!state.isEditMode) return;

        const type = e.dataTransfer.getData("type");
        if (type) {
            const rect = canvas.getBoundingClientRect();
            // Calculate drop position in pixels first
            const dropX = e.clientX - rect.left - 50;
            const dropY = e.clientY - rect.top - 30;

            createComponent(type, Math.max(0, dropX), Math.max(0, dropY));
        }
    });
}

function setupCanvasInteractions() {
    // Deselect click
    canvas.addEventListener('mousedown', (e) => {
        if (e.target === canvas) selectComponent(null);
    });

    canvas.addEventListener('mousedown', (e) => {
        if (!state.isEditMode) return;

        // Check Resize first
        if (e.target.classList.contains('resize-handle')) {
            handleResize(e);
            return;
        }

        const compEl = e.target.closest('.component');
        if (!compEl) return;

        handleDrag(e, compEl);
    });
}

function handleResize(e) {
    e.stopPropagation();
    saveSnapshot(); // History Point

    const compEl = e.target.closest('.component');
    const id = parseInt(compEl.dataset.id);
    const comp = state.components.find(c => c.id === id);

    const startX = e.clientX;
    const startY = e.clientY;

    // Store initial dimensions in Grid Units
    const startW = comp.width;
    const startH = comp.height;

    function onMouseMove(moveE) {
        const dx = moveE.clientX - startX;
        const dy = moveE.clientY - startY;

        // precise grid delta
        const dGridW = Math.round(dx / GRID_SIZE);
        const dGridH = Math.round(dy / GRID_SIZE);

        comp.width = Math.max(2, startW + dGridW); // Min 2 units
        comp.height = Math.max(2, startH + dGridH);
        renderComponent(comp);
    }

    function onMouseUp() {
        window.removeEventListener('mousemove', onMouseMove);
        window.removeEventListener('mouseup', onMouseUp);
    }

    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
}

function handleDrag(e, compEl) {
    saveSnapshot(); // History Point for Move

    const id = parseInt(compEl.dataset.id);
    const comp = state.components.find(c => c.id === id);
    selectComponent(id);

    // Show Trash
    trashBin.classList.remove('hidden');

    const startX = e.clientX;
    const startY = e.clientY;

    // Initial Pos in Grid Units
    const startCompX = comp.x;
    const startCompY = comp.y;

    function onMouseMove(moveE) {
        const dx = moveE.clientX - startX;
        const dy = moveE.clientY - startY;

        // Calculate raw pixel position for smooth dragging feel (optional)
        // OR calculate new Grid Position directly

        const dGridX = Math.round(dx / GRID_SIZE);
        const dGridY = Math.round(dy / GRID_SIZE);

        let newX = startCompX + dGridX;
        let newY = startCompY + dGridY;

        // --- Alignment & Grid ---
        lineH.classList.add('hidden');
        lineV.classList.add('hidden');

        // Check Alignment (in Grid Units now!)
        const myCx = newX + comp.width / 2; // This can be .5
        const myCy = newY + comp.height / 2;
        const threshold = 0.5; // Grid Unit threshold

        state.components.forEach(other => {
            if (other.id === id) return;
            const otherCx = other.x + other.width / 2;
            const otherCy = other.y + other.height / 2;

            // X Align (Centers)
            if (Math.abs(myCx - otherCx) < threshold) {
                // If aligned by center, we might need to shift X so centers match
                // newX + w/2 = otherCx => newX = otherCx - w/2
                // We keep it integer grid if possible? Or allow half-grid?
                // Per requirement 1: "Unit is grid cell". Let's stick to INTEGERS largely.
                // Center alignment might force half-grid if widths differ by odd/even.
                // But let's show guide anyway.

                // For simplicity in this requirement, we snap EDGES or CELLS.
                // Let's stick to Grid Snap mostly.

                lineV.style.left = (otherCx * GRID_SIZE) + 'px';
                lineV.classList.remove('hidden');
            }
            // X Align (Left Edge)
            else if (Math.abs(newX - other.x) < threshold) {
                newX = other.x;
                lineV.style.left = (other.x * GRID_SIZE) + 'px';
                lineV.classList.remove('hidden');
            }
            // X Align (Right Edge to Left Edge)
            else if (Math.abs((newX + comp.width) - other.x) < threshold) {
                newX = other.x - comp.width;
                lineV.style.left = (other.x * GRID_SIZE) + 'px';
                lineV.classList.remove('hidden');
            }

            // Y Align (Top Edge)
            if (Math.abs(newY - other.y) < threshold) {
                newY = other.y;
                lineH.style.top = (other.y * GRID_SIZE) + 'px';
                lineH.classList.remove('hidden');
            }
        });

        comp.x = Math.max(0, newX);
        comp.y = Math.max(0, newY);
        renderComponent(comp);

        // --- Trash Bin Magnetic ---
        const trashRect = trashBin.getBoundingClientRect();
        const mouseX = moveE.clientX;
        const mouseY = moveE.clientY;
        const dist = Math.hypot(mouseX - (trashRect.left + trashRect.width / 2), mouseY - (trashRect.top + trashRect.height / 2));

        if (dist < 80) {
            trashBin.classList.add('active');
            compEl.style.opacity = '0.3';
        } else {
            trashBin.classList.remove('active');
            compEl.style.opacity = '1.0';
        }
    }

    function onMouseUp(upE) {
        window.removeEventListener('mousemove', onMouseMove);
        window.removeEventListener('mouseup', onMouseUp);

        // Hide guides
        lineH.classList.add('hidden');
        lineV.classList.add('hidden');

        // Trash Delete
        if (trashBin.classList.contains('active')) {
            state.components = state.components.filter(c => c.id !== id);
            compEl.remove();
            selectComponent(null);
        }

        trashBin.classList.add('hidden');
        trashBin.classList.remove('active');
        compEl.style.opacity = '1.0';
    }

    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
}

// --- Properties Logic ---
function setupPropertiesPanel() {
    const update = () => {
        if (!state.selectedId) return;
        saveSnapshot(); // History Point
        const comp = state.components.find(c => c.id === state.selectedId);
        if (comp) {
            comp.label = inputs.label.value;
            comp.topic = inputs.topic.value;
            comp.props.color = inputs.color.value;
            renderComponent(comp);
        }
    };

    // ID is Read-only friendly string now
    inputs.label.addEventListener('input', update);
    inputs.topic.addEventListener('change', update);
    inputs.color.addEventListener('input', update);

    document.getElementById('btnCloseProps').addEventListener('click', () => {
        selectComponent(null);
    });
}

function setupToolbar() {
    document.getElementById('btnToggleMode').addEventListener('click', (e) => {
        state.isEditMode = !state.isEditMode;
        e.target.innerText = state.isEditMode ? "Switch to RUN" : "Switch to EDIT";
        e.target.className = state.isEditMode ? "btn primary" : "btn";

        if (!state.isEditMode) {
            selectComponent(null);
            trashBin.classList.add('hidden');
        }
    });

    document.getElementById('btnUndo').addEventListener('click', () => {
        performUndo();
    });

    document.getElementById('btnToggleGrid').addEventListener('click', (e) => {
        state.showGrid = !state.showGrid;
        const el = document.getElementById('editorCanvas');
        if (state.showGrid) {
            el.classList.remove('no-grid');
            e.target.innerText = "Grid: ON";
            e.target.classList.add('active');
        } else {
            el.classList.add('no-grid');
            e.target.innerText = "Grid: OFF";
            e.target.classList.remove('active');
        }
    });

    document.getElementById('btnExport').addEventListener('click', () => {
        const json = JSON.stringify(state.components, null, 2);
        console.log("Exported JSON (Grid Units):", json);
        alert("JSON Exported to Console");
    });
}

// Start
init();
