<template>
  <v-row>
    <v-col cols="2">
      <v-checkbox
        v-model="hideAIUsers"
        label="Hide AI Users"
        @change="toggleAIUsers()"
      />
    </v-col>
    <v-col cols="2">
      <v-checkbox
        v-model="playSoundOnPlayerTurn"
        label="Pling on my turn"
      />
    </v-col>
    <v-col
      cols="8"
      sm="4"
      md="2"
    />
  </v-row>
</template>
<script>
export default {
    name: "LobbyOptions",
    data() {
      return {
        hideAIUsers: false,
        playSoundOnPlayerTurn: false
      }
    },
    mounted() {
      if (localStorage.hideAIUsers && localStorage.hideAIUsers === 'true' != this.hideAIUsers) {
        this.hideAIUsers = localStorage.hideAIUsers === 'true';
      }
      if (localStorage.playSoundOnPlayerTurn && localStorage.playSoundOnPlayerTurn === 'true' != this.playSoundOnPlayerTurn) {
        this.playSoundOnPlayerTurn = localStorage.playSoundOnPlayerTurn === 'true';
      }
    },
    methods: {
      toggleAIUsers() {
        this.$store.commit("lobby/setLobbyUsersWithOptions", { hideAIUsers: !!this.hideAIUsers });
      }
    },    
    watch: {
      hideAIUsers(newHideAIUsers) {
        localStorage.hideAIUsers = newHideAIUsers;
      },
      playSoundOnPlayerTurn(newPlaySoundOnPlayerTurn) {
        localStorage.playSoundOnPlayerTurn = newPlaySoundOnPlayerTurn;
      }
    }
}
</script>