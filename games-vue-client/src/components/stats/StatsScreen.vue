<template>
    <v-container fluid>
        <v-row dense>
            <v-col cols="12">
                <v-btn @click="query()" />
            </v-col>
        </v-row>
        <v-row dense v-for="game in gameList" :key="game.gameId">
            <v-col cols="12">
                <v-card>
                    <v-card-title class="subheading font-weight-bold">
                        <router-link :to="`/stats/games/${game.gameId}/replay`">{{ game.gameId }}</router-link>
                    </v-card-title>

                    <v-divider />
                    <v-list>
                        <v-list-item
                            v-for="(player, index) in game.players"
                            :key="index"
                        >
                            <v-list-item-content>{{ player.playerId }}</v-list-item-content>
                            <v-list-item-content class="align-end">{{ player.resultPosition }}</v-list-item-content>
                        </v-list-item>
                    </v-list>
                    <v-divider />
                    <v-list>
                        <v-list-item
                            v-for="(tag, index) in game.tags"
                            :key="index"
                        >
                            <v-list-item-content>{{ tag.tagId }}</v-list-item-content>
                            <v-list-item-content class="align-end">{{ tag.tagParameter }}</v-list-item-content>
                        </v-list-item>
                    </v-list>
                </v-card>
            </v-col>
        </v-row>

        <!--
            Show games from <timeperiod> (24 hours)
            
            Search player name

            Query based on tags and players


            For each game show: Players playing, tags.
        -->
    </v-container>
</template>
<script>
import axios from "axios"

export default {
    name: "StatsScreen",
    props: ["players", "tags"],
    data() {
        return {
            gameList: [],
            playerConditions: this.players ? this.players : "",
            tagConditions: this.tags ? this.tags : ""
        }
    },
    methods: {
        query() {
            let cleanURI = encodeURI(`${this.baseURL}stats/query?players=${this.playerConditions}&tags=${this.tagConditions}`).replace(/#/g, '%23')
            axios.get(cleanURI).then(response => {
                console.log(response.data)
                this.gameList = response.data
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