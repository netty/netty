Date.prototype.format = function (format) {
    var o = {
        "M+": this.getMonth() + 1, // 月
        "d+": this.getDate(), // 日
        "h+": this.getHours(), // 时
        "m+": this.getMinutes(), // 分
        "s+": this.getSeconds(), // 秒
        "q+": Math.floor((this.getMonth() + 3) / 3), // 刻
        "S": this.getMilliseconds() // 毫秒
    }

    if (/(y+)/.test(format)) {
        format = format.replace(RegExp.$1, (this.getFullYear() + "").substr(4 - RegExp.$1.length));
    }

    for (var k in o) {
        if (new RegExp("(" + k + ")").test(format)) {
            format = format.replace(RegExp.$1, RegExp.$1.length == 1 ? o[k] : ("00" + o[k]).substr(("" + o[k]).length));
        }
    }
    return format;
};

$(document).ready(function () {
    var host = location.href.replace(/http:\/\//i, "");
    window.CHAT = {
        // 保存服务器端WebSocket的请求地址
        serverAddr: "ws://" + host + "im",
        // 保存用户输入的昵称
        nickname: null,
        // 保存浏览器socket对象
        socket: null,
        // 将滚动条设置到最顶部，以便能看到最新的消息
        scrollToBottom: function () {
            window.scrollTo(0, $("#onlinemsg")[0].scrollHeight);
        },
        // 登录到聊天室
        login: function () {
            $("#error-msg").empty();
            var _reg = /^\S{1,10}$/;
            var nickname = $("#nickname").val();
            if (nickname != "") {
                if (!(_reg.test($.trim(nickname)))) {
                    $('#error-msg').html("昵称长度必须在10个字以内");
                    return false;
                }
                $("#nickname").val('');
                $("#loginbox").hide();
                $("#chatbox").show();
                this.init(nickname);
            } else {
                $('#error-msg').html("先输入昵称才能进入聊天室");
                return false;
            }
            return false;
        },
        // 退出登录
        logout: function () {
            location.reload();
        },
        // 清空聊天记录
        clear: function () {
            CHAT.box.innerHTML = "";
        },
        // 发送聊天消息
        sendText: function () {
            var message = $("#send-message");
            // 去掉空格
            if (message.html().replace(/\s/ig, "") == "") {
                return;
            }
            if (!window.WebSocket) {
                return;
            }
            if (CHAT.socket.readyState == WebSocket.OPEN) {
                var msg = ("[CHAT][" + new Date().getTime() + "]" + "[" + CHAT.nickname + "] - " + message.html().replace(/\n/ig, "<br/>"));
                CHAT.socket.send(msg);
                message.empty();
                message.focus();
            } else {
                alert("与服务器连接失败.");
            }
        },
        // 发送鲜花
        sendFlower: function () {
            if (!window.WebSocket) {
                return;
            }
            if (CHAT.socket.readyState == WebSocket.OPEN) {
                var message = ("[FLOWER][" + new Date().getTime() + "]" + "[" + CHAT.nickname + "][WebSocket]");
                CHAT.socket.send(message);
                $("#send-message").focus();
            } else {
                alert("与服务器连接失败.");
            }
        },
        // 选择表情
        selectFace: function (img) {
            var faceBox = $("#face-box");
            faceBox.hide();
            faceBox.removeClass("open");
            var i = '<img src="' + img + '" />';
            $("#send-message").html($("#send-message").html() + i);
            $("#send-message").focus();
        },
        // 打开表情弹窗
        openFace: function (e) {
            var faceBox = $("#face-box");
            if (faceBox.hasClass("open")) {
                faceBox.hide();
                faceBox.removeClass("open");
                return;
            }
            faceBox.addClass("open");
            faceBox.show();
            var box = '';
            for (var i = 1; i <= 130; i++) {
                var img = '/images/face/' + i + '.gif';
                box += '<span class="face-item" onclick="CHAT.selectFace(\'' + img + '\');">';
                box += '<img src="' + img + '"/>';
                box += '</span>';
            }
            faceBox.html(box);
        },
        // 初始化聊天组件
        init: function (nickname) {
            var message = $("#send-message");
            // 自动获取焦点
            message.focus();
            // 按回车键自动发送
            message.keydown(function (e) {
                if ((e.ctrlKey && e.which == 13) || e.which == 10) {
                    CHAT.sendText();
                }
            });

            CHAT.nickname = nickname;

            $("#shownikcname").html(nickname);

            // 添加系统提示
            var addSystemTip = function (c) {
                var html = "";
                html += '<div class="msg-system">';
                html += c;
                html += '</div>';
                var section = document.createElement('section');
                section.className = 'system J-mjrlinkWrap J-cutMsg';
                section.innerHTML = html;

                $("#onlinemsg").append(section);
            };
            // 将消息添加到聊天面板
            var appendToPanel = function (message) {
                var regx = /^\[(.*)\](\s\-\s(.*))?/g;
                var group = '', label = "", content = "", cmd = "", time = 0, name = "";
                while (group = regx.exec(message)) {
                    label = group[1];
                    content = group[3];
                }
                var labelArr = label.split("][");
                cmd = labelArr[0];
                time = labelArr[1];
                name = labelArr[2];

                if (cmd == "SYSTEM") {
                    var total = labelArr[2];
                    $("#onlinecount").html("" + total);
                    addSystemTip(content);
                } else if (cmd == "CHAT") {
                    var date = new Date(parseInt(time));
                    addSystemTip('<span class="time-label">' + date.format("hh:mm:ss") + '</span>');
                    var isme = (name == "you") ? true : false;
                    var label = (isme ? '' : ('<span class="label">' + name + '</span>'));
                    var contentDiv = '<div>' + label + '<span class="content">' + content + '</span></div>';
                    var photoUrl = '/images/photos/' + (isme ? CHAT.nickname : name) + '.jpg';
                    var photoDiv = '<span><img class="photo" src="' + photoUrl + '"/></span>';

                    var section = document.createElement('section');
                    if (isme) {
                        section.className = 'user';
                        section.innerHTML = contentDiv + photoDiv;
                    } else {
                        section.className = 'service';
                        section.innerHTML = photoDiv + contentDiv;
                    }
                    $("#onlinemsg").append(section);
                } else if (cmd == "FLOWER") {
                    addSystemTip(content);
                    // 鲜花特效
                    $(document).snowfall('clear');
                    $(document).snowfall({
                        image: "/images/face/50.gif",
                        flakeCount: 60,
                        minSize: 20,
                        maxSize: 40
                    });
                    window.flowerTimer = window.setTimeout(function () {
                        $(document).snowfall('clear');
                        window.clearTimeout(flowerTimer);
                    }, 5000);

                }
                // 有新的消息过来以后，自动切到最底部
                CHAT.scrollToBottom();
            };


            if (!window.WebSocket) {
                window.WebSocket = window.MozWebSocket;
            }
            if (window.WebSocket) {
                CHAT.socket = new WebSocket(CHAT.serverAddr);
                CHAT.socket.onmessage = function (e) {
                    appendToPanel(e.data);
                };
                CHAT.socket.onopen = function (e) {
                    CHAT.socket.send("[LOGIN][" + new Date().getTime() + "][" + nickname + "][WebSocket]");
                };
                CHAT.socket.onclose = function (e) {
                    appendToPanel("[SYSTEM][" + new Date().getTime() + "][0] - 服务器关闭,暂不能聊天!");
                };
            } else {
                alert("你的浏览器不支持 WebSocket！");
            }
        }
    };
});
