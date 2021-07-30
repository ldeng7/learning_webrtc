package main

import (
	"encoding/json"
	"net/http"
	"os"
	"time"

	"github.com/gorilla/websocket"
)

const (
	PHASE_DIAL = iota + 1
	PHASE_OFFER
	PHASE_ANSWER
	PHASE_CANDIDATE
	PHASE_STOP
)

type Req struct {
	Phase int
	Data  string
}

type DialReqData struct {
	Uid  string
	Ruid string
}

type Resp struct {
	Phase   int    `json:"phase"`
	Success bool   `json:"success"`
	Message string `json:"message"`
	Data    string `json:"data"`
}

type Handler struct {
	userConns map[*User]*websocket.Conn
	upgrader  *websocket.Upgrader
}

func NewHandler() *Handler {
	return &Handler{
		userConns: map[*User]*websocket.Conn{},
		upgrader: &websocket.Upgrader{
			CheckOrigin: func(_ *http.Request) bool { return true },
		},
	}
}

func send(conn *websocket.Conn, resp *Resp) {
	if err := conn.WriteJSON(resp); err != nil {
		println(err.Error())
	}
}

func sendJson(conn *websocket.Conn, phase int, success bool, msg string, json string) {
	resp := Resp{phase, success, msg, json}
	send(conn, &resp)
}

func sendData(conn *websocket.Conn, phase int, success bool, msg string, data interface{}) {
	json, _ := json.Marshal(data)
	resp := Resp{phase, success, msg, string(json)}
	send(conn, &resp)
}

func (h *Handler) closeUser(uPtr, pPtr **User) {
	u, p := *uPtr, *pPtr
	if nil == u {
		return
	}

	users.DeleteUserById(u.Id)
	if conn := h.userConns[u]; nil != conn {
		conn.Close()
	}
	delete(h.userConns, u)

	if nil != p {
		if pConn := h.userConns[p]; nil != pConn {
			sendData(pConn, PHASE_STOP, true, "", nil)
		}
	}
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	conn, err := h.upgrader.Upgrade(w, r, nil)
	if nil != err {
		println(err.Error())
		return
	}
	var u, p *User
	defer h.closeUser(&u, &p)

	for {
		var req Req
		err := conn.ReadJSON(&req)
		if nil != err {
			if _, ok := err.(*websocket.CloseError); ok {
				break
			}
			println(err.Error())
			time.Sleep(time.Second)
			continue
		}

		switch req.Phase {
		case PHASE_DIAL:
			data := DialReqData{}
			if err := json.Unmarshal([]byte(req.Data), &data); nil != err {
				continue
			}
			u = users.FindOrCreateUserById(data.Uid)
			switch u.State {
			case USER_STATE_IDLE:
				p = users.FindOrCreateUserById(data.Ruid)
				if p.State != USER_STATE_IDLE {
					p = nil
					sendData(conn, req.Phase, false, "invalid remote uid", nil)
					continue
				}
				u.State, p.State = USER_STATE_DIALING, USER_STATE_DIALING
				u.PartnerId, p.PartnerId = data.Ruid, data.Uid
				h.userConns[u] = conn
			case USER_STATE_DIALING:
				if data.Ruid != u.PartnerId {
					sendData(conn, req.Phase, false, "invalid remote uid", nil)
					continue
				}
				p = users.FindOrCreateUserById(data.Ruid)
				u.State, p.State = USER_STATE_BUSY, USER_STATE_BUSY
				h.userConns[u] = conn
				sendData(conn, req.Phase, true, "", false)
				sendData(h.userConns[p], req.Phase, true, "", true)
			default:
				sendData(conn, req.Phase, false, "invalid local uid", nil)
			}
		case PHASE_OFFER, PHASE_ANSWER, PHASE_CANDIDATE:
			if nil == p || nil == h.userConns[p] {
				sendData(conn, req.Phase, false, "incorrect phase", nil)
				continue
			}
			sendJson(h.userConns[p], req.Phase, true, "", req.Data)
		}
	}
}

func main() {
	h := NewHandler()
	if len(os.Args) > 4 {
		go func() {
			err := http.ListenAndServe(os.Args[4], h)
			if nil != err {
				println(err.Error())
				return
			}
		}()
	}
	err := http.ListenAndServeTLS(os.Args[1], os.Args[2], os.Args[3], h)
	if nil != err {
		println(err.Error())
		return
	}
}
