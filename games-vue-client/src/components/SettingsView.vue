<template>
  <v-menu
    transition="slide-y-transition"
    offset-y
  >
    <template v-slot:activator="{ on }">
      <v-btn
        text
        v-on="on"
      >
        <v-icon color="black">
          mdi-cog
        </v-icon>
      </v-btn>
    </template>
    <v-list>
      <v-slider
        v-show="false"
        v-model="volume"
        class="slider"
        prepend-icon="mdi-volume-high"
        @click:prepend="mute"
      />
      <v-checkbox
        v-show="true"
        v-model="theme"
        value="dark"
        label="Dark mode"
        prepend-icon="mdi-theme-light-dark"
      />
      <v-checkbox
        v-model="hideAIUsers"
        label="Hide AI Users"
      />
      <v-checkbox
        v-model="playSoundOnPlayerTurn"
        label="Pling on my turn"
      />
      <v-checkbox
        v-model="compactList"
        label="Compact"
        prepend-icon="mdi-view-list"
      />
      <v-checkbox
        v-model="woodBackground"
        label="Wood"
        prepend-icon="mdi-image-text"
      />
    </v-list>
  </v-menu>
</template>
<script>
export default {
  name: "SettingsView",
  data() {
    let volume = parseInt(localStorage.volume || "42", 10);
    return {
      previousVolume: volume,
    };
  },
  computed: {
    hideAIUsers: {
      get() {
        return this.$store.state.settings.hideAIUsers;
      },
      set(value) {
        this.$store.commit("settings/set", {
          key: "hideAIUsers",
          value: value ? "true" : "",
        });
        this.$store.commit("lobby/setLobbyUsersWithOptions", {
          hideAIUsers: !!this.hideAIUsers,
        });
      },
    },
    playSoundOnPlayerTurn: {
      get() {
        return this.$store.state.settings.playSoundOnPlayerTurn;
      },
      set(value) {
        this.$store.commit("settings/set", {
          key: "playSoundOnPlayerTurn",
          value: value ? "true" : "",
        });
      },
    },
    compactList: {
      get() {
        return this.$store.state.settings.compactList;
      },
      set(value) {
        this.$store.commit("settings/set", {
          key: "compactList",
          value: value ? "true" : "",
        });
      },
    },
    woodBackground: {
      get() {
        return this.$store.state.settings.background;
      },
      set(value) {
        this.$store.commit("settings/set", {
          key: "background",
          value: value ? "woodBackground" : "",
        });
      },
    },
    volume: {
      get() {
        return this.$store.state.settings.volume;
      },
      set(value) {
        this.$store.commit("settings/setVolume", value);
      },
    },
    theme: {
      get() {
        return this.$store.state.settings.theme;
      },
      set(value) {
        this.$store.commit("settings/setTheme", value);
        this.$vuetify.theme.dark = !this.$vuetify.theme.dark;
      },
    },
  },
  methods: {
    mute() {
      let previousVolume = this.volume;
      this.volume = this.volume === 0 ? this.previousVolume : 0;
      this.previousVolume = previousVolume;
    },
  },
};
</script>
<style scoped>
.slider {
  margin-right: 12px;
}
</style>
