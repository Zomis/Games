<template>
  <v-container
    fluid
    class="game-menu"
  >
    <v-row>
      <v-col
        v-for="gameType in availableGames"
        :key="gameType"
        cols="12"
        md="6"
        lg="4"
      >
        <v-card class="games">
          <v-toolbar
            color="cyan"
            dark
          >
            <v-toolbar-title>{{ displayNames[gameType] }}</v-toolbar-title>
            <v-spacer />
            <router-link :to="'/local/' + gameType">
              <v-btn rounded>
                Play
              </v-btn>
            </router-link>
          </v-toolbar>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>
<script>
import supportedGames from "@/supportedGames"

export default {
  name: "PlayLocalGame",
  props: ["gameInfo", "showRules"],
  data() {
    return {
      availableGames: supportedGames.enabledGameKeys().filter(game => supportedGames.games[game].dsl)
    }
  },
  methods: {
  },
  computed: {
    displayNames() {
      let games = {};
      supportedGames.enabledGameKeys().forEach(gameType => {
        games[gameType] = supportedGames.games[gameType].displayName ? supportedGames.games[gameType].displayName : gameType;
      });
      return games
    }
  }
}
</script>