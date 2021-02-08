<template>
  <v-menu transition="slide-y-transition" offset-y>
    <template v-slot:activator="{ on }">
      <v-btn v-on="on" text>
        <v-icon color="black">mdi-cog</v-icon>
      </v-btn>
    </template>
    <v-list>
      <v-slider
        v-show="false"
        class="slider"
        v-model="volume"
        prepend-icon="mdi-volume-high"
        @click:prepend="mute"
      />
      <v-checkbox v-show="false" v-model="theme" value="dark" label="Dark mode" />
      <v-btn @click="testGame('DSL-TTT')">Test game</v-btn>
      <v-checkbox
        v-model="hideAIUsers"
        label="Hide AI Users"
      />
      <v-checkbox
        v-model="playSoundOnPlayerTurn"
        label="Pling on my turn"
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
      previousVolume: volume
    };
  },
  computed: {
    hideAIUsers: {
      get() {
        return this.$store.state.settings.hideAIUsers;
      },
      set(value) {
        this.$store.commit("settings/set", { key: "hideAIUsers", value: value ? "true" : "" });
        this.$store.commit("lobby/setLobbyUsersWithOptions", { hideAIUsers: !!this.hideAIUsers });
      }
    },
    playSoundOnPlayerTurn: {
      get() {
        return this.$store.state.settings.playSoundOnPlayerTurn;
      },
      set(value) {
        this.$store.commit("settings/set", { key: "playSoundOnPlayerTurn", value: value ? "true" : "" });
      }
    },
    volume: {
      get() {
        return this.$store.state.settings.volume;
      },
      set(value) {
        this.$store.commit("settings/setVolume", value);
      }
    },
    theme: {
      get() {
        return this.$store.state.settings.theme;
      },
      set(value) {
        this.$store.commit("settings/setTheme", value);
      }
    }
  },
  methods: {
    mute() {
      let previousVolume = this.volume;
      this.volume = this.volume === 0 ? this.previousVolume : 0;
      this.previousVolume = previousVolume;
    }
  }
};
</script>
<style scoped>
.slider {
  margin-right: 12px;
}
</style>
