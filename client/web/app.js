let logErr = (title) => {
  return (err) => {
    console.log(title)
    console.log(err)
  }
}

class WebRTCClient {
  static WS_PHASE_DIAL  = 1
  static WS_PHASE_OFFER = 2
  static WS_PHASE_ANS   = 3
  static WS_PHASE_CAND  = 4
  static WS_PHASE_STOP  = 5
  static wsHandlers = {
    [WebRTCClient.WS_PHASE_DIAL]:  WebRTCClient.prototype.onWsRecvDial,
    [WebRTCClient.WS_PHASE_OFFER]: WebRTCClient.prototype.onWsRecvOffer,
    [WebRTCClient.WS_PHASE_ANS]:   WebRTCClient.prototype.onWsRecvAns,
    [WebRTCClient.WS_PHASE_CAND]:  WebRTCClient.prototype.onWsRecvCand,
    [WebRTCClient.WS_PHASE_STOP]:  WebRTCClient.prototype.onWsRecvStop,
  }

  constructor() {
    this.conf = null
    this.stream = null
    this.wsConn = null
    this.peerConn = null
    this.dataChan = null

    this.onConnected = null
    this.onDataChanMsg = null
    this.onStop = null
  }

  wsSend(phase, data) {
    console.log("send phase " + phase)
    console.log(data)
    let req = {
      phase: phase,
      data: JSON.stringify(data),
    }
    this.wsConn.send(JSON.stringify(req))
  }

  onCreateSdFactory(phase) {
    return (sdp) => {
      this.peerConn.setLocalDescription(sdp)
      this.wsSend(phase, sdp)
    }
  }

  setConf(conf) {
    if (!conf.localUid || !conf.remoteUid) {
      return "invalid uid"
    }
    if (!conf.stunServer && !conf.turnServer) {
      return "invalid stun/turn server"
    }

    let iceServers = []
    if (conf.stunServer) {
      iceServers.push({ urls: conf.stunServer })
    }
    if (conf.turnServer) {
      let e = { urls: conf.turnServer }
      if (conf.turnUser) {
        e.username = conf.turnUser
        e.credential = conf.turnCredential
      }
      iceServers.push(e)
    }
    conf.iceServers = iceServers

    this.conf = conf
    return true
  }

  start(conf, localVideo, remoteVideo) {
    let ret = this.setConf(conf)
    if (true !== ret) {
      return ret
    }

    this.localVideo = localVideo
    this.remoteVideo = remoteVideo
    let constraints = {}
    constraints.audio = true
    if (!conf.noVideo) {
      constraints.video = { width: { max: 480 } }
    }
    navigator.mediaDevices.getUserMedia(constraints)
      .then((stream) => {
        this.onLoadLocalMedia(stream)
      }).catch(logErr("failed to load local media:"))
    return true
  }

  onLoadLocalMedia(stream) {
    this.stream = stream
    if (!this.conf.noVideo) {
      this.localVideo.srcObject = stream
    }

    let wsConn = new WebSocket(this.conf.wsServer)
    this.wsConn = wsConn
    wsConn.onopen = () => {
      this.wsSend(WebRTCClient.WS_PHASE_DIAL, { uid: this.conf.localUid, ruid: this.conf.remoteUid })
    }
    wsConn.onmessage = (msg) => {
      let resp = JSON.parse(msg.data)
      resp.data = JSON.parse(resp.data)
      console.log("recv phase " + resp.phase)
      console.log(resp.data)
      WebRTCClient.wsHandlers[resp.phase].call(this, resp)
    }
    wsConn.onclose = () => {
      this.stop()
    }
  }

  onWsRecvDial(resp) {
    if (true !== resp.success) {
      alert(resp.msg)
      this.stop()
      return
    }

    let peerConn = new RTCPeerConnection({ iceServers: this.conf.iceServers })
    this.peerConn = peerConn
    peerConn.onicecandidate = (ev) => {
      if (ev.candidate) {
        this.wsSend(WebRTCClient.WS_PHASE_CAND, ev.candidate)
      }
    }
    peerConn.onaddstream = (ev) => {
      if (!this.conf.noVideo) {
        this.remoteVideo.srcObject = ev.stream
      }
      this.onConnected()
    }

    peerConn.addStream(this.stream)
    this.dataChan = peerConn.createDataChannel("dc", { negotiated: true, id: 1 })
    this.dataChan.onmessage = (ev) => {
      this.onDataChanMsg(ev.data)
    }

    if (true !== resp.data) {
      return
    }
    peerConn.createOffer(this.onCreateSdFactory(WebRTCClient.WS_PHASE_OFFER),
      logErr("failed to create offer:"))
  }

  onWsRecvOffer(resp) {
    this.peerConn.setRemoteDescription(new RTCSessionDescription(resp.data))
    this.peerConn.createAnswer(this.onCreateSdFactory(WebRTCClient.WS_PHASE_ANS),
      logErr("failed to create answer:"))
  }

  onWsRecvAns(resp) {
    this.peerConn.setRemoteDescription(new RTCSessionDescription(resp.data))
  }

  onWsRecvCand(resp) {
    this.peerConn.addIceCandidate(new RTCIceCandidate(resp.data))
  }

  onWsRecvStop(resp) {
    this.stop()
  }

  dataChanSend(msg) {
    this.dataChan.send(msg)
  }

  stop() {
    if (this.wsConn) {
      this.wsConn.close()
      this.wsConn = null
    }
    if (this.peerConn) {
      this.peerConn.close()
      this.peerConn = null
    }
    this.onStop()
  }
}

window.onload = () => {
  document.getElementById("button-start").onclick = onButtonStartClick
  document.getElementById("button-dc-send").onclick = onButtonDcSendClick
  document.getElementById("button-stop").onclick = onButtonStopClick

  let webRTCClient = new WebRTCClient
  window.webRTCClient = webRTCClient
  webRTCClient.onConnected = () => {
    document.getElementById("button-stop").disabled = false
    document.getElementById("button-dc-send").disabled = false
  }
  webRTCClient.onDataChanMsg = (msg) => {
    document.getElementById("text-dc").textContent = msg
  }
  webRTCClient.onStop = () => {
    document.getElementById("button-start").disabled = false
    document.getElementById("button-dc-send").disabled = true
    document.getElementById("button-stop").disabled = true
  }
}

let onButtonStartClick = () => {
  document.getElementById("button-start").disabled = true
  let conf = {
    localUid: document.getElementById("input-local-uid").value,
    remoteUid: document.getElementById("input-remote-uid").value,
    wsServer: document.getElementById("input-ws-server").value,
    stunServer: document.getElementById("input-stun-server").value,
    turnServer: document.getElementById("input-turn-server").value,
    turnUser: document.getElementById("input-turn-user").value,
    turnCredential: document.getElementById("input-turn-cred").value,
    noVideo: document.getElementById("check-no-video").checked,
  }
  let ret = window.webRTCClient.start(conf,
    document.getElementById("video-local"), document.getElementById("video-remote"))
  if (true !== ret) {
    document.getElementById("button-start").disabled = false
    alert(ret)
  }
}

let onButtonDcSendClick = () => {
  window.webRTCClient.dataChanSend(document.getElementById("input-dc").value)
}

let onButtonStopClick = () => {
  window.webRTCClient.stop()
}
