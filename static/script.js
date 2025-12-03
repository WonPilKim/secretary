function sendCommand() {
    const text = document.getElementById("inputText").value;
    if (!text) return;

    addBubble(text, "user");
    document.getElementById("inputText").value = "";

    fetch("/command", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: text })
    })
    .then(res => res.json())
    .then(data => {
        if (data.redirect) {
            window.location.href = data.redirect;   // ⭐ 페이지 이동
            return;
        }

        // 기존 메시지 출력
        addBubble(data.response, "bot");
    });
}

function addBubble(msg, type) {
    const chat = document.getElementById("chat");
    const div = document.createElement("div");
    div.className = `bubble ${type}`;
    div.innerText = msg;
    chat.appendChild(div);
    chat.scrollTop = chat.scrollHeight;
}