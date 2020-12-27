<template>
  <v-container fluid>
    <v-row dense>
      <v-col cols="4">
        <v-text-field
          v-model="playerConditions"
          label="Player Ids"
        />
      </v-col>
      <v-col cols="2">
        <v-select
          v-model="tagConditions"
          :items="supportedGames"
          item-text="text"
          item-value="value"
          label="Game"
        />
      </v-col>
      <v-col cols="2">
        <v-btn @click="applyFilter()">
          Apply filter
        </v-btn>
      </v-col>
    </v-row>
    <v-row
      v-for="game in gameList"
      :key="game.gameId"
      dense
    >
      <v-col cols="12">
        <v-card>
          <v-card-title class="subheading font-weight-bold">
            <v-icon color="blue">
              mdi-replay
            </v-icon>
            <router-link :to="`/stats/games/${game.gameId}/replay`">
              {{ game.title }}
            </router-link>
          </v-card-title>

          <v-divider />
          <v-list>
            <v-list-item
              v-for="(player, index) in game.players"
              :key="index"
            >
              <v-list-item-content>{{ player.playerId }}</v-list-item-content>
              <v-list-item-content class="align-end">
                {{ player.resultPosition }}
              </v-list-item-content>
            </v-list-item>
          </v-list>
          <v-divider />
          <v-list>
            <v-list-item
              v-for="(tag, index) in game.tags"
              :key="index"
            >
              <v-list-item-content>{{ tag.tagId }}</v-list-item-content>
              <v-list-item-content class="align-end">
                {{ tag.tagParameter }}
              </v-list-item-content>
            </v-list-item>
          </v-list>
        </v-card>
      </v-col>
    </v-row>

    <!--
            Show games from <timeperiod> (24 hours)
                - How to query on a time period?
            
            Search player name
                - How to search and list a player's name?

            Query based on tags and players
                - Tags - success!

            For each game show: Players playing, tags.
        -->
  </v-container>
</template>
<script>
import axios from "axios"
import supportedGames from "@/supportedGames"

const buildTitleFromGame = (game) => {
    const titleTag = game.tags.reduce((acc, { tagId }) => {
        const pre = acc.length > 0 ? ' ' : '';
        const tagName = tagId.substring(5, tagId.length);

        return `${pre}${acc} ${tagName}`;
    }, '');
    const shortGameId = game.gameId.substring(0, 8);

    return `${titleTag} - ${shortGameId}`;
}

const appendedData = (data) => data.map((game) => {
    return { ...game, title: buildTitleFromGame(game) };
});

export default {
    name: "StatsScreen",
    props: ["players", "tags"],
    data() {
        return {
            supportedGames: [
                { text: '', value: '' },
                ...supportedGames.enabledGamesTextValue()
            ],
            gameList: [],
            playerConditions: this.players ? this.players : "",
            tagConditions: this.tags ? this.tags : "",
        }
    },
    created() {
        this.query();
    },
    methods: {
        applyFilter() {
            this.$router.push(`/stats?players=${this.playerConditions}&tags=${this.tagConditions}`);
            this.query();
        },
        query() {
            const cleanURI = encodeURI(`${this.baseURL}stats/query?players=${this.playerConditions}&tags=${this.tagConditions}`).replace(/#/g, '%23')
            axios.get(cleanURI).then(response => {
                this.gameList = appendedData(response.data);
            })
        }
    },
    computed: {
        baseURL() {
            return process.env.VUE_APP_URL
        }
    }
}
</script>