<template>
  <v-container
    fluid
    class="game-menu"
  >
    <v-data-table
      :headers="headers"
      :items="games"
      :items-per-page="20"
      hide-default-footer
      class="elevation-1"
    >
      <template v-slot:[`body.prepend`]>
        <tr>
          <td />
          <td>
            <v-text-field
              v-model="amountOfPlayers"
              type="number"
              label="Players"
            />
          </td>
          <td>
            <v-text-field
              v-model="playTime"
              type="number"
              label="Play Time"
            />
          </td>
          <td />
        </tr>
      </template>
      <template v-slot:[`item.actions`]="{ item }">
        <v-btn
          rounded
          @click="testGame(item.gameType)"
        >
          Try it
        </v-btn>
        <v-btn
          rounded
          @click="createInvite(item.gameType)"
        >
          New Game
        </v-btn>
      </template>
    </v-data-table>
  </v-container>
</template>
<script>
import Socket from "@/socket";
import supportedGames from "@/supportedGames"

export default {
  name: "LobbyCompactList",
  props: [],
  data() {
    const enabledGames = supportedGames.enabledGameKeys().filter(game => supportedGames.games[game].dsl);
    return {
      amountOfPlayers: '',
      playTime: '',
      headers: [
        { 
          text: 'Game',
          value: 'displayName'
        },
        { 
          text: 'Players',
          value: 'amountOfPlayers',
          width: '150',
          filter: value => {
            if (!this.amountOfPlayers) {
              return true;
            }

            const [minPlayers = 0, maxPlayers = minPlayers] = value.split('-');
            const query = parseInt(this.amountOfPlayers);
            return query >= parseInt(minPlayers) && query <= maxPlayers;
          },
        },
        { 
          text: 'Playing Time',
          value: 'playTime',
          width: '150',
          filter: value => {
            const [minPlayTime = 0, maxPlayTime = minPlayTime] = value.split('-');

            if (!this.playTime || isNaN(minPlayTime)) {
              return true;
            }

            const query = parseInt(this.playTime);
            return query >= parseInt(minPlayTime) && query <= maxPlayTime;
          },
        },
        {
          text: 'Actions',
          value: 'actions',
          sortable: false,
          width: '300',
        },
      ],
      games: enabledGames.map((gameType) => {
        const { displayName = gameType, playTime = 'xx', amountOfPlayers = '-' } = supportedGames?.games[gameType];
        return {
            gameType,
            displayName,
            playTime,
            amountOfPlayers,
        }
      }),
    }
  },
  methods: {
    testGame(gameType) {
      Socket.route("testGames/game", { gameType: gameType })
    },
    createInvite(gameType) {
      Socket.route("invites/prepare", { gameType: gameType })
    },
  },
  computed: {
  },
  created() {
    // if (!Socket.isConnected()) {
    //   this.$router.push("/login");
    //   return;
    // }
  },
}
</script>