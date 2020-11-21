<template>
  <v-row>
    <v-col cols="1">
      <v-checkbox
        v-model="hideAIUsers"
        label="Hide AI Users"
        @change="toggleAIUsers()"
      />
    </v-col>
    <v-col
      cols="11"
      sm="6"
      md="2"
    />
  </v-row>
</template>
<script>
export default {
    name: "LobbyOptions",
    data() {
      return {
        hideAIUsers: false
      }
    },
    mounted() {
      if (localStorage.hideAIUsers && localStorage.hideAIUsers === 'true' != this.hideAIUsers) {
        this.hideAIUsers = localStorage.hideAIUsers === 'true';
      }
    },
    methods: {
      toggleAIUsers() {
        this.$store.commit("lobby/setLobbyUsersWithOptions", { hideAIUsers: !!this.hideAIUsers });
      },
    },    
    watch: {
      hideAIUsers(newHideAIUsers) {
        localStorage.hideAIUsers = newHideAIUsers;
      }
    }
}
</script>