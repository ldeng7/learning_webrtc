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

    this.onAddLocalStream = null
    this.onAddRemoteStream = null
    this.onStop = null
  }

  wsSend(phase, data) {
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

  start() {
    let conf = {
      audio: true,
      video: { width: { max: 480 } },
    }
    navigator.mediaDevices.getUserMedia(conf)
      .then((stream) => {
        this.onLoadLocalMedia(stream)
      }).catch(logErr("failed to load local media:"))
  }

  onLoadLocalMedia(stream) {
    this.stream = stream
    this.onAddLocalStream(stream)

    let wsConn = new WebSocket(this.conf.wsServer)
    this.wsConn = wsConn
    wsConn.onopen = () => {
      this.wsSend(WebRTCClient.WS_PHASE_DIAL, { uid: this.conf.localUid, ruid: this.conf.remoteUid })
    }
    wsConn.onmessage = (msg) => {
      let resp = JSON.parse(msg.data)
      resp.data = JSON.parse(resp.data)
      WebRTCClient.wsHandlers[resp.phase].call(this, resp)
    }
    wsConn.onclose = () => {
      this.stop()
    }
  }

  onWsRecvDial(resp) {
    if (true !== resp.success) {
      this.stop()
      alert(resp.msg)
      return
    }

    let peerConn = new RTCPeerConnection({ iceServers: this.conf.iceServers })
    this.peerConn = peerConn
    peerConn.addStream(this.stream)
    peerConn.onicecandidate = (ev) => {
      if (ev.candidate) {
        this.wsSend(WebRTCClient.WS_PHASE_CAND, ev.candidate)
      }
    }
    peerConn.onaddstream = (ev) => {
      this.onAddRemoteStream(ev.stream)
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
  document.getElementById("button-stop").onclick = onButtonStopClick
  let webRTCClient = new WebRTCClient
  window.webRTCClient = webRTCClient
  webRTCClient.onAddLocalStream = (stream) => {
    document.getElementById("video-local").srcObject = stream
  }
  webRTCClient.onAddRemoteStream = (stream) => {
    document.getElementById("video-remote").srcObject = stream
    document.getElementById("button-stop").disabled = false
  }
  webRTCClient.onStop = onStop
}

let onButtonStartClick = () => {
  let conf = {
    localUid: document.getElementById("input-lu").value,
    remoteUid: document.getElementById("input-ru").value,
    wsServer: document.getElementById("input-ws").value,
    stunServer: document.getElementById("input-ss").value,
    turnServer: document.getElementById("input-ts").value,
    turnUser: document.getElementById("input-tu").value,
    turnCredential: document.getElementById("input-tc").value,
  }
  let ret = window.webRTCClient.setConf(conf)
  if (true !== ret) {
    alert(ret)
    return
  }

  for (let e of document.getElementsByClassName("input-conf")) {
    e.disabled = true
  }
  document.getElementById("button-start").disabled = true
  window.webRTCClient.start()
}

let onButtonStopClick = () => {
  window.webRTCClient.stop()
}

let onStop = () => {
  for (let e of document.getElementsByClassName("input-conf")) {
    e.disabled = false
  }
  document.getElementById("button-stop").disabled = true
  document.getElementById("button-start").disabled = false
}
