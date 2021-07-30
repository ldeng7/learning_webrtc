package main

const (
	USER_STATE_IDLE = iota
	USER_STATE_DIALING
	USER_STATE_BUSY
)

type User struct {
	Id        string
	State     int
	PartnerId string
}

type Users map[string]*User

func (us Users) FindOrCreateUserById(id string) *User {
	u := us[id]
	if nil == u {
		u = &User{Id: id, State: USER_STATE_IDLE}
		us[id] = u
	}
	return u
}

func (us Users) DeleteUserById(id string) {
	delete(us, id)
}

var users = Users{}
