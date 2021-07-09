import Vue from "vue";

export default {
  namespaced: true,
  state: {
    playSoundOnPlayerTurn: localStorage.playSoundOnPlayerTurn === "true",
    hideAIUsers: localStorage.hideAIUsers === "true",
    volume: parseInt(localStorage.volume || "42", 10),
    theme: localStorage.theme || "",
    background: localStorage.background || false
  },
  mutations: {
    set(state, data) {
      console.log("Settings: Set", data);
      let key = data.key;
      let value = data.value;
      Vue.set(state, key, value);
      state[key] = value;
      localStorage[key] = value;
    },
    setVolume(state, value) {
      state.volume = value;
      localStorage.volume = value;
    },
    setTheme(state, value) {
      state.theme = value;
      localStorage.theme = value;
    }
  },
  actions: {}
};
