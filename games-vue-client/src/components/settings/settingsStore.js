export default {
  namespaced: true,
  state: {
    volume: parseInt(localStorage.volume || "42", 10),
    theme: localStorage.theme || ""
  },
  mutations: {
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
