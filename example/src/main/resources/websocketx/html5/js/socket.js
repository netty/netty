(function() {
    var Sock = function() {
        var socket;
        if (!window.WebSocket) {
            window.WebSocket = window.MozWebSocket;
        }

        if (window.WebSocket) {
            socket = new WebSocket("ws://localhost:8080/websocket");
            socket.onopen = onopen;
            socket.onmessage = onmessage;
            socket.onclose = onclose;
        } else {
            alert("Your browser does not support Web Socket.");
        }

        function onopen(event) {
            getTextAreaElement().value = "Web Socket opened!";
        }

        function onmessage(event) {
            appendTextArea(event.data);
        }
        function onclose(event) {
            appendTextArea("Web Socket closed");
        }

        function appendTextArea(newData) {
            var el = getTextAreaElement();
            el.value = el.value + '\n' + newData;
        }

        function getTextAreaElement() {
            return document.getElementById('responseText');
        }

        function send(event) {
            event.preventDefault();
            if (window.WebSocket) {
                if (socket.readyState == WebSocket.OPEN) {
                    socket.send(event.target.message.value);
                } else {
                    alert("The socket is not open.");
                }
            }
        }
        document.forms.inputform.addEventListener('submit', send, false);
    }
    window.addEventListener('load', function() { new Sock(); }, false);
})();
