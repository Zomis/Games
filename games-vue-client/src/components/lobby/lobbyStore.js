import Vue from "vue";

import Socket from "@/socket";

function findInvite(state, inviteId) {
  if (state.complexInvite && state.complexInvite.inviteWaiting && state.complexInvite.inviteWaiting.inviteId === inviteId) {
    console.log("Found complexInvite", state.complexInvite);
    return state.complexInvite.inviteWaiting;
  }
  if (state.inviteWaiting.inviteId === inviteId) {
    console.log("Found invite waiting", state.inviteWaiting);
    return state.inviteWaiting;
  } else {
    let result = state.invites.find(i => i.inviteId === inviteId);
    console.log("Found invite", result);
    return result;
  }
}

function emptyInvite() {
  return {
    dialogActive: false,
    waitingFor: [],
    inviteId: null,
    cancelled: false,
    accepted: [],
    declined: []
  };
}

const lobbyStore = {
  namespaced: true,
  state: {
    complexInvite: null,
    invites: [],
    inviteWaiting: emptyInvite(),
    lobby: {} // key: gameType, value: array of players (names)
  },
  getters: {},
  mutations: {
    setInviteWaiting(state, e) {
      if (state.complexInvite) {
        state = state.complexInvite;
      }
      state.inviteWaiting = e;
      state.inviteWaiting.dialogActive = true
      Vue.set(state.inviteWaiting, "cancelled", false);
      Vue.set(state.inviteWaiting, "accepted", []);
      Vue.set(state.inviteWaiting, "declined", []);
    },
    setLobbyUsers(state, data) {
        state.lobby = data;
    },
    inviteResponseReceived(state, e) {
      // This should only happen to the inviteWaiting at the moment
      let invite = findInvite(state, e.inviteId);
      invite.waitingFor.splice(invite.waitingFor.indexOf(e.user), 1);
      let responseArray = e.accepted ? invite.accepted : invite.declined;
      responseArray.push(e.user);
    },
    inviteReceived(state, e) {
      this.$set(e, "cancelled", false);
      this.$set(e, "response", null);
      this.invites.push(e);
    },
    cancelInvite(state, e) {
      // This can be either an invite recieved or the inviteWaiting
      let invite = this.findInvite(e.inviteId);
      invite.cancelled = true;
    },
    resetInviteWaiting(state) {
      state.inviteWaiting = emptyInvite();
      state.inviteWaiting = {
        waitingFor: [],
        inviteId: null,
        cancelled: false,
        accepted: [],
        declined: []
      };
    },
    setComplexInvite(state, gameType) {
      state.complexInvite = { gameType: gameType };
    }
  },
  actions: {
    createInvite(context, gameType) {
      context.commit("setComplexInvite", gameType);
      Socket.route("invites/start", { gameType: gameType });
    },
    onSocketMessage(context, data) {
      if (data.type == "InviteWaiting") {
        context.commit("setInviteWaiting", data);
      }
      if (data.type == "InviteResponse") {
        context.commit("inviteResponseReceived", data);
      }
      if (data.type == "InviteCancelled") {
        context.commit("cancelInvite", data);
      }
      if (data.type == "Invite") {
        context.commit("inviteReceived", data);
      }
      if (data.type == "GameStarted") {
        context.commit("resetInviteWaiting");
      }
    }
  }
};

export default lobbyStore;
